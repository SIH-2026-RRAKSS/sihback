import json
from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_predict_fixture_contract():
    # Load actual contract fixture from storage/fixtures/predict_request.json
    fixture_path = Path(__file__).resolve().parent.parent.parent / "storage" / "fixtures" / "predict_request.json"
    with open(fixture_path, "r", encoding="utf-8") as f:
        payload = json.load(f)

    # Test /api/v1/predict with graphsage
    response = client.post("/api/v1/predict", json=payload)
    assert response.status_code == 200, response.text
    data = response.json()

    assert "risk" in data
    assert "confidence" in data
    assert "top_nodes" in data
    assert "top_features" in data
    assert "model_version" in data
    assert 0.0 <= data["risk"] <= 1.0
    assert 0.5 <= data["confidence"] <= 1.0
    assert len(data["top_nodes"]) > 0
    assert len(data["top_features"]) > 0
    assert "graphsage" in data["model_version"].lower()

    # Test root /predict endpoint
    resp_root = client.post("/predict", json=payload)
    assert resp_root.status_code == 200


def test_predict_xgboost_contract():
    fixture_path = Path(__file__).resolve().parent.parent.parent / "storage" / "fixtures" / "predict_request.json"
    with open(fixture_path, "r", encoding="utf-8") as f:
        payload = json.load(f)

    payload["model_name"] = "xgboost"
    response = client.post("/api/v1/predict", json=payload)
    assert response.status_code == 200, response.text
    data = response.json()

    assert "risk" in data
    assert "confidence" in data
    assert "top_nodes" in data
    assert "top_features" in data
    assert "xgboost" in data["model_version"].lower()
