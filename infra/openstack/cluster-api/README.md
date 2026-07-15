# OpenStack Cluster API

This directory models the published Toss Payments "OKS" shape more directly now:

- **Pod0** management cluster: `itunda-mgmt`
- **Pod1** workload pod serving **Live3**: `itunda-live3`
- **Pod2** workload pod serving **Live4**: `itunda-live4`
- OpenStack as the infrastructure provider
- Cluster API Provider OpenStack for cluster lifecycle
- OpenStack Cloud Controller Manager on workload clusters
- ClusterResourceSet-driven workload add-ons
- Harbor values scaffold for the Pod0 registry layer
- Mimir values scaffold plus a monitoring overlay for the Pod0 observability layer
- Zabbix values scaffold for Pod0 management-plane monitoring

The cluster manifests are intentionally separate to preserve independent failure domains and
now include:

- explicit Pod0 / Pod1 / Pod2 labels
- current kubelet extra-arg structure for external cloud-provider wiring
- `MachineHealthCheck` resources for control-plane and worker pools

## Bootstrap order

These manifests follow the management-cluster model from Cluster API and CAPO:

1. Create a temporary bootstrap cluster.
2. `clusterctl init --infrastructure openstack`
3. Apply `openstack-cloud-config.secret.example.yaml` after replacing the placeholders.
4. Apply `itunda-mgmt-cluster.yaml`.
5. Move Cluster API management to `itunda-mgmt`.
6. Apply `itunda-live3-cluster.yaml` and `itunda-live4-cluster.yaml`.
7. Render management-cluster workload add-ons:

   ```bash
   yarn private-cloud:oks:plan
   bash scripts/openstack-cluster-api.sh render-workload-addons <path/to/clouds.yaml> <cloud-name> <output-dir>
   kubectl apply -f <output-dir>
   ```

8. Install Pod0 shared services from `pod0-platform/`, starting with:
   - Harbor
   - Mimir
   - the monitoring overlay
   - Zabbix
   - `yarn private-cloud:oks:pod0-plan`

## Pod0 shared services

`pod0-platform/` now contains:

- `README.md` — the Pod0 responsibility split
- `harbor-values.example.yaml` — a Harbor chart values baseline aligned to an OpenStack storage
  class and management-cluster monitoring
- `mimir-values.example.yaml` — a Mimir distributed chart values baseline for long-term metrics
- `zabbix-values.example.yaml` — a Zabbix chart values baseline for Pod0 service coverage
- `monitoring/` — a kustomize overlay that remote-writes Prometheus into Mimir and provisions
  Grafana datasources for both Prometheus and Mimir

`addons/` now documents the ClusterResourceSet path for Calico + OCCM + cloud-config delivery to
the workload clusters.
