# Roadmap

## Phase 0：决策与骨架（当前）

- [x] 硬件可行性评估
- [x] 多 Agent 架构设计
- [x] 项目骨架与文档
- [ ] 本地后端 + Worker 可运行
- [ ] Android 工程创建与基础导航

## Phase 1：MVP — 单图 → GLB

目标：用户从 Android 上传一张图片，30 秒内拿到可预览的 GLB 模型。

- [ ] Android：CameraX 拍照 + Photo Picker 选图
- [ ] Android：上传进度 + 任务列表
- [ ] 后端：预签名上传 + 任务创建 + 队列
- [ ] Worker：Stable Fast 3D 推理（最低 VRAM 路径）
- [ ] Android：Filament GLB 预览
- [ ] 端到端联调

## Phase 2：视频 / 多图 → 3D

- [ ] 多图上传与视频抽帧
- [ ] COLMAP + 3D Gaussian Splatting pipeline
- [ ] PLY/Splat 预览（集成 splat renderer）
- [ ] 网格化导出（SuGaR）

## Phase 3：高清增强与商业化

- [ ] xatlas UV 展开
- [ ] Real-ESRGAN 4K–8K 纹理超分
- [ ] PBR 材质生成
- [ ] 用户账户、积分、支付
- [ ] 导出格式：GLB / OBJ / FBX / USDZ / STL

## Phase 4：生产化

- [ ] GPU 自动扩缩容
- [ ] 监控告警与成本看板
- [ ] CI/CD 全自动发布
- [ ] 应用市场上架

## 下一步行动

1. 确认 Phase 1 MVP 的技术栈与模型选择。
2. 搭建本地可运行的后端 + Worker。
3. 创建 Android 工程并实现首页拍摄/上传流程。
