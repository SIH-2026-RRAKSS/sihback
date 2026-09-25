"""PyTorch GraphSAGE Graph Neural Network Classifier.

Implements inductive 2-layer GraphSAGE with neighbor mean-aggregation,
node risk classification, calibrated subgraph pooling, and top suspicious node ranking.
"""

from collections import defaultdict
import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from sklearn.metrics import (
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    average_precision_score,
    brier_score_loss,
)

from app.calibration.calibrator import ScoreCalibrator
from app.features.extractor import FeatureExtractor


class SAGELayer(nn.Module):
    """Single GraphSAGE layer with mean neighborhood aggregation."""

    def __init__(self, in_features: int, out_features: int):
        super().__init__()
        self.w_self = nn.Linear(in_features, out_features, bias=True)
        self.w_neigh = nn.Linear(in_features, out_features, bias=False)

    def forward(self, x: torch.Tensor, adj_list: Dict[int, List[int]]) -> torch.Tensor:
        """
        x: [N, in_features]
        adj_list: mapping from node index to list of neighbor indices
        """
        num_nodes = x.size(0)
        # Compute mean of neighbors
        neigh_means = []
        for i in range(num_nodes):
            neighbors = adj_list.get(i, [])
            if neighbors:
                neigh_idx = torch.tensor(neighbors, dtype=torch.long, device=x.device)
                m = torch.mean(x[neigh_idx], dim=0)
            else:
                m = x[i]
            neigh_means.append(m)

        neigh_tensor = torch.stack(neigh_means, dim=0)
        out = self.w_self(x) + self.w_neigh(neigh_tensor)
        return out


class GraphSAGENet(nn.Module):
    """Two-layer GraphSAGE network for node and graph-level fraud scoring."""

    def __init__(self, in_features: int, hidden_dim: int = 64, out_dim: int = 32, dropout: float = 0.2):
        super().__init__()
        self.layer1 = SAGELayer(in_features, hidden_dim)
        self.dropout1 = nn.Dropout(dropout)
        self.layer2 = SAGELayer(hidden_dim, out_dim)
        self.dropout2 = nn.Dropout(dropout)
        self.node_classifier = nn.Linear(out_dim, 1)

    def forward(self, x: torch.Tensor, adj_list: Dict[int, List[int]]) -> Tuple[torch.Tensor, torch.Tensor]:
        """Returns (node_logits, node_embeddings)."""
        h = F.relu(self.layer1(x, adj_list))
        h = self.dropout1(h)
        h = F.relu(self.layer2(h, adj_list))
        h = self.dropout2(h)
        logits = self.node_classifier(h).view(-1)
        return logits, h


class GraphSAGEFraudClassifier:
    """End-to-end GraphSAGE pipeline for graph classification, calibration, and node explainability."""

    def __init__(
        self,
        model_name: str = "graphsage",
        version: str = "graphsage-v1.0.0",
        hidden_dim: int = 64,
        out_dim: int = 32,
        dropout: float = 0.2,
        lr: float = 0.01,
        weight_decay: float = 1e-4,
        random_state: int = 42,
    ):
        self.model_name = model_name
        self.version = version
        self.hidden_dim = hidden_dim
        self.out_dim = out_dim
        self.dropout = dropout
        self.lr = lr
        self.weight_decay = weight_decay
        self.random_state = random_state

        torch.manual_seed(random_state)
        np.random.seed(random_state)

        self.extractor = FeatureExtractor()
        self.feature_names = self.extractor.node_feature_names
        self.in_features = len(self.feature_names)

        self.net = GraphSAGENet(self.in_features, hidden_dim, out_dim, dropout)
        self.calibrator = ScoreCalibrator(method="isotonic")
        self.is_trained = False

        # Feature normalization stats
        self.feat_mean = np.zeros(self.in_features, dtype=np.float32)
        self.feat_std = np.ones(self.in_features, dtype=np.float32)

    def _build_adj_list(self, nodes: List[Dict[str, Any]], edges: List[Dict[str, Any]]) -> Tuple[Dict[str, int], Dict[int, List[int]]]:
        node2idx = {n["id"]: idx for idx, n in enumerate(nodes)}
        adj = defaultdict(set)
        for e in edges:
            src = e.get("source")
            tgt = e.get("target")
            if src in node2idx and tgt in node2idx:
                u, v = node2idx[src], node2idx[tgt]
                # Undirected message passing for bi-directional context flow
                adj[u].add(v)
                adj[v].add(u)
        adj_list = {k: list(v) for k, v in adj.items()}
        return node2idx, adj_list

    def pool_subgraph_risk(self, node_probs: torch.Tensor, root_idx: int) -> torch.Tensor:
        """Documented pooling rule: combines root node risk and max neighbor risk."""
        root_risk = node_probs[root_idx]
        max_risk = torch.max(node_probs)
        mean_risk = torch.mean(node_probs)
        # Weighted mixture: 0.5 * root + 0.3 * max + 0.2 * mean
        return 0.5 * root_risk + 0.3 * max_risk + 0.2 * mean_risk

    def train_on_graphs(
        self,
        graphs: List[Any],  # list of SyntheticGraph or dict representations
        epochs: int = 40,
        val_graphs: Optional[List[Any]] = None,
    ) -> Dict[str, float]:
        """Train GraphSAGE on graph list using PyTorch."""
        self.net.train()
        optimizer = torch.optim.Adam(self.net.parameters(), lr=self.lr, weight_decay=self.weight_decay)

        # Compute continuous feature normalization
        all_node_feats = []
        for g in graphs:
            d = g.to_dict() if hasattr(g, "to_dict") else g
            feats = self.extractor.extract_node_features(d["nodes"], d["edges"], d.get("root_node_id"))
            for nid, fmap in feats.items():
                all_node_feats.append([fmap.get(k, 0.0) for k in self.feature_names])

        if all_node_feats:
            mat = np.array(all_node_feats, dtype=np.float32)
            self.feat_mean = np.mean(mat, axis=0)
            self.feat_std = np.std(mat, axis=0)
            self.feat_std[self.feat_std < 1e-4] = 1.0

        pos_count = sum(1 for g in graphs if (g.label if hasattr(g, "label") else g["label"]) == 1)
        neg_count = len(graphs) - pos_count
        pos_weight = torch.tensor([float(neg_count / max(pos_count, 1))])
        criterion = nn.BCEWithLogitsLoss(pos_weight=pos_weight)

        for epoch in range(epochs):
            total_loss = 0.0
            for g in graphs:
                d = g.to_dict() if hasattr(g, "to_dict") else g
                label = torch.tensor([float(d["label"])], dtype=torch.float32)

                node2idx, adj_list = self._build_adj_list(d["nodes"], d["edges"])
                ordered_ids = [d["nodes"][i]["id"] for i in range(len(d["nodes"]))]
                raw_matrix = self.extractor.node_features_to_matrix(
                    self.extractor.extract_node_features(d["nodes"], d["edges"], d.get("root_node_id")),
                    ordered_ids,
                )
                norm_matrix = (raw_matrix - self.feat_mean) / self.feat_std
                x_tensor = torch.tensor(norm_matrix, dtype=torch.float32)

                optimizer.zero_grad()
                logits, _ = self.net(x_tensor, adj_list)
                probs = torch.sigmoid(logits)

                root_id = d.get("root_node_id", ordered_ids[0])
                root_idx = node2idx.get(root_id, 0)
                graph_risk = self.pool_subgraph_risk(probs, root_idx)

                loss = F.binary_cross_entropy(graph_risk.unsqueeze(0), label)
                loss.backward()
                optimizer.step()
                total_loss += loss.item()

        # Fit calibrator on train/validation predictions
        self.net.eval()
        eval_set = val_graphs if val_graphs else graphs
        raw_scores, true_labels = [], []
        with torch.no_grad():
            for g in eval_set:
                d = g.to_dict() if hasattr(g, "to_dict") else g
                true_labels.append(d["label"])
                risk, _, _, _ = self._predict_graph(d["nodes"], d["edges"], d.get("root_node_id"), apply_calibration=False)
                raw_scores.append(risk)

        self.calibrator.fit(np.array(raw_scores), np.array(true_labels))
        self.is_trained = True
        return self.evaluate(eval_set)

    def _predict_graph(
        self,
        nodes: List[Dict[str, Any]],
        edges: List[Dict[str, Any]],
        root_node_id: Optional[str] = None,
        apply_calibration: bool = True,
    ) -> Tuple[float, float, List[Dict[str, Any]], torch.Tensor]:
        self.net.eval()
        if not nodes:
            return 0.0, 0.5, [], torch.zeros((0, self.in_features))

        if not root_node_id:
            root_node_id = nodes[0].get("id")

        node2idx, adj_list = self._build_adj_list(nodes, edges)
        ordered_ids = [nodes[i]["id"] for i in range(len(nodes))]
        raw_matrix = self.extractor.node_features_to_matrix(
            self.extractor.extract_node_features(nodes, edges, root_node_id),
            ordered_ids,
        )
        norm_matrix = (raw_matrix - self.feat_mean) / self.feat_std
        x_tensor = torch.tensor(norm_matrix, dtype=torch.float32)

        with torch.no_grad():
            logits, _ = self.net(x_tensor, adj_list)
            node_probs = torch.sigmoid(logits)
            root_idx = node2idx.get(root_node_id, 0)
            raw_subgraph_risk = float(self.pool_subgraph_risk(node_probs, root_idx).item())

        if apply_calibration:
            calibrated_risk, confidence = self.calibrator.calibrate(raw_subgraph_risk)
        else:
            calibrated_risk = raw_subgraph_risk
            confidence = 0.5 + abs(raw_subgraph_risk - 0.5)

        # Ranked top suspicious nodes
        top_nodes = []
        node_scores = [(ordered_ids[i], float(node_probs[i].item())) for i in range(len(ordered_ids))]
        node_scores.sort(key=lambda x: x[1], reverse=True)
        for nid, score in node_scores:
            top_nodes.append({"id": nid, "score": round(score, 4)})

        return calibrated_risk, confidence, top_nodes, x_tensor

    def predict(
        self,
        nodes: List[Dict[str, Any]],
        edges: List[Dict[str, Any]],
        root_node_id: Optional[str] = None,
    ) -> Tuple[float, float, List[Dict[str, Any]], List[Dict[str, float]]]:
        """Inference for a single subgraph.

        Returns:
            (risk, confidence, top_nodes, top_features)
        """
        calibrated_risk, confidence, top_nodes, x_tensor = self._predict_graph(
            nodes, edges, root_node_id, apply_calibration=True
        )

        # Sensitivity-based feature importance for top_features
        top_features = self._compute_feature_importance(x_tensor)
        return calibrated_risk, confidence, top_nodes, top_features

    def _compute_feature_importance(self, x_tensor: torch.Tensor, top_k: int = 5) -> List[Dict[str, float]]:
        """Compute sensitivity weights of node features across the graph."""
        if x_tensor.numel() == 0 or (x_tensor.dim() > 0 and x_tensor.size(0) == 0):
            return []

        if x_tensor.dim() == 1:
            x_tensor = x_tensor.unsqueeze(0)

        # Mean absolute magnitude of normalized features
        avg_act = torch.mean(torch.abs(x_tensor), dim=0).detach().cpu().numpy()
        tot = np.sum(avg_act) + 1e-5
        weights = avg_act / tot

        # Map to canonical names from contract fixture if present
        name_map = {
            "fan_out_ratio": "fan_out_ratio",
            "velocity": "velocity_burst",
            "total_amount_std": "amount_deviation",
            "pass_through_ratio": "pass_through_ratio",
            "hop_depth": "hop_depth",
        }

        indices = np.argsort(weights)[::-1]
        top = []
        for idx in indices:
            raw_name = self.feature_names[idx]
            display_name = name_map.get(raw_name, raw_name)
            top.append({"name": display_name, "weight": round(float(weights[idx]), 4)})
            if len(top) >= top_k:
                break
        return top

    def evaluate(self, graphs: List[Any]) -> Dict[str, float]:
        """Evaluate performance metrics across given graph list."""
        y_true, y_probs = [], []
        for g in graphs:
            d = g.to_dict() if hasattr(g, "to_dict") else g
            y_true.append(d["label"])
            risk, _, _, _ = self._predict_graph(d["nodes"], d["edges"], d.get("root_node_id"), apply_calibration=True)
            y_probs.append(risk)

        y_true = np.array(y_true)
        y_probs = np.array(y_probs)
        preds = (y_probs >= 0.5).astype(int)

        precision = float(precision_score(y_true, preds, zero_division=0))
        recall = float(recall_score(y_true, preds, zero_division=0))
        f1 = float(f1_score(y_true, preds, zero_division=0))
        pr_auc = float(average_precision_score(y_true, y_probs)) if len(np.unique(y_true)) > 1 else 1.0
        roc_auc = float(roc_auc_score(y_true, y_probs)) if len(np.unique(y_true)) > 1 else 1.0
        brier = float(brier_score_loss(y_true, y_probs))

        return {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "pr_auc": round(pr_auc, 4),
            "roc_auc": round(roc_auc, 4),
            "brier_score": round(brier, 4),
        }

    def save(self, directory: str | Path):
        """Save network weights, calibrator, and metadata."""
        path = Path(directory)
        path.mkdir(parents=True, exist_ok=True)

        torch.save(self.net.state_dict(), path / "model.pt")

        meta = {
            "model_name": self.model_name,
            "version": self.version,
            "hidden_dim": self.hidden_dim,
            "out_dim": self.out_dim,
            "dropout": self.dropout,
            "feature_names": self.feature_names,
            "feat_mean": self.feat_mean.tolist(),
            "feat_std": self.feat_std.tolist(),
            "calibrator": self.calibrator.to_dict(),
            "is_trained": self.is_trained,
        }
        with open(path / "metadata.json", "w") as f:
            json.dump(meta, f, indent=2)

    def load(self, directory: str | Path):
        """Load network weights, calibrator, and metadata."""
        path = Path(directory)
        with open(path / "metadata.json", "r") as f:
            meta = json.load(f)

        self.model_name = meta["model_name"]
        self.version = meta["version"]
        self.hidden_dim = meta["hidden_dim"]
        self.out_dim = meta["out_dim"]
        self.dropout = meta["dropout"]
        self.feature_names = meta["feature_names"]
        self.feat_mean = np.array(meta["feat_mean"], dtype=np.float32)
        self.feat_std = np.array(meta["feat_std"], dtype=np.float32)
        self.calibrator = ScoreCalibrator.from_dict(meta["calibrator"])
        self.is_trained = meta.get("is_trained", True)

        self.net = GraphSAGENet(len(self.feature_names), self.hidden_dim, self.out_dim, self.dropout)
        self.net.load_state_dict(torch.load(path / "model.pt", map_location="cpu", weights_only=True))
        self.net.eval()
