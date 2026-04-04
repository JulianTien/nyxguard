import logging

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from config import get_settings
from database import Base, engine
from routers import auth, chat, guardians, notifications, trips, user, v2
from schema_compat import ensure_schema_compatibility


settings = get_settings()


def create_app() -> FastAPI:
    logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO))

    app = FastAPI(
        title="NyxGuard API",
        version="2.0.0",
        docs_url="/docs" if settings.app_env != "production" else None,
        redoc_url="/redoc" if settings.app_env != "production" else None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=settings.cors_origins != ["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.on_event("startup")
    def startup() -> None:
        if settings.should_auto_bootstrap_schema:
            Base.metadata.create_all(bind=engine)
            ensure_schema_compatibility(engine)

    @app.get("/")
    def health() -> dict[str, str]:
        return {"status": "ok", "service": "NyxGuard API", "version": "2.0.0"}

    @app.exception_handler(HTTPException)
    async def http_exception_handler(_: Request, exc: HTTPException) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content={"detail": str(exc.detail)})

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        first_error = exc.errors()[0]
        message = first_error.get("msg", "请求参数不合法")
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"detail": message})

    app.include_router(auth.router)
    app.include_router(user.router)
    app.include_router(guardians.router)
    app.include_router(trips.router)
    app.include_router(notifications.router)
    app.include_router(chat.router)
    app.include_router(v2.router)
    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=5001, reload=settings.app_env != "production")
