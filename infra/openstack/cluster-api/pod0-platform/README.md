# Pod0 Shared Platform

This directory is the management-pod layer for the Toss-style private cloud target.

It exists because Toss Payments' public write-up is not "just run Kubernetes on OpenStack" —
their **Pod0** carries the shared platform services that make Pod1 and Pod2 usable:

- Cluster API management
- monitoring / security tooling
- private registry

For itunda, Pod0 now has concrete scaffolding for the two shared layers that matter first:

- image distribution
- observability

## Harbor

Toss explicitly maps AWS ECR to **Harbor** in its OpenStack private cloud. This repo now
includes a Harbor values example at:

- `harbor-values.example.yaml`

It assumes:

- Harbor runs on the `itunda-mgmt` cluster
- ingress termination is provided by the platform ingress controller
- persistent volumes come from an OpenStack-backed storage class such as `cinder-csi`
- Harbor metrics are scraped by the management-cluster monitoring stack

Install it with Helm once `itunda-mgmt` is reachable:

```bash
helm repo add harbor https://helm.goharbor.io
clusterctl get kubeconfig itunda-mgmt --namespace capo-itunda > /tmp/itunda-mgmt.kubeconfig
helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install harbor harbor/harbor \
  --namespace harbor-system \
  --create-namespace \
  -f infra/openstack/cluster-api/pod0-platform/harbor-values.example.yaml
```

Or use the repo-native helper:

```bash
yarn private-cloud:registry:install-harbor -- /path/to/itunda-mgmt.kubeconfig
```

Create the Harbor project expected by the workload image pipeline:

```bash
yarn private-cloud:registry:ensure-project -- https://harbor.platform.itunda.internal admin <harbor-password>
```

Log Docker into Harbor before building or pushing images:

```bash
yarn private-cloud:registry:login -- harbor.platform.itunda.internal robot\$itunda pull-token
```

## Pull secrets

Once Harbor exists, create and distribute a pull secret for workload namespaces:

```bash
yarn private-cloud:registry:render-pull-secret -- harbor.platform.itunda.internal robot\$itunda pull-token
yarn private-cloud:registry:apply-pull-secret -- /path/to/workload.kubeconfig harbor.platform.itunda.internal robot\$itunda pull-token
```

For the current Multipass rehearsal cluster specifically:

```bash
yarn private-cloud:registry:apply-current-pull-secret -- harbor.platform.itunda.internal robot\$itunda pull-token
```

## Workload images

The workload layer should pull from Pod0 Harbor, not directly from GHCR.

Print the exact build/push plan:

```bash
yarn private-cloud:images:plan -- harbor.platform.itunda.internal 2026-07-14
```

Build and push all four images:

```bash
ITUNDA_HARBOR_PROJECT=itunda \
yarn private-cloud:images:build-push -- harbor.platform.itunda.internal 2026-07-14
```

Emit the exact env vars the plain deploy and progressive rollout flows consume:

```bash
yarn private-cloud:images:print-env -- harbor.platform.itunda.internal 2026-07-14
```

## Monitoring

Toss explicitly names **Prometheus**, **Mimir**, **Grafana**, and **Zabbix** in the management
platform around its private cloud. This repo now has concrete Pod0 artifacts for that shape:

- `monitoring/`
  - kustomize overlay that keeps Prometheus scraping the management cluster while remote-writing
    to Mimir and provisioning Grafana datasources for both Prometheus and Mimir
- `mimir-values.example.yaml`
  - source-backed `grafana/mimir-distributed` values baseline for object-storage-backed,
    persistent Mimir components on OpenStack-backed storage
- `zabbix-values.example.yaml`
  - source-backed `zabbix-community/zabbix` values baseline for management-plane service and host
    coverage with PostgreSQL persistence

Install Mimir on `itunda-mgmt`:

```bash
helm repo add grafana https://grafana.github.io/helm-charts
clusterctl get kubeconfig itunda-mgmt --namespace capo-itunda > /tmp/itunda-mgmt.kubeconfig
helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install mimir grafana/mimir-distributed \
  --namespace monitoring \
  --create-namespace \
  -f infra/openstack/cluster-api/pod0-platform/mimir-values.example.yaml
```

Apply the Pod0 monitoring overlay after Mimir is present:

```bash
kubectl --kubeconfig /tmp/itunda-mgmt.kubeconfig apply -k \
  infra/openstack/cluster-api/pod0-platform/monitoring
```

Install Zabbix on `itunda-mgmt`:

```bash
helm repo add zabbix-community https://zabbix-community.github.io/helm-zabbix
helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install zabbix zabbix-community/zabbix \
  --dependency-update \
  --namespace monitoring \
  --create-namespace \
  -f infra/openstack/cluster-api/pod0-platform/zabbix-values.example.yaml
```

Or print the combined Pod0 sequence with:

```bash
yarn private-cloud:oks:pod0-plan
```

## Still missing on purpose

This repo does **not** yet claim a full Toss-equivalent Pod0. The remaining shared services are
still backlog items, not hidden assumptions:

- security policy tooling and image-signing workflow
- multi-cluster traffic steering and LB policy around Live3 / Live4

Mimir and Zabbix are now represented as installable Pod0 chart baselines, but they are still not
claimed as live in the local Multipass rehearsal.

Those should be added the same way: as repo-native configuration tied to `itunda-mgmt`, not
as prose pretending they already run locally.
