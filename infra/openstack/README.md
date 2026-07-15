# Itunda OpenStack Platform Scaffold

This directory turns the private-cloud target into repo-native infrastructure instead of prose.

It is intentionally split the same way Toss publicly described its private-cloud work:

- `cmdb/`
  - source-of-truth inventory for pods, clusters, nodes, and shared platform services
- `cluster-api/`
  - declarative Kubernetes cluster lifecycle manifests for OpenStack via Cluster API Provider OpenStack
  - Pod0 / Pod1 / Pod2 cluster split
  - ClusterResourceSet-driven workload add-on generation for Calico + OCCM
  - Pod0 Harbor registry values scaffold

This is a scaffold, not a claim that the laptop Multipass lab is already a real OpenStack region.
The live rehearsal is still two VMs. These files define the shape the repo should converge toward.
