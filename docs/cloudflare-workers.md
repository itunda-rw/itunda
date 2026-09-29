# Itunda on Cloudflare Workers

Itunda's web layer is moving from a Pages-only static deployment to **Workers Static Assets + an edge Worker**.

## Architecture

- `site/` — marketing/product HTML, CSS, JavaScript and assets.
- `worker/index.ts` — edge runtime and API/BFF entry point.
- `wrangler.jsonc` — Workers deployment configuration.
- `/api/*` — dynamic edge routes.
- Existing Itunda Go/Postgres/Kafka services remain the core platform; the Worker is the web edge/BFF layer rather than a replacement for those services.

Cloudflare's current guidance recommends Workers Static Assets for new static, SPA and full-stack projects. Static assets can be served directly while selected routes such as `/api/*` invoke Worker code first.

## Current dynamic foundation

- `GET /api/health` — edge health response.
- `GET /api/version` — web platform metadata.
- All other paths fall through to the Itunda static asset collection.

## Deployment

From the repository root:

`npx wrangler deploy`

A Cloudflare API token/account connection is required for CI or direct deployment.

## Migration principle

Do not turn the marketing homepage into a server-heavy application just to make it dynamic. Keep the product film, typography and visual assets cacheable at the edge, while authentication, personalized data, product APIs and BFF behavior become dynamic behind `/api/*`.

This gives Itunda the product-led web behavior we want while preserving fast static delivery.
