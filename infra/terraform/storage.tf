# =============================================================================
# Itunda Platform - Storage Resources
# OpenStack Cinder & Manila - Ceph OSD Volumes, Boot Volumes, Shared FS
# =============================================================================
# Storage architecture:
#   - Boot volumes: SSD-replicated Ceph pool for OS (fast, durable)
#   - Data volumes: SSD-replicated for etcd/logs (IOPS-optimized)
#   - Ceph OSD volumes: NVMe-raw for Rook-Ceph managed storage
#   - Shared filesystem: CephFS via Manila for shared config/data
#
# PCI-DSS Note: All volumes are encrypted at rest via Ceph's built-in
# encryption. Volume type "ssd-replicated" maps to a 3-replica Ceph pool.
# =============================================================================

# =============================================================================
# Ceph OSD Volumes for Rook-Ceph
# =============================================================================
# These raw volumes are attached to worker nodes and consumed by Rook-Ceph
# to build the in-cluster storage layer. Rook manages OSD creation,
# replication, and failure recovery.
# =============================================================================

resource "openstack_blockstorage_volume_v3" "ceph_osd" {
  for_each = {
    for pair in setproduct(range(var.worker_count), range(var.ceph_osd_count_per_worker)) :
    "worker-${pair[0]}-osd-${pair[1]}" => {
      worker_index = pair[0]
      osd_index    = pair[1]
    }
  }

  name              = "${var.cluster_name}-${var.environment}-${each.key}"
  description       = "Ceph OSD volume for Rook-Ceph managed storage"
  size              = var.ceph_osd_volume_size
  volume_type       = var.ceph_osd_volume_type
  availability_zone = element(var.availability_zones, each.value.worker_index % length(var.availability_zones))

  metadata = merge(var.common_tags, {
    "itunda.rw/role"         = "ceph-osd"
    "itunda.rw/worker-index" = tostring(each.value.worker_index)
    "itunda.rw/osd-index"    = tostring(each.value.osd_index)
    "itunda.rw/managed-by"   = "rook-ceph"
  })

  lifecycle {
    # Prevent accidental deletion of OSD volumes (data loss!)
    prevent_destroy = false  # Set to true in production
  }
}

# Attach OSD volumes to worker nodes
resource "openstack_compute_volume_attach_v2" "ceph_osd" {
  for_each = openstack_blockstorage_volume_v3.ceph_osd

  instance_id = module.workers["worker-${each.value.metadata["itunda.rw/worker-index"]}"].instance_id
  volume_id   = each.value.id

  # Let OpenStack assign device names
  # Rook-Ceph discovers devices by volume metadata, not device path
}

# =============================================================================
# Shared Filesystem (Manila CephFS)
# =============================================================================
# A shared CephFS filesystem for cross-node data sharing:
# - Shared configuration files
# - ML model artifacts
# - Report generation output
# - Audit logs aggregation
# =============================================================================

resource "openstack_sharedfilesystem_share_v2" "shared_data" {
  name             = "${var.cluster_name}-${var.environment}-shared"
  description      = "Shared CephFS filesystem for cross-pod data access"
  share_proto      = var.shared_filesystem_protocol
  size             = var.shared_filesystem_size
  share_type       = "cephfs-default"
  availability_zone = var.availability_zones[0]

  metadata = merge(var.common_tags, {
    "itunda.rw/role" = "shared-storage"
  })
}

# Grant access to the management network for shared filesystem
resource "openstack_sharedfilesystem_share_access_v2" "shared_data_mgmt" {
  share_id    = openstack_sharedfilesystem_share_v2.shared_data.id
  access_type = "cephx"
  access_to   = "${var.cluster_name}-${var.environment}-cephfs"
  access_level = "rw"
}

# =============================================================================
# Volume Type Data Sources
# =============================================================================
# These verify that the required Cinder volume types exist in OpenStack
# before attempting to create volumes.
# =============================================================================

# Audit log volume for PCI-DSS compliance
# Stores audit logs separately from application data (Requirement 10.5.4)
resource "openstack_blockstorage_volume_v3" "audit_logs" {
  name              = "${var.cluster_name}-${var.environment}-audit-logs"
  description       = "Dedicated volume for PCI-DSS audit log storage"
  size              = 200
  volume_type       = var.data_volume_type
  availability_zone = var.availability_zones[0]

  metadata = merge(var.common_tags, {
    "itunda.rw/role"       = "audit-logs"
    "itunda.rw/compliance" = "pci-dss-req-10"
    "itunda.rw/retention"  = "365-days"
  })

  lifecycle {
    prevent_destroy = false  # Set to true in production
  }
}

# Attach audit log volume to first control plane node
resource "openstack_compute_volume_attach_v2" "audit_logs" {
  instance_id = module.control_plane["cp-0"].instance_id
  volume_id   = openstack_blockstorage_volume_v3.audit_logs.id
}

# =============================================================================
# Backup Configuration
# =============================================================================
# Automated backup of critical volumes using Cinder backup service.
# Backups are stored in a separate Ceph pool for disaster recovery.
# =============================================================================

# Note: Cinder backup resources are created via scheduled jobs, not Terraform.
# The following documents the backup strategy:
#
# Backup Schedule:
#   - etcd data volumes: Every 6 hours, retain 28 backups
#   - Audit log volume: Daily, retain 365 backups (PCI-DSS Requirement 10.7)
#   - Shared filesystem snapshots: Daily, retain 30 snapshots
#
# Implementation:
#   - Use Velero for Kubernetes-aware backups
#   - Use Cinder backup API for volume-level backups
#   - Store backups in cross-DC Ceph pool for DR

# Shared filesystem snapshot for point-in-time recovery
resource "openstack_sharedfilesystem_share_v2" "shared_data_backup" {
  name             = "${var.cluster_name}-${var.environment}-shared-backup"
  description      = "Backup share for disaster recovery replication target"
  share_proto      = var.shared_filesystem_protocol
  size             = var.shared_filesystem_size
  share_type       = "cephfs-default"
  availability_zone = var.availability_zones[0]

  metadata = merge(var.common_tags, {
    "itunda.rw/role"    = "shared-storage-backup"
    "itunda.rw/purpose" = "disaster-recovery"
  })
}
