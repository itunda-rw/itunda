# Itunda Mini-App Sandbox Contract

The sandbox is the first local/remote environment developers use before production.

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

Production traffic must reject `X-Itunda-Sandbox-Scenario`.

## Test identity

Sandbox identities are synthetic and must be clearly marked. They must never overlap with production identity records.

## Release boundary

The CLI may generate and validate release metadata, but publishing a production release requires the developer console's authorization and review workflow.

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
 developer console
        |
 staged production
        |
    production
```

This mirrors the important public Apps in Toss pattern: scaffold -> local mock development -> debug/device validation -> console upload/registration -> release. The implementation remains Itunda-native.
