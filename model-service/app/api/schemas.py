"""Pydantic request and response schemas for prediction and model registry endpoints."""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class NodeDto(BaseModel):
    id: str
    features: Optional[Dict[str, float]] = Field(default_factory=dict)


class EdgeDto(BaseModel):
    source: str
    target: str
    amount: float
    timestamp: str


class PredictRequest(BaseModel):
    model_name: Optional[str] = "graphsage"
    nodes: List[NodeDto]
    edges: List[EdgeDto]


class TopNodeExplanation(BaseModel):
    id: str
    score: float


class TopFeatureExplanation(BaseModel):
    name: str
    weight: float


class PredictResponse(BaseModel):
    risk: float
    confidence: float
    top_nodes: List[TopNodeExplanation]
    top_features: List[TopFeatureExplanation]
    model_version: str


class ModelCardResponse(BaseModel):
    model_name: str
    version: str
    status: str
    architecture: Optional[str] = None
    metrics: Optional[Dict[str, float]] = None
    features: Optional[List[str]] = None
    pooling_rule: Optional[str] = None
    sample_size: Optional[int] = None
    is_active: Optional[bool] = None
    saved_at: Optional[str] = None


class TrainRequest(BaseModel):
    model_name: str
    version: str
    snapshot_path: Optional[str] = None
    use_synthetic_data: bool = True
    num_synthetic_samples: int = 150


class TrainResponse(BaseModel):
    model_name: str
    version: str
    status: str
    candidate_metrics: Dict[str, float]
    active_metrics: Optional[Dict[str, float]] = None
    beats_active: bool


class ActivateRequest(BaseModel):
    version: str
