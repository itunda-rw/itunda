# Saronite DevTools

Development-only tools for Apps in Itunda.

The dev layer mirrors the platform contract without shipping host internals into production:

- mock SDK capabilities
- permission-aware call logging
- lifecycle simulation
- floating developer panel
- explicit unsupported/denied/error states

Production Saronite remains the native host/runtime. DevTools is never a replacement for native verification.

## Contract

Every mocked capability call is represented as:

`{ capability, method, status, timestamp, detail }`

Status is one of `ok`, `denied`, `unsupported`, or `error`.

The mock must fail loudly for unknown capabilities instead of pretending an API exists.

## Capability registry

DevTools must consume the same `packages/saronite/sdk-manifest.json` registry as the SDK and developer console. A capability not present in the registry is unsupported and must produce an explicit error.

This keeps local browser development aligned with the native Android/iOS contract instead of allowing a mock-only API to drift into production assumptions.
