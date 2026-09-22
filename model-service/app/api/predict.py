"""Prediction endpoint for fraud scoring and graph analytics."""

from fastapi import APIRouter, HTTPException, status
from app.api.schemas import PredictRequest, PredictResponse, TopNodeExplanation, TopFeatureExplanation
from app.explain.explainer import PredictionExplainer
from app.registry.registry import registry

router = APIRouter()


@router.post("/predict", response_model=PredictResponse)
def predict_subgraph(request: PredictRequest):
    """Perform fraud risk scoring and explainability on a de-identified transaction subgraph."""
    model_name = (request.model_name or "graphsage").lower()
    active_version = registry.get_active_version(model_name)
    if not active_version:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"No active model version deployed for '{model_name}'.",
        )

    try:
        model = registry.get_model(model_name, active_version)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to load model {model_name}:{active_version}: {str(e)}",
        )

    # Convert nodes and edges to standard dict format
    nodes = [n.model_dump() for n in request.nodes]
    edges = [e.model_dump() for e in request.edges]
    root_node_id = nodes[0]["id"] if nodes else "node-0"

    try:
        if model_name == "graphsage":
            risk, confidence, top_nodes_raw, top_features_raw = model.predict(nodes, edges, root_node_id)
        else:
            risk, confidence, top_features_raw = model.predict(nodes, edges, root_node_id)
            # Default ranking for top nodes from node degree/activity
            top_nodes_raw = [{"id": n["id"], "score": risk} for n in nodes]

        top_nodes = [
            TopNodeExplanation(id=n["id"], score=n["score"])
            for n in PredictionExplainer.format_top_nodes(top_nodes_raw, top_k=5)
        ]
        top_features = [
            TopFeatureExplanation(name=f["name"], weight=f["weight"])
            for f in PredictionExplainer.format_top_features(top_features_raw, top_k=5)
        ]

        return PredictResponse(
            risk=risk,
            confidence=confidence,
            top_nodes=top_nodes,
            top_features=top_features,
            model_version=active_version,
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Inference error: {str(e)}",
        )
