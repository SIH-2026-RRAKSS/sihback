from fastapi import APIRouter
from typing import Dict, Any
from app.registry.registry import registry

router = APIRouter()


@router.get("/health", response_model=Dict[str, Any])
def health_check() -> Dict[str, Any]:
    """Health check endpoint returning service status and model info."""
    return {
        "status": "healthy",
        "service": "model-service",
        "active_models": {
            "graphsage": registry.get_active_version("graphsage") or "none",
            "xgboost": registry.get_active_version("xgboost") or "none"
        }
    }
