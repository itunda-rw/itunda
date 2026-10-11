# Apps in Itunda developer guide

## Goal

Build an Itunda mini-app as a first-class app inside the Itunda super-app. The developer experience follows the same category of workflow as modern mini-app platforms: scaffold, develop locally, preview with a mock host, test permissions and lifecycle behavior, build a bundle, submit a manifest, pass review, publish, and operate the app.

The implementation is Itunda-native. Do not copy private Toss code, assets, or branding.

## 1. Mini-app contract

Every submitted app has:

- stable app id
- semantic version
- category
- description
- icon
- bundle entry
- explicit permission list
- optional semantic partner brand theme
- runtime compatibility

The host is the security boundary.

## 2. Runtime ownership

### Host owns

- authentication boundary
- user/session identity
- navigation and return-to-Itunda
- permission enforcement
- native bridge
- bundle loading
- lifecycle
- caching
- failure recovery
- update policy

### Partner owns

- feature UI
- business logic
- content
- partner brand expression

Partner code must never receive unrestricted host credentials or arbitrary native access.

## 3. Development lifecycle

### Scaffold and validate

From the repository root, use the checked-in CLI. It derives the schema, manifest version, and default semantic theme from the canonical partner template:

```sh
node tools/itunda-miniapp/bin/itunda-miniapp.mjs new "My Service"
node tools/itunda-miniapp/bin/itunda-miniapp.mjs validate my-service/manifest.json
```

The generated `manifest.json` uses placeholder bundle and icon URLs so the scaffold is deterministic. Replace both with your real HTTPS assets before submitting; the validator rejects HTTP URLs and URLs containing embedded credentials. Choose the narrowest supported permission list and set a stable partner app ID before publishing.

1. Scaffold from the Itunda mini-app template.
2. Run the mini-app in a browser/mock host.
3. Exercise host APIs through the Saronite SDK.
4. Test every requested permission in isolation.
5. Build a production bundle.
6. Validate the manifest and bundle.
7. Submit to the Apps in Itunda partner API.
8. Human review validates identity, permissions, UX, security and policy.
9. Approved versions enter the published catalog.
10. The host downloads and runs the approved version.

## 4. SDK design

Saronite APIs are capability based.

A mini-app should ask for the smallest permission needed. APIs return explicit unsupported/denied/error states instead of silently falling back to privileged behavior.

Initial capability families:

- identity
- navigation
- share
- storage
- notifications
- payments
- location
- camera
- contacts

A capability only becomes callable after the host grants the corresponding manifest permission.

## 5. Design-system rules

Use IDS for the app shell and components.

The partner may customize only semantic brand roles:

- brand
- brandStrong
- brandSurface
- onBrand
- focus
- pressed

IDS still controls:

- typography
- spacing
- component anatomy
- accessibility states
- motion
- light/dark semantics
- non-brand colors
- interaction behavior

The default partner theme follows Itunda Brand v3: light primary `#8F89FF`, pressed `#7D77F7`, and dark brand surface `#25252D`. Treat old `#7472F4` values in legacy examples as stale; use the canonical partner manifest and semantic IDS tokens instead.

## 6. Submission checklist

Before submission:

- manifest parses
- app id is stable
- version is valid
- category is supported
- icon is available
- bundle URL is HTTPS
- requested permissions are actually used
- partner theme passes light/dark contrast checks
- no host secrets are embedded
- back/close behavior returns to Itunda
- loading, error and unsupported states are implemented
- reduced motion is respected
- large text remains usable
- analytics events do not expose sensitive data

## 7. Versioning

A published version is immutable.

A new bundle requires a new semantic version. The catalog points users to the currently approved version while the host keeps a rollback-safe previous version when possible.

## 8. Platform parity roadmap

The platform will grow toward:

- first-party mini-app SDK domains
- partner analytics
- deep links
- sharing
- payments
- rewards
- subscriptions
- notifications
- developer console
- automated review checks
- staged rollout
- crash/error diagnostics
- app version management
- sandbox/mock host
- design-system showcase
- generated SDK contracts for Web, Android, iOS and Flutter

## Reference

The public Apps in Toss repositories show a comparable end-to-end model: scaffolding, examples, mock development, devtools/debugging, bundling, registration and operations. Itunda is implementing the same class of developer lifecycle independently. 
