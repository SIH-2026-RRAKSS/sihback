"""Model registry, activation, and training endpoints."""

from typing import Any, Dict, List
from fastapi import APIRouter, HTTPException, status
from app.api.schemas import ActivateRequest, ModelCardResponse, TrainRequest, TrainResponse
from app.registry.registry import registry
from app.training.trainer import ModelTrainer

router = APIRouter()


@router.get("/models", response_model=List[ModelCardResponse])
def list_models():
    """List all registered models, versions, and deployment statuses."""
    return registry.list_models()


@router.get("/models/{model_name}/info", response_model=ModelCardResponse)
def get_model_info(model_name: str, version: str = None):
    """Get detailed model card and evaluation metrics for a specific model version."""
    try:
        return registry.get_model_card(model_name, version)
    except FileNotFoundError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Model card not found for '{model_name}'.",
        )
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))


@router.post("/models/{model_name}/activate")
def activate_model_version(model_name: str, request: ActivateRequest):
    """Promote a specific model version to ACTIVE."""
    try:
        registry.set_active_version(model_name, request.version)
        return {
            "status": "SUCCESS",
            "message": f"Version '{request.version}' is now ACTIVE for model '{model_name}'.",
            "active_version": request.version,
        }
    except FileNotFoundError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Version '{request.version}' not found for model '{model_name}'.",
        )
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))


@router.post("/train", response_model=TrainResponse)
def train_candidate(request: TrainRequest):
    """Trigger training of a new candidate model and evaluate against active version."""
    try:
        trainer = ModelTrainer()
        result = trainer.train_candidate(
            model_name=request.model_name,
            version=request.version,
            snapshot_path=request.snapshot_path,
            use_synthetic_data=request.use_synthetic_data,
            num_synthetic_samples=request.num_synthetic_samples,
        )
        return TrainResponse(**result)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Training failed: {str(e)}",
        )
