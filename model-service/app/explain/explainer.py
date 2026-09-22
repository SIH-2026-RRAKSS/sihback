"""Explanation generator for model predictions.

Provides rankings for top suspicious nodes and contributing feature weights
in accordance with the /predict de-identified contract.
"""

from typing import Any, Dict, List, Optional
import numpy as np


class PredictionExplainer:
    """Formats and refines explainability components for fraud predictions."""

    @staticmethod
    def format_top_nodes(node_scores: List[Dict[str, Any]], top_k: int = 5) -> List[Dict[str, Any]]:
        """Sorts and truncates nodes by suspicion score."""
        sorted_nodes = sorted(node_scores, key=lambda x: x.get("score", 0.0), reverse=True)
        return [
            {"id": str(n["id"]), "score": round(float(n["score"]), 4)}
            for n in sorted_nodes[:top_k]
        ]

    @staticmethod
    def format_top_features(feature_weights: List[Dict[str, Any]], top_k: int = 5) -> List[Dict[str, Any]]:
        """Normalizes and truncates top contributing features."""
        sorted_feats = sorted(feature_weights, key=lambda x: x.get("weight", 0.0), reverse=True)
        top = sorted_feats[:top_k]
        tot = sum(f.get("weight", 0.0) for f in top)
        if tot <= 0:
            tot = 1.0

        return [
            {"name": str(f["name"]), "weight": round(float(f["weight"]) / tot, 4)}
            for f in top
        ]
