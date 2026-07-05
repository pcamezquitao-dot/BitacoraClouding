from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.core.config import settings

def _db_url() -> str:
    user = settings.DB_USER
    pwd = settings.DB_PASSWORD
    host = settings.DB_HOST
    port = settings.DB_PORT
    db = settings.DB_NAME
    return f"mysql+pymysql://{user}:{pwd}@{host}:{port}/{db}?charset=utf8mb4"

engine = create_engine(_db_url(), pool_pre_ping=True, pool_recycle=1800)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
