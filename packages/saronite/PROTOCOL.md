# Saronite Protocol

Saronite uses a small versioned, platform-neutral message contract between a mini-app and its host.

## Goals

- Keep the wire contract independent of React Native, Android, iOS, and the browser.
- Correlate every request and response with a stable request ID.
- Represent lifecycle, permission, capability, and native failures consistently.
- Make protocol/version mismatches explicit instead of silently falling back.
- Give DevTools and future debugger tooling the same contract as the production bridge.

## Message envelope

Every message carries:

- `protocol`: `saronite`
- `version`: protocol major version
- `kind`: `request`, `response`, or `event`
- `requestId`: correlation identifier
- `timestamp`: host/transport timestamp

Requests additionally identify a `capability` and `method`. Responses are explicitly `ok: true|false` and carry a typed payload or a structured `SaroniteError`.

## Errors

The initial error vocabulary is deliberately small:

`UNSUPPORTED`, `PERMISSION_DENIED`, `INVALID_REQUEST`, `NOT_AUTHENTICATED`, `NOT_AUTHORIZED`, `LIFECYCLE_BLOCKED`, `NATIVE_FAILURE`, `NETWORK_FAILURE`, `TIMEOUT`, `INTERNAL_ERROR`, and `PROTOCOL_MISMATCH`.

Errors may also identify the capability/method and whether retrying is reasonable.

## Lifecycle

The host lifecycle vocabulary is:

`created` → `visible` ↔ `hidden` → `suspended` → `destroyed`.

The protocol does not prescribe how a platform implements those transitions. Android and iOS remain responsible for their native lifecycle.

## Ownership

The protocol contract lives at:

`packages/saronite/packages/brownfield-module/src/protocol.ts`

It is exported through `@itunda/saronite-brownfield-module` so the React Native SDK can consume the same definitions.

This is an Itunda-native implementation inspired by the public architectural pattern of Toss Apps in Toss. It does not copy Toss's private protocol, generated code, or proprietary bridge.

## Next layers

1. Map every public Saronite capability to a protocol method + permission.
2. Make the DevTools mock emit/consume the same envelopes.
3. Add a host transport adapter for Android and iOS.
4. Add debugger inspection of correlated request/response/event traffic.
5. Keep protocol version negotiation separate from app/SDK semantic versions.
