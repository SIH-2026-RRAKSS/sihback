"""Feature extraction for transaction subgraphs and nodes.

Extracts node-level and graph-level temporal and structural features.
Features are versioned with the model service.
"""

from collections import defaultdict
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple
import numpy as np


class FeatureExtractor:
    """Extracts topological, temporal, and flow features from transaction subgraphs."""

    def __init__(self):
        # Canonical ordered feature names for node vectors
        self.node_feature_names: List[str] = [
            "in_degree",
            "out_degree",
            "degree",
            "fan_in_ratio",
            "fan_out_ratio",
            "in_amount_sum",
            "in_amount_mean",
            "in_amount_std",
            "out_amount_sum",
            "out_amount_mean",
            "out_amount_std",
            "total_amount_sum",
            "total_amount_mean",
            "total_amount_std",
            "velocity",
            "pass_through_ratio",
            "hop_depth",
        ]

        # Canonical ordered feature names for graph-level vectors (used by XGBoost)
        self.graph_feature_names: List[str] = [
            "num_nodes",
            "num_edges",
            "density",
            "total_volume",
            "avg_amount",
            "max_amount",
            "std_amount",
            "root_in_degree",
            "root_out_degree",
            "root_degree",
            "fan_out_ratio",
            "velocity_burst",
            "amount_deviation",
            "pass_through_ratio",
            "max_fan_out",
            "time_span_seconds",
        ]

    def _parse_timestamp(self, ts: Any) -> datetime:
        if isinstance(ts, datetime):
            return ts
        if isinstance(ts, str):
            clean_ts = ts.replace("Z", "+00:00")
            return datetime.fromisoformat(clean_ts)
        raise ValueError(f"Unsupported timestamp format: {ts}")

    def extract_node_features(
        self,
        nodes: List[Dict[str, Any]],
        edges: List[Dict[str, Any]],
        root_node_id: Optional[str] = None,
    ) -> Dict[str, Dict[str, float]]:
        """Extract rich node-level features for every node in the subgraph."""
        if not root_node_id and nodes:
            root_node_id = nodes[0].get("id")

        node_ids = [n.get("id") for n in nodes]
        in_amounts: Dict[str, List[float]] = defaultdict(list)
        out_amounts: Dict[str, List[float]] = defaultdict(list)
        in_times: Dict[str, List[datetime]] = defaultdict(list)
        out_times: Dict[str, List[datetime]] = defaultdict(list)

        # Adjacency for BFS hop depth
        adj = defaultdict(set)
        for e in edges:
            src = e.get("source")
            tgt = e.get("target")
            amt = float(e.get("amount", 0.0))
            ts = self._parse_timestamp(e.get("timestamp"))

            out_amounts[src].append(amt)
            out_times[src].append(ts)
            in_amounts[tgt].append(amt)
            in_times[tgt].append(ts)
            adj[src].add(tgt)
            adj[tgt].add(src)

        # BFS for hop depth from root
        hop_depths: Dict[str, float] = {}
        if root_node_id:
            queue = [(root_node_id, 0.0)]
            visited = {root_node_id}
            while queue:
                curr, depth = queue.pop(0)
                hop_depths[curr] = depth
                for neighbor in adj[curr]:
                    if neighbor not in visited:
                        visited.add(neighbor)
                        queue.append((neighbor, depth + 1.0))

        # Build feature maps per node
        results: Dict[str, Dict[str, float]] = {}
        for n in nodes:
            nid = n.get("id")
            # If features are pre-supplied, use them as base
            existing_feats = n.get("features", {})

            in_deg = float(len(in_amounts[nid]))
            out_deg = float(len(out_amounts[nid]))
            deg = in_deg + out_deg

            # Prefer existing features if present in request
            if "in_degree" in existing_feats:
                in_deg = float(existing_feats["in_degree"])
            if "out_degree" in existing_feats:
                out_deg = float(existing_feats["out_degree"])
            if "degree" in existing_feats:
                deg = float(existing_feats["degree"])

            fan_in = in_deg / (deg + 1e-5)
            fan_out = out_deg / (deg + 1e-5)

            in_sum = float(sum(in_amounts[nid]))
            in_mean = float(np.mean(in_amounts[nid])) if in_amounts[nid] else 0.0
            in_std = float(np.std(in_amounts[nid])) if in_amounts[nid] else 0.0

            out_sum = float(sum(out_amounts[nid]))
            out_mean = float(np.mean(out_amounts[nid])) if out_amounts[nid] else 0.0
            out_std = float(np.std(out_amounts[nid])) if out_amounts[nid] else 0.0

            all_amounts = in_amounts[nid] + out_amounts[nid]
            tot_sum = float(sum(all_amounts))
            tot_mean = float(np.mean(all_amounts)) if all_amounts else 0.0
            tot_std = float(np.std(all_amounts)) if all_amounts else 0.0

            if "amount_mean" in existing_feats:
                tot_mean = float(existing_feats["amount_mean"])
            if "amount_std" in existing_feats:
                tot_std = float(existing_feats["amount_std"])

            # Pass-through ratio: min(in, out) / max(in, out)
            max_flow = max(in_sum, out_sum)
            pass_through = (min(in_sum, out_sum) / (max_flow + 1e-5)) if max_flow > 0 else 0.0

            # Velocity: total transactions associated
            vel = float(existing_feats.get("velocity", deg))

            # Hop depth
            depth = existing_feats.get("hop_depth", hop_depths.get(nid, 1.0))

            results[nid] = {
                "in_degree": in_deg,
                "out_degree": out_deg,
                "degree": deg,
                "fan_in_ratio": round(fan_in, 4),
                "fan_out_ratio": round(fan_out, 4),
                "in_amount_sum": round(in_sum, 2),
                "in_amount_mean": round(in_mean, 2),
                "in_amount_std": round(in_std, 2),
                "out_amount_sum": round(out_sum, 2),
                "out_amount_mean": round(out_mean, 2),
                "out_amount_std": round(out_std, 2),
                "total_amount_sum": round(tot_sum, 2),
                "total_amount_mean": round(tot_mean, 2),
                "total_amount_std": round(tot_std, 2),
                "velocity": round(vel, 2),
                "pass_through_ratio": round(pass_through, 4),
                "hop_depth": round(float(depth), 2),
            }

        return results

    def extract_graph_features(
        self,
        nodes: List[Dict[str, Any]],
        edges: List[Dict[str, Any]],
        root_node_id: Optional[str] = None,
    ) -> Dict[str, float]:
        """Extract graph-level tabular features for tabular / GBDT models."""
        num_nodes = float(len(nodes))
        num_edges = float(len(edges))

        if not root_node_id and nodes:
            root_node_id = nodes[0].get("id")

        node_feats = self.extract_node_features(nodes, edges, root_node_id=root_node_id)
        amounts = [float(e.get("amount", 0.0)) for e in edges]

        total_vol = float(sum(amounts))
        avg_amt = float(np.mean(amounts)) if amounts else 0.0
        max_amt = float(np.max(amounts)) if amounts else 0.0
        std_amt = float(np.std(amounts)) if amounts else 0.0

        # Graph density: E / (V * (V - 1))
        density = (num_edges / (num_nodes * (num_nodes - 1))) if num_nodes > 1 else 0.0

        # Root node stats
        root_feats = node_feats.get(root_node_id, {})
        root_in = root_feats.get("in_degree", 0.0)
        root_out = root_feats.get("out_degree", 0.0)
        root_deg = root_feats.get("degree", 0.0)
        root_fan_out = root_feats.get("fan_out_ratio", 0.0)
        root_pass_through = root_feats.get("pass_through_ratio", 0.0)

        # Max fan out across all nodes
        max_fan_out = max((f.get("fan_out_ratio", 0.0) for f in node_feats.values()), default=0.0)
        max_vel = max((f.get("velocity", 0.0) for f in node_feats.values()), default=0.0)

        # Time span calculation
        timestamps = [self._parse_timestamp(e.get("timestamp")) for e in edges if "timestamp" in e]
        if timestamps:
            time_span = max((max(timestamps) - min(timestamps)).total_seconds(), 1.0)
        else:
            time_span = 3600.0

        # Burstiness: velocity / time span in hours
        velocity_burst = (max_vel / (time_span / 3600.0 + 1e-5)) if time_span > 0 else 0.0

        # Amount deviation: std / (mean + 1e-5)
        amount_deviation = (std_amt / (avg_amt + 1e-5)) if avg_amt > 0 else 0.0

        return {
            "num_nodes": num_nodes,
            "num_edges": num_edges,
            "density": round(density, 4),
            "total_volume": round(total_vol, 2),
            "avg_amount": round(avg_amt, 2),
            "max_amount": round(max_amt, 2),
            "std_amount": round(std_amt, 2),
            "root_in_degree": root_in,
            "root_out_degree": root_out,
            "root_degree": root_deg,
            "fan_out_ratio": round(root_fan_out, 4),
            "velocity_burst": round(velocity_burst, 4),
            "amount_deviation": round(amount_deviation, 4),
            "pass_through_ratio": round(root_pass_through, 4),
            "max_fan_out": round(max_fan_out, 4),
            "time_span_seconds": round(time_span, 2),
        }

    def node_features_to_matrix(
        self,
        node_features_map: Dict[str, Dict[str, float]],
        ordered_node_ids: List[str],
    ) -> np.ndarray:
        """Convert node features to 2D numpy matrix [num_nodes, num_features]."""
        matrix = []
        for nid in ordered_node_ids:
            feats = node_features_map.get(nid, {})
            row = [feats.get(name, 0.0) for name in self.node_feature_names]
            matrix.append(row)
        return np.array(matrix, dtype=np.float32)

    def graph_features_to_vector(self, graph_features: Dict[str, float]) -> np.ndarray:
        """Convert graph features to 1D numpy vector."""
        row = [graph_features.get(name, 0.0) for name in self.graph_feature_names]
        return np.array(row, dtype=np.float32)
