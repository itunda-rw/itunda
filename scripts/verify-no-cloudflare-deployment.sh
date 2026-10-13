#!/usr/bin/env bash
set -euo pipefail

# Keep Itunda's repository aligned with its GCP Cloud Run deployment boundary.
# External GitHub App installations are managed separately from source code.
for path in wrangler.toml wrangler.json wrangler.jsonc worker/index.ts docs/cloudflare-workers.md; do
  if git ls-files --error-unmatch "$path" >/dev/null 2>&1; then
    echo "Forbidden legacy deployment artifact is tracked: $path" >&2
    exit 1
  fi
done

if git grep -n -i -E 'pages[.]dev|cloudflare[[:space:]]+pages|cloudflare[[:space:]]+tunnel|wrangler' -- . ':!scripts/verify-no-cloudflare-deployment.sh'; then
  echo "Legacy Cloudflare deployment reference detected. Use the GCP Cloud Run deployment path." >&2
  exit 1
fi

echo "No legacy Cloudflare deployment artifacts or references found."
