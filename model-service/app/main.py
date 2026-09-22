from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.core.config import settings
from app.api.health import router as health_router
from app.api.predict import router as predict_router
from app.api.models import router as models_router
from app.registry.registry import registry


# Ensure baseline models exist on startup / import
registry.bootstrap_if_empty()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Lifespan hook
    registry.bootstrap_if_empty()
    yield


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url=f"{settings.API_V1_STR}/docs",
    redoc_url=f"{settings.API_V1_STR}/redoc",
    lifespan=lifespan,
)

# Include routers under /api/v1 prefix
app.include_router(health_router, prefix=settings.API_V1_STR, tags=["Health"])
app.include_router(predict_router, prefix=settings.API_V1_STR, tags=["Prediction"])
app.include_router(models_router, prefix=settings.API_V1_STR, tags=["Models"])

# Also mount /predict and /health at root for direct convenience
app.include_router(predict_router, tags=["Prediction"])
app.include_router(health_router, tags=["Health"])


@app.get("/")
def root():
    return {
        "message": "SIH Cybercrime Predictive Analytics Model Service",
        "docs": f"{settings.API_V1_STR}/docs"
    }
