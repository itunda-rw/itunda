# Saronite Debugger

Development-only host-side boundary for Saronite diagnostics.

This package is deliberately separate from the runtime debug console. It is not imported by production applications. The transport/relay implementation can be added later without expanding the production host path.

The wire-level message contract lives in `@itunda/saronite-debug-protocol`.