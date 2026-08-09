# 3DCreator

> 手机端图片/视频 → 高精度 3D 模型生成应用

## 一句话定位

3DCreator 是一款面向 Android 手机的 3D 内容创作工具：用户拍摄或上传照片/视频，云端 GPU 集群完成 3D 重建与高清增强，最终在手机上预览、下载、分享 3D 模型。

## 目标设备

- Honor 旗舰机，MagicOS 10.0 / Android 16
- 骁龙 8 Gen 2 (SM8550) / Adreno 740 / 16 GB RAM / 1 TB 存储

## 核心架构

```
┌─────────────────────────────────────┐
│  Android App (Kotlin + Jetpack Compose)
│  拍摄 / 选图 / 上传 / 任务看板 / 3D 预览 / 导出
└──────────────┬──────────────────────┘
               │ HTTPS + WebSocket
┌──────────────▼──────────────────────┐
│  FastAPI Backend                    │
│  认证 / 预签名上传 / 任务调度 / 计费等
└──────────────┬──────────────────────┘
               │ RabbitMQ / Redis
┌──────────────▼──────────────────────┐
│  GPU Worker (Docker on RunPod/Modal)│
│  图片→3D (TRELLIS.2 / Stable Fast 3D)
│  视频→3D (COLMAP + 3D Gaussian Splatting)
│  高清增强 (xatlas + Real-ESRGAN)
└──────────────┬──────────────────────┘
               │ GLB / OBJ / PLY / USDZ
┌──────────────▼──────────────────────┐
│  R2/S3 Object Storage + Cloudflare CDN
└─────────────────────────────────────┘
```

## 项目目录

```
3DCreator/
├── docs/               # 架构与设计方案
├── android/            # Android 客户端（Kotlin + Jetpack Compose）
├── backend/            # FastAPI 后端服务
├── worker/             # 云端 GPU 重建 worker
└── devops/             # Docker、CI/CD、监控
```

## 快速开始

### 1. 启动本地后端

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
docker-compose up -d   # postgres + redis
uvicorn app.main:app --reload
```

### 2. 启动本地 Worker（需 GPU，可选）

```bash
cd worker
pip install -r requirements.txt
python worker.py
```

### 3. Android

使用 Android Studio 打开 `android/` 目录，连接 Honor 设备或模拟器运行。

## 文档索引

1. [硬件评估](docs/01-hardware-assessment.md)
2. [总体架构](docs/02-architecture.md)
3. [Android 设计](docs/03-android-design.md)
4. [3D 重建 Pipeline](docs/04-ml-pipeline.md)
5. [后端 API 设计](docs/05-backend-design.md)
6. [DevOps 部署](docs/06-devops-design.md)
7. [Roadmap](docs/07-roadmap.md)

## 状态

当前阶段：MVP 骨架已完成，下一步实现「单张图片 → GLB」端到端闭环。
