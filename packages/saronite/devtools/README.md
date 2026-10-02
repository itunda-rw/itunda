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
