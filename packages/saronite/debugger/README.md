# Saronite Debugger

The debugger is a development-only boundary. It consumes the versioned
Saronite Protocol and connects to Android, iOS or Web transports without being
linked into production mini-app bundles.

The debugger owns cross-platform inspection state:

- request/response correlation and timing
- lifecycle events
- permission state
- recent event history
- recent capability request diagnostics
- connection shutdown and pending-request cleanup

The transport remains platform-owned:

`transport -> protocol -> debugger session -> DevTools UI`

Production hosts must not import this package.