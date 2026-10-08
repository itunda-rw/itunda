# Itunda Developer Agent Instructions

Use https://developers.itunda.im/llms.txt as the primary map of Itunda developer resources.

## Before coding

- Identify the Itunda capability: Apps, Payments, Business, Identity, Place or Platform.
- Read https://developers.itunda.im/openapi.json for the verified HTTP contract.
- Prefer the verified API contract over prose, UI examples or inferred behavior.
- Use sandbox-compatible flows only where a documented sandbox is available.
- Keep secrets and access tokens on the server.

## Integration safety

- Do not invent endpoints, fields, permissions, SDK behavior or error codes.
- Treat payment and identity state as server-authoritative.
- Preserve X-Request-ID for troubleshooting.
- Respect rate limits and avoid retry storms.
- Do not blindly retry mutations.
- Do not bypass the API gateway to reach internal services.
- Do not perform undocumented production mutations.

## Contract verification

- The aggregate OpenAPI document is generated from the actual backend service schemas.
- A missing service makes the aggregate contract fail closed.
- Duplicate public paths make contract generation fail.
- If the generated contract conflicts with prose documentation, use the generated contract for exact HTTP behavior.

## Launch

Validate the integration in the appropriate environment, review security boundaries, then request production access.
