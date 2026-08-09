from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
async def health_check():
    return {"status": "ok", "service": "3dcreator-api"}


@router.get("/healthz")
async def healthz():
    """Kubernetes/load-balancer liveness alias of /health."""
    return {"status": "ok"}
