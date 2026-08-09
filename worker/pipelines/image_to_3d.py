"""
Legacy alias for the single-image GENERATIVE pipeline.

The canonical implementation now lives in `single_image_to_3d.py` (clearer
GPU-structuring, docstrings, expected I/O, and a WATERTIGHT dev fallback). This
module keeps the `image_to_3d` JobType alias and old imports working without
duplicating logic. See single_image_to_3d.py for the full product distinction
note and expected I/O contract.
"""

from .single_image_to_3d import run  # noqa: F401  (canonical entry point)
from .single_image_to_3d import run as run_image_to_3d  # noqa: F401  (legacy alias)
from .single_image_to_3d import (  # noqa: F401  (re-export public symbols)
    DEFAULT_MODE,
)

__all__ = ["run", "run_image_to_3d", "DEFAULT_MODE"]
