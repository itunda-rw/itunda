# Production boundary

Saronite DevTools is development-only.

Production mini-app bundles and the native Itunda host must not import this package. The package exists to simulate the same capability contract in a browser and to expose observable permission/lifecycle outcomes.

The required separation is:

- **DevTools:** mock capabilities, call inspection, lifecycle simulation.
- **Saronite runtime:** native lifecycle, navigation, permissions, identity, bridge, bundle loading, caching, failure recovery.
- **Partner app:** feature UI and business logic within the granted capability scope.

Real-device verification remains mandatory before treating a mini-app as production-ready.
