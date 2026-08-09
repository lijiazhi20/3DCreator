# 3DCreator — Bilingual Documentation Index

> 3DCreator 把手机拍摄的照片/视频转换为高精度 3D 模型，对标华为鸿蒙系统 Remy。
> 3DCreator turns phone photos/videos into high-resolution 3D models — inspired by Huawei HarmonyOS "Remy".

## 文档导航 / Documentation

| 文档 / Doc | 中文 (zh) | English (en) |
|-----------|-----------|--------------|
| 总体架构 / Architecture | `../02-architecture.md` | `02-architecture.md` |
| 硬件评估 / Hardware | `../01-hardware-assessment.md` | `01-hardware-assessment.md` |
| Android 设计 / Android | `../03-android-design.md` | `03-android-design.md` |
| 3D 重建 Pipeline / Pipeline | `../04-ml-pipeline.md` | `04-ml-pipeline.md` |
| 后端 API / Backend | `../05-backend-design.md` | `05-backend-design.md` |
| DevOps 部署 / DevOps | `../06-devops-design.md` | `06-devops-design.md` |
| 路线图 / Roadmap | `../07-roadmap.md` | `07-roadmap.md` |

> 中文文档位于 `docs/` 根目录；英文文档位于 `docs/en/`。
> Chinese docs live at `docs/` root; English docs at `docs/en/`.

## 关键决策 / Key Decisions

- **端云分工 / Hybrid**: 手机端只做采集/上传/预览/分享；高精度重建在云端 GPU。
  Phone does capture/upload/preview/share; heavy reconstruction runs on cloud GPU.
- **移动端 / Mobile**: Kotlin + Jetpack Compose + CameraX + Filament。
- **后端 / Backend**: FastAPI + PostgreSQL + Redis + RabbitMQ + R2/S3。
- **重建模型 / Models**: 图片 TRELLIS.2 / Stable Fast 3D；视频 COLMAP + gsplat。
- **GPU 云 / GPU cloud**: Modal 为主，RunPod 为备。
- **多语言 / i18n**: App 支持中英文运行时切换（见 `03-android-design.md` §5）。
  App supports runtime zh/en switch (see `03-android-design.md` §5).

## 项目结构 / Project Layout

```
3DCreator/
├── docs/        # 中文设计文档 (zh)
├── docs/en/     # English design docs
├── backend/     # FastAPI 后端
├── worker/      # GPU 重建 worker
├── android/     # Android 客户端指南
└── devops/      # 部署配置
```
