# =============================================================================
# Itunda Platform - Disaster Recovery Environment (DC2 Kigali)
# =============================================================================
# DR site mirrors production in a secondary datacenter (DC2).
# Uses non-overlapping CIDRs to enable Submariner cross-cluster networking.
# Apply with:
#   terraform plan -var-file=environments/dr.tfvars
#   terraform apply -var-file=environments/dr.tfvars
#
# DR Strategy: Active-Active with federation
#   - Both DCs run production workloads
#   - Submariner provides cross-cluster networking
#   - KubeFed distributes workloads across DCs
#   - DNS-based failover for external traffic
# =============================================================================

# --- Cluster Identity ---
cluster_name = "itunda"
environment  = "dr"
region       = "rw-kigali-2"  # DC2 region

availability_zones = ["az-1", "az-2", "az-3"]

# --- Kubernetes ---
kubernetes_version  = "1.30.2"
api_server_endpoint = "api.dr.k8s.itunda.rw"

# DR cluster uses separate pod/service CIDRs to avoid conflicts
# These MUST NOT overlap with production CIDRs for Submariner to work
pod_cidr     = "10.245.0.0/16"
service_cidr = "10.97.0.0/16"

# --- Networking ---
# DR uses a completely separate network space (10.30.x.x)
external_network_name = "ext-net-dc2"

management_cidr = "10.30.1.0/24"
compute_cidr    = "10.30.2.0/24"
storage_cidr    = "10.30.3.0/24"
dmz_cidr        = "10.30.4.0/24"
inter_dc_cidr   = "10.30.250.0/24"

# --- DNS ---
dns_domain      = "itunda.rw"
dns_nameservers = ["10.30.0.10", "10.30.0.11"]  # DC2 DNS servers
ntp_servers     = ["ntp1.dc2.itunda.rw", "ntp2.dc2.itunda.rw", "ntp1.dc1.itunda.rw"]

# --- Control Plane (3-node HA, mirrors production) ---
control_plane_count            = 3
control_plane_flavor           = "m1.xlarge"
control_plane_boot_volume_size = 100
control_plane_data_volume_size = 200

# --- Workers (6-node, mirrors production for active-active) ---
worker_count            = 6
worker_flavor           = "m1.2xlarge"
worker_boot_volume_size = 100
worker_data_volume_size = 500

# --- Bastion ---
bastion_flavor     = "m1.small"
bastion_image_name = "ubuntu-22.04-hardened"

# --- Storage (mirrors production capacity) ---
boot_volume_type          = "ssd-replicated"
data_volume_type          = "ssd-replicated"
ceph_osd_volume_size      = 1000
ceph_osd_volume_type      = "nvme-raw"
ceph_osd_count_per_worker = 2
shared_filesystem_size    = 500

# --- Container Registry (DC2 mirror) ---
docker_registry = "registry.dc2.itunda.rw"
node_image_name = "ubuntu-22.04-k8s-1.30"

# --- Tags ---
common_tags = {
  "app.kubernetes.io/part-of" = "itunda-platform"
  "itunda.rw/managed-by"     = "terraform"
  "itunda.rw/compliance"     = "pci-dss"
  "itunda.rw/country"        = "RW"
  "itunda.rw/organization"   = "itunda"
  "itunda.rw/environment"    = "dr"
  "itunda.rw/datacenter"     = "dc2-kigali"
  "itunda.rw/dr-pair"        = "dc1-kigali"
}

cost_center = "INFRA-DR-001"
owner       = "platform-engineering"

# --- Federation ---
# DR cluster peers with DC1 production
enable_federation    = true
peer_dc_api_endpoint = "api.production.k8s.itunda.rw"
peer_dc_pod_cidr     = "10.244.0.0/16"   # DC1's pod CIDR
peer_dc_service_cidr = "10.96.0.0/16"    # DC1's service CIDR
