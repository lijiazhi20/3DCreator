"""
3DCreator worker pipelines.

Three reconstructed pipelines, each exposing:
    run(input_paths: list[str], params: dict, mode: str) -> dict
returning {"output_path": str, "format": str, "metrics": dict}.

  - image_to_3d       : SINGLE-IMAGE GENERATIVE ("fast preview"). One photo ->
                        AI-guessed 3D. Back/bottom hallucinated, NOT measured.
  - multi_image_to_3d : MULTI-IMAGE RECONSTRUCTION ("high precision"). 20-50
                        360-deg photos -> COLMAP + 3DGS + SuGaR -> textured mesh.
                        This is the user's real goal.
  - video_to_3d       : VIDEO -> RECONSTRUCTION. Extracts frames via ffmpeg then
                        delegates to multi_image_to_3d (same high-precision path).

Dispatch table (job_type -> pipeline.run), used by worker/worker.py:
    "single_image"  -> single_image_to_3d.run  (generative; canonical)
    "image_to_3d"   -> image_to_3d.run         (legacy alias -> single_image_to_3d)
    "multi_image"   -> multi_image_to_3d.run   (reconstruction / HIGH PRECISION)
    "video"         -> video_to_3d.run         (reconstruction / HIGH PRECISION)
(Legacy aliases "image_to_3d" / "video_to_3d" map to the same callables.)
"""

from .image_to_3d import run as run_image_to_3d
from .multi_image_to_3d import run as run_multi_image_to_3d
from .single_image_to_3d import run as run_single_image_to_3d
from .video_to_3d import run as run_video_to_3d

__all__ = [
    "run_image_to_3d",
    "run_single_image_to_3d",
    "run_multi_image_to_3d",
    "run_video_to_3d",
]

# job_type -> pipeline callable. Backend/Android teams use these names.
DISPATCH = {
    "single_image": run_single_image_to_3d,  # generative preview (canonical)
    "image_to_3d": run_image_to_3d,          # legacy alias
    "multi_image": run_multi_image_to_3d,    # reconstruction (HIGH PRECISION)
    "video": run_video_to_3d,                # reconstruction (HIGH PRECISION)
    "video_to_3d": run_video_to_3d,          # legacy alias
}
