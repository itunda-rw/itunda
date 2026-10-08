# Saronite Debugger

Development-only host-side boundary for Saronite diagnostics.

This package is deliberately separate from the runtime debug console. It is not imported by production applications. The transport/relay implementation can be added later without expanding the production host path.

The wire-level message contract lives in `@itunda/saronite-debug-protocol`.

## Relay boundary

The debugger exposes a development-only WebSocket-compatible transport and JSON framing helpers. Relay frames always contain an explicit session ID and a validated Saronite debug message. Authentication, pairing, and network exposure belong to the local relay/host process; the production app must not import this package.
