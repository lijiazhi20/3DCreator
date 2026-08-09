# 后端 API 设计

## 技术栈

- **Framework**: FastAPI (Python 3.11+)
- **Database**: PostgreSQL 16
- **Cache / Queue**: Redis 7 + RabbitMQ
- **Auth**: Supabase Auth (JWT)
- **Storage**: Cloudflare R2 + CDN
- **Task Queue**: Celery (GPU workers)

## 目录结构

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── config.py
│   ├── models.py          # SQLAlchemy models
│   ├── dependencies.py    # auth / db / redis
│   ├── routers/
│   │   ├── health.py
│   │   ├── auth.py
│   │   ├── upload.py
│   │   └── jobs.py
│   └── services/
│       ├── storage.py
│       └── queue.py
├── Dockerfile
├── requirements.txt
└── docker-compose.yml
```

## 数据模型

```python
class User(Base):
    id: UUID
    email: str
    credits: int
    created_at: datetime

class Asset(Base):
    id: UUID
    owner_id: UUID
    filename: str
    content_type: str
    size: int
    storage_key: str
    created_at: datetime

class Job(Base):
    id: UUID
    owner_id: UUID
    asset_id: UUID
    job_type: str          # image_to_3d | video_to_3d
    tier: str              # preview | standard | high
    status: str            # queued | running | succeeded | failed
    progress: int          # 0-100
    result_key: str | None
    preview_key: str | None
    credits_charged: int
    created_at: datetime
    updated_at: datetime
```

## API 端点

| Method | Path | 说明 |
|--------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/auth/login` | 登录（Supabase 代理） |
| GET | `/auth/me` | 当前用户 |
| POST | `/upload/presign` | 获取预签名上传 URL |
| POST | `/jobs` | 创建重建任务 |
| GET | `/jobs` | 任务列表 |
| GET | `/jobs/{id}` | 任务详情 |
| DELETE | `/jobs/{id}` | 取消/删除任务 |
| GET | `/jobs/{id}/download` | 获取结果下载 URL |
| POST | `/webhooks/worker` | Worker 进度/结果回调 |

## 核心流程

### 创建任务

1. 校验用户 credits。
2. 原子扣减 credits 并创建 `Job`（同一事务）。
3. 发布消息到 RabbitMQ `gpu_jobs`。
4. 返回 job id 与预计时间。

### Worker 回调

- Worker 使用 `X-Worker-Secret` 鉴权。
- 回调需带 idempotency key。
- 更新 `Job.status`、`progress`、`result_key`、`preview_key`。
- 失败时自动重试 3 次，最终失败退还 credits。

## 安全

- JWT 验证（Supabase）
- 文件 magic-byte 校验 + 大小上限
- Redis token-bucket 限流
- 预签名 URL 有效期 15 分钟
- Webhook 使用 shared secret + idempotency
