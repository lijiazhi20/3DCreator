# Backend & API Design

## Stack

- **Framework**: FastAPI (Python 3.11+)
- **Database**: PostgreSQL 16
- **Cache / Queue**: Redis 7 + RabbitMQ
- **Auth**: Supabase Auth (JWT)
- **Storage**: Cloudflare R2 + CDN
- **Task Queue**: Celery (GPU workers)

## Data Models

```python
class User:    id, email, credits, created_at
class Asset:   id, owner_id, filename, content_type, size, storage_key, created_at
class Job:     id, owner_id, asset_id, job_type, tier, status,
               progress, result_key, preview_key, credits_charged,
               created_at, updated_at
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | health check |
| POST | `/auth/login` | login (Supabase proxy) |
| GET | `/auth/me` | current user |
| POST | `/upload/presign` | presigned upload URL |
| POST | `/jobs` | create reconstruction job |
| GET | `/jobs` | list jobs |
| GET | `/jobs/{id}` | job detail |
| DELETE | `/jobs/{id}` | cancel / delete |
| GET | `/jobs/{id}/download` | result download URL |
| POST | `/webhooks/worker` | worker progress / result callback |

## Core Flow

### Create Job
1. Validate user credits.
2. Atomically deduct credits and create `Job` (same transaction).
3. Publish message to RabbitMQ `gpu_jobs`.
4. Return job id + ETA.

### Worker Callback
- Worker auth via `X-Worker-Secret`.
- Idempotency key required.
- Update `Job.status`, `progress`, `result_key`, `preview_key`.
- Auto-retry 3×; on final failure, refund credits.

## Security

- JWT verification (Supabase)
- File magic-byte validation + size cap
- Redis token-bucket rate limiting
- Presigned URL TTL 15 min
- Webhook shared secret + idempotency
