# Saronite DevTools

Development-only browser tooling for Saronite mini-apps.

It provides a deterministic mock host plus an inspection surface for:
- authentication/session state
- platform/environment state
- permissions
- navigation
- storage
- emitted events
- request/response logs
- simulated network failures and latency

The mock is intentionally browser-only and must never be imported by production
native bundles. Production capability calls continue through the native Saronite
host.

The public control surface is `window.__saronite` in development:

```ts
__saronite.state
__saronite.update({ platform: 'android', network: 'offline' })
__saronite.setPermission('camera', 'denied')
__saronite.clearLogs()
```

This follows the useful public pattern of browser mocks and floating runtime
inspection used by Apps in Toss, while keeping the protocol and UI entirely
Itunda-owned.
