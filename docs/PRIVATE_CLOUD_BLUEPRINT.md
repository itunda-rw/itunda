# Itunda Private Cloud Blueprint

Last updated: 2026-07-16

This document separates three things that were getting conflated:

1. what Toss has publicly said about its own private-cloud and datacenter patterns,
2. what the live Itunda Multipass environment actually is today,
3. what Itunda should improve next if the target is a Toss-like Rwanda deployment.

The point is not to cosplay Toss's exact production stack on one laptop. The point is to copy
the real architectural shape honestly, and demo only the parts that cannot be made fully real
locally.

The repo-native target architecture now lives in
[docs/TOSS_STYLE_ARCHITECTURE.md](TOSS_STYLE_ARCHITECTURE.md).

## Toss patterns worth copying

Public sources:

- Toss Payments private cloud / hybrid cloud:
  `https://toss.tech/article/payments-legacy-9`
- Toss Securities Kafka dual-datacenter design:
  `https://toss.tech/article/kafka-distribution-1`

The reusable patterns are:

- **Independent failure domains, not fake redundancy.**
  Toss Payments runs fully independent private-cloud clusters and removes traffic from a failed
  cluster instead of depending on one giant stretched control plane.
- **Automation first.**
  Toss explicitly describes Terraform + Ansible, golden images, and inventory/CMDB collection
  as table stakes for operating private infrastructure with a small team.
- **Observability is part of the platform.**
  Toss calls out Zabbix, Prometheus, Mimir, and Grafana as core plumbing, not an afterthought.
- **Kafka active-active is two independent clusters plus mirroring and offset sync.**
  Toss Securities rejected a stretched Kafka cluster because inter-DC latency and split-brain
  risk were unacceptable.
- **Single-writer systems should stay honest.**
  Toss's public material describes active-active at the site and application level. It does not
  imply naive multi-master ledger writes.

## Current live Itunda Multipass topology

Audited 2026-07-16 with `scripts/private-cloud-audit.sh` against:

- `itunda-dc-a` — `192.168.252.2`
- `itunda-dc-b` — `192.168.252.3`

Current state:

- **Kubernetes**: two-node `kubeadm` cluster on the Multipass VMs
- **Platform controllers**: Istio, Argo Rollouts, MirrorMaker 2, Grafana, and Prometheus are
  live on that cluster
- **VM observability**: node-exporter plus VM-side MySQL/Redis/Kafka role metrics are now live
  in Prometheus/Grafana
- **MySQL**: one writable node on `itunda-dc-b`, one replica on `itunda-dc-a` — failover
  automation (`scripts/private-cloud-failover.sh`) was rehearsed live on 2026-07-16: promoted
  `dc-a` to writer, verified a healthy zero-lag takeover, then promoted `dc-b` back. Both
  directions passed topology verification.
- **Redis**: real master (`redis-b` on `dc-b`) plus a replica (`redis-a` on `dc-a`,
  `replicaof`-linked, confirmed with `INFO replication` showing `master_link_status:up` and
  `connected_slaves:1`). No Sentinel/automated failover yet — promotion would still be manual.
- **Kafka**: one single-broker KRaft cluster per node, not one shared cluster
- **Kafka mirroring**: a Kubernetes-hosted MM2 worker is now live against the two brokers
- **Registry plumbing**: an arm64-safe OCI registry rehearsal is now live on
  `http://192.168.252.2:32000`, and worker-node pull has been verified through `containerd`
- **Application ingress**: Istio NodePort is live, and as of 2026-07-16 all four app images
  (`api-gateway`, `backend`, `ledger-service`, `payment-service`) are built, pushed to that
  private registry, and running — `kubectl get pods -n itunda` shows all four `2/2 Running`
  behind the Istio sidecar.

That means the current private cloud is **not** active-active end to end. It is closer to:

- active-passive for MySQL, with the promotion path now proven by a live round-trip drill
- master+replica for Redis, with no automated failover yet
- split Kafka clusters with a real MM2 baseline but not Toss-equivalent same-topic mirroring
- stateless app orchestration is present, the private-registry path is live, and the full app
  image set is now published and running behind Istio and Argo Rollouts

### Known hardware ceiling (found 2026-07-16)

The two Multipass VMs are 2 vCPU / 4GiB / 10GB disk each, running on a single 8-core / 8GB host.
That is the real, current bottleneck, not a code bug:

- Both VMs together already claim 4 of 8 host CPUs and 7.6 of 8GiB host RAM, leaving almost no
  host headroom.
- An unbounded Prometheus (`--storage.tsdb.path` on an `emptyDir` with no retention flags and no
  `ephemeral-storage` resource declaration) grew until the node ephemeral-storage eviction
  threshold tripped, which evicted pods across every namespace, not just Prometheus. Over time
  that produced 800+ dead pod objects cluster-wide and a disk sitting at 80-86% full on both
  nodes. Fixed in `infra/k8s/monitoring/prometheus.yaml`: `--storage.tsdb.retention.time=6h`,
  `--storage.tsdb.retention.size=768MB`, a `1Gi` `emptyDir` size limit, and explicit
  `ephemeral-storage` requests/limits so the scheduler accounts for it correctly.
- Cleaning ~800 dead pod objects plus `apt-get clean` / `journalctl --vacuum-size` / image
  pruning reclaimed roughly 500-700MB per node.
- Separately, `itunda-dc-b` (the only untainted worker) was carrying the entire application and
  platform workload alone — Istio, MySQL, Redis, Kafka, MM2, Prometheus, Grafana, and all four
  app rollouts — while `itunda-dc-a`'s control-plane taint kept it idle. Removing the taint was
  tried and reverted: `itunda-dc-a`'s own control-plane processes (`kube-apiserver`,
  `etcd`) were already CPU-starved enough that `kube-controller-manager`/`kube-scheduler` were
  losing their leader-election lease (`context deadline exceeded` talking to the local
  apiserver) and crash-looping. Adding app pods there made that worse, not better. The taint is
  back in place; a genuinely idle third node (or more host CPU) is what this actually needs, not
  a scheduling trick.
- Net effect of the fixes above: cluster-wide dead/unknown pod count dropped from 800+ to
  roughly 20, and both nodes' 1-minute load average was trending back down.

### Building the `backend` image hit the ceiling directly (resolved 2026-07-16)

`api-gateway` (Node) built and pushed cleanly on the first attempt. `backend` (Spring Boot +
Kotlin, a much heavier Gradle/Kotlin compile) did not fit in the standing footprint three times
in a row, on both nodes, before it worked:

1. Building on `itunda-dc-a` (the control-plane node) drove its own SSH/kubectl unresponsive for
   several minutes from pure CPU contention with `kube-apiserver`/`etcd`.
2. Building on `itunda-dc-b` (after scaling Istio/MM2/Prometheus/Grafana/Argo Rollouts to zero
   first to free RAM) still ran memory down to ~20MB available with no swap configured, and the
   OOM killer took out Grafana and Prometheus (both already scaled down elsewhere, so this was
   residual) before the build died on its own. MySQL/Redis/Kafka were never touched by the OOM
   killer in either incident — kubelet's own oom_score_adj protected them as higher-priority
   pods, which held.
3. What actually fixed it: **Multipass doesn't support live/elastic VM resizing, but a
   stop → resize → start cycle gets the same effect.** `itunda-dc-a` was stopped entirely
   (it only hosted the MySQL/Redis *replicas*, not the writer, so this was safe), freeing its
   4GiB. `itunda-dc-b` was stopped, resized from 4GiB to 7GiB, and restarted — the build then
   completed in 2m17s (vs. 30+ minutes of thrashing before). After the build and push,
   `itunda-dc-b` was resized back down to 4GiB and `itunda-dc-a` was restarted, restoring the
   normal two-node topology. `docker stop`/`start` on `redis-b`/`mysql-b`/`kafka-b` was never
   needed — Docker's `restart: unless-stopped` policy brought them back automatically each time
   a VM rebooted, and none of them lost data or state across any of this.
4. A second, independent ceiling then showed up: disk pressure kept evicting `istiod`,
   `istio-ingressgateway`, and freshly-scheduled app pods on `itunda-dc-b` in a loop, even after
   the memory issue was fixed and the images existed. Unlike the memory problem, this was judged
   a standing issue (not a one-off build spike), so `itunda-dc-b`'s disk was grown
   **permanently** from 10GB to 20GB the same way (stop → `multipass set
   local.itunda-dc-b.disk=20G` → start). The guest's `sda1` partition auto-grew to fill it on
   boot — no manual `growpart`/`resize2fs` needed. Disk pressure cleared immediately and stayed
   clear.

After both fixes, all four app rollouts reached `2/2 Running` behind Istio, and the whole cluster
(24 pods) settled to 100% `Running` with zero dead/evicted pods — the first time that happened
all session.

**Takeaway for future sessions:** if a build or workload needs more headroom than the standing
2 vCPU/4GiB/10GB-disk footprint provides, prefer temporarily stopping the *other* node (verify
first it's not hosting a writer/master) and resizing the *build* node up, over fighting the
problem in place. Grow memory back down after use since the host has none to spare; grow disk
can stay permanent since host disk has real headroom (~27GB free at last check).

## Toss vs. Itunda gap table

| Layer | Toss pattern | Itunda now | Gap |
|---|---|---|---|
| Private-cloud control plane | Real private cloud, independent pods/clusters | Two Ubuntu Multipass VMs running a kubeadm cluster (`dc-a`: 2 vCPU/4GiB/10GB disk, `dc-b`: 2 vCPU/4GiB/20GB disk as of 2026-07-16) | Local demo scale only; no real OpenStack Pod0/Pod1/Pod2 yet; VMs are at their host's CPU/RAM ceiling |
| Infra lifecycle | Terraform + Ansible + golden images + CMDB | Repo-native bootstrap, CMDB, and workload scripts exist | Still no real Terraform/Ansible/golden-image pipeline |
| Traffic management | Active-active traffic steering, remove failed site from ingress | Istio ingress is live on the rehearsal cluster | No cross-cluster traffic steering or site cutover yet |
| App runtime | Real workload scheduling across failure domains | All four rollouts (`api-gateway`, `backend`, `ledger-service`, `payment-service`) are `2/2 Running` behind Istio as of 2026-07-16, images built/pushed into the private registry | Still single-node in practice (`dc-b` only, since `dc-a` stays tainted for control-plane stability); no real cross-node app HA yet |
| MySQL | Honest site HA, not pretend multi-master | `dc-b` writer, `dc-a` replica | Failover promotion automation now exists and was verified live (2026-07-16 round-trip drill); still need scheduled/repeatable drills and backup/restore scripts |
| Redis | Platform-grade HA strategy | `dc-b` master with a live `dc-a` replica (2026-07-16) | No Sentinel/automated failover; promotion is still manual |
| Kafka | Two independent clusters with real mirroring + offset sync | Two independent clusters plus live MM2 baseline | Need repeated offset-sync drills and Toss-style same-topic mirroring contract enforcement |
| Observability | Prometheus/Mimir/Grafana/Zabbix-class platform | Prometheus/Grafana plus live node and VM-service metrics; Prometheus retention is now bounded so it can't fill the disk again | Still need long-term storage and broader Zabbix/Mimir-class coverage |
| Failure drills | Active-active claims backed by drills | Live MySQL failover round-trip drill and Redis replication verified 2026-07-16; audit script now reports Redis HA state instead of always calling it a singleton | Need scheduled/repeatable drills, not one-off manual runs |

## What should become real next

### 1. Truthful local baseline

This repo now has the minimum missing operational layer:

- `yarn audit:private-cloud`
- `yarn env:private-cloud`

Those commands turn the Multipass VMs into something inspectable instead of something described
from memory.

### 2. Active-active stateless app layer

Before touching the data plane, run the stateless services across both nodes:

- API gateway
- canonical backend
- ledger-service
- payment-service

The Kubernetes manifests should assume multi-node placement, anti-affinity, and disruption
budgets. That is the safe part of active-active to make real first — but see the hardware-ceiling
note above: on 2026-07-16 the `itunda-dc-a` control-plane node was found too CPU-starved by its
own static pods (`kube-apiserver`, `etcd`) to also take application pods without crash-looping
`kube-controller-manager`/`kube-scheduler`. Real multi-node placement for the app layer needs a
third node or more host capacity, not just removing the `NoSchedule` taint.

### 3. Honest stateful HA

Keep the data layer truthful:

- **MySQL**: single writer, replica promotion, backup, restore, rehearse failover — the
  promotion/rehearsal half of this is now real and was verified live on 2026-07-16
  (`scripts/private-cloud-failover.sh promote <node>`, round-tripped `dc-b` → `dc-a` → `dc-b`).
  Backup/restore scripts are still missing.
- **Redis**: add replica + Sentinel or use a managed HA equivalent in the private cloud — the
  replica half is now real (`redis-a` on `dc-a` replicating from `redis-b` on `dc-b`, confirmed
  live 2026-07-16). Sentinel/automated failover is still missing; promotion today would be a
  manual `REPLICAOF NO ONE` plus re-pointing the other node.
- **Kafka**: either prove MM2 properly or call the second broker a demo-only second cluster

Do not label these "active-active" until the operational behavior is actually there.

The repo now has a dedicated MM2 drill entry point for that proof step:

- `yarn private-cloud:kafka:status`
- `yarn private-cloud:kafka:drill`

### 4. Private-cloud automation

The repo still lacks a real bootstrap story for the nodes themselves. The next useful code after
the audit script is:

- node bootstrap scripts or Ansible playbooks
- declared container/unit config per node
- one inventory file describing writer/replica/redis/kafka roles
- backup and restore scripts checked into the repo

The first version of that source-of-truth inventory now exists at:

- `infra/openstack/cmdb/private-cloud-inventory.json`
- `scripts/private-cloud-cmdb.sh`
- `scripts/private-cloud-platform.sh`

### 5. Observability and cutover

The useful production-grade next step is not more architecture prose. It is:

- health checks for the VM services
- dashboards for MySQL replication, Redis role, Kafka quorum, and app health
- a documented cutover runbook for `dc-a` and `dc-b`

## What can stay demo-only locally

Some Toss patterns should stay demos in a laptop environment:

- full OpenStack control plane
- Cluster API on top of a real private IaaS
- global traffic steering across real sites
- production-sized Kafka mirroring volume and consumer-group offset tooling

Those should be represented as blueprints and scripts, not falsely claimed as already running.

The corresponding scaffolds now exist in:

- `infra/openstack/cluster-api/`
- `infra/openstack/cluster-api/addons/`
- `infra/openstack/cluster-api/pod0-platform/`
- `infra/k8s/progressive-delivery/`
- `infra/kafka/active-active/`

## Local operator workflow

1. Audit the VMs:

   ```bash
   yarn audit:private-cloud
   ```

2. Generate the current `.env` values for local app processes:

   ```bash
   yarn env:private-cloud > .env
   ```

3. Start the local app stack against the private cloud:

   ```bash
   yarn dev:ecosystem
   ```

4. If the writer moves, regenerate the env file and restart the local processes.

That is the current honest workflow until the VMs host the actual active-active app layer.
