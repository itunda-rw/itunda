# =============================================================================
# Itunda Platform - Staging Environment (DC1 Kigali)
# =============================================================================
# Reduced-resource staging environment for pre-production validation.
# Apply with:
#   terraform plan -var-file=environments/staging.tfvars
#   terraform apply -var-file=environments/staging.tfvars
# =============================================================================

# --- Cluster Identity ---
cluster_name = "itunda"
environment  = "staging"
region       = "rw-kigali-1"

# Staging uses fewer AZs to reduce resource consumption
availability_zones = ["az-1", "az-2"]

# --- Kubernetes ---
kubernetes_version  = "1.30.2"
api_server_endpoint = "api.staging.k8s.itunda.rw"

pod_cidr     = "10.244.0.0/16"
service_cidr = "10.96.0.0/16"

# --- Networking ---
# Staging uses separate CIDR ranges to allow parallel operation
external_network_name = "ext-net-dc1"

management_cidr = "10.20.1.0/24"
compute_cidr    = "10.20.2.0/24"
storage_cidr    = "10.20.3.0/24"
dmz_cidr        = "10.20.4.0/24"
inter_dc_cidr   = "10.20.250.0/24"

# --- DNS ---
dns_domain      = "itunda.rw"
dns_nameservers = ["10.10.0.10", "10.10.0.11"]
ntp_servers     = ["ntp1.dc1.itunda.rw", "pool.ntp.org"]

# --- Control Plane (single-node, no HA for staging) ---
control_plane_count            = 1
control_plane_flavor           = "m1.xlarge"   # 8 vCPU, 16GB RAM
control_plane_boot_volume_size = 50
control_plane_data_volume_size = 100

# --- Workers (3-node for staging workloads) ---
worker_count            = 3
worker_flavor           = "m1.xlarge"   # 8 vCPU, 16GB RAM (smaller than prod)
worker_boot_volume_size = 50
worker_data_volume_size = 200

# --- Bastion ---
bastion_flavor     = "m1.small"
bastion_image_name = "ubuntu-22.04-hardened"

# --- Storage (reduced for staging) ---
boot_volume_type          = "ssd-replicated"
data_volume_type          = "ssd-replicated"
ceph_osd_volume_size      = 200   # 200GB per OSD (smaller for staging)
ceph_osd_volume_type      = "nvme-raw"
ceph_osd_count_per_worker = 1     # 1 OSD × 3 workers = 3 OSDs, ~600GB raw
shared_filesystem_size    = 100

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
  "itunda.rw/environment"    = "staging"
  "itunda.rw/datacenter"     = "dc1-kigali"
}

cost_center = "INFRA-STG-001"
owner       = "platform-engineering"

# --- Federation (disabled for staging) ---
enable_federation    = false
peer_dc_api_endpoint = ""
peer_dc_pod_cidr     = "10.245.0.0/16"
peer_dc_service_cidr = "10.97.0.0/16"
