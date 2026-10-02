# Saronite Protocol

Saronite Protocol is the stable boundary between the Itunda host, mini-app SDK,
web mock, debugger and development tooling.

## Message model

- **request** — a capability invocation from a mini-app/tool to a host.
- **response** — the correlated result or typed error.
- **event** — lifecycle, permission and host-originated notifications.

Every message carries protocol version and correlation ID. Production hosts may
implement the transport differently on Android, iOS or Web; transports must not
change the message contract.

## Boundary

`@itunda/saronite-protocol` contains data contracts only. It must not import
native UI, browser APIs, DevTools, debugger code or application business logic.

This keeps the production bridge stable while allowing developer tooling to
evolve independently.
