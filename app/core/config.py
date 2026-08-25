from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    DB_HOST: str = "127.0.0.1"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = ""
    DB_NAME: str = "bitacora"
    APP_ENV: str = "production"

    PARTICIPANTE_TABLE: str = "participante"
    EMPLEADO_AREA_TABLE: str = "empleado_area"
    AREAS_TABLE: str = "areas_administrativas"
    BITACORA_DIARIA_TABLE: str = "bitacora_diaria"
    BAO_TABLE: str = "bitacora_area_observacion"
    BAE_TABLE: str = "bitacora_area_evidencia"

    UPLOAD_DIR: str = "uploads"
    EVIDENCIAS_DIR: str = "evidencias"
    EVIDENCIA_FOTO_MAX_BYTES: int = 15 * 1024 * 1024
    EVIDENCIA_AUDIO_MAX_BYTES: int = 50 * 1024 * 1024
    EVIDENCIA_VIDEO_MAX_BYTES: int = 200 * 1024 * 1024
    EVIDENCIA_TEXTO_MAX_BYTES: int = 512 * 1024

    FACE_TEMPLATE_TABLE: str = "participante_face_template"
    FACE_TEMPLATE_API_TOKEN: str = ""
    FACE_TEMPLATE_MASTER_KEY: str = ""
    ADMIN_API_TOKEN: str = ""
    # Prototipo C20A: debe habilitarse y configurarse solo en desarrollo.
    SUPERVISOR_DEV_AUTH_ENABLED: bool = False
    SUPERVISOR_TOKEN_SECRET: str = ""
    SUPERVISOR_TOKEN_TTL_SECONDS: int = 600

    FFMPEG_PATH: str = "/usr/bin/ffmpeg"
    WHISPER_CLI_PATH: str = "/srv/bitacora/tools/whisper.cpp/build/bin/whisper-cli"
    WHISPER_MODEL_PATH: str = "/srv/bitacora/tools/whisper.cpp/models/ggml-base.bin"
    TRANSCRIPTION_LANGUAGE: str = "es"
    TRANSCRIPTION_ENGINE: str = "whisper.cpp/base"

settings = Settings()
