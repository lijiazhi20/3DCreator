# DevOps 与部署

## GPU 云服务选型

| 平台 | 模式 | 价格参考 | 用途 |
|------|------|----------|------|
| **Modal** | Serverless，按秒计费 | ~$0.3–1.5/hr GPU | 主 worker 平台 |
| **RunPod Serverless** | Serverless | 类似 Modal | 备用 |
| **Lambda Labs** | 预留 GPU | ~$0.6–2/hr | 稳定量产后 |
| **Vast.ai** | 竞价实例 | 便宜但不稳定 | 开发/测试 |
| **AutoDL** | 国内 GPU 云 | 按小时 | 中国大陆部署 |

## 容器镜像

| 镜像 | 说明 |
|------|------|
| `backend-api` | FastAPI 无状态服务 |
| `worker-inference` | CUDA 12.4 + Python + 模型依赖 |
| `admin-web` | 可选 Next.js 管理后台 |
| `migrations` | Alembic 数据库迁移 |

## 部署拓扑

```
Internet
   │
Cloudflare (DNS + SSL + CDN)
   │
┌─────────────┐      ┌─────────────┐
│  Vercel     │      │  Modal /    │
│  Admin Web  │      │  RunPod     │
└─────────────┘      │  Workers    │
                     └──────┬──────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   PostgreSQL            Redis             R2 Object Storage
   (Supabase/             (Queue/           (source + results)
   managed)               cache)
```

## CI/CD

- GitHub Actions 分工作流：
  - `backend.yml`：测试、构建镜像、推送 ghcr.io
  - `worker.yml`：构建 CUDA 镜像、推送 ghcr.io
  - `android.yml`：lint、单元测试、构建签名 AAB
  - `web.yml`：Next.js 构建并部署 Vercel
- 使用 OIDC 认证，无需长期密钥。
- 按 tag 回滚。

## 监控与日志

- **指标**：Prometheus + Grafana（GPU 利用率、队列深度、API 延迟）
- **日志**：Loki / Grafana
- **错误追踪**：Sentry
- **告警**：Alertmanager → Slack/企业微信
- **成本看板**：按任务/用户统计 GPU 耗时

## 成本估算（MVP）

| 项目 | 100 jobs/月 | 1000 jobs/月 |
|------|-------------|--------------|
| 固定 infra（DB/Redis/域名/存储） | ~$100–180 | ~$100–180 |
| 计算（Preview） | ~$2–5 | ~$20–50 |
| 计算（Standard） | ~$20–60 | ~$200–600 |
| 计算（High） | ~$60–200 | ~$600–2000 |
| 总计 | ~$105–300 | ~$500–900+ |

> 实际成本高度依赖质量等级与模型选择。

## 必需基础设施清单

- [ ] 域名 + Cloudflare DNS/SSL
- [ ] 对象存储桶（R2 或 S3）
- [ ] PostgreSQL 数据库
- [ ] Redis / RabbitMQ
- [ ] GPU Worker 服务账号
- [ ] Container Registry（ghcr.io 或 ECR）
- [ ] 密钥管理（GitHub Secrets / 1Password / Doppler）
