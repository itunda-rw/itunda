# Active-Active Rehearsal (Multipass Snapshot)

Last updated: 2026-07-14

This file now records the **current** state of the local Multipass private-cloud rehearsal.
Earlier versions mixed one real historical MySQL failover drill with broader "active-active"
language and made the present topology sound more symmetric than it is. The current wording is
strictly about what is live today.

For the Toss comparison and the improvement plan, see
[docs/PRIVATE_CLOUD_BLUEPRINT.md](PRIVATE_CLOUD_BLUEPRINT.md) and
[docs/TOSS_STYLE_ARCHITECTURE.md](TOSS_STYLE_ARCHITECTURE.md).

## What the local rehearsal actually is

Two Ubuntu 22.04 VMs:

- `itunda-dc-a` — `192.168.252.2`
- `itunda-dc-b` — `192.168.252.3`

Both VMs run Docker inside the private cloud. The current container layout is:

- `itunda-dc-a`: `mysql-a`, `kafka-a`
- `itunda-dc-b`: `mysql-b`, `redis-b`, `kafka-b`

Above that VM layer, the current live app/platform layer is Kubernetes, not just host containers:

- two-node `kubeadm` cluster
- Istio ingress
- Argo Rollouts
- Prometheus + Grafana + node-exporter
- a Kubernetes-hosted MM2 worker in `kafka-replication`

## Current verified topology

Audited live on 2026-07-14:

- **MySQL**
  - `dc-b` is the writable primary
  - `dc-a` is a replica following `dc-b`
  - this is a **single-writer failover pattern**, not multi-primary
- **Redis**
  - only `dc-b` runs Redis
  - current role is singleton master
- **Kafka**
  - each VM runs its own single-broker KRaft cluster
  - the two brokers do **not** form one shared quorum
  - a Kubernetes-hosted MM2 worker is live against those two brokers
  - this is still the OSS topic-prefix baseline, not Toss-equivalent same-topic mirroring
- **App layer**
  - the four stateless services are live on Kubernetes through Argo Rollouts
  - Istio ingress is live on `:30082`

## What this means

The current Multipass environment is **not** a full active-active platform. The honest shape is:

- stateless app layer: live on Kubernetes, but not enough by itself to call the whole platform
  active-active
- MySQL: real single-writer plus replica
- Redis: singleton
- Kafka: two independent clusters plus a real MM2 baseline, but not Toss-style same-topic active-active

That distinction matters because Toss's published active-active patterns are about independent
failure domains and truthful operations, not just running two machines and calling it done.

## What was historically proven here

One thing **was** proven in an earlier drill and is still worth keeping:

- a real MySQL failover/promotion drill was successfully executed on this rehearsal stack

That proves the repo can rehearse single-writer promotion logic locally. It does **not** prove
that the current stack is symmetric active-active for the whole platform.

## How to inspect it now

Use the repo-native audit instead of relying on stale prose:

```bash
yarn audit:private-cloud
yarn verify:private-cloud
```

To generate local env vars against the current writer/master nodes:

```bash
yarn env:private-cloud > .env
```

Then run the local app stack against the private cloud:

```bash
yarn dev:ecosystem
```

## Honest next steps

1. Add Redis HA instead of a singleton master.
2. Rehearse MM2 offset-sync and failover behavior repeatedly, not just keep the worker running.
3. Move from topic-prefix MM2 to the stricter same-topic contract in `infra/kafka/active-active/toss-style-mirror-contract.yaml`.
4. Automate audits, failover drills, backup checks, and node bootstrap.

The Kafka drill is now repo-native:

```bash
yarn private-cloud:kafka:status
yarn private-cloud:kafka:drill
```

The failover drill is now repo-native:

```bash
yarn private-cloud:failover status
yarn private-cloud:failover promote itunda-dc-a
yarn private-cloud:failover promote itunda-dc-b
```
