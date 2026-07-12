# infra/k8s/local-dev

This directory exists for one reason: `infra/k8s/production/*.yaml` deliberately
does **not** create `itunda-db-credentials`, `itunda-jwt-secret`, or any of the
`itunda-*-config` ConfigMaps it references -- see `production/backend.yaml`'s
header comment. That's the right call for a real cluster (secrets are provisioned
out-of-band by whoever owns the credentials), but it also means the production
manifests can't be `kubectl apply`'d against an empty cluster and actually verified
end-to-end.

This directory closes that gap for **local kind/minikube verification only**:

- `mysql.yaml`, `redis.yaml`, `kafka.yaml` -- single-replica, no-persistence,
  in-cluster stand-ins for the managed MySQL/Redis/Kafka that `docker-compose.yml`
  runs for host-based local dev. Same database layout as
  `infra/mysql-init/01-init-databases.sql` (one MySQL instance, three databases:
  `itunda`, `itunda_ledger`, `itunda_payment`).
- `config-and-secrets.yaml` -- the ConfigMaps/Secret the production manifests
  expect, pointed at the Services above, with **dev-only, non-production values**.

None of this is meant to be layered onto a real cluster. It exists so
`infra/k8s/production` can be applied against a disposable local cluster and
actually reach `Running`/`Ready`, the same way `docker-compose.yml` does for
host-based development.

`infra/k8s/production/*.yaml` also names real images
(`ghcr.io/itunda/backend:latest`, etc.) that have never actually been published
anywhere -- there's no registry to pull them from. A `kustomization.yaml` retargeting
those to locally-built images was tried first and abandoned: `kubectl apply -k`'s
built-in kustomize refuses to load resources from outside its own directory (a real
security restriction, not a bug), and no standalone `kustomize` binary is available
in this environment either. `kubectl set image` + `kubectl patch` after the fact is
the more portable equivalent and doesn't require either:

```
# 1. Build the 4 images and load them into the kind node (no registry needed locally)
docker build -t itunda/backend:local -f services/backend/Dockerfile services/backend
docker build -t itunda/ledger-service:local -f services/microservices/ledger-service/Dockerfile services/microservices
docker build -t itunda/payment-service:local -f services/microservices/payment-service/Dockerfile services/microservices
docker build -t itunda/api-gateway:local -f services/api-gateway/Dockerfile services/api-gateway
kind load docker-image itunda/backend:local itunda/ledger-service:local itunda/payment-service:local itunda/api-gateway:local --name <your-kind-cluster>

# 2. Namespaces, then local stateful deps + config/secrets, then the real production manifests
kubectl apply -f infra/k8s/base/namespace.yaml
kubectl apply -f infra/k8s/local-dev/mysql.yaml -f infra/k8s/local-dev/redis.yaml -f infra/k8s/local-dev/kafka.yaml -f infra/k8s/local-dev/config-and-secrets.yaml
kubectl apply -f infra/k8s/production/

# 3. Retarget each Deployment at the locally-loaded image instead of ghcr.io/itunda/*:latest,
#    and set imagePullPolicy: Never so kubelet uses the image already in the kind node
#    rather than trying (and failing) to reach a real registry
for svc in backend:backend ledger-service:ledger-service payment-service:payment-service api-gateway:api-gateway; do
  deployment="${svc%%:*}"; container="${svc##*:}"
  kubectl -n itunda set image "deployment/$deployment" "$container=itunda/$deployment:local"
  kubectl -n itunda patch "deployment/$deployment" --type=json \
    -p '[{"op":"add","path":"/spec/template/spec/containers/0/imagePullPolicy","value":"Never"}]'
done
```
