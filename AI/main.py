import io

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import PlainTextResponse
from PIL import Image, UnidentifiedImageError
from starlette.concurrency import run_in_threadpool
from sentence_transformers import SentenceTransformer

MODEL_NAME = "clip-ViT-B-32"
MAX_BYTES = 6 * 1024 * 1024

# Loaded once at startup. The first run downloads the model (a few hundred MB).
model = SentenceTransformer(MODEL_NAME)
app = FastAPI(title="CampusVault image matcher")


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed", response_class=PlainTextResponse)
async def embed(request: Request):
    """Body is the raw image bytes. Returns the normalised embedding as comma-separated floats."""
    data = await request.body()
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(status_code=400, detail="Send an image of up to 6 MB.")
    try:
        image = Image.open(io.BytesIO(data)).convert("RGB")
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise HTTPException(status_code=400, detail="That isn't a valid image.")

    vector = await run_in_threadpool(model.encode, image, normalize_embeddings=True)
    return ",".join(f"{x:.6f}" for x in vector.tolist())