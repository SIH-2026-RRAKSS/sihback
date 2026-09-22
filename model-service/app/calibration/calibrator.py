"""Probability and confidence calibration for risk scores.

Uses Isotonic Regression or Sigmoid (Platt) calibration to ensure risk scores
reflect empirical probabilities, and provides calibrated confidence estimates.
"""

from typing import Any, Dict, Optional, Tuple
import numpy as np
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression


class ScoreCalibrator:
    """Calibrator for converting raw model scores into empirical probabilities and confidence."""

    def __init__(self, method: str = "isotonic"):
        self.method = method
        self.is_fitted = False
        self.x_thresholds: Optional[np.ndarray] = None
        self.y_thresholds: Optional[np.ndarray] = None
        self.coef: Optional[float] = None
        self.intercept: Optional[float] = None

    def fit(self, scores: np.ndarray, y_true: np.ndarray) -> "ScoreCalibrator":
        """Fit calibrator on validation predictions and true labels."""
        scores = np.asarray(scores, dtype=np.float64).ravel()
        y_true = np.asarray(y_true, dtype=np.float64).ravel()

        if len(scores) == 0:
            self.is_fitted = False
            return self

        if self.method == "sigmoid":
            lr = LogisticRegression(C=1.0, solver="lbfgs", random_state=42)
            lr.fit(scores.reshape(-1, 1), y_true)
            self.coef = float(lr.coef_[0, 0])
            self.intercept = float(lr.intercept_[0])
        else:
            iso = IsotonicRegression(out_of_bounds="clip", y_min=0.01, y_max=0.99)
            iso.fit(scores, y_true)
            self.x_thresholds = np.array(iso.X_thresholds_, dtype=np.float64)
            self.y_thresholds = np.array(iso.y_thresholds_, dtype=np.float64)

        self.is_fitted = True
        return self

    def calibrate(self, raw_score: float) -> Tuple[float, float]:
        """Calibrate a single raw score into (calibrated_risk, confidence).

        Returns:
            calibrated_risk: float in [0.0, 1.0]
            confidence: float in [0.0, 1.0] reflecting decision certainty
        """
        raw_score = float(np.clip(raw_score, 0.0, 1.0))
        if not self.is_fitted:
            risk = raw_score
        elif self.method == "sigmoid" and self.coef is not None and self.intercept is not None:
            # Sigmoid / Platt scaling: 1 / (1 + exp(-(w * x + b)))
            z = self.coef * raw_score + self.intercept
            risk = float(1.0 / (1.0 + np.exp(-z)))
        elif self.x_thresholds is not None and self.y_thresholds is not None:
            # Piecewise linear interpolation (Isotonic)
            risk = float(np.interp(raw_score, self.x_thresholds, self.y_thresholds))
        else:
            risk = raw_score

        risk = float(np.clip(risk, 0.0, 1.0))

        # Confidence: distance from decision boundary (0.5), mapped to [0.5, 0.99]
        confidence = float(np.clip(0.5 + abs(risk - 0.5), 0.5, 0.99))

        return round(risk, 4), round(confidence, 4)

    def to_dict(self) -> Dict[str, Any]:
        """Serialize calibrator parameters for persistence."""
        if not self.is_fitted:
            return {"method": self.method, "is_fitted": False}

        if self.method == "sigmoid":
            return {
                "method": "sigmoid",
                "is_fitted": True,
                "coef": self.coef,
                "intercept": self.intercept,
            }
        else:
            return {
                "method": "isotonic",
                "is_fitted": True,
                "x_thresholds": self.x_thresholds.tolist() if self.x_thresholds is not None else [],
                "y_thresholds": self.y_thresholds.tolist() if self.y_thresholds is not None else [],
            }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ScoreCalibrator":
        """Deserialize calibrator from dictionary."""
        calibrator = cls(method=data.get("method", "isotonic"))
        if not data.get("is_fitted", False):
            return calibrator

        if calibrator.method == "sigmoid":
            calibrator.coef = float(data["coef"])
            calibrator.intercept = float(data["intercept"])
        else:
            calibrator.x_thresholds = np.array(data["x_thresholds"], dtype=np.float64)
            calibrator.y_thresholds = np.array(data["y_thresholds"], dtype=np.float64)

        calibrator.is_fitted = True
        return calibrator
