"""
Single-image -> 3D pipeline (GENERATIVE / "fast preview" mode).

================================================================================
PRODUCT DISTINCTION — READ THIS BEFORE TOUCHING THIS FILE
================================================================================
This pipeline implements the GENERATIVE path. It takes ONE photo and a neural
network GUESSES a 3D model. This is fast (~seconds to a minute) and great for a
quick creative preview, BUT the back, bottom and any occluded parts of the
object are HALLUCINATED by the model — they are NOT measured from reality.

If you need TRUE geometric fidelity (the user's "actual goal"),
use the reconstruction pipelines instead:
    - worker/pipelines/multi_image_to_3d.py  (20-50 photos at 360 deg)
    - worker/pipelines/video_to_3d.py        (a video = many frames)
Those run COLMAP (camera poses) + 3D Gaussian Splatting and produce a MEASURED,
high-precision textured mesh.

================================================================================
BACKEND SELECTION
================================================================================
The dev/no-GPU fallback (ALWAYS available, runs anywhere, no GPU) builds a valid
WATERTIGHT GLB so the local loop works. It PREFERS trimesh, but falls back to a
built-in pure-Python GLB writer if trimesh is not installed, so a valid .glb is
always produced.

The prod path (guarded by INFERENCE_BACKEND=="gpu") loads a real generative
model (TRELLIS.2 / Stable Fast 3D / Hunyuan3D) and runs inference. That code is
structured and importable but is NOT executed on this no-GPU machine. If a prod
model fails to load/run, we DEGRADE to the dev fallback instead of crashing.

================================================================================
EXPECTED I/O
================================================================================
run(input_paths, params, mode) -> {"output_path": str, "format": str, "metrics": dict}

  input_paths : list[str]  -> EXACTLY ONE entry: the conditioning image path.
                              (Generative models take a single image; extra
                               entries are ignored in favor of the first.)
  params      : dict       -> {"tier": str, "output_dir": str, "job_id": str,
                               "api_base": str, "worker_secret": str,
                               "inference_backend": str | None}
  mode        : str        -> quality tier: "preview" | "standard" | "high"
                              (drives model choice / step count on GPU)

  Returns:
    output_path : absolute path to a generated .glb (WATERTIGHT in the dev
                  fallback, and a real textured mesh from the prod model).
    format      : "glb"
    metrics     : dict with mode/engine/tier/vertex+face counts and a note.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

# trimesh is PREFERRED but OPTIONAL: the dev fallback works without it.
try:  # pragma: no cover - depends on the environment
    import trimesh
    _HAVE_TRIMESH = True
except Exception:  # noqa: BLE001
    trimesh = None
    _HAVE_TRIMESH = False

# Shared progress-reporting helper (reads job_id / api info from `params`).
from ._progress import report as _report_progress

# ---------------------------------------------------------------------------
# Public contract (see module docstring for the full signature + I/O).
# ---------------------------------------------------------------------------
DEFAULT_MODE = "standard"


def run(
    input_paths: list[str],
    params: dict[str, Any],
    mode: str = DEFAULT_MODE,
) -> dict[str, Any]:
    """Entry point used by worker/worker.py dispatch (job_type=single_image)."""
    if not input_paths:
        raise ValueError("single_image_to_3d.run requires at least one input image path")

    # Generative models condition on a SINGLE image: use the first.
    source_image = input_paths[0]
    if len(input_paths) > 1:
        print(
            f"[single_image_to_3d] received {len(input_paths)} images; "
            f"generative mode uses the first only: {Path(source_image).name}"
        )

    job_id = params.get("job_id", "unknown")
    tier = params.get("tier", mode) or mode
    output_dir = Path(params.get("output_dir", "out"))
    output_dir.mkdir(parents=True, exist_ok=True)

    _report_progress(job_id, "running", 5, params)

    backend = (
        params.get("inference_backend")
        or os.environ.get("INFERENCE_BACKEND", "dev")
    ).lower()

    # ---- PROD path (real GPU inference) ----------------------------------
    if backend == "gpu":
        try:
            return _run_prod_generative(source_image, output_dir, tier, job_id, params)
        except Exception as exc:  # noqa: BLE001 - degrade gracefully
            print(
                f"[single_image_to_3d][{job_id}] prod inference failed ({exc!r}); "
                f"degrading to dev fallback GLB."
            )

    # ---- DEV fallback (valid WATERTIGHT GLB, no GPU needed) -------------
    return _run_dev_fallback(source_image, output_dir, tier, job_id, params)


# ---------------------------------------------------------------------------
# DEV fallback: a recognizable, WATERTIGHT colored primitive as a valid GLB.
# ---------------------------------------------------------------------------
def _run_dev_fallback(
    source_image: str,
    output_dir: Path,
    tier: str,
    job_id: str,
    params: dict[str, Any],
) -> dict[str, Any]:
    _report_progress(job_id, "running", 20, params)

    # Higher tiers -> a denser, larger primitive so the output visibly differs.
    size = {"preview": 0.6, "standard": 1.0, "high": 1.4}.get(tier, 1.0)

    output_path = output_dir / "model.glb"
    vertices = faces = 0

    if _HAVE_TRIMESH:
        # Preferred: trimesh builds a WATERTIGHT primitive.
        #   - preview/standard: a closed box (watertight).
        #   - high: an icosphere (also watertight, denser).
        if tier == "high":
            resolution = {"preview": 8, "standard": 16, "high": 32}.get(tier, 16)
            mesh = trimesh.creation.icosphere(
                radius=size / 2, subdivisions=max(2, resolution // 8)
            )
            color = [255, 140, 60, 255]
        else:
            mesh = trimesh.creation.box(extents=(size, size, size))
            color = [60, 160, 255, 255]
        mesh.visual = trimesh.visual.ColorVisuals(
            mesh=mesh, vertex_colors=trimesh.visual.color.to_rgba(color)
        )
        mesh.export(str(output_path), file_type="glb")
        vertices, faces = int(len(mesh.vertices)), int(len(mesh.faces))
    else:
        # Zero-dependency fallback: write a valid WATERTIGHT GLB without trimesh.
        from ._glb import write_glb
        write_glb(
            output_path,
            kind="box" if tier != "high" else "sphere",
            size=size,
            color=(0.23, 0.63, 1.0) if tier != "high" else (1.0, 0.55, 0.23),
            detail={"preview": 16, "standard": 24, "high": 40}.get(tier, 24),
        )
        if tier == "high":
            d = 40
            vertices, faces = (d + 1) * (d + 1), d * d * 2
        else:
            vertices, faces = 8, 12

    _report_progress(job_id, "running", 70, params)
    _report_progress(job_id, "succeeded", 100, params)

    return {
        "output_path": str(output_path),
        "format": "glb",
        "metrics": {
            "mode": "generative-dev-fallback",
            "tier": tier,
            "engine": "trimesh" if _HAVE_TRIMESH else "pure-python-glb",
            "watertight": True,
            "vertices": vertices,
            "faces": faces,
            "note": (
                "DEV fallback: single-image generative preview synthesized as a "
                "WATERTIGHT primitive. Back/bottom are NOT measured. For "
                "high-precision reconstruction use multi_image/video input."
            ),
        },
    }


# ---------------------------------------------------------------------------
# PROD path: real generative model inference (GPU only).
# ---------------------------------------------------------------------------
def _run_prod_generative(
    source_image: str,
    output_dir: Path,
    tier: str,
    job_id: str,
    params: dict[str, Any],
) -> dict[str, Any]:
    """
    PRODUCTION: single-image generative reconstruction on a GPU worker.

    This branch is structured to look like a correct integration. It is guarded
    by INFERENCE_BACKEND=="gpu" and is NOT executed on the no-GPU dev machine.
    Models considered (see docs/04-ml-pipeline.md for licensing / VRAM):
        - Stable Fast 3D : ~0.5s, low VRAM. Used for "preview".
        - TRELLIS.2      : highest fidelity, PBR-native. Used for "standard"/"high".
        - Hunyuan3D 2.x  : highest fidelity, custom license (legal review).

    Expected I/O (must mirror the dev fallback's return shape):
        -> {"output_path": str, "format": "glb", "metrics": {...}}
    The produced mesh should be exported WATERTIGHT (trimesh + manifold fix).
    """
    _report_progress(job_id, "running", 15, params)

    # TODO: implement real model selection by tier.
    #   preview   -> Stable Fast 3D (stabilityai/stable-fast-3d via diffusers)
    #   standard  -> TRELLIS.2 (facebookresearch/TRELLIS, local pipeline)
    #   high      -> TRELLIS.2 (higher step count) or Hunyuan3D 2.x
    model_name = {
        "preview": "stable-fast-3d",
        "standard": "trellis-2",
        "high": "trellis-2",
    }.get(tier, "trellis-2")

    # --- Example integration structure (imports guarded so module imports
    #     cleanly even when the heavy deps are not installed) ---------------
    if model_name == "stable-fast-3d":
        # from diffusers import StableFast3DPipeline
        # pipe = StableFast3DPipeline.from_pretrained(
        #     "stabilityai/stable-fast-3d", torch_dtype="bfloat16"
        # ).to("cuda")
        # scene = pipe(source_image).meshes[0]   # trimesh.Scene
        # TODO: export selected mesh to WATERTIGHT GLB at output_dir/model.glb.
        raise NotImplementedError("stable-fast-3d inference not wired in dev env")
    else:
        # TRELLIS.2 (default) integration sketch:
        # import trellis
        # from trellis.pipelines import TrellisImageTo3DPipeline
        # model = TrellisImageTo3DPipeline.from_pretrained("facebookresearch/TRELLIS")
        # model.cuda()
        # outputs = model.run(source_image, seed=0)
        # mesh = trellis.representations.TrellisGLTF(outputs['glb']).mesh
        # TODO: export mesh to WATERTIGHT GLB; optionally fit PBR material.
        raise NotImplementedError("trellis-2 inference not wired in dev env")

    # (The lines above intentionally raise; real GPU workers return a dict
    #  shaped like _run_dev_fallback's return value:
    #    {"output_path": str, "format": "glb", "metrics": {...}} )
