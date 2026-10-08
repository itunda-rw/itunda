# Saronite Debugger

Development-only host-side boundary for Saronite diagnostics.

This package is deliberately separate from the runtime debug console. It is not imported by production applications. The transport/relay implementation can be added later without expanding the production host path.

The wire-level message contract lives in `@itunda/saronite-debug-protocol`.

## Relay boundary

The debugger exposes a development-only WebSocket-compatible transport and JSON framing helpers. Relay frames always contain an explicit session ID and a validated Saronite debug message. Authentication, pairing, and network exposure belong to the local relay/host process; the production app must not import this package.


## Authenticated development sessions

The debugger exposes an in-memory session manager through `createSaroniteDebugSessionManager()`.

A development launcher should:
1. create a session for the target platform;
2. keep the returned `authToken` outside the app bundle;
3. require that token before attaching a relay transport;
4. pass only authenticated messages into the debugger;
5. destroy the session when the development run ends.

The session manager intentionally does not provide a production network listener. A future CLI/relay daemon can bind it to WebSocket, USB, emulator, or simulator transports without putting a socket server into the Saronite host runtime.
