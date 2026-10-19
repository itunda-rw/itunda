# Saronite / Granite Compatibility Assessment

**Assessment date:** 2026-10-13  
**Decision:** keep the current Granite-backed Saronite runtime; do not replace or broadly upgrade it in this change.  
**Evidence source:** repository branch `agent/itunda-agent-network`, package manifests and lockfile. This assessment does not claim a fresh native build or production runtime test.

## Current dependency evidence

| Package / area | Declared state in repository | Implication |
|---|---|---|
| Saronite root | Private npm workspace `saronite@0.1.0`; independent `package-lock.json`; workspace packages, mini-apps and host app | Do not fold it into the root Yarn workspace without a deliberate lockfile and Metro-resolution migration. |
| Granite host dependencies | `@granite-js/brownfield-module`, `@granite-js/native`, `@granite-js/react-native` all declared as `^1.0.36` in `packages/saronite/host-app/package.json` | The semver ranges allow newer compatible minor/patch releases; the lockfile, not the range alone, determines the installed versions. |
| React Native runtime | Host manifest pins `react-native@0.84.0` and `react@19.2.3`; safe-area-context 5.6.2, screens 4.23.0, SVG 15.15.3 | Keep React/RN peer alignment and Metro resolution as explicit compatibility gates. |
| Saronite typed bridge | `@itunda/saronite-brownfield-module@0.1.0`; JS wrapper package `@itunda/saronite-react-native@0.1.0` | Saronite adds Itunda-specific bridge operations beyond Granite's generic brownfield surface. Contract parity matters more than package-name parity. |
| Mini-app catalog | Host manifest depends on pay-bills, reward-tasks and wallet-balance mini-app workspaces | Validate every mini-app against the same host React/RN version and native bridge contract. |
| Manifest validation | `validate-manifest.mjs` validates schema version, ID, semantic version, entry, unique/known capabilities, protocol version, permissions and release metadata including SHA-256 and rollout percentage | Existing baseline validator is a useful security gate; add automated negative-case tests and tie manifest validation to every packaged artifact. |
| SDK validation commands | Root Saronite scripts expose `npm run typecheck --workspaces --if-present` and `npm run validate:manifest` | A script being declared is not proof it currently passes. Run from the Saronite root in CI and retain logs/artifact SHA. |

## Public Granite facts relevant to Itunda

The public [Toss Granite repository](https://github.com/toss/granite) documents React Native integration into existing native iOS/Android apps, split mini-app bundles, ESBuild-based builds, end-to-end tests and AWS-oriented deployment examples. Its license is Apache-2.0 according to the public repository README. These are public framework capabilities, not proof that every capability is already enabled in Itunda.

The current Itunda host already declares Granite packages and uses a native Android host integration. Therefore, **“migrate to Granite” is not an accurate description of the next step**. The next step is to measure the current Granite-backed Saronite integration and close parity gaps.

References:
- https://github.com/toss/granite
- https://github.com/toss/apps-in-toss-examples
- https://github.com/toss/frontend-fundamentals

## Decision: preserve the current runtime until these gates are satisfied

### Gate A — resolve exact dependency graph

- Read the lockfile's resolved versions for all three Granite packages and compare them with the host manifest ranges.
- Confirm the same React Native version is resolved by the host, brownfield module and every mini-app.
- Check duplicate React / React Native instances, peer warnings and Metro resolver assumptions.
- Save an inventory artifact from CI so future upgrades are diffable.

### Gate B — test the host/native contract

- Typecheck Saronite root workspaces and all mini-apps.
- Run manifest validation on every shipped manifest; add fixtures for unknown capability, duplicate capability, unsupported protocol, invalid digest and invalid rollout percentage.
- Build Android and iOS from the same commit and record the exact toolchain.
- Exercise bridge calls through the actual native host, including rejected promises, app background/foreground, close/navigation, auth expiry and unsupported capabilities.
- Ensure denied partner capabilities fail closed and cannot be bypassed by a JS-only check.

### Gate C — measure bundle and startup performance

Measure before/after for each mini-app:
- compressed and uncompressed bundle bytes;
- cold and warm launch latency;
- time until first usable screen;
- bundle download/cache failure and fallback behavior;
- memory and crash rate on representative low-memory Android devices and supported iPhones.

Do not claim a 200 KB bundle or “Naver/Toss-level” performance without reproducible measurements and defined measurement conditions.

### Gate D — harden independent delivery

- Require a signed or integrity-checked release manifest and verify its SHA-256 against downloaded bytes.
- Validate channel, rollout percentage and protocol compatibility before loading a bundle.
- Support kill switch, rollback to the last known-good bundle, expiry/revocation, and explicit offline behavior.
- Keep partner capabilities least-privilege and deny unknown capabilities by default.
- Ensure release/deploy credentials remain server-side; never ship signing secrets in a mini-app bundle.

### Gate E — decide on upgrades using evidence

Only upgrade Granite packages when the dependency graph, native build, end-to-end bridge tests, bundle measurements and rollback checks pass on the candidate commit. Do not copy Granite's AWS infrastructure examples; Itunda's stated infrastructure baseline remains GCP.

## Current status

- **Source inspection:** completed for the listed manifests and validator.
- **Fresh CI execution:** not performed by this assessment.
- **Native runtime validation:** not performed by this assessment.
- **Production rollout:** unchanged; this is documentation only.
- **Go/no-go:** **GO** to keep and measure the existing Granite-backed Saronite integration; **NO-GO** to an unmeasured broad runtime upgrade or replacement.
