from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    PROJECT_NAME: str = "SIH Cybercrime ML Model Service"
    VERSION: str = "0.1.0"
    API_V1_STR: str = "/api/v1"
    
    # Storage and Artifacts
    ARTIFACTS_DIR: str = "artifacts"
    SNAPSHOTS_DIR: str = "storage/snapshots"
    
    # Server settings
    HOST: str = "0.0.0.0"
    PORT: int = 8001

    model_config = SettingsConfigDict(case_sensitive=True, env_file=".env", extra="ignore")


settings = Settings()
