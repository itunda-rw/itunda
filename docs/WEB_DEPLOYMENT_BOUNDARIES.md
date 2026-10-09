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

The global HTTPS load balancer currently has `itunda-web-backend` as the URL map's default service. Dedicated public-site services and host rules must be provisioned and verified before claiming the public-site hostname split is complete. Domain DNS being configured does not by itself prove that each hostname reaches the correct product.

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
