# Roadmap

## Phase 0: Decisions & Scaffold (current)

- [x] Hardware feasibility assessment
- [x] Multi-agent architecture design
- [x] Project scaffold & docs
- [ ] Local backend + worker runnable
- [ ] Android project created with basic nav

## Phase 1: MVP — Image → GLB

Goal: user uploads one image from Android, gets a previewable GLB in 30 s.

- [ ] Android: CameraX capture + Photo Picker
- [ ] Android: upload progress + job list
- [ ] Backend: presigned upload + job creation + queue
- [ ] Worker: Stable Fast 3D inference (lowest VRAM path)
- [ ] Android: Filament GLB preview
- [ ] End-to-end integration

## Phase 2: Video / Multi-image → 3D

- [ ] Multi-image upload + video frame extraction
- [ ] COLMAP + 3D Gaussian Splatting pipeline
- [ ] PLY/Splat preview (splat renderer)
- [ ] Mesh export (SuGaR)

## Phase 3: HD Enhancement & Monetization

- [ ] xatlas UV unwrap
- [ ] Real-ESRGAN 4K–8K texture upscale
- [ ] PBR material generation
- [ ] Accounts, credits, payments
- [ ] Export formats: GLB / OBJ / FBX / USDZ / STL

## Phase 4: Production

- [ ] GPU auto-scaling
- [ ] Monitoring, alerting, cost dashboard
- [ ] Full CI/CD release
- [ ] App store release

## Next Steps

1. Confirm Phase 1 stack and model choice.
2. Stand up a local runnable backend + worker.
3. Create the Android project and implement capture/upload flow.
