# Private Cloud Operations

Last updated: 2026-07-16

This is the repo-native workflow for the current Multipass private cloud.

## What these scripts do

- `scripts/private-cloud-audit.sh`
  - inspects the live VMs and reports the current MySQL writer, Redis master, and Kafka nodes
- `scripts/private-cloud-bootstrap.sh`
  - installs a two-node kubeadm-based Kubernetes cluster on the Multipass VMs
- `scripts/private-cloud-kafka-topics.sh`
  - creates the Kafka topics the repo's real services expect
- `scripts/private-cloud-deploy.sh`
  - applies the Kubernetes manifests plus runtime ConfigMaps/Secrets and fixed NodePort entrypoints

## Expected topology

- `itunda-dc-a`
  - kubeadm control plane
  - MySQL replica
  - Redis replica
  - Kafka cluster A
- `itunda-dc-b`
  - kubeadm worker
  - MySQL writer
  - Redis master
  - Kafka cluster B

This is still not a full Toss-style active-active data plane. The goal here is to make the
stateless app layer deployable across both nodes while keeping the stateful roles explicit.

## 0. Lean mode (default posture)

The two VMs share an 8GB host and cannot run the full platform stack continuously without
repeated CPU/disk/memory pressure -- see "Known hardware ceiling" in
[docs/PRIVATE_CLOUD_BLUEPRINT.md](PRIVATE_CLOUD_BLUEPRINT.md). Rather than dropping to a single
VM (which would eliminate the independent-failure-domain pattern this whole setup exists to
rehearse), the default posture keeps only the always-on core running and scales the rest to zero:

Always on: the kubeadm control plane, MySQL (writer + replica), Redis (master + replica),
Kafka (both brokers) + MM2 mirroring, and the four app services (`api-gateway`, `backend`,
`ledger-service`, `payment-service`). This is the actual Toss pattern being rehearsed.

Scaled to zero by default: Istio (`istiod`, `istio-ingressgateway`), the Argo Rollouts
controller, Prometheus, Grafana, and the private registry. These are platform capabilities to
switch on only while actively rehearsing that specific thing (a canary rollout, a dashboard
check, pushing a new image), then switch back off.

```bash
yarn private-cloud:mode:status   # see what's currently toggled
yarn private-cloud:mode:full     # turn platform extras on before rehearsing one of them
yarn private-cloud:mode:lean     # back to the default posture when done
```

Existing app pods are untouched by this toggle either way -- their already-injected Istio
sidecars keep running even with `istiod` at zero replicas; only new pod creation needs the
webhook, so switch to `full` before a rollout/restart of the app layer.

## 1. Audit the VMs

```bash
yarn audit:private-cloud
yarn verify:private-cloud
```

Generate local env vars from the live writer/master roles:

```bash
yarn env:private-cloud > .env
```

Compare the desired source-of-truth inventory against the live audited topology:

```bash
yarn private-cloud:cmdb:desired
yarn private-cloud:cmdb:live
yarn private-cloud:cmdb
```

## 2. Bootstrap Kubernetes

```bash
yarn private-cloud:bootstrap
```

Default kubeadm rehearsal posture:

- the control-plane node remains tainted
- app workloads should land on `itunda-dc-b` unless you explicitly set
  `ITUNDA_PRIVATE_CLOUD_ALLOW_WORKLOADS_ON_CONTROL_PLANE=1`
- the old `k3s` path is now treated as legacy migration state only; active private-cloud deploy,
  platform, and registry workflows assume `kubeadm` Kubernetes

Check status:

```bash
yarn private-cloud:k8s:status
```

Print a host-usable kubeconfig if you want to use your own `kubectl`:

```bash
yarn private-cloud:k8s:kubeconfig
```

## 2a. Rehearse MySQL failover

Show the live topology:

```bash
yarn private-cloud:failover status
```

Promote the healthy replica to writer:

```bash
yarn private-cloud:failover promote itunda-dc-a
```

or back to `itunda-dc-b`:

```bash
yarn private-cloud:failover promote itunda-dc-b
```

The failover script refuses promotion unless the target node is a healthy read-only replica with
zero reported lag, then re-homes replication using GTID auto-position and re-runs topology
verification at the end.

## 3. Provision Kafka topics

```bash
yarn private-cloud:topics
```

This ensures the real event topics exist on both Kafka clusters:

- `ledger.posted`
- `transfer.confirmed`
- `payment.provider_succeeded`
- `payment.provider_failed`
- `payment-events`

## 4. Deploy the app layer

```bash
yarn private-cloud:deploy
```

The deploy script:

- refuses to deploy if the live MySQL/Redis/Kafka topology is unsafe, unless
  `ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY=1` is set deliberately
- mounts this repo into the primary VM
- creates `itunda-db-credentials`, `itunda-jwt-secret`, and the external-service ConfigMaps
  from `.env` or the live audit
- applies `infra/k8s/base/`
- applies `infra/k8s/production/`
- applies `infra/k8s/monitoring/`
- applies `infra/k8s/private-cloud/nodeports.yaml`

Optional image overrides:

```bash
ITUNDA_BACKEND_IMAGE=registry.example.com/itunda/backend:2026-07-14 \
ITUNDA_API_GATEWAY_IMAGE=registry.example.com/itunda/api-gateway:2026-07-14 \
ITUNDA_LEDGER_IMAGE=registry.example.com/itunda/ledger-service:2026-07-14 \
ITUNDA_PAYMENT_IMAGE=registry.example.com/itunda/payment-service:2026-07-14 \
ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME=itunda-registry \
yarn private-cloud:deploy
```

The same image env vars now work for the progressive-delivery path:

```bash
ITUNDA_BACKEND_IMAGE=harbor.platform.itunda.internal/itunda/backend:2026-07-14 \
ITUNDA_API_GATEWAY_IMAGE=harbor.platform.itunda.internal/itunda/api-gateway:2026-07-14 \
ITUNDA_LEDGER_IMAGE=harbor.platform.itunda.internal/itunda/ledger-service:2026-07-14 \
ITUNDA_PAYMENT_IMAGE=harbor.platform.itunda.internal/itunda/payment-service:2026-07-14 \
ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME=itunda-registry \
yarn private-cloud:progressive
```

## 4a. Install the arm64 rehearsal registry

```bash
yarn private-cloud:registry:install-rehearsal
yarn private-cloud:registry:configure-rehearsal-runtime
yarn private-cloud:registry:status-rehearsal
yarn private-cloud:registry:smoke-rehearsal
```

This installs the upstream `registry:3` image on the cluster and configures `containerd` plus
Docker on the private-cloud nodes to trust `http://<primary-ip>:32000` as an insecure internal
registry.

Smoke check:

```bash
yarn private-cloud:registry:ping-rehearsal
```

Build and push the app images on the primary VM:

```bash
yarn private-cloud:images:build-push-private-cloud -- 192.168.252.2:32000 2026-07-14
```

Then export the image refs and redeploy:

```bash
eval "$(yarn -s private-cloud:images:print-env-private-cloud -- 192.168.252.2:32000 2026-07-14)"
yarn private-cloud:progressive
```

## 4b. Install the platform controllers

```bash
yarn private-cloud:platform
yarn private-cloud:platform:status
```

This installs:

- Istio
- Argo Rollouts

The mesh is deliberately configured with a low-overhead `minimal` profile plus an ingress gateway
so it is usable on the current rehearsal hardware.

The cluster under this workflow is standard `kubeadm` Kubernetes, not `k3s`. The remaining `k3s`
logic in the scripts only exists to detect and replace an old cluster with
`yarn private-cloud:bootstrap` or `scripts/private-cloud-bootstrap.sh migrate-kubeadm`.

## 4c. Switch to progressive delivery

```bash
yarn private-cloud:progressive
```

That replaces the four plain stateless `Deployment` objects with:

- `Rollout`
- stable service
- canary service
- Istio `VirtualService`

See [docs/PRIVATE_CLOUD_PLATFORM.md](PRIVATE_CLOUD_PLATFORM.md) for the full workflow and the
current image-pull caveat.

Recommended low-memory env for this laptop rehearsal:

```bash
ITUNDA_PRIVATE_CLOUD_ROLLOUT_PROFILE=auto \
ITUNDA_PRIVATE_CLOUD_ENABLE_HPA=0 \
ITUNDA_PRIVATE_CLOUD_ENSURE_TOPICS_ON_DEPLOY=0 \
ITUNDA_PRIVATE_CLOUD_APP_REPLICAS=1 \
yarn private-cloud:progressive
```

`auto` keeps the Rollout objects, but it drops to a rolling-update profile when the cluster only
has one ready worker or a single desired replica. Use `ITUNDA_PRIVATE_CLOUD_ROLLOUT_PROFILE=canary`
only when you actually have spare schedulable capacity for stable plus canary pods.
In that rolling lab profile, the backend rollout is also reduced to a low-capacity request so the
single worker can still place it.

If the rehearsal is intentionally running without a healthy MySQL replica and you still need to
render runtime config from the current writer, add `ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY=1`.

If the control plane becomes saturated, run:

```bash
yarn private-cloud:platform:stabilize-control-plane
```

That cleanup path is now repo-native and is safer than manually re-running the full platform
workflow against an overloaded API server. It also re-applies the control-plane `NoSchedule` taint
unless `ITUNDA_PRIVATE_CLOUD_ALLOW_WORKLOADS_ON_CONTROL_PLANE=1` is set.

## 4d. Plan the OpenStack / OKS target

```bash
yarn private-cloud:oks:plan
```

Once a real OpenStack management cluster exists, bootstrap the workload clusters with:

```bash
bash scripts/openstack-cluster-api.sh render-workload-addons <path/to/clouds.yaml> <cloud-name> <output-dir>
kubectl apply -f <output-dir>
```

## 4e. Deploy the Kafka replication baseline

```bash
yarn private-cloud:kafka-replication
```

This applies a repo-native MM2 worker using the live audited broker IPs.

## 5. Reach the deployed services

Using the primary VM IP:

- API gateway: `http://<primary-ip>:30080/health`
- Backend actuator: `http://<primary-ip>:30081/actuator/health`
- Prometheus: `http://<primary-ip>:30090`
- Grafana: `http://<primary-ip>:30300`

If Istio is installed, the mesh ingress path is:

- `curl -H 'Host: api.itunda.internal' http://<primary-ip>:30082/health`

## 6. Run local dev against the private cloud

```bash
yarn env:private-cloud > .env
yarn dev:ecosystem
```

That uses the live MySQL/Redis/Kafka roles from the audited private cloud while keeping the web
and backend processes local.

## 7. Scheduled drills

`scripts/private-cloud-scheduled-drills.sh run` runs the audit plus a bidirectional Kafka MM2
drill and appends timestamped output to `~/Library/Logs/itunda-private-cloud/drills.log`. Both
are read-only/non-mutating against the live MySQL/Redis roles, so it's safe to run unattended.

Install it as a recurring macOS launchd job (every 6 hours):

```bash
bash scripts/private-cloud-drill-schedule-install.sh install
bash scripts/private-cloud-drill-schedule-install.sh status
bash scripts/private-cloud-drill-schedule-install.sh uninstall
```

Deliberately not included: an automated MySQL failover round-trip. That mutates the live writer
role twice and isn't safe to run unattended on a machine that can sleep or lose network mid-drill.
Rehearse it by hand instead:

```bash
yarn private-cloud:failover promote <standby-node>
yarn private-cloud:failover promote <original-writer-node>
```
