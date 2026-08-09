# Overall Architecture

## System Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌────────┐│
│  │ Camera  │  │ Gallery │  │ Upload  │  │  Jobs   │  │ Viewer ││
│  │ Capture │  │ Picker  │  │ Queue   │  │ Status  │  │ 3D     ││
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └───┬────┘│
│       └─────────────┴─────────────┴─────────────┘         │     │
│                           │                               │     │
│                    MVVM + Repository                      │     │
│                           │                               │     │
│              Retrofit / OkHttp / WebSocket                │     │
└───────────────────────────┬───────────────────────────────┘     │
                            │ HTTPS / WSS
┌───────────────────────────▼───────────────────────────────┐     │
│                    FastAPI Backend                         │     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────────────┐  │     │
│  │  Auth   │  │ Upload  │  │  Jobs   │  │   Billing    │  │     │
│  │(Supabase)│  │(Presign)│  │(Queue)  │  │   Credits    │  │     │
│  └────┬────┘  └────┬────┘  └────┬────┘  └──────┬───────┘  │     │
│       └─────────────┴─────────────┴─────────────┘          │     │
│                         PostgreSQL + Redis                 │     │
└───────────────────────────┬───────────────────────────────┘     │
                            │ AMQP / Redis Queue
┌───────────────────────────▼───────────────────────────────┐
│                  GPU Worker Pool (Docker)                  │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────┐ │
│  │ Image → 3D  │  │ Video → 3D  │  │ HD Enhancer        │ │
│  │ TRELLIS.2   │  │ COLMAP      │  │ xatlas + Real-ESRGAN│ │
│  │ StableFast3D│  │ GaussianSpl │  │ subdiv + PBR fit   │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬─────────┘ │
│         └─────────────────┴────────────────────┘          │
└───────────────────────────┬───────────────────────────────┘
                            │ GLB / OBJ / PLY / USDZ / STL
┌───────────────────────────▼───────────────────────────────┐
│           Object Storage (R2/S3) + CDN                     │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow

1. **Capture**: User takes photo / selects images / records video on Android.
2. **Upload**: App requests a presigned URL and uploads directly to object storage.
3. **Submit job**: App calls `/jobs`; backend deducts credits and enqueues.
4. **Process**: GPU worker consumes the task, pulls the source, runs reconstruction + enhancement.
5. **Write back**: Worker uploads GLB/textures/preview and calls backend webhook to update status.
6. **Preview / download**: App fetches the result; previews locally via Filament or downloads to share.

## Quality Tiers

| Tier | Input | Output | ETA | Use case |
|------|-------|--------|-----|----------|
| Preview | single image | low-poly GLB, 1K texture | < 30 s | quick preview |
| Standard | image / multi-image | medium mesh, 2K PBR | 1–3 min | general products |
| High | multi-image / video | dense mesh + subdiv, 4K–8K texture | 5–15 min | film / e-commerce |

## Tech Stack Summary

| Layer | Choice |
|-------|--------|
| Mobile | Kotlin + Jetpack Compose + CameraX + Filament |
| Backend | FastAPI + PostgreSQL + Redis + RabbitMQ |
| Worker | Python + Docker + CUDA 12.4 |
| Image→3D | TRELLIS.2 (hi-fi) / Stable Fast 3D (fast) |
| Video→3D | COLMAP + gsplat + SuGaR |
| Texture | xatlas + Real-ESRGAN |
| Storage | Cloudflare R2 + CDN |
| GPU cloud | Modal (primary) + RunPod Serverless (fallback) |
| Observability | Prometheus + Grafana + Sentry |
