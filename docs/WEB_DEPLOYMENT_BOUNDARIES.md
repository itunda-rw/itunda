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

The production Cloud Run inventory includes the app services and dedicated public-site services `itunda-public-site`, `itunda-business-site`, `itunda-developers-site`, and `itunda-tech-blog` in `asia-northeast3`.

**Configuration audit (2026-10-10):** the live global URL map `itunda-global-url-map` now has explicit host rules and separate backend services/serverless NEGs for all seven product hostnames:

| Hostname | URL-map matcher | Backend service | Cloud Run target |
|---|---|---|---|
| `itunda.im`, `www.itunda.im` | `itunda-public-site` | `itunda-public-site-backend` | `itunda-public-site` |
| `app.itunda.im` | `itunda-web` | `itunda-web-backend` | `itunda-web` |
| `business.itunda.im` | `itunda-business-site` | `itunda-business-site-backend` | `itunda-business-site` |
| `business-app.itunda.im` | `itunda-business-app` | `itunda-business-app-backend` | `itunda-business-app` |
| `developers.itunda.im` | `itunda-developers-site` | `itunda-developers-site-backend` | `itunda-developers-site` |
| `tech-blog.itunda.im` | `itunda-tech-blog` | `itunda-tech-blog-backend` | `itunda-tech-blog` |
| `api.itunda.im` | `itunda-api` | `itunda-api-backend` | `itunda-api` |

This is verified load-balancer configuration, not proof of successful end-to-end browser rendering, authentication, or API calls. Independently smoke-test each HTTPS hostname and app login journey before declaring production acceptance. The URL-map default remains `itunda-web-backend`; every distinct hostname must continue to have an explicit host rule.

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

### 1. Runtime identity

The deployment workflow explicitly sets `--service-account` to `itunda-public-site@itunda-org.iam.gserviceaccount.com` by default (override with the repository variable `GCP_PUBLIC_SITE_RUNTIME_SERVICE_ACCOUNT` only when intentionally changing the runtime identity). Do not silently fall back to the project-default Compute identity. Before deployment, grant `github-deployer@itunda-org.iam.gserviceaccount.com` the `roles/iam.serviceAccountUser` role scoped to the dedicated `itunda-public-site` service account. This least-privilege runtime identity is intended for static HTML/CSS/JS containers that do not call Google APIs. If the grant is missing, deployment should fail safely; resolve the IAM binding through the supported GCP IAM interface rather than broadening project-level roles.


### 2. Deploy and verify services

Run the `Deploy Public Sites to GCP` workflow from the protected `main` branch only after CI passes and the dedicated runtime identity is configured. Verify each deployed Cloud Run service URL directly before editing URL-map rules:

- `itunda-public-site`
- `itunda-business-site`
- `itunda-developers-site`
- `itunda-tech-blog`

Each should return non-empty HTML and its required IDS/theme and canonical brand assets.

### 3. Split the global HTTPS load balancer

The dedicated serverless NEGs, global backend services, and explicit host rules are already present in the audited configuration above. Do not recreate them blindly. If a future change is needed, review the proposed URL map first and update `itunda-global-url-map` so that:

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
