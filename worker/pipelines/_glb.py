"""
Pure-Python minimal GLB writer (no third-party deps).

trimesh is the preferred tool for building dev-fallback meshes (used when it is
installed in the venv). This module is a ZERO-DEPENDENCY fallback so the worker
loop still emits a VALID .glb even on machines where trimesh cannot be
installed (e.g. locked-down sandboxes). It writes a correct glTF 2.0 binary
(GLB) containing a colored box or sphere.
"""

from __future__ import annotations

import json
import struct
from pathlib import Path


def _pad4(b: bytes) -> bytes:
    pad = (4 - (len(b) % 4)) % 4
    return b + b" " * pad


def _pad4_zero(b: bytes) -> bytes:
    pad = (4 - (len(b) % 4)) % 4
    return b + b"\x00" * pad


def _uv_sphere(radius: float, sectors: int, rings: int):
    """Return (positions: list[float*3], indices: list[int]) for a UV sphere."""
    positions: list[float] = []
    for y in range(rings + 1):
        v = y / rings
        phi = v * 3.141592653589793
        for x in range(sectors + 1):
            u = x / sectors
            theta = u * 2.0 * 3.141592653589793
            cx = radius * float(__import__("math").sin(phi) * __import__("math").cos(theta))
            cy = radius * float(__import__("math").cos(phi))
            cz = radius * float(__import__("math").sin(phi) * __import__("math").sin(theta))
            positions.extend([cx, cy, cz])
    indices: list[int] = []
    for y in range(rings):
        for x in range(sectors):
            a = y * (sectors + 1) + x
            b = a + sectors + 1
            indices.extend([a, b, a + 1])
            indices.extend([b, b + 1, a + 1])
    return positions, indices


def _box(size: float):
    """Return (positions, indices) for a unit-ish box centered at origin."""
    h = size / 2.0
    # 8 corners
    corners = [
        (-h, -h, -h), (h, -h, -h), (h, h, -h), (-h, h, -h),
        (-h, -h, h), (h, -h, h), (h, h, h), (-h, h, h),
    ]
    positions = [c for corner in corners for c in corner]
    # 12 triangles (36 indices)
    faces = [
        (0, 1, 2), (0, 2, 3),  # back
        (4, 6, 5), (4, 7, 6),  # front
        (0, 4, 5), (0, 5, 1),  # bottom
        (3, 2, 6), (3, 6, 7),  # top
        (0, 3, 7), (0, 7, 4),  # left
        (1, 5, 6), (1, 6, 2),  # right
    ]
    indices = [i for f in faces for i in f]
    return positions, indices


def write_glb(
    path: str | Path,
    kind: str = "box",
    size: float = 1.0,
    color: tuple[float, float, float] = (0.23, 0.63, 1.0),
    detail: int = 24,
) -> None:
    """Write a valid glTF 2.0 GLB. `color` is RGB in 0..1."""
    if kind == "sphere":
        positions, indices = _uv_sphere(size / 2.0, detail, detail)
    else:
        positions, indices = _box(size)

    n_verts = len(positions) // 3
    n_idx = len(indices)

    pos_bytes = struct.pack("<%df" % len(positions), *positions)
    # Index buffer: use UNSIGNED_SHORT (5123) if it fits, else UNSIGNED_INT (5125).
    if max(indices) < 65536:
        idx_bytes = struct.pack("<%dH" % n_idx, *indices)
        comp_type = 5123
    else:
        idx_bytes = struct.pack("<%dI" % n_idx, *indices)
        comp_type = 5125

    bin_blob = _pad4_zero(pos_bytes) + _pad4_zero(idx_bytes)
    pos_len = len(pos_bytes)
    idx_len = len(idx_bytes)

    # axis-aligned bounds for POSITION accessor
    xs = positions[0::3]
    ys = positions[1::3]
    zs = positions[2::3]
    pmin = [min(xs), min(ys), min(zs)]
    pmax = [max(xs), max(ys), max(zs)]

    gltf = {
        "asset": {"version": "2.0", "generator": "3DCreator-dev-fallback"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0}],
        "meshes": [{
            "primitives": [{
                "attributes": {"POSITION": 0},
                "indices": 1,
                "material": 0,
            }]
        }],
        "materials": [{
            "pbrMetallicRoughness": {
                "baseColorFactor": [color[0], color[1], color[2], 1.0],
                "metallicFactor": 0.0,
                "roughnessFactor": 0.85,
            }
        }],
        "buffers": [{"byteLength": len(bin_blob)}],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": pos_len, "target": 34962},
            {"buffer": 0, "byteOffset": len(pos_bytes), "byteLength": idx_len, "target": 34963},
        ],
        "accessors": [
            {
                "bufferView": 0, "componentType": 5126, "count": n_verts,
                "type": "VEC3", "min": pmin, "max": pmax,
            },
            {
                "bufferView": 1, "componentType": comp_type, "count": n_idx,
                "type": "SCALAR",
            },
        ],
    }

    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_chunk = _pad4(json_bytes)

    glb = bytearray()
    glb += struct.pack("<III", 0x46546C67, 2, 0)  # header placeholder
    # chunk 0: JSON
    glb += struct.pack("<II", len(json_chunk), 0x4E4F534A)
    glb += json_chunk
    # chunk 1: BIN
    glb += struct.pack("<II", len(bin_blob), 0x004E4942)
    glb += bin_blob
    # patch total length
    glb[8:12] = struct.pack("<I", len(glb))

    Path(path).write_bytes(glb)
