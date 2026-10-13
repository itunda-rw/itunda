# Engineering status contract

The current GitHub Actions run observed for commit bec311921fc2b1ddb18b2a740926b9ab379607c6 was pending, with its listed jobs queued. This is not a passing build. The combined commit-status endpoint returned no legacy status entries; that is not proof of success. Reading main branch protection was denied by the connector, so required checks remain unverified.

The existing Ops MFE is an operations dashboard, not yet an engineering status console. The first increment should be a read-only engineering status view backed by a server-side provider adapter. Do not put GitHub credentials in the browser.

Each check must include its name, explicit result, immutable commit SHA, source, observation timestamp, environment, and details URL where available. Only explicit, fresh success for the same required commit may count as green. Pending, failed, stale, unknown, missing, mismatched-commit, provider-error, and empty results must never be rendered as success. A hidden UI tab is not authorization; enforce staff permissions server-side.

Acceptance tests should cover success, failure, pending, unknown, stale results, missing or mixed commit SHAs, provider timeout, empty results, unauthorized access, and secret leakage. The UI should reuse Itunda design tokens and clearly show source and freshness.
