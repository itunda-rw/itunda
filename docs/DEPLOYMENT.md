# Deployment Guide - Itunda Fintech System

> **Rewritten 2026-07-12.** The previous version of this file was generic AWS/Postgres/Stripe
> boilerplate that did not match anything in this repo (no Terraform, no AWS SDK usage, no
> Stripe key anywhere in code, and the real database is MySQL, not Postgres/DocumentDB). This
> version describes what actually exists to deploy today: real Dockerfiles, real Kubernetes
> manifests under `infra/k8s/`, and a real local Docker Compose stack. It does not invent a
> cloud provider or a CI/CD pipeline that isn't in this repo. See `docs/ARCHITECTURE.md` §4 for
> the verification history behind these manifests (what's been build/YAML-verified vs. what
> still needs a live cluster to confirm).
>
> Rwanda-specific note: nothing here should ever route through Toss's own systems — Toss
> operates in South Korea under its own licenses. Itunda's rails are MTN Mobile Money, Airtel
> Money, RNP, and Rwandan banks; that separation is architectural, not just a deployment detail.

## What's real to deploy today

| Component | Image build | K8s manifest | Port |
|---|---|---|---|
| `services/backend` (canonical Kotlin/Spring backend) | `services/backend/Dockerfile` | `infra/k8s/{production,staging}/backend.yaml` | 4001 |
| `services/api-gateway` | `services/api-gateway/Dockerfile` | `infra/k8s/{production,staging}/api-gateway.yaml` | 3000 |
| `services/microservices/ledger-service` | `services/microservices/ledger-service/Dockerfile` | `infra/k8s/{production,staging}/ledger-service.yaml` | 8082 |
| `services/microservices/payment-service` | `services/microservices/payment-service/Dockerfile` | `infra/k8s/{production,staging}/payment-service.yaml` | 8081 |
| Namespace, Grafana, Prometheus, network policies | — | `infra/k8s/base/`, `infra/k8s/monitoring/`, `infra/k8s/production/policies.yaml` | — |
| Local-only MySQL/Redis/Kafka for cluster dev | — | `infra/k8s/local-dev/{mysql,redis,kafka}.yaml` | — |

`services/microservices` and `services/backend` deliberately run against **separate MySQL
databases** (`itunda_ledger` for the backend, isolated DBs for the microservices) — they are
real, still-unreconciled architectural directions, not a bug (`docs/ARCHITECTURE.md` §1 has the
full reasoning). No manifest here deploys MySQL, Redis, or Kafka as a production StatefulSet;
`production`/`staging` manifests point at external managed instances via ConfigMaps/Secrets
provisioned out-of-band (see below). `infra/k8s/local-dev/` is the only place this repo runs
those as pods, and only for local cluster development.

## 1. Local development (no cluster needed)

The fastest real path, and the one actually exercised in this repo's own verification history:

```bash
# Databases, cache, Kafka -- infra/docker-compose.yml
cd infra && docker compose up -d

# Canonical backend
cd services/backend && ./gradlew :app:bootRun

# Microservices (separate terminals)
cd services/microservices/ledger-service && ../gradlew bootRun
cd services/microservices/payment-service && ../gradlew bootRun

# Gateway
cd services/api-gateway && BACKEND_URL=http://localhost:4001 yarn node index.js
```

MySQL runs on host port `3307` (container port `3306`), Redis on `16379`, matching
`infra/docker-compose.yml`'s pinned `mysql:8.0.39` (see that file's own comment on why it's
pinned, not `mysql:8` floating) and `redis:7-alpine`. Flyway (`services/backend`) manages the
schema — do not hand-run migrations; `ddl-auto: validate` will fail loudly if the schema and
entities disagree, by design.

## 2. Building images

```bash
docker build -t itunda/backend:latest -f services/backend/Dockerfile .
docker build -t itunda/api-gateway:latest services/api-gateway
docker build -t itunda/ledger-service:latest services/microservices/ledger-service
docker build -t itunda/payment-service:latest services/microservices/payment-service
```

`services/backend/Dockerfile` builds only the `:app` Gradle module (the one module that
assembles a runnable jar — see its own header comment for the two wrong-module Dockerfiles it
replaced) and runs as a non-root `itunda` user on port `4001`.

## 3. Kubernetes deployment

```bash
# Namespace + policies
kubectl apply -f infra/k8s/base/
kubectl apply -f infra/k8s/production/policies.yaml

# Provision secrets and config out-of-band first (never commit real values):
kubectl create secret generic itunda-db-credentials \
  --from-literal=username=itunda --from-literal=password=<real value> -n itunda
kubectl create secret generic itunda-jwt-secret \
  --from-literal=secret=<real value> -n itunda
# Plus itunda-db-config, itunda-microservices-db-config, itunda-redis-config,
# itunda-kafka-config ConfigMaps -- see each manifest's header for the exact keys it reads.

# Deploy services
kubectl apply -f infra/k8s/production/   # or infra/k8s/staging/
kubectl get pods -n itunda
kubectl rollout status deployment/backend -n itunda
```

Every service exposes Spring Boot Actuator at `/actuator/health` (added specifically so these
liveness/readiness probes have something real to hit — see `docs/ARCHITECTURE.md` §4) and
`/actuator/prometheus` for the Grafana/Prometheus manifests under `infra/k8s/monitoring/`.

**Not yet decided or built, and not asserted here as if they were:** which cloud provider (or
whether this stays self-hosted), managed-MySQL provisioning, DNS, TLS certificate issuance, a
CI/CD pipeline, autoscaling policy, and multi-region/DR topology. Claiming any of these as done
before they exist would repeat exactly the fabrication problem `docs/TOSS_ARCHITECTURE_FACTS.md`
§5 and `SECURITY.md`'s rewrite already had to correct elsewhere in this repo.

## 4. Verification status

Everything above has been build/YAML-verified (`./gradlew build` for every backend module,
`docker build` for every image, YAML-validated with both Ruby's parser and `kubectl`'s
client-side parsing) but **not verified against a live cluster** — no Docker daemon capable of
running `kind`/a real cluster was available in the environment this was last checked from. Before
trusting this against real traffic: run `kubectl apply --dry-run=server`, then a real cluster
smoke test of the full request path (gateway → backend → MySQL) the way `docs/ARCHITECTURE.md`
§1's Docker Compose runs already did locally.

## 5. Production readiness checklist (target, not current state)

- [ ] Managed MySQL provisioned with real backup/replication (currently: local Compose only)
- [ ] Secrets provisioned via a real secrets manager, not `kubectl create secret` by hand
- [ ] TLS termination and certificate rotation configured
- [ ] DNS and load balancer routing configured
- [ ] CI/CD pipeline building and pushing these four images on merge to `main`
- [ ] Kafka running as a durable cluster, not `infra/docker-compose.yml`'s local single-broker setup
- [ ] Alerting rules wired in Grafana (`infra/k8s/monitoring/grafana.yaml` ships the deployment, not alert rules)
- [ ] Live-cluster smoke test of every money-moving endpoint before any real-money traffic
- [ ] Security review of the deployed configuration (separate from the code-level review in `SECURITY.md`)

None of the above should be marked done until it's actually true of a running environment —
that's the same discipline `docs/TOSS_PARITY_MATRIX.md` and `SECURITY.md` apply to code status.

---

**Last updated**: 2026-07-12
