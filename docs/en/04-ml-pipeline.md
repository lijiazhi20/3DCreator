# 3D Reconstruction Pipeline

## Model Selection (verified Aug 2026)

| Task | Recommended | License | VRAM |
|------|-------------|---------|------|
| Image → 3D (hi-fi) | **TRELLIS.2** | MIT | 24 GB |
| Image → 3D (fast preview) | **Stable Fast 3D** | Stability (commercial threshold) | 8 GB |
| Image → 3D (geo only) | **TripoSG** | MIT | 12 GB |
| Image → 3D (max fidelity) | **Hunyuan3D 2.1** | Custom (region-restricted) | 24 GB |
| Video / multi-view | **COLMAP + gsplat (Apache-2.0) + SuGaR** | Permissive | 12–24 GB |
| Texture upscale | **Real-ESRGAN x4** | AGPL (self-host ok) | 8 GB |

Avoid Inria 3DGS (non-commercial) and Meshroom (AGPL) for commercial products.

## Pipeline Stages

```
Source (image / frames / video)
        │
   ┌────▼─────┐
   │Preprocess│  resize, dedupe frames, background removal
   └────┬─────┘
        │
   ┌────▼─────┐   branch by job_type
   │Reconstruct│  image→3D (TRELLIS.2/StableFast3D) OR video→3D (COLMAP+gsplat)
   └────┬─────┘
        │
   ┌────▼─────┐
   │ Refine   │  xatlas UV unwrap, subdiv + displacement, PBR fit
   └────┬─────┘
        │
   ┌────▼─────┐
   │ Export   │  GLB / OBJ / FBX / USDZ / STL + 4K–8K texture
   └──────────┘
```

## Quality Tiers

- **Preview**: < 30 s, 1K texture — Stable Fast 3D.
- **Standard**: 1–3 min, 2K PBR — TRELLIS.2 (L4 GPU).
- **High**: 5–15 min, 1536³ + subdiv, 4K–8K PBR — TRELLIS.2 / Hunyuan3D-full (A100 80 GB).

## GPU Estimate

- L4 (24 GB): default worker for Standard.
- A100 80 GB: large scenes / Hunyuan3D-full.

## Failover

- Primary: open-source self-hosted.
- On SLA breach: route to fal.ai (Tripo / Rodin) commercial API.

## Licensing Flags

- Hunyuan3D 2.1: excludes EU/UK/SK; paid license required >1M MAU.
- Stable Fast 3D: Stability Enterprise needed if revenue > $1M.
- Legal sign-off required before public launch.
