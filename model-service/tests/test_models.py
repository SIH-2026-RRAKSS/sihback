import pytest
import numpy as np
from app.calibration.calibrator import ScoreCalibrator
from app.features.extractor import FeatureExtractor
from app.models.graphsage import GraphSAGEFraudClassifier
from app.models.xgboost_model import XGBoostFraudClassifier
from app.synthetic.mule_generator import SyntheticMuleGenerator


def test_score_calibrator():
    calibrator = ScoreCalibrator(method="isotonic")
    scores = np.array([0.1, 0.2, 0.7, 0.8, 0.9])
    y_true = np.array([0, 0, 1, 1, 1])
    calibrator.fit(scores, y_true)

    risk, conf = calibrator.calibrate(0.85)
    assert 0.0 <= risk <= 1.0
    assert 0.5 <= conf <= 1.0

    # Serialization test
    d = calibrator.to_dict()
    restored = ScoreCalibrator.from_dict(d)
    r2, c2 = restored.calibrate(0.85)
    assert risk == r2
    assert conf == c2


def test_xgboost_train_and_predict():
    gen = SyntheticMuleGenerator(seed=42)
    dataset = gen.generate_dataset(num_samples=40, fraud_ratio=0.4)
    extractor = FeatureExtractor()

    X = np.array([
        extractor.graph_features_to_vector(
            extractor.extract_graph_features(g.to_dict()["nodes"], g.to_dict()["edges"], g.root_node_id)
        )
        for g in dataset
    ])
    y = np.array([g.label for g in dataset])

    clf = XGBoostFraudClassifier(random_state=42)
    metrics = clf.train(X[:25], y[:25], X_val=X[25:], y_val=y[25:])

    assert "f1" in metrics
    assert "precision" in metrics
    assert "recall" in metrics
    assert "pr_auc" in metrics

    # Test single prediction
    sample_g = dataset[0].to_dict()
    risk, conf, top_feats = clf.predict(sample_g["nodes"], sample_g["edges"], sample_g["root_node_id"])
    assert 0.0 <= risk <= 1.0
    assert 0.5 <= conf <= 1.0
    assert len(top_feats) > 0


def test_graphsage_train_and_predict():
    gen = SyntheticMuleGenerator(seed=42)
    dataset = gen.generate_dataset(num_samples=30, fraud_ratio=0.4)

    sage = GraphSAGEFraudClassifier(random_state=42)
    metrics = sage.train_on_graphs(dataset[:20], epochs=15, val_graphs=dataset[20:])

    assert "f1" in metrics
    assert "pr_auc" in metrics

    # Test prediction with explainability
    sample_g = dataset[0].to_dict()
    risk, conf, top_nodes, top_features = sage.predict(sample_g["nodes"], sample_g["edges"], sample_g["root_node_id"])

    assert 0.0 <= risk <= 1.0
    assert 0.5 <= conf <= 1.0
    assert len(top_nodes) == len(sample_g["nodes"])
    assert len(top_features) > 0
