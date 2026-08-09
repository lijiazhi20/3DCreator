# 硬件评估：Honor 骁龙 8 Gen 2

## 设备参数

| 项目 | 参数 |
|------|------|
| 设备 | Honor 旗舰机 |
| OS | MagicOS 10.0 / Android 16 |
| SoC | 第二代骁龙 8 移动平台 (SM8550) |
| GPU | Adreno 740 |
| RAM | 16 GB |
| 存储 | 1 TB |
| 屏幕 | 2344 × 2156（内屏）/ 2376 × 1060（外屏） |
| NPU | Qualcomm Hexagon (AI Engine Gen 2) |

## 端侧算力边界

### 可以做

- 4K 视频录制与多帧连拍
- 轻量图像预处理（去噪、对齐、缩略图）
- 小型量化模型的快速预览：
  - Stable Fast 3D（量化版）约 0.5–2 s 生成低多边形预览
  - 小型 NeRF/GS 查看器（几十 MB 模型）
- 本地 GLB/OBJ/PLY 预览（Filament）

### 不建议做（当前 SOTA 对手机太重）

- TRELLIS.2 / Hunyuan3D 2.1 全量模型（24 GB+ VRAM 需求）
- COLMAP + 3D Gaussian Splatting 完整重建流程
- 4K–8K 纹理超分与 PBR 烘焙
- 多视图大场景 photogrammetry

## 推荐分工

| 阶段 | 端侧 | 云侧 |
|------|------|------|
| 采集 | 拍照/录像、选图、本地缓存 | — |
| 预处理 | 压缩、抽帧、生成缩略图 | — |
| 重建 | 可选：轻量预览模型 | 主流程：TripoSG / TRELLIS / COLMAP+3DGS |
| 增强 | — | 网格细分、UV、纹理超分 |
| 预览 | Filament 渲染 GLB/PLY/Splat | 生成预览图/视频 |
| 导出/分享 | 下载到本地、系统分享 | CDN 分发 |

## 结论

应采用 **「端侧负责体验，云侧负责算力」** 的混合架构。端侧仅在 Wi-Fi/充电场景下可选运行轻量预览模型，主力高清重建必须上云。
