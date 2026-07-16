# Private Cloud Platform Workflow

Last updated: 2026-07-14

This is the repo-native platform path for the Toss-style Kubernetes layer on top of the existing
Multipass private cloud.

It is intentionally separate from `yarn private-cloud:deploy`, which still applies the plain
Deployment-based workload manifests.

## What this workflow adds

- low-overhead Istio control plane for traffic management
- Argo Rollouts controller for progressive delivery
- Prometheus + Grafana + node-level exporters for host and VM-service metrics
- full rollout resources for:
  - `api-gateway`
  - `backend`
  - `ledger-service`
  - `payment-service`
- live MirrorMaker 2 baseline generated from the current audited Kafka broker IPs

## Install the platform controllers

```bash
yarn private-cloud:platform
```

Or install them separately:

```bash
yarn private-cloud:platform:mesh
yarn private-cloud:platform:rollouts
yarn private-cloud:platform:metrics-server
yarn private-cloud:platform:observability
```

Check platform state:

```bash
yarn private-cloud:k8s:status
yarn private-cloud:platform:status
```

These platform and progressive-delivery workflows now assume a live `kubeadm` cluster. If a
legacy `k3s` control plane is detected, the helpers fail fast and point you at
`scripts/private-cloud-bootstrap.sh migrate-kubeadm`.

## Apply the progressive workload layer

```bash
yarn private-cloud:progressive
```

This does three important things:

1. reuses the live audited DB/Redis/Kafka runtime config
2. removes the plain `Deployment` resources for the four stateless services
3. hands those workloads over to Argo Rollouts plus Istio `VirtualService` routing

Low-memory kubeadm rehearsal defaults:

- the control-plane node stays tainted by default; only set
  `ITUNDA_PRIVATE_CLOUD_ALLOW_WORKLOADS_ON_CONTROL_PLANE=1` if you deliberately want app pods on
  `itunda-dc-a`
- `ITUNDA_PRIVATE_CLOUD_ROLLOUT_PROFILE=auto` is the default; it falls back to a rolling-update
  Rollout profile when the lab only has one ready worker or `ITUNDA_PRIVATE_CLOUD_APP_REPLICAS=1`
  so Argo Rollouts does not keep trying to hold stable and canary pods at the same time
- when that rolling lab profile is active, the backend rollout is also patched down to a
  low-capacity request/limit (`100m/128Mi` request, `500m/512Mi` limit) so the single worker can
  actually schedule it alongside Istio and the other three services
- HPA is opt-in on this laptop rehearsal; set `ITUNDA_PRIVATE_CLOUD_ENABLE_HPA=1` if you want the
  `HorizontalPodAutoscaler` objects left in place
- Kafka topic creation is no longer forced on every progressive deploy; set
  `ITUNDA_PRIVATE_CLOUD_ENSURE_TOPICS_ON_DEPLOY=1` if you want that bootstrap step folded into the
  app rollout path

If you explicitly want traffic-split canaries on a larger cluster, set:

```bash
ITUNDA_PRIVATE_CLOUD_ROLLOUT_PROFILE=canary \
yarn private-cloud:progressive
```

If the kubeadm control plane gets saturated, run:

```bash
yarn private-cloud:platform:stabilize-control-plane
```

That is the low-memory recovery path: it removes the progressive-delivery HPAs when they are
disabled and re-applies the control-plane `NoSchedule` taint unless you explicitly opted out.
It also now preserves the `dc-a` MySQL replica and `dc-a` Kafka broker instead of stopping them,
because those are part of the honest failover-domain baseline for this rehearsal.

## Apply the Kafka active-active baseline

```bash
yarn private-cloud:kafka-replication
```

This deploys a real MM2 worker against the current audited brokers.

Important:

- the local rehearsal has one Kafka broker per DC, so the generated MM2 internal topics use
  replication factor `1`, not production-grade `3`
- the generated MM2 config now pins explicit topic/group exclude filters, short topic/group
  refresh intervals for local drills, plus
  `ephemeral-storage` requests/limits for the low-disk worker node
- this is the open-source baseline, not Toss-equivalent same-topic mirroring

Run the drill after deploying or reconfiguring MM2:

```bash
yarn private-cloud:kafka:status
yarn private-cloud:kafka:drill
```

## VM-level observability

Toss calls out Zabbix, Prometheus, Mimir, and Grafana as platform primitives. The local private
cloud now has the Prometheus/Grafana side plus VM-level collectors for:

- node CPU and memory
- MySQL writer/replica role and replica lag
- Redis role
- Kafka KRaft quorum voter count

The VM collectors are installed by the platform observability command:

```bash
yarn private-cloud:platform:observability
```

Or directly:

```bash
yarn private-cloud:observability:install-vm-metrics
yarn private-cloud:observability:status
```

## Access path

After Istio is installed:

```bash
curl -H 'Host: api.itunda.internal' http://<primary-ip>:30082/health
```

## Current limit

The progressive manifests are real, and by default they reference the repo's placeholder images:

- `ghcr.io/itunda/api-gateway:latest`
- `ghcr.io/itunda/backend:latest`
- `ghcr.io/itunda/ledger-service:latest`
- `ghcr.io/itunda/payment-service:latest`

If those images are not pullable in the cluster, the rollout resources will exist but the pods
will not become healthy. As of 2026-07-16, all four services have been built and pushed into the
arm64 rehearsal registry (`192.168.252.2:32000/itunda/*:2026-07-16`) and the live rollouts patched
to use them — `kubectl get pods -n itunda` shows all four `2/2 Running`. Building `backend`
(Spring Boot + Kotlin) needed more RAM/disk than the standing per-node footprint; see the
"Building the `backend` image hit the ceiling directly" section in
[docs/PRIVATE_CLOUD_BLUEPRINT.md](PRIVATE_CLOUD_BLUEPRINT.md) for the stop/resize/start recipe
that fixed it.

Use these env vars to point the rollouts at a private registry instead:

```bash
ITUNDA_API_GATEWAY_IMAGE=harbor.platform.itunda.internal/itunda/api-gateway:2026-07-14 \
ITUNDA_BACKEND_IMAGE=harbor.platform.itunda.internal/itunda/backend:2026-07-14 \
ITUNDA_LEDGER_IMAGE=harbor.platform.itunda.internal/itunda/ledger-service:2026-07-14 \
ITUNDA_PAYMENT_IMAGE=harbor.platform.itunda.internal/itunda/payment-service:2026-07-14 \
ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME=itunda-registry \
yarn private-cloud:progressive
```

Create that pull secret with:

```bash
yarn private-cloud:registry:apply-current-pull-secret -- harbor.platform.itunda.internal robot\$itunda pull-token
```

## Arm64 rehearsal registry workflow

The current Multipass cluster is `arm64`, so the repo now includes a real open OCI registry
rehearsal on Kubernetes to unblock app distribution on this laptop.

Install the registry and configure the node runtime to trust it:

```bash
yarn private-cloud:registry:install-rehearsal
yarn private-cloud:registry:configure-rehearsal-runtime
yarn private-cloud:registry:status-rehearsal
yarn private-cloud:registry:ping-rehearsal
yarn private-cloud:registry:smoke-rehearsal
```

That installs `registry:3` on the cluster and exposes it on:

```bash
http://<primary-ip>:32000
```

Build and push the four app images on the primary Multipass VM:

```bash
yarn private-cloud:images:build-push-private-cloud -- 192.168.252.2:32000 2026-07-14
```

Then export the rollout image env vars and redeploy:

```bash
eval "$(yarn -s private-cloud:images:print-env-private-cloud -- 192.168.252.2:32000 2026-07-14)"
yarn private-cloud:progressive
```

Unlike the Harbor path, this arm64 rehearsal registry is intentionally unauthenticated inside the
private VM network, so the helper clears `ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME`.

## Harbor target workflow

The live cluster is already standard Kubernetes. The remaining blocker is app image distribution,
not the control plane.

Important on the current laptop rehearsal:

- the Multipass nodes are `arm64`
- the pinned official Harbor chart/images do not currently publish a matching `arm64` manifest
- `yarn private-cloud:registry:install-harbor-rehearsal` now fails fast on that mismatch instead
  of leaving broken Harbor pods behind

Create the Harbor project and log Docker into the registry:

```bash
yarn private-cloud:registry:ensure-project -- https://harbor.platform.itunda.internal admin <harbor-password>
yarn private-cloud:registry:login -- harbor.platform.itunda.internal robot\$itunda pull-token
```

Print the exact build/push plan:

```bash
yarn private-cloud:images:plan -- harbor.platform.itunda.internal 2026-07-14
```

Build and push the four app images:

```bash
ITUNDA_HARBOR_PROJECT=itunda \
yarn private-cloud:images:build-push -- harbor.platform.itunda.internal 2026-07-14
```

Then export the rollout image env vars from the helper and redeploy:

```bash
eval "$(yarn -s private-cloud:images:print-env -- harbor.platform.itunda.internal 2026-07-14)"
yarn private-cloud:progressive
```
