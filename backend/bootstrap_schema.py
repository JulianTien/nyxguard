import logging

from config import get_settings
from database import Base, build_engine
import models  # noqa: F401  # Ensure SQLAlchemy models are registered before create_all().
from schema_compat import ensure_schema_compatibility


def main() -> None:
    settings = get_settings()
    database_url = settings.migration_database_url
    engine = build_engine(database_url)

    logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))
    logging.info("Bootstrapping schema using %s", database_url.split("://", 1)[0])

    Base.metadata.create_all(bind=engine)
    ensure_schema_compatibility(engine)


if __name__ == "__main__":
    main()
