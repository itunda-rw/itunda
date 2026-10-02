# Saronite Debugger

The debugger is a development-only boundary. It consumes the versioned
Saronite Protocol and connects to Android, iOS or Web transports without being
linked into production mini-app bundles.

The debugger is intentionally transport-agnostic:

`transport -> protocol -> debugger session -> DevTools UI`

Production hosts own the native transport implementation; this package owns
debugger lifecycle, request correlation and event subscriptions.
