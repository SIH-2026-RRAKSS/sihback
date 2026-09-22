import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_list_and_get_models():
    # List models
    resp = client.get("/api/v1/models")
    assert resp.status_code == 200
    models = resp.json()
    assert len(models) >= 2
    model_names = [m["model_name"] for m in models]
    assert "graphsage" in model_names
    assert "xgboost" in model_names

    # Get info for graphsage
    resp_info = client.get("/api/v1/models/graphsage/info")
    assert resp_info.status_code == 200
    info = resp_info.json()
    assert info["model_name"] == "graphsage"
    assert "metrics" in info
    assert "f1" in info["metrics"]


def test_train_candidate_and_activate():
    # Train candidate version of xgboost
    train_payload = {
        "model_name": "xgboost",
        "version": "xgboost-v2.0.0",
        "use_synthetic_data": True,
        "num_synthetic_samples": 40,
    }
    resp = client.post("/api/v1/train", json=train_payload)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "CANDIDATE"
    assert data["version"] == "xgboost-v2.0.0"
    assert "candidate_metrics" in data

    # Activate the new candidate version
    activate_payload = {"version": "xgboost-v2.0.0"}
    resp_act = client.post("/api/v1/models/xgboost/activate", json=activate_payload)
    assert resp_act.status_code == 200
    act_data = resp_act.json()
    assert act_data["status"] == "SUCCESS"
    assert act_data["active_version"] == "xgboost-v2.0.0"

    # Switch back to v1.0.0
    client.post("/api/v1/models/xgboost/activate", json={"version": "xgboost-v1.0.0"})
