"""Model Registry and Model Card Management.

Manages artifact versioning, active model pointers, model cards,
and in-memory model caching for inference.
"""

from datetime import datetime, timezone
import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

from app.core.config import settings
from app.models.graphsage import GraphSAGEFraudClassifier
from app.models.xgboost_model import XGBoostFraudClassifier
from app.synthetic.mule_generator import SyntheticMuleGenerator


class ModelRegistry:
    """Manages versioned models, model cards, and active version pointers."""

    def __init__(self, artifacts_dir: Optional[str | Path] = None):
        if artifacts_dir is None:
            artifacts_dir = Path(settings.ARTIFACTS_DIR)
        self.artifacts_dir = Path(artifacts_dir)
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.active_file = self.artifacts_dir / "active_models.json"

        # In-memory cache: (model_name, version) -> model instance
        self._cache: Dict[str, Union[GraphSAGEFraudClassifier, XGBoostFraudClassifier]] = {}

    def _load_active_map(self) -> Dict[str, str]:
        if not self.active_file.exists():
            return {}
        try:
            with open(self.active_file, "r") as f:
                return json.load(f)
        except Exception:
            return {}

    def _save_active_map(self, active_map: Dict[str, str]):
        with open(self.active_file, "w") as f:
            json.dump(active_map, f, indent=2)

    def get_active_version(self, model_name: str) -> Optional[str]:
        """Get the active version tag for a model name."""
        return self._load_active_map().get(model_name.lower())

    def set_active_version(self, model_name: str, version: str) -> bool:
        """Set a version as active and retire previously active version."""
        model_name = model_name.lower()
        active_map = self._load_active_map()
        prev_version = active_map.get(model_name)

        model_dir = self.artifacts_dir / model_name / version
        if not model_dir.exists():
            raise FileNotFoundError(f"Version {version} does not exist for model {model_name}")

        # Update model cards status
        if prev_version and prev_version != version:
            prev_card_path = self.artifacts_dir / model_name / prev_version / "model_card.json"
            if prev_card_path.exists():
                with open(prev_card_path, "r") as f:
                    card = json.load(f)
                card["status"] = "RETIRED"
                with open(prev_card_path, "w") as f:
                    json.dump(card, f, indent=2)

        card_path = model_dir / "model_card.json"
        if card_path.exists():
            with open(card_path, "r") as f:
                card = json.load(f)
            card["status"] = "ACTIVE"
            card["activated_at"] = datetime.now(timezone.utc).isoformat()
            with open(card_path, "w") as f:
                json.dump(card, f, indent=2)

        active_map[model_name] = version
        self._save_active_map(active_map)
        return True

    def get_model(
        self,
        model_name: str,
        version: Optional[str] = None,
    ) -> Union[GraphSAGEFraudClassifier, XGBoostFraudClassifier]:
        """Retrieve model instance from cache or disk."""
        model_name = model_name.lower()
        if not version:
            version = self.get_active_version(model_name)
            if not version:
                raise ValueError(f"No active version found for model '{model_name}'.")

        cache_key = f"{model_name}:{version}"
        if cache_key in self._cache:
            return self._cache[cache_key]

        model_dir = self.artifacts_dir / model_name / version
        if not model_dir.exists():
            raise FileNotFoundError(f"Model path {model_dir} does not exist.")

        if model_name == "graphsage":
            classifier = GraphSAGEFraudClassifier(model_name=model_name, version=version)
            classifier.load(model_dir)
        elif model_name == "xgboost":
            classifier = XGBoostFraudClassifier(model_name=model_name, version=version)
            classifier.load(model_dir)
        else:
            raise ValueError(f"Unsupported model type '{model_name}'")

        self._cache[cache_key] = classifier
        return classifier

    def save_model(
        self,
        classifier: Union[GraphSAGEFraudClassifier, XGBoostFraudClassifier],
        model_card: Dict[str, Any],
        is_active: bool = False,
    ):
        """Save a trained model and its model card."""
        model_name = classifier.model_name.lower()
        version = classifier.version
        target_dir = self.artifacts_dir / model_name / version
        target_dir.mkdir(parents=True, exist_ok=True)

        classifier.save(target_dir)

        model_card["status"] = "ACTIVE" if is_active else "CANDIDATE"
        model_card["saved_at"] = datetime.now(timezone.utc).isoformat()
        with open(target_dir / "model_card.json", "w") as f:
            json.dump(model_card, f, indent=2)

        if is_active:
            self.set_active_version(model_name, version)

        # Update cache
        self._cache[f"{model_name}:{version}"] = classifier

    def get_model_card(self, model_name: str, version: Optional[str] = None) -> Dict[str, Any]:
        """Fetch model card for a specific model version."""
        model_name = model_name.lower()
        if not version:
            version = self.get_active_version(model_name)
            if not version:
                raise ValueError(f"No active version for model '{model_name}'")

        card_path = self.artifacts_dir / model_name / version / "model_card.json"
        if not card_path.exists():
            raise FileNotFoundError(f"Model card not found at {card_path}")

        with open(card_path, "r") as f:
            return json.load(f)

    def list_models(self) -> List[Dict[str, Any]]:
        """List all models, versions, statuses, and performance metrics."""
        results = []
        active_map = self._load_active_map()

        for model_dir in self.artifacts_dir.iterdir():
            if not model_dir.is_dir():
                continue
            model_name = model_dir.name
            for ver_dir in model_dir.iterdir():
                if not ver_dir.is_dir():
                    continue
                card_path = ver_dir / "model_card.json"
                if card_path.exists():
                    try:
                        with open(card_path, "r") as f:
                            card = json.load(f)
                        is_active = (active_map.get(model_name) == ver_dir.name)
                        card["is_active"] = is_active
                        if is_active:
                            card["status"] = "ACTIVE"
                        results.append(card)
                    except Exception:
                        pass
        return results

    def bootstrap_if_empty(self):
        """Bootstrap initial v1.0.0 models for GraphSAGE and XGBoost if not present."""
        active_map = self._load_active_map()
        needs_bootstrap = ("graphsage" not in active_map) or ("xgboost" not in active_map)
        if not needs_bootstrap:
            return

        print("Bootstrapping baseline models (GraphSAGE & XGBoost v1.0.0)...")
        gen = SyntheticMuleGenerator(seed=42)
        dataset = gen.generate_dataset(num_samples=120, fraud_ratio=0.35)
        train_set = dataset[:80]
        test_set = dataset[80:]

        # 1. Train & Register XGBoost
        if "xgboost" not in active_map:
            xgb_clf = XGBoostFraudClassifier(model_name="xgboost", version="xgboost-v1.0.0", random_state=42)
            from app.features.extractor import FeatureExtractor
            extractor = FeatureExtractor()

            X_train = np.array([
                extractor.graph_features_to_vector(
                    extractor.extract_graph_features(g.to_dict()["nodes"], g.to_dict()["edges"], g.root_node_id)
                )
                for g in train_set
            ])
            y_train = np.array([g.label for g in train_set])

            X_test = np.array([
                extractor.graph_features_to_vector(
                    extractor.extract_graph_features(g.to_dict()["nodes"], g.to_dict()["edges"], g.root_node_id)
                )
                for g in test_set
            ])
            y_test = np.array([g.label for g in test_set])

            metrics = xgb_clf.train(X_train, y_train, X_val=X_test, y_val=y_test)
            xgb_card = {
                "model_name": "xgboost",
                "version": "xgboost-v1.0.0",
                "architecture": "Gradient Boosted Decision Trees (XGBoost)",
                "features": xgb_clf.feature_names,
                "metrics": metrics,
                "dataset": "synthetic_benchmark_seed42",
                "sample_size": len(dataset),
            }
            self.save_model(xgb_clf, xgb_card, is_active=True)

        # 2. Train & Register GraphSAGE
        if "graphsage" not in active_map:
            sage_clf = GraphSAGEFraudClassifier(model_name="graphsage", version="graphsage-v1.0.0", random_state=42)
            metrics = sage_clf.train_on_graphs(train_set, epochs=30, val_graphs=test_set)
            sage_card = {
                "model_name": "graphsage",
                "version": "graphsage-v1.0.0",
                "architecture": "2-Layer Inductive GraphSAGE (Mean Aggregator)",
                "pooling_rule": "0.5 * root_node_risk + 0.3 * max_neighbor_risk + 0.2 * mean_neighbor_risk",
                "metrics": metrics,
                "dataset": "synthetic_benchmark_seed42",
                "sample_size": len(dataset),
            }
            self.save_model(sage_clf, sage_card, is_active=True)

        print("Baseline models bootstrapped successfully.")


registry = ModelRegistry()
