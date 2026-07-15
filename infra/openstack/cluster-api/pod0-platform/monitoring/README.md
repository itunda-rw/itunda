# Pod0 Monitoring Overlay

This overlay makes the management-cluster monitoring stack concrete instead of leaving Pod0 with
only Harbor.

It reuses the repo's current Prometheus/Grafana/node-exporter manifests and adds the two Pod0
behaviors that match Toss's public write-up more closely:

- Prometheus keeps scraping the management cluster and remote-writes to **Grafana Mimir**
- Grafana gets both **Prometheus** and **Mimir** datasources provisioned

The overlay now vendors the three monitoring manifests under `base/` instead of reaching outside
the tree. That keeps `kubectl kustomize` and `kubectl apply -k` usable without special
load-restrictor flags.

Apply it to the OpenStack management cluster:

```bash
clusterctl get kubeconfig itunda-mgmt --namespace capo-itunda > /tmp/itunda-mgmt.kubeconfig
kubectl --kubeconfig /tmp/itunda-mgmt.kubeconfig apply -k \
  infra/openstack/cluster-api/pod0-platform/monitoring
```

What this overlay assumes:

- Mimir is installed in the same `monitoring` namespace with the default gateway service name
  `mimir-gateway`
- the tenant header for remote-write and Grafana queries is `itunda-platform`

If you change either of those, patch:

- `prometheus-config-mimir.patch.yaml`
- `grafana-datasources-mimir.patch.yaml`

This overlay intentionally does **not** install Mimir or Zabbix itself. Those stay as Helm chart
installs because Toss's public pattern is a shared management platform, and those two systems are
better represented here as versioned chart values than as ad-hoc raw manifests.
