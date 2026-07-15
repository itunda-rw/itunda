# Progressive Delivery

This directory is the next step after `infra/k8s/production/`.

It models the Toss-style deployment shape described publicly:

- Istio as the traffic layer
- Argo Rollouts as the deployment controller
- independent stable and canary services
- gradual traffic movement instead of plain rolling updates

Apply this directory instead of the plain `Deployment` manifests when you are ready to move the
app layer to progressive delivery.

Requirements:

- Istio installed
- Argo Rollouts CRDs/controller installed
- Prometheus reachable if you later add automated analysis templates

These manifests deliberately keep names aligned with the production services so the eventual cutover
is a replacement, not a parallel shadow stack.
