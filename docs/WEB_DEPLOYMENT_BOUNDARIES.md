# Itunda Web Deployment Boundaries

**Production hosting policy: Google Cloud Platform only.** GitHub Actions is the CI/CD runner; it must deploy production workloads to GCP. GitHub Pages and Cloudflare Pages are not production targets.

## Product boundaries

| Product | Intended hostname | GCP service / deployment boundary |
|---|---|---|
| Itunda public site | `itunda.im` | Dedicated public-site service and host rule |
| Consumer app | `app.itunda.im` | Cloud Run `itunda-web` |
| Itunda Business public site | `business.itunda.im` | Dedicated business-site service and host rule |
| Merchant app | `business-app.itunda.im` | Cloud Run `itunda-business-app` |
| Itunda Developers | `developers.itunda.im` | Dedicated developer-site service and host rule |
| Itunda Tech | `tech-blog.itunda.im` | Dedicated tech-site service and host rule |
| API | `api.itunda.im` | Cloud Run `itunda-api` |

Public information sites must remain separate products from the authenticated consumer and merchant apps. Do not route every hostname to the same generic site.

## Existing GCP deployment services

The verified Cloud Run inventory in `asia-northeast3` contains:

- `itunda-api`
- `itunda-web`
- `itunda-business-app`

The global HTTPS load balancer currently maps `app.itunda.im`, `business.itunda.im`, `developers.itunda.im`, `itunda.im`, `tech-blog.itunda.im`, and `www.itunda.im` to the same `itunda-web` path matcher. `api.itunda.im` maps to `itunda-api`; `business-app.itunda.im` maps to `itunda-business-app`. The `itunda-web` default is therefore still serving multiple distinct public-site hostnames. Dedicated public-site services need corresponding backend services/NEGs and explicit host rules before the split is complete. Domain DNS and active TLS alone do not prove correct product routing.

## App deployment contracts

- Consumer app source: `services/micro-frontends/host-app` plus its bundled remotes; image build: `services/micro-frontends/Dockerfile.gcp`; Cloud Run service: `itunda-web`.
- Merchant app source: `services/micro-frontends/merchant-mfe`; image build: `services/micro-frontends/Dockerfile.business-gcp`; Cloud Run service: `itunda-business-app`.
- API source: `services/backend`; Cloud Run service: `itunda-api`.
- GCP deployments use Artifact Registry and GitHub Actions Workload Identity Federation. Do not add long-lived service-account keys to repository secrets.

## Required release verification

For every product hostname, verify independently:

1. DNS points at the GCP load balancer or the intended GCP custom-domain target.
2. HTTPS certificate is active.
3. Host-based routing reaches the intended product, not the URL map's unrelated default.
4. HTML, JS/CSS bundles, canonical Itunda brand assets and splash screen load.
5. Browser startup has no fatal errors; light and dark themes render.
6. Consumer/business apps can reach the GCP API and complete the authentication journey.

## Native distribution

Android and iOS distribution remain separate from web hosting. Shared Itunda brand assets must be used unchanged from the canonical brand package.


## Public-site rollout runbook

The public-site workflow deploys Cloud Run services but intentionally does not mutate the production load balancer or DNS. Complete these steps in order; do not switch host rules before the corresponding service passes its smoke test.

### 1. Prepare a dedicated runtime identity

Create a runtime identity for static public sites rather than reusing the API's runtime identity:

```sh
gcloud iam service-accounts create itunda-public-site \
  --project=itunda-org \
  --display-name="Itunda public sites Cloud Run runtime"
```

Grant only the runtime permissions the static containers actually need (normally none beyond logging/monitoring defaults). Allow the GitHub deployer to attach this identity by granting `roles/iam.serviceAccountUser` on this specific service account, not project-wide:

```sh
gcloud iam service-accounts add-iam-policy-binding \
  itunda-public-site@itunda-org.iam.gserviceaccount.com \
  --project=itunda-org \
  --member="serviceAccount:github-deployer@itunda-org.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

Then configure the public-site Cloud Run deployment to explicitly use `itunda-public-site@itunda-org.iam.gserviceaccount.com`. Verify the deployer can attach this identity before dispatching the production workflow. Do not use `itunda-api@itunda-org.iam.gserviceaccount.com` as the static-site runtime identity.

### 2. Deploy and verify services

Run the `Deploy Public Sites to GCP` workflow from the protected `main` branch only after CI passes and the dedicated runtime identity is configured. Verify each deployed Cloud Run service URL directly before editing URL-map rules:

- `itunda-public-site`
- `itunda-business-site`
- `itunda-developers-site`
- `itunda-tech-blog`

Each should return non-empty HTML and its required IDS/theme and canonical brand assets.

### 3. Split the global HTTPS load balancer

For each public site, create a regional serverless NEG in `asia-northeast3` targeting its corresponding Cloud Run service, then a global backend service. Update `itunda-global-url-map` so that:

- `itunda.im` and `www.itunda.im` target `itunda-public-site`
- `business.itunda.im` targets `itunda-business-site`
- `developers.itunda.im` targets `itunda-developers-site`
- `tech-blog.itunda.im` targets `itunda-tech-blog`
- `app.itunda.im` remains on `itunda-web`
- `business-app.itunda.im` remains on `itunda-business-app`
- `api.itunda.im` remains on `itunda-api`

Preserve the existing HTTPS proxy, forwarding rule, certificate map/certificate attachments, and unrelated URL-map paths. Review the proposed URL map before applying it. Do not change Spaceship DNS unless DNS verification shows it is required.

### 4. Production acceptance

After the URL map update, run independent HTTPS checks for all seven product hostnames, verify each response belongs to the intended product, and inspect app/browser startup and API calls. Roll back the URL map to its prior configuration if any hostname returns the wrong product or fails its smoke test. Record the before/after URL-map snapshots in the release evidence.
