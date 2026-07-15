# =============================================================================
# Itunda Platform - Terraform Outputs
# =============================================================================
# These outputs provide essential connection details after provisioning.
# Sensitive values are marked to prevent accidental exposure in logs.
# =============================================================================

# =============================================================================
# Cluster Identity
# =============================================================================

output "cluster_id" {
  description = "Unique cluster identifier"
  value       = random_id.cluster_id.hex
}

output "cluster_name" {
  description = "Cluster name with environment suffix"
  value       = "${var.cluster_name}-${var.environment}"
}

# =============================================================================
# Control Plane
# =============================================================================

output "control_plane_private_ips" {
  description = "Private IP addresses of control plane nodes"
  value = {
    for k, v in module.control_plane : k => v.private_ip
  }
}

output "control_plane_instance_ids" {
  description = "OpenStack instance IDs of control plane nodes"
  value = {
    for k, v in module.control_plane : k => v.instance_id
  }
}

output "api_server_floating_ip" {
  description = "Floating IP of the Kubernetes API server"
  value       = openstack_networking_floatingip_v2.api_server.address
}

output "api_server_endpoint" {
  description = "FQDN endpoint for the Kubernetes API server"
  value       = "https://api.${var.environment}.k8s.${var.dns_domain}:6443"
}

# =============================================================================
# Worker Nodes
# =============================================================================

output "worker_private_ips" {
  description = "Private IP addresses of worker nodes"
  value = {
    for k, v in module.workers : k => v.private_ip
  }
}

output "worker_instance_ids" {
  description = "OpenStack instance IDs of worker nodes"
  value = {
    for k, v in module.workers : k => v.instance_id
  }
}

# =============================================================================
# Bastion Host
# =============================================================================

output "bastion_floating_ip" {
  description = "Public floating IP of the bastion host for SSH access"
  value       = openstack_networking_floatingip_v2.bastion.address
}

output "bastion_private_ip" {
  description = "Private IP of the bastion host"
  value       = openstack_compute_instance_v2.bastion.access_ip_v4
}

output "bastion_ssh_command" {
  description = "SSH command to connect to the bastion host"
  value       = "ssh -i ${local_sensitive_file.ssh_private_key.filename} itunda-admin@${openstack_networking_floatingip_v2.bastion.address}"
}

# =============================================================================
# Ingress
# =============================================================================

output "ingress_http_floating_ip" {
  description = "Floating IP for HTTP/HTTPS ingress traffic"
  value       = openstack_networking_floatingip_v2.ingress_http.address
}

output "ingress_tcp_floating_ip" {
  description = "Floating IP for TCP ingress traffic"
  value       = openstack_networking_floatingip_v2.ingress_tcp.address
}

output "ingress_wildcard_domain" {
  description = "Wildcard domain for ingress-exposed applications"
  value       = "*.apps.${var.environment}.k8s.${var.dns_domain}"
}

# =============================================================================
# Network Details
# =============================================================================

output "network_ids" {
  description = "OpenStack network IDs for each tier"
  value = {
    management = openstack_networking_network_v2.management.id
    compute    = openstack_networking_network_v2.compute.id
    storage    = openstack_networking_network_v2.storage.id
    dmz        = openstack_networking_network_v2.dmz.id
  }
}

output "subnet_cidrs" {
  description = "CIDR blocks for each subnet"
  value = {
    management = var.management_cidr
    compute    = var.compute_cidr
    storage    = var.storage_cidr
    dmz        = var.dmz_cidr
  }
}

output "router_id" {
  description = "OpenStack router ID"
  value       = openstack_networking_router_v2.main.id
}

# =============================================================================
# Security Groups
# =============================================================================

output "security_group_ids" {
  description = "Security group IDs for reference in other modules"
  value = {
    common        = openstack_networking_secgroup_v2.common.id
    control_plane = openstack_networking_secgroup_v2.control_plane.id
    workers       = openstack_networking_secgroup_v2.workers.id
    bastion       = openstack_networking_secgroup_v2.bastion.id
    database      = openstack_networking_secgroup_v2.database.id
    monitoring    = openstack_networking_secgroup_v2.monitoring.id
  }
}

# =============================================================================
# DNS
# =============================================================================

output "dns_zone_id" {
  description = "Designate DNS zone ID for the cluster"
  value       = openstack_dns_zone_v2.cluster.id
}

output "dns_zone_name" {
  description = "DNS zone name for the cluster"
  value       = openstack_dns_zone_v2.cluster.name
}

# =============================================================================
# Storage
# =============================================================================

output "shared_filesystem_id" {
  description = "Manila shared filesystem ID"
  value       = openstack_sharedfilesystem_share_v2.shared_data.id
}

output "shared_filesystem_export" {
  description = "Manila shared filesystem export location"
  value       = openstack_sharedfilesystem_share_v2.shared_data.export_locations
}

output "ceph_osd_volume_ids" {
  description = "Cinder volume IDs for Ceph OSD volumes"
  value = {
    for k, v in openstack_blockstorage_volume_v3.ceph_osd : k => v.id
  }
}

# =============================================================================
# SSH Keys
# =============================================================================

output "ssh_private_key_path" {
  description = "Path to the SSH private key for node access"
  value       = local_sensitive_file.ssh_private_key.filename
}

output "ssh_public_key" {
  description = "SSH public key deployed to all nodes"
  value       = tls_private_key.ssh.public_key_openssh
  sensitive   = false
}

# =============================================================================
# Kubernetes Bootstrap
# =============================================================================

output "bootstrap_token" {
  description = "Kubeadm bootstrap token for node joining"
  value       = "${random_password.bootstrap_token_id.result}.${random_password.bootstrap_token_secret.result}"
  sensitive   = true
}

output "kubeconfig_path" {
  description = "Expected path to kubeconfig after cluster bootstrap"
  value       = "${path.module}/generated/kubeconfig/${var.cluster_name}-${var.environment}.yaml"
}

# =============================================================================
# Connection Summary
# =============================================================================

output "connection_summary" {
  description = "Human-readable connection summary"
  value = <<-EOT

    ══════════════════════════════════════════════════════════════
    Itunda Platform - ${var.cluster_name} (${var.environment})
    Region: ${var.region}
    ══════════════════════════════════════════════════════════════

    Bastion SSH:
      ssh -i ${local_sensitive_file.ssh_private_key.filename} \
          itunda-admin@${openstack_networking_floatingip_v2.bastion.address}

    API Server:
      https://api.${var.environment}.k8s.${var.dns_domain}:6443

    Ingress:
      HTTP/HTTPS: ${openstack_networking_floatingip_v2.ingress_http.address}
      TCP:        ${openstack_networking_floatingip_v2.ingress_tcp.address}
      Wildcard:   *.apps.${var.environment}.k8s.${var.dns_domain}

    Nodes:
      Control Plane: ${var.control_plane_count}x ${var.control_plane_flavor}
      Workers:       ${var.worker_count}x ${var.worker_flavor}

    ══════════════════════════════════════════════════════════════
  EOT
}
