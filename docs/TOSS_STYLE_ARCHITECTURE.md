# Toss-Style Architecture Target

Last updated: 2026-07-14

This repo now has a concrete target architecture for the private cloud instead of only a gap
analysis.

It copies the public architectural shape Toss described, while staying honest about what is
currently live in the local Multipass rehearsal.

## What is now in the repo

- `infra/openstack/cmdb/private-cloud-inventory.json`
  - source-of-truth inventory for pods, workload clusters, node roles, and shared platform
    services
- `scripts/private-cloud-cmdb.sh`
  - desired vs. live CMDB output
- `infra/openstack/cluster-api/`
  - Cluster API Provider OpenStack manifests for:
    - `itunda-mgmt` as Pod0
    - `itunda-live3` as Pod1 / Live3
  - `itunda-live4` as Pod2 / Live4
  - management-cluster generated ClusterResourceSet add-ons for Calico + OCCM
  - Pod0 Harbor values scaffold
  - Pod0 Mimir values scaffold
  - Pod0 Zabbix values scaffold
  - Pod0 monitoring overlay that remote-writes Prometheus to Mimir and provisions Grafana
    datasources
- `infra/k8s/progressive-delivery/`
  - Istio gateway and VirtualServices
  - Argo Rollouts canary resources for all four stateless services
- `infra/k8s/private-cloud/registry-rehearsal.yaml`
  - arm64-safe Pod0-style OCI registry rehearsal for the current laptop cluster
- `infra/kafka/active-active/`
  - dedicated MirrorMaker 2 baseline deployment
  - explicit same-topic active-active contract for the custom mirroring path
- `scripts/private-cloud-platform.sh`
  - install/apply workflow for Istio, Argo Rollouts, and the live MM2 baseline
- `scripts/private-cloud-registry.sh`
  - Harbor preflight plus the live registry rehearsal/runtime configuration path
- `scripts/private-cloud-images.sh`
  - remote image build/push workflow on the primary VM for the private-cloud registry

## What this matches from Toss's public writeups

- OpenStack private cloud with Kubernetes lifecycle handled declaratively
- Pod0 management plus Pod1 / Pod2 independent workload pods
- multiple independent clusters instead of one stretched control plane
- progressive delivery with Istio plus Argo Rollouts
- dual-cluster Kafka with explicit mirroring and offset-sync concerns
- inventory / CMDB as a first-class platform primitive

The local rehearsal now has an explicit low-capacity concession: when the cluster only has one
ready worker, the platform script keeps the Rollout CRDs but switches the app layer to a rolling
profile instead of a traffic-split canary. That preserves the kubeadm+k8s control plane and the
GitOps surface without pretending the laptop has Toss-class spare scheduling headroom.

## What is still scaffold, not live

- a real OpenStack control plane
- Harbor, Octavia, BigIP, and other non-Kubernetes infrastructure services
- real multi-cluster traffic steering across sites
- Toss-equivalent same-topic Kafka mirroring implementation
- live Pod0 long-term observability stack beyond the current Prometheus/Grafana/node-exporter
  rehearsal

The important nuance is that image distribution is now partially live:

- the laptop rehearsal runs a real OCI registry on Kubernetes for arm64 nodes
- the Toss-aligned Harbor target remains in the repo, but the scripted install now fails fast on
  this environment because the pinned official Harbor images are `amd64`-only

## Operator workflow

Generate the source-of-truth and live views:

```bash
yarn private-cloud:cmdb:desired
yarn private-cloud:cmdb:live
yarn private-cloud:cmdb
yarn private-cloud:platform
yarn private-cloud:platform:status
```

Then use the OpenStack and workload scaffolding directories as the implementation backlog:

- `infra/openstack/cluster-api/`
- `infra/k8s/progressive-delivery/`
- `infra/kafka/active-active/`
