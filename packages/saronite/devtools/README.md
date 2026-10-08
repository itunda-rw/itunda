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


## Floating panel

Import `@itunda/saronite-devtools/panel` only from a development entrypoint and call `mountSaroniteDevTools()` after installing the browser mock. The panel is intentionally dependency-free and is not a production runtime capability.

The first panel surfaces platform, network, locale, latency, authentication, permissions, reset, and recent runtime logs. More capability-specific inspectors can be added without changing the host protocol.


## Capability inspection

The mock host exposes a capability protocol through registerCapability() and callCapability(). Calls are logged as request/response pairs, latency and offline simulation apply automatically, and unregistered capabilities fail closed with CAPABILITY_NOT_MOCKED.

The panel includes inspection controls for Auth, Navigation, Permissions, Storage, Payment, and Analytics. These are inspection calls, not production API implementations; real capability behavior remains owned by the native/web host integration.
