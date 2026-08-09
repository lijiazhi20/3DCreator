# Hardware Feasibility Assessment

## Target Device

- **Model**: Honor phone, MagicOS 10.0
- **OS**: Android 16
- **SoC**: Qualcomm Snapdragon 8 Gen 2 (SM8550-AB), Adreno 740 GPU, Hexagon NPU
- **Memory**: 16 GB RAM
- **Storage**: 1 TB

## Verdict

The Snapdragon 8 Gen 2 is a 2023 flagship platform. It can run lightweight quantized models for quick previews, but it **cannot** run state-of-the-art high-resolution reconstruction on-device:

- **Generative image→3D** (TRELLIS.2, Hunyuan3D 2.x, TripoSG): needs 16–24 GB VRAM-class GPU; phone NPU/Adreno are far below this.
- **Video / multi-view reconstruction** (COLMAP + 3D Gaussian Splatting): heavy CPU/GPU + large RAM; runs in minutes-to-hours even on desktop GPUs.

**Conclusion**: 3DCreator must be a **hybrid architecture**: the Android app handles capture, upload, job tracking, 3D preview, and sharing; heavy reconstruction runs on **cloud GPU workers**.

## On-device vs Cloud Split

| Capability | On-device | Cloud |
|------------|-----------|-------|
| Camera capture / video recording | ✅ | |
| Frame extraction / light preprocess | ✅ | ✅ |
| Upload + resumable transfer | ✅ | |
| Job queue + live status | ✅ | ✅ |
| 3D preview (GLB / PLY / Splat) | ✅ (Filament) | |
| High-res reconstruction (mesh + 4K–8K texture) | | ✅ (GPU worker) |
| Export / share | ✅ | ✅ (storage) |

## Notes for MagicOS / Android 16

- 16 KB memory page size: native libs (Filament) must support it or install is blocked.
- 64-bit only (`arm64-v8a`).
- Aggressive battery management: use Foreground Service for uploads.
- Use Photo Picker to avoid broad storage permission.
- `FLAG_SECURE` on viewer/capture to prevent recents snapshot leak.
