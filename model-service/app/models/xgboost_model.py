"""XGBoost Baseline Model for Graph Fraud Detection.

Uses tabular graph-level and root-node features with class-imbalance weighting,
probability calibration, and feature importance explainability.
"""

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
from sklearn.metrics import (
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    average_precision_score,
    brier_score_loss,
)
import xgboost as xgb

from app.calibration.calibrator import ScoreCalibrator
from app.features.extractor import FeatureExtractor


class XGBoostFraudClassifier:
    """XGBoost classifier for subgraph fraud risk assessment."""

    def __init__(
        self,
        model_name: str = "xgboost",
        version: str = "xgboost-v1.0.0",
        random_state: int = 42,
    ):
        self.model_name = model_name
        self.version = version
        self.random_state = random_state
        self.extractor = FeatureExtractor()
        self.feature_names = self.extractor.graph_feature_names

        self.model: Optional[xgb.XGBClassifier] = None
        self.calibrator = ScoreCalibrator(method="isotonic")
        self.is_trained = False

    def train(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
        scale_pos_weight: Optional[float] = None,
    ) -> Dict[str, float]:
        """Train XGBoost model and fit score calibrator."""
        if scale_pos_weight is None:
            num_pos = np.sum(y_train == 1)
            num_neg = np.sum(y_train == 0)
            scale_pos_weight = float(num_neg / max(num_pos, 1))

        self.model = xgb.XGBClassifier(
            n_estimators=100,
            max_depth=4,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            scale_pos_weight=scale_pos_weight,
            eval_metric="logloss",
            random_state=self.random_state,
        )

        eval_set = [(X_train, y_train)]
        if X_val is not None and y_val is not None:
            eval_set.append((X_val, y_val))

        self.model.fit(
            X_train,
            y_train,
            eval_set=eval_set,
            verbose=False,
        )

        # Fit calibrator on validation or training predictions
        if X_val is not None and y_val is not None:
            raw_val_probs = self.model.predict_proba(X_val)[:, 1]
            self.calibrator.fit(raw_val_probs, y_val)
            eval_x, eval_y = X_val, y_val
        else:
            raw_train_probs = self.model.predict_proba(X_train)[:, 1]
            self.calibrator.fit(raw_train_probs, y_train)
            eval_x, eval_y = X_train, y_train

        self.is_trained = True
        return self.evaluate(eval_x, eval_y)

    def evaluate(self, X: np.ndarray, y: np.ndarray) -> Dict[str, float]:
        """Evaluate performance metrics on given dataset."""
        if not self.is_trained or self.model is None:
            raise RuntimeError("Model is not trained yet.")

        raw_probs = self.model.predict_proba(X)[:, 1]
        calibrated_risks = np.array([self.calibrator.calibrate(p)[0] for p in raw_probs])
        preds = (calibrated_risks >= 0.5).astype(int)

        precision = float(precision_score(y, preds, zero_division=0))
        recall = float(recall_score(y, preds, zero_division=0))
        f1 = float(f1_score(y, preds, zero_division=0))
        pr_auc = float(average_precision_score(y, calibrated_risks)) if len(np.unique(y)) > 1 else 1.0
        roc_auc = float(roc_auc_score(y, calibrated_risks)) if len(np.unique(y)) > 1 else 1.0
        brier = float(brier_score_loss(y, calibrated_risks))

        return {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "pr_auc": round(pr_auc, 4),
            "roc_auc": round(roc_auc, 4),
            "brier_score": round(brier, 4),
        }

    def predict(
        self,
        nodes: List[Dict[str, Any]],
        edges: List[Dict[str, Any]],
        root_node_id: Optional[str] = None,
    ) -> Tuple[float, float, List[Dict[str, float]]]:
        """Inference for a single subgraph.

        Returns:
            (risk, confidence, top_features)
        """
        if not self.is_trained or self.model is None:
            raise RuntimeError("Model is not trained yet.")

        graph_feats = self.extractor.extract_graph_features(nodes, edges, root_node_id)
        feat_vector = self.extractor.graph_features_to_vector(graph_feats).reshape(1, -1)

        raw_prob = float(self.model.predict_proba(feat_vector)[0, 1])
        risk, confidence = self.calibrator.calibrate(raw_prob)

        # Feature contributions / importances
        top_features = self.get_top_features(feat_vector)
        return risk, confidence, top_features

    def get_top_features(self, feat_vector: np.ndarray, top_k: int = 5) -> List[Dict[str, float]]:
        """Get top contributing features for the prediction."""
        if self.model is None:
            return []

        importances = self.model.feature_importances_
        # Weight by input feature scaling/activation
        vals = feat_vector[0]
        # Normalized absolute feature impact
        norm_val = np.abs(vals) / (np.abs(vals).max() + 1e-5)
        impact = importances * (1.0 + norm_val)
        tot_impact = np.sum(impact) + 1e-5

        indices = np.argsort(impact)[::-1][:top_k]
        top = []
        for idx in indices:
            top.append({
                "name": self.feature_names[idx],
                "weight": round(float(impact[idx] / tot_impact), 4),
            })
        return top

    def save(self, directory: str | Path):
        """Save model weights, calibrator, and schema to artifact directory."""
        path = Path(directory)
        path.mkdir(parents=True, exist_ok=True)

        if self.model is not None:
            self.model.save_model(str(path / "model.json"))

        meta = {
            "model_name": self.model_name,
            "version": self.version,
            "feature_names": self.feature_names,
            "calibrator": self.calibrator.to_dict(),
            "is_trained": self.is_trained,
        }
        with open(path / "metadata.json", "w") as f:
            json.dump(meta, f, indent=2)

    def load(self, directory: str | Path):
        """Load model weights, calibrator, and schema from artifact directory."""
        path = Path(directory)
        with open(path / "metadata.json", "r") as f:
            meta = json.load(f)

        self.model_name = meta["model_name"]
        self.version = meta["version"]
        self.feature_names = meta["feature_names"]
        self.calibrator = ScoreCalibrator.from_dict(meta["calibrator"])
        self.is_trained = meta.get("is_trained", True)

        self.model = xgb.XGBClassifier()
        self.model.load_model(str(path / "model.json"))
