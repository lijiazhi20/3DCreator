# 3D 重建 Pipeline 设计

> 本文件是 ML/3D Pipeline 的权威说明。后端 / Android 团队请重点看
> 「§0 产品区分」「§质量分级（VRAM/时间估算）」和末尾「Job 类型 → Pipeline 映射表」。

---

## §0 最重要的产品区分：生成式 vs 重建式

系统有两条**本质不同**的 3D 生成路径，必须向用户讲清楚：

### A. 单图 → 3D（生成式 / "快速预览"）
- **输入**：1 张照片。
- **原理**：神经网络**猜**出一个 3D 模型。
- **特点**：快（秒级~分钟级），适合快速创意预览。
- **代价**：物体的**背面 / 底面 / 被遮挡部分都是模型"想象"出来的，不是测量得到的**。
- **生产模型**：TRELLIS.2、Stable Fast 3D、Hunyuan3D 2.x。
- **代码**：`worker/pipelines/image_to_3d.py`（GENERATIVE 模式）。

### B. 多图 / 视频 → 3D（重建式 / "高精度"，★ 用户真正目标）
- **输入**：环拍 360° 的 20–50 张照片，**或**一段视频（= 多帧）。
- **原理**：COLMAP 求相机位姿（SfM）→ 3D Gaussian Splatting 训练 →
  SuGaR / TSDF 网格提取 → 带纹理网格。
- **特点**：**真实几何重建**，背面 / 底面 / 遮挡部分都是**测量**出来的。
- **代价**：慢（分钟级），需要 GPU。
- **代码**：
  - 多图 `worker/pipelines/multi_image_to_3d.py`（RECONSTRUCTION / 高精度）
  - 视频 `worker/pipelines/video_to_3d.py`（先 ffmpeg 抽帧，再走 multi_image 同一条高精度路径）

> **一句话**：生成式是"猜"，快但不准；重建式是"量"，慢但高精度。
> 用户要的"高精度还原真实物体"走 B 路径（多图 / 视频）。

---

## 输入类型与对应模型

### 1. 单图 → 3D（生成式）

| 模型 | 特点 | 许可 | 适用 |
|------|------|------|------|
| **TRELLIS.2** | 高保真、PBR-native、细节好 | MIT | High 级输出 |
| **Stable Fast 3D** | 0.5 s 级、低 VRAM | Stability License | Preview / Standard |
| **TripoSG** | MIT、几何强、无材质 | MIT | 零法律风险 fallback |
| **Hunyuan3D 2.1** | 最高保真 | 自定义，限制欧盟/英国/韩国 | 需法务确认 |

### 2. 视频 / 多图 → 3D（重建式）

| 组件 | 用途 | 许可 |
|------|------|------|
| **COLMAP** | SfM + MVS，相机位姿与稀疏点云 | BSD |
| **gsplat** | 3D Gaussian Splatting 训练与渲染 | Apache-2.0 |
| **SuGaR** | splat → 可编辑网格 | 研究/非商业需确认 |

> 避免原始 Inria 3DGS（非商业许可）和 Meshroom（AGPL）。

---

## Pipeline 阶段

```
源文件
  │
  ▼
[预处理]
  • 图片：去背景/对齐/缩放到统一尺寸
  • 视频：FFmpeg 抽帧、筛选关键帧、去模糊
  • 多图：COLMAP 特征匹配
  │
  ▼
[重建]
  ├─ 单图分支：TRELLIS.2 / Stable Fast 3D / TripoSG        （生成式，猜）
  └─ 视频/多图分支：COLMAP → gsplat → SuGaR（可选网格化）   （重建式，量 ★）
  │
  ▼
[精修]
  • 网格清理、拓扑重划分
  • xatlas UV 展开
  • Real-ESRGAN x4 纹理超分
  • 可选 PBR 材质拟合
  │
  ▼
[导出]
  GLB / OBJ / FBX / USDZ / STL
```

---

## 质量分级（预览 vs 高精度）与 VRAM / 时间估算

> 时间 / VRAM 为 **A100 80GB** 与 **L4 24GB** 上的工程估算，用于排期与
> 容量规划。生成式走小显存卡即可；重建式需要大显存。

| 等级 | 输入 | 路径 | 模型 / 组件 | 输出 | L4 24GB | A100 80GB |
|------|------|------|------------|------|---------|-----------|
| **Preview** | 单图 | 生成式 | Stable Fast 3D | 低多边形 + 1K 纹理 | ~0.5–2 s，~4 GB | ~0.5 s，~3 GB |
| **Standard** | 单图 | 生成式 | TRELLIS.2 | 中等多边形 + 2K PBR | 30–60 s，~12 GB | 20–40 s，~10 GB |
| **Standard** | 多图 | 重建式 ★ | COLMAP + gsplat(轻) | 中密网格 + 2K | 3–6 min，~16 GB | 2–4 min，~14 GB |
| **High** | 多图/视频 | 重建式 ★ | COLMAP + gsplat + SuGaR + ESRGAN | 高密网格 + 4K–8K 纹理 | 8–15 min，~20 GB | 5–10 min，~24 GB |

- **高精度（high precision）= 多图 / 视频 → 重建式路径。** 这是测量得到的真实几何。
- 生成式路径（单图）无论选多高 tier，**背面/底面仍为幻觉**，不算"高精度还原"。

---

## Job 类型 → Pipeline 映射表（后端 / Android 必读）

Worker 通过 `job_type` 字段分发到对应 pipeline；所有 pipeline 暴露同一签名：

```
run(input_paths: list[str], params: dict, mode: str) -> dict
# 返回 {"output_path": str, "format": str, "metrics": dict}
```

| job_type（传给 worker） | 调用模块 | 模式 | 说明 |
|--------------------------|----------|------|------|
| `single_image` | `pipelines.image_to_3d` | 生成式 | 单图快速预览（猜） |
| `image_to_3d`（旧别名） | `pipelines.image_to_3d` | 生成式 | 兼容旧字段 |
| `multi_image` | `pipelines.multi_image_to_3d` | **重建式 ★** | 20–50 张环拍照，高精度 |
| `video` | `pipelines.video_to_3d` | **重建式 ★** | 视频抽帧后走高精度路径 |
| `video_to_3d`（旧别名） | `pipelines.video_to_3d` | **重建式 ★** | 兼容旧字段 |

- `single_image` = 快速预览；`multi_image` / `video` = **高精度重建（用户目标）**。
- 本地 dev / 无 GPU worker：每个 pipeline 都有 dev fallback，用 trimesh（若有）
  或内置纯 Python GLB 写出器产出**合法 GLB**，保证端到端闭环可跑。
- 生产 GPU worker：设 `INFERENCE_BACKEND=gpu`，prod 模型缺失时**自动降级**到
  dev fallback，不会让任务崩溃。

---

## Worker 容器策略

- 基础镜像：`nvidia/cuda:12.4.1-devel-ubuntu22.04`
- 每个 Worker 启动时从对象存储拉取模型权重（cache 到本地 EBS）。
- 单容器单任务，避免显存碎片。
- 超时/失败自动重试 3 次，失败退款。

## 商业化 API 兜底

若开源链路 SLA 不达标，可接入：
- fal.ai（Tripo / Rodin）
- Meshy API
- Tripo API

## 法务注意

- Hunyuan3D 2.1：排除 EU/UK/SK，MAU > 1M 需付费许可。
- Stable Fast 3D：年收入 > $1M 需 Stability Enterprise。
- 上线前需法务 review。
