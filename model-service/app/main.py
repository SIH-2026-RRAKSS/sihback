from fastapi import FastAPI
from app.core.config import settings
from app.api.health import router as health_router

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url=f"{settings.API_V1_STR}/docs",
    redoc_url=f"{settings.API_V1_STR}/redoc",
)

# Include routers
app.include_router(health_router, prefix=settings.API_V1_STR, tags=["Health"])


@app.get("/")
def root():
    return {
        "message": "SIH Cybercrime Predictive Analytics Model Service",
        "docs": f"{settings.API_V1_STR}/docs"
    }
