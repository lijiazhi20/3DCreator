# DevOps & Deployment

## GPU Cloud Options

| Platform | Mode | Price | Use |
|----------|------|-------|-----|
| **Modal** | Serverless, per-second | ~$0.3–1.5/hr | primary worker |
| **RunPod Serverless** | Serverless | similar | fallback |
| **Lambda Labs** | Reserved | ~$0.6–2/hr | steady volume |
| **Vast.ai** | Spot | cheap, unstable | dev/QA |
| **AutoDL** | CN GPU cloud | hourly | China region |

## Container Images

| Image | Description |
|-------|-------------|
| `backend-api` | FastAPI stateless |
| `worker-inference` | CUDA 12.4 + Python + model deps |
| `admin-web` | optional Next.js dashboard |
| `migrations` | Alembic DB migrations |

## Deployment Topology

```
Internet → Cloudflare (DNS+SSL+CDN)
   ├── Vercel (Admin Web)
   └── Modal / RunPod (Workers)
            │
   ┌────────┼────────┐
   ▼        ▼        ▼
 Postgres  Redis   R2 Object Storage
```

## CI/CD

- GitHub Actions split workflows: `backend`, `worker`, `android`, `web`.
- OIDC auth (no long-lived secrets).
- Tag-based rollback.

## Observability

- Metrics: Prometheus + Grafana (GPU util, queue depth, API latency)
- Logs: Loki
- Errors: Sentry
- Alerts: Alertmanager → Slack/WeCom
- Cost dashboard: GPU hours per job/user

## MVP Cost Estimate

| Item | 100 jobs/mo | 1000 jobs/mo |
|------|-------------|--------------|
| Fixed infra | ~$100–180 | ~$100–180 |
| Compute (Preview) | ~$2–5 | ~$20–50 |
| Compute (Standard) | ~$20–60 | ~$200–600 |
| Compute (High) | ~$60–200 | ~$600–2000 |
| **Total** | **~$105–300** | **~$500–900+** |

## Required Infrastructure

- [ ] Domain + Cloudflare DNS/SSL
- [ ] Object storage bucket (R2/S3)
- [ ] PostgreSQL
- [ ] Redis / RabbitMQ
- [ ] GPU worker account
- [ ] Container registry (ghcr.io / ECR)
- [ ] Secrets management
