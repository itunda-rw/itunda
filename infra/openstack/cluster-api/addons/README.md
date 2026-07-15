# Workload Add-ons

This directory documents the management-cluster add-on path for the OpenStack workload clusters.

Why this exists:

- Cluster API's official **ClusterResourceSet** feature is meant to push CNI / CSI / cloud
  provider resources into matching workload clusters.
- CAPO's own bootstrap story still requires workload-cluster add-ons such as an external cloud
  provider and a CNI.
- Toss Payments' published private-cloud shape implies this should be part of the management
  platform, not a sequence of ad-hoc `kubectl` commands against each live cluster.

## What gets generated

Use:

```bash
bash scripts/openstack-cluster-api.sh render-workload-addons <path/to/clouds.yaml> <cloud-name> <output-dir>
```

That produces management-cluster manifests for:

- one shared `itunda-calico` `ConfigMap`
- one `cloud-config` `Secret` bundle per workload cluster
- one OCCM `ConfigMap` bundle per workload cluster
- one `ClusterResourceSet` per workload cluster

The generated artifacts are intentionally not committed, because they include live OpenStack
credentials rendered from `clouds.yaml`.

## Source pattern

This follows the official Cluster API `ClusterResourceSet` example for external cloud providers:

- Cluster API Book: `tasks/cluster-resource-set`
- CAPO helper: `templates/create_cloud_conf.sh`
- upstream OCCM manifests from `kubernetes/cloud-provider-openstack`

For the laptop rehearsal, the imperative `bootstrap-workload` command still exists. The
generated CRS path is the more faithful private-cloud direction.
