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
