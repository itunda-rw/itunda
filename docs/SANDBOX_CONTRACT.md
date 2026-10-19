# Itunda Mini-App Sandbox Contract

The sandbox is the first local/remote environment developers use before production. It must let a developer reproduce behavior in a realistic mini-app host without risking production data or credentials.

## Required capabilities

Every supported payment-capable mini-app must be able to simulate:

- success
- insufficient funds
- provider decline
- provider timeout
- duplicate request
- expired payment
- webhook retry
- refund
- settlement delay

## Determinism

Sandbox responses are selected by explicit scenario headers or test fixtures.

Example:

```http
X-Itunda-Sandbox-Scenario: payment.success
Idempotency-Key: test-payment-001
```

Supported scenarios:

```text
payment.success
payment.insufficient_funds
payment.provider_declined
payment.provider_timeout
payment.duplicate
payment.expired
webhook.retry
refund.success
settlement.delayed
```

Production traffic must reject `X-Itunda-Sandbox-Scenario`. The scenario header must never select or alter a production payment path.

## Host-aware web debugging

The developer toolchain must include an opt-in WebView inspector for mini-apps launched through the Itunda sandbox or a signed debug build. It should expose the information needed to reproduce host-only failures:

- **Logs:** `console.log`, `console.warn`, and `console.error`, with timestamps and severity.
- **Network:** request method, sanitized URL, status, duration, and failure reason; support correlation IDs to connect client requests with server traces.
- **Runtime:** host app version, SDK version, platform, WebView/runtime version, viewport, and relevant capability availability.
- **Page structure:** a read-only DOM/element inspector for layout and accessibility debugging.
- **Reproduction:** capture a shareable diagnostic bundle containing metadata and redacted logs, plus a deterministic fixture reference where possible.

### Security and privacy boundaries

- Debugging is disabled in production builds by default and must require explicit developer/test authorization.
- Never capture PINs, passwords, access tokens, session cookies, payment credentials, or raw identity documents.
- Redact authorization headers, sensitive query parameters, and configured personal-data fields before display or export.
- Do not capture page content or network bodies by default; make any exceptional capture explicit, scoped, and visibly enabled.
- Diagnostic bundles must have a retention limit, access controls, and a clear delete path.
- The inspector is read-only with respect to production state; it must not expose arbitrary code execution or a production request-replay control.

## Test identity

Sandbox identities are synthetic and must be clearly marked. They must never overlap with production identity records. Test fixtures must be isolated by environment and tenant.

## Release boundary

The CLI may generate and validate release metadata, but publishing a production release requires the developer console's authorization and review workflow. Every submitted bundle must have immutable release metadata and a verifiable SHA-256 digest.

## Developer workflow

```text
itunda app create
itunda app dev
itunda app validate
itunda app build
itunda app test
itunda app release
        |
     sandbox
        |
  host debugger
        |
 developer console
        |
 staged production
        |
    production
```

This follows public mini-app platform patterns—scaffold, local mock development, sandbox/device debugging, console submission, review, and release—while keeping the implementation and identity Itunda-native.
