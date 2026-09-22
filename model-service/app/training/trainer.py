"""Training and Evaluation Pipeline for Candidate Models.

Trains candidates from snapshot files or benchmark datasets, compares
candidate metrics against active baseline on held-out splits, and registers candidates.
"""

from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
from sklearn.model_selection import train_test_split

from app.features.extractor import FeatureExtractor
from app.models.graphsage import GraphSAGEFraudClassifier
from app.models.xgboost_model import XGBoostFraudClassifier
from app.registry.registry import registry
from app.synthetic.mule_generator import SyntheticMuleGenerator


class ModelTrainer:
    """Orchestrates candidate model training and comparative evaluation."""

    def __init__(self, seed: int = 42):
        self.seed = seed
        self.extractor = FeatureExtractor()

    def load_snapshot(self, snapshot_path: str | Path) -> List[Dict[str, Any]]:
        """Load de-identified graphs from snapshot file."""
        path = Path(snapshot_path)
        if not path.exists():
            raise FileNotFoundError(f"Snapshot not found at {path}")

        graphs = []
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    graphs.append(json.loads(line))
        return graphs

    def train_candidate(
        self,
        model_name: str,
        version: str,
        snapshot_path: Optional[str] = None,
        use_synthetic_data: bool = True,
        num_synthetic_samples: int = 150,
    ) -> Dict[str, Any]:
        """Train and evaluate candidate model against current active model."""
        model_name = model_name.lower()
        if model_name not in ["graphsage", "xgboost"]:
            raise ValueError(f"Unsupported model type '{model_name}'")

        graphs = []
        if snapshot_path:
            graphs.extend(self.load_snapshot(snapshot_path))

        if use_synthetic_data or not graphs:
            gen = SyntheticMuleGenerator(seed=self.seed)
            syn_graphs = gen.generate_dataset(num_samples=num_synthetic_samples, fraud_ratio=0.35)
            graphs.extend([g.to_dict() for g in syn_graphs])

        # Hold-out test split (25%)
        train_graphs, test_graphs = train_test_split(graphs, test_size=0.25, random_state=self.seed)

        # 1. Train candidate
        if model_name == "xgboost":
            candidate = XGBoostFraudClassifier(model_name=model_name, version=version, random_state=self.seed)
            X_train = np.array([
                self.extractor.graph_features_to_vector(
                    self.extractor.extract_graph_features(g["nodes"], g["edges"], g.get("root_node_id"))
                )
                for g in train_graphs
            ])
            y_train = np.array([g["label"] for g in train_graphs])

            X_test = np.array([
                self.extractor.graph_features_to_vector(
                    self.extractor.extract_graph_features(g["nodes"], g["edges"], g.get("root_node_id"))
                )
                for g in test_graphs
            ])
            y_test = np.array([g["label"] for g in test_graphs])

            metrics = candidate.train(X_train, y_train, X_val=X_test, y_val=y_test)
            card = {
                "model_name": model_name,
                "version": version,
                "architecture": "XGBoost GBDT",
                "features": candidate.feature_names,
                "metrics": metrics,
                "sample_size": len(graphs),
            }
        else:
            candidate = GraphSAGEFraudClassifier(model_name=model_name, version=version, random_state=self.seed)
            metrics = candidate.train_on_graphs(train_graphs, epochs=35, val_graphs=test_graphs)
            card = {
                "model_name": model_name,
                "version": version,
                "architecture": "2-Layer Inductive GraphSAGE (Mean Aggregator)",
                "pooling_rule": "0.5 * root + 0.3 * max + 0.2 * mean",
                "metrics": metrics,
                "sample_size": len(graphs),
            }

        # 2. Compare against active model if present
        active_version = registry.get_active_version(model_name)
        active_metrics = None
        beats_active = True
        if active_version:
            try:
                active_model = registry.get_model(model_name, active_version)
                if model_name == "xgboost":
                    active_metrics = active_model.evaluate(X_test, y_test)
                else:
                    active_metrics = active_model.evaluate(test_graphs)
                beats_active = metrics["f1"] >= active_metrics.get("f1", 0.0)
            except Exception:
                pass

        card["comparison"] = {
            "active_version": active_version,
            "active_metrics": active_metrics,
            "beats_active": beats_active,
        }

        # Save as candidate (not active until approved/promoted)
        registry.save_model(candidate, card, is_active=False)

        return {
            "model_name": model_name,
            "version": version,
            "status": "CANDIDATE",
            "candidate_metrics": metrics,
            "active_metrics": active_metrics,
            "beats_active": beats_active,
        }
