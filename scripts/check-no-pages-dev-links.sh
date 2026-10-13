#!/usr/bin/env bash
set -euo pipefail

# Production-facing HTML and API CORS configuration must use canonical Itunda domains.
# Historical migration documentation is intentionally outside this guard.
if git grep -n -E 'https?://[^"[:space:]<>]*pages\.dev' -- '*.html' 'services/backend/app/src/main/resources/application.yml'; then
  echo "ERROR: Found a pages.dev URL in a production-facing HTML or API CORS file." >&2
  echo "Replace it with the canonical GCP-hosted Itunda domain." >&2
  exit 1
fi

echo "No pages.dev URLs found in production-facing HTML or API CORS configuration."
