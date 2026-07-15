# =============================================================================
# Itunda Platform - Production Environment (DC1 Kigali)
# =============================================================================
# Apply with:
#   terraform plan -var-file=environments/production.tfvars
#   terraform apply -var-file=environments/production.tfvars
# =============================================================================

# --- Cluster Identity ---
cluster_name = "itunda"
environment  = "production"
region       = "rw-kigali-1"

availability_zones = ["az-1", "az-2", "az-3"]

# --- Kubernetes ---
kubernetes_version  = "1.30.2"
api_server_endpoint = "api.production.k8s.itunda.rw"

pod_cidr     = "10.244.0.0/16"
service_cidr = "10.96.0.0/16"

# --- Networking ---
external_network_name = "ext-net-dc1"

management_cidr = "10.10.1.0/24"
compute_cidr    = "10.10.2.0/24"
storage_cidr    = "10.10.3.0/24"
dmz_cidr        = "10.10.4.0/24"
inter_dc_cidr   = "10.10.250.0/24"

# --- DNS ---
dns_domain      = "itunda.rw"
dns_nameservers = ["10.10.0.10", "10.10.0.11"]
ntp_servers     = ["ntp1.dc1.itunda.rw", "ntp2.dc1.itunda.rw", "ntp1.dc2.itunda.rw"]

# --- Control Plane (3-node HA) ---
control_plane_count            = 3
control_plane_flavor           = "m1.xlarge"   # 8 vCPU, 16GB RAM
control_plane_boot_volume_size = 100
control_plane_data_volume_size = 200

# --- Workers (6-node production workload) ---
worker_count            = 6
worker_flavor           = "m1.2xlarge"  # 16 vCPU, 32GB RAM
worker_boot_volume_size = 100
worker_data_volume_size = 500

# --- Bastion ---
bastion_flavor     = "m1.small"
bastion_image_name = "ubuntu-22.04-hardened"

# --- Storage ---
boot_volume_type          = "ssd-replicated"
data_volume_type          = "ssd-replicated"
ceph_osd_volume_size      = 1000  # 1TB per OSD
ceph_osd_volume_type      = "nvme-raw"
ceph_osd_count_per_worker = 2     # 2 OSDs × 6 workers = 12 OSDs, ~12TB raw
shared_filesystem_size    = 500

# --- Container Registry ---
docker_registry = "registry.dc1.itunda.rw"
node_image_name = "ubuntu-22.04-k8s-1.30"

# --- Tags ---
common_tags = {
  "app.kubernetes.io/part-of" = "itunda-platform"
  "itunda.rw/managed-by"     = "terraform"
  "itunda.rw/compliance"     = "pci-dss"
  "itunda.rw/country"        = "RW"
  "itunda.rw/organization"   = "itunda"
  "itunda.rw/environment"    = "production"
  "itunda.rw/datacenter"     = "dc1-kigali"
}

cost_center = "INFRA-PROD-001"
owner       = "platform-engineering"

# --- Federation (Multi-DC) ---
enable_federation    = true
peer_dc_api_endpoint = "api.production.k8s-dc2.itunda.rw"
peer_dc_pod_cidr     = "10.245.0.0/16"
peer_dc_service_cidr = "10.97.0.0/16"
