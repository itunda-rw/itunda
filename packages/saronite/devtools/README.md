# Saronite DevTools

Development-only tools for Apps in Itunda.

The dev layer mirrors the platform contract without shipping host internals into production:

- mock SDK capabilities
- permission-aware call logging and permission toggles
- lifecycle simulation
- request IDs and optional correlation IDs for tracing a user action across mock calls
- live panel updates through explicit subscriptions (no polling timer)
- explicit unsupported/denied/error states
- clearable request history

Production Saronite remains the native host/runtime. DevTools is never a replacement for native verification.

## Request contract

Each call record includes:

- `requestId`: stable identifier within the current mock session
- `correlationId`: optional caller-provided trace label
- `capability` and `method`
- `status`: `ok`, `denied`, `unsupported`, or `error`
- `timestamp` and optional safe diagnostic `detail`

The mock must fail loudly for unknown capabilities instead of pretending an API exists. Permission denials must not execute the handler. Handler exceptions are recorded as error results and return `undefined`; callers must not treat that as a successful result.

## Local verification

From this package directory:

```sh
npm run contract-test
npm run typecheck
```

The contract tests cover permission denial, granted access, unsupported methods, handler exceptions, correlation/request IDs, state notifications, and unsubscribe/clear behavior.

## Boundary

These controls simulate state only. They do not validate the native Android/iOS permission boundary, real bridge transport, authentication, or backend authorization. Those require separate native and end-to-end tests.


## Deterministic SDK scenario host

Use `createSaroniteScenarioHost` to exercise common integration outcomes without changing production SDK code:

```ts
import { createSaroniteScenarioHost } from '@itunda/saronite-devtools';

const host = createSaroniteScenarioHost({
  id: 'wallet-offline-check',
  permissions: { 'wallet:read': true },
  unsupported: ['camera.openCamera'],
  failures: { 'wallet.getBalance': 'simulated upstream timeout' },
});

host.request('wallet', 'getBalance', () => 1200, {
  permission: 'wallet:read',
});
```

- Scenario IDs produce deterministic default correlation IDs (for example, `wallet-offline-check-1`).
- `unsupported` accepts a capability name or a `capability.method` key.
- `failures` maps a `capability.method` key to the error detail recorded in the request log.
- Permissions not explicitly granted remain denied; scenario state is isolated per host.
- The scenario runner is a development/test utility, not a network emulator and not evidence of native Android/iOS parity. Production builds must not depend on this package.
- Contract tests cover injected failures, unsupported methods, denied permissions, successful calls, and correlation IDs. The tests must pass in CI before this change is considered verified.


## Manifest and release-readiness checks

The exported `validateMiniAppManifest(input)` helper validates the versioned manifest contract without reading files or calling a service. It checks the manifest schema version, app ID, semantic version, safe relative entrypoint, target platforms, reviewed permission names, and optional bundle-size budget.

```ts
import { isReleaseReady, validateMiniAppManifest } from '@itunda/saronite-devtools';

const findings = validateMiniAppManifest({
  schemaVersion: 1,
  id: 'wallet-balance',
  name: 'Wallet Balance',
  version: '1.0.0',
  entrypoint: 'pages/index.tsx',
  platforms: ['android', 'ios'],
  permissions: ['identity:read'],
  bundle: { maxBytes: 200_000, entryBytes: 142_000 },
});

if (!isReleaseReady(findings)) {
  throw new Error(findings.filter((item) => item.severity === 'error').map((item) => item.message).join('; '));
}
```

Findings are machine-readable `severity` + `code` + `message`. Errors block readiness; warnings and informational findings require review but do not automatically block. Bundle size is only checked when actual `entryBytes` and an explicit `maxBytes` budget are supplied. The helper does not inspect a generated bundle, verify signatures, attest provenance, or replace Android/iOS build and security checks. A release pipeline must feed it measured build outputs and enforce the result.
