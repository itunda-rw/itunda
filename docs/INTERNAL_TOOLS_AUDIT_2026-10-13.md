# Internal Tools Audit — Evidence and First Implementation Plan

Date: 2026-10-13
Scope: GitHub source review of the current `main` branch. This is not a live CI run, production health check, or deployment verification.

## Evidence reviewed

- `services/micro-frontends/ops-mfe/package.json`
- `services/micro-frontends/ops-mfe/src/App.tsx`
- `services/micro-frontends/ops-mfe/src/OpsDashboard.tsx`
- `services/micro-frontends/ops-mfe/src/lib/api.ts`
- `services/micro-frontends/ops-mfe/src/index.css`
- `.github/workflows/ci-cd.yml`
- `docs/CI_WOODPECKER_SETUP.md`
- `services/api-gateway/package.json`
- `packages/saronite/package.json`
- `services/developer-docs/package.json`

## Findings

### 1. Ops MFE is an existing operations console, not yet an engineering control center

The current `OpsDashboard.tsx` exposes product-operation queues such as fraud, compliance, incidents, reconciliation, support, merchant moderation, webhook failures and analytics. It is a useful base to extend, but the reviewed navigation does not show engineering views for pull-request checks, build provenance, deployment history or environment promotion.

The app already has a login boundary and shared backend API helper. Do not add unauthenticated direct access to GitHub, cloud infrastructure, or deployment mutation endpoints in the browser.

### 2. CI already has many useful checks, but their current status is not visible from this source review

The GitHub Actions workflow defines jobs for monorepo lint/type checks, host-app build, public-site build, gateway tests, container builds, migration/security checks, backend and microservices builds, and Android/iOS builds.

Source code establishes that these jobs are configured; it does not prove the latest run passed. Current run state, required branch-protection checks, artifact retention, and whether each path is actively triggered need to be verified from live GitHub Actions settings/run data.

### 3. There are two CI execution paths

`.github/workflows/ci-cd.yml` is described as the primary pipeline. `docs/CI_WOODPECKER_SETUP.md` documents a parallel, independently operated Woodpecker pipeline. This can be valuable resilience, but it creates a risk of inconsistent check coverage or ambiguity over which result is authoritative.

Recommendation: define one release-readiness contract and map both systems to it. Do not remove either pipeline until actual usage and fallback needs are verified.

### 4. API and SDK workbenches have clear starting points

- `services/api-gateway/package.json` defines a gateway test command.
- `packages/saronite/package.json` is an independent Yarn workspace with type-check scripts across workspaces.
- `services/developer-docs/package.json` defines a buildable Vite/React documentation site.

These manifests do not establish complete OpenAPI coverage, generated SDK provenance, contract-test coverage, or production deployment. Those require a file-level audit of source and workflows.

### 5. Security and truthful status are foundational

The operations API helper clears the local session on HTTP 401/403. That is useful for authenticated product-operations views. The new engineering views should additionally ensure:
- Unknown, stale, loading, failed, and successful states are distinct.
- Every status has a source and timestamp.
- Secrets and privileged tokens never reach browser code or logs.
- Deployment mutations are not added in the first slice.
- A dashboard never infers green status from missing data.

## First safe implementation slice

1. Add an engineering-status data contract and types with source, commit SHA, environment, check name, result, observed-at time, and optional detail URL.
2. Make UI rendering fail closed: missing or stale status becomes “Unknown/Stale”, never “Success”.
3. Connect read-only data through a backend-owned, authenticated endpoint after verifying the actual backend routing/auth model. Do not have the browser query privileged provider APIs directly.
4. Add unit tests for success, failure, missing, malformed and stale data.
5. Add CI tests and only then add the view to the existing Ops MFE.

## Proposed status contract

```ts
type CheckResult = "success" | "failure" | "pending" | "cancelled" | "unknown";

interface EngineeringCheck {
  id: string;
  name: string;
  result: CheckResult;
  commitSha: string;
  environment: "local" | "sandbox" | "staging" | "production";
  source: string;
  observedAt: string; // ISO-8601 timestamp
  detailsUrl?: string;
}
```

A UI may display “Success” only when the result is explicitly `success`, the commit SHA is known, and the observation is within a documented freshness window. For any other case, display the actual result or an explicit stale/unknown state.

## Out of scope until evidence exists

- Claiming current CI is green without checking the run.
- Claiming the sites or services are healthy from repository configuration alone.
- Triggering deployments or changing production infrastructure.
- Replacing GitHub Actions or Woodpecker before comparing their actual checks.
- Adding a new database, queue, or observability vendor solely to imitate Toss.

## Next verification task

Inspect the current GitHub Actions runs and branch protection, then inspect backend routes and auth policy to find the correct read-only source for build/deployment metadata. Only after that should the status contract be implemented in production code.
