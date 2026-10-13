#!/usr/bin/env bash
set -euo pipefail

# Prevent new legacy Pages links without blocking migration PRs on historical links
# that have not yet been touched. Historical migration documentation is outside this guard.
if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
  base_ref="origin/${GITHUB_BASE_REF:-main}"
  git diff --unified=0 "${base_ref}...HEAD" -- '*.html' 'services/backend/app/src/main/resources/application.yml' |
    grep -E '^\+[^+].*https?://[^"[:space:]<>]*pages\.dev' && {
      echo "ERROR: This change adds a pages.dev URL to production-facing HTML or API CORS." >&2
      echo "Use the canonical GCP-hosted Itunda domain instead." >&2
      exit 1
    }
else
  # On pushes to main, check the commit being delivered.
  git diff --unified=0 HEAD^ HEAD -- '*.html' 'services/backend/app/src/main/resources/application.yml' |
    grep -E '^\+[^+].*https?://[^"[:space:]<>]*pages\.dev' && {
      echo "ERROR: This commit adds a pages.dev URL to production-facing HTML or API CORS." >&2
      echo "Use the canonical GCP-hosted Itunda domain instead." >&2
      exit 1
    }
fi

echo "No new pages.dev URLs added to production-facing HTML or API CORS configuration."
