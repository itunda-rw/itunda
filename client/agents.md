# Itunda Developer Agent Instructions

Use https://developers.itunda.im/llms.txt as the primary map of Itunda developer resources.

## Before coding

- Identify the Itunda capability: Apps, Payments, Business, Identity, Place or Platform.
- Prefer documented API contracts over inferred behavior.
- Start with sandbox-compatible flows.
- Keep secrets and access tokens on the server.

## Integration safety

- Do not invent endpoints, fields, permissions or SDK behavior.
- Treat payment and identity state as server-authoritative.
- Preserve request IDs for troubleshooting.
- Respect rate limits and avoid retry storms.

## Launch

Validate the integration in sandbox, review security boundaries, then request production access.
