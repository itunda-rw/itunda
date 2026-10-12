# Fast development workflow

This guide applies the publicly documented performance ideas from Toss's open-source Granite repository without replacing Itunda's architecture or branding.

## What the public source demonstrates

Granite's public README and CLI source make several concrete choices:

- **Fast iteration:** its README says JavaScript bundle builds can take seconds using esbuild, with bundle splitting and optimization for small mini-app bundles.
- **Focused dev server:** `granite dev` starts the configured bundler/server, and cache reset is opt-in with `--reset-cache` rather than clearing caches on every launch.
- **Targeted builds and tests:** the monorepo uses Nx targets and caches build outputs; its CLI includes tests for dev-server shutdown and cache-reset behavior.
- **Release/deploy automation:** Granite publishes a dedicated deployment CLI and infrastructure tooling.
- **Tests are part of the framework:** its README explicitly calls out end-to-end tests.

Sources: [toss/granite README](https://github.com/toss/granite/blob/main/README.md), [Granite dev command](https://github.com/toss/granite/blob/main/packages/cli/src/commands/DevCommand/DevCommand.ts), [Granite build command](https://github.com/toss/granite/blob/main/packages/cli/src/commands/BuildCommand/BuildCommand.ts), [Granite Nx configuration](https://github.com/toss/granite/blob/main/nx.json), and the [official Apps in Toss open-source tools overview](https://toss.im/apps-in-toss/blog/opensource-3-tools).

Another relevant public reference is [apps-in-toss-community/devtools](https://github.com/apps-in-toss-community/devtools): its source demonstrates browser-based SDK mocks, a floating inspection panel, device presets, and bundler integration. **Its README says the project is no longer maintained and the repository is archived**, so Itunda should learn from its architecture and keep maintaining Saronite DevTools rather than introducing it as a new dependency. The companion [SDK example catalog](https://github.com/apps-in-toss-community/sdk-example) and [console CLI](https://github.com/apps-in-toss-community/console-cli) illustrate interactive API exploration and repeatable developer operations. These are public implementation details—not a claim about Toss's private internal systems.

## Start only the thing you are changing

Run from the repository root after installing the root Yarn workspace dependencies:

| Goal | Command |
|---|---|
| Consumer web shell + all current web MFEs | `yarn dev:web` |
| Bank MFE only | `yarn dev:bank` |
| KYC MFE only | `yarn dev:kyc` |
| Operations MFE only | `yarn dev:ops` |
| Web host only | `yarn dev:host` |
| Saronite browser demo | `yarn dev:saronite:web` |
| Saronite native Metro server | `yarn dev:saronite:native` |
| Saronite workspace typecheck | `yarn typecheck:saronite` |
| Saronite mock capability contract tests | `yarn test:saronite:contract` |

The Saronite workspaces are independently managed under `packages/saronite`; install their dependencies with `npm --prefix packages/saronite install` when they are not already installed. Root web commands use the root Yarn workspace. Do not install the nested Saronite dependencies with the root Yarn command.

## Rules for keeping feedback fast

1. **Run the narrowest useful check first.** While editing a single MFE, start its own Vite server and run its own typecheck/lint; use `yarn dev:web` when validating host-to-MFE integration.
2. **Do not clear caches by default.** Reset a bundler cache only when there is evidence of stale or corrupted state. Cache deletion should be an explicit recovery step, not the normal dev loop.
3. **Keep JavaScript iteration separate from native rebuilds.** Use Vite HMR for browser mini-app work and Metro Fast Refresh for native-host/React Native work. Rebuild the native app only when native code, native dependencies, generated project configuration, or the embedded JS bundle/release artifact requires it.
4. **Mock safely.** Use Saronite DevTools for permission-aware capability mocks, call logs, and lifecycle simulation. Unknown capabilities and denied permissions must remain visible failures, never silent successes.
5. **Measure before optimizing.** Record cold start, warm start, first-ready time, incremental edit-to-refresh time, and production bundle size on a documented machine/commit. Do not claim “seconds” or a bundle-size target for Itunda until measured.
6. **Keep production gates intact.** Fast dev commands are not release validation. Run platform builds, boundary checks, security tests, and device tests before release.

## Current limits

The added commands make existing dev targets easier to launch independently. They do **not** switch Itunda's native runtime to Granite, prove that all apps support HMR, or establish a measured build-speed improvement. Those require execution on a configured developer machine and performance evidence.
