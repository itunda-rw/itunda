# Itunda platform parity contract

Updated 2026-10-03.

Itunda is intentionally modeled on the product architecture and experience patterns visible in Toss and Apps in Toss, while all Itunda code, copy, visual identity, APIs, tokens, and runtime implementation remain independently authored.

## Target

Itunda should provide the same class of platform surfaces that make a super-app ecosystem work:

1. Consumer super-app shell
2. First-party services and mini-apps
3. Apps in Itunda partner ecosystem
4. Partner onboarding and app submission
5. Human review and publishing
6. App catalog/discovery
7. Native host/runtime + SDK bridge
8. Permission-scoped partner capabilities
9. Partner-specific brand expression inside a shared design system
10. Developer documentation, examples, tooling and release workflow
11. Business/merchant platform
12. Hosted payment/API integration
13. Growth, sharing, analytics, monetization and platform APIs as capabilities mature

Public Toss repositories confirm that Apps in Toss is an actual platform with examples, TDS documentation, WebView/React Native support, and a dedicated project generator. Itunda follows the same architectural category, not a source-code copy.

## Apps in Itunda

### Consumer side
- Discover Apps in Itunda from the main Itunda experience.
- Show first-party and approved partner apps through the same discovery model.
- Launch an app without requiring a separate app-store installation.
- Preserve the Itunda shell/navigation and return path.
- Keep app-specific branding inside the IDS semantic brand boundary.

### Partner side
- Partner registration
- Mini-app manifest submission
- Requested permission scopes
- Human review
- Approval/rejection
- Published catalog
- App metadata and icon
- Bundle delivery
- Runtime launch
- Security-scoped native bridge
- Future analytics, payments, rewards, sharing and growth APIs

### Runtime
Saronite is the Itunda-owned mini-app SDK/host layer.

The host owns:
- lifecycle
- navigation contract
- authentication boundary
- permission enforcement
- native bridge
- bundle loading
- caching
- error handling
- return-to-Itunda behavior

A partner app owns:
- its feature UI
- its business logic
- its partner brand tokens
- its content

The partner does not own:
- Itunda authentication internals
- unrestricted native APIs
- ledger internals
- arbitrary money movement
- host security policy

## Design-system parity
IDS is the equivalent architectural layer for Itunda.

### Shared
- typography
- spacing
- component anatomy
- interaction states
- accessibility
- motion
- layout primitives
- light/dark behavior
- semantic non-brand colors
- platform contracts

### Customizable
- brand
- brandStrong
- brandSurface
- onBrand
- focus
- pressed
- partner identity assets

The default Itunda theme remains #7472F4.
Partner apps can express their own identity without replacing the host's interaction language.

## Product experience rules
- One clear primary action.
- Money/state first when the product is transactional.
- Flat information hierarchy before decorative cards.
- Generous whitespace.
- Short, direct copy.
- Progressive disclosure for advanced actions.
- Floating/overlay surfaces may use restrained glass treatment.
- No decorative gradients as a default pattern.
- Motion communicates state and hierarchy; reduced-motion must remain supported.
- Every important interaction has an accessible semantic state.
- Consumer, business, developer and partner surfaces remain clearly separated.

## Repository structure
Keep the monorepo as the source of truth while the platform is still evolving:
- packages/design-tokens — platform-neutral token source
- packages/design-system-web — web IDS
- packages/saronite — mini-app SDK/host contracts
- android/... — native Android shell and IDS
- ios/... — native iOS shell and IDS
- services/backend/partners — partner platform
- business/apps-in-itunda — partner-facing surface
- business/pay-itunda — payment integration surface

Do not split the platform into separate repositories merely to imitate a repository layout. The important parity is the architecture and developer experience, not the number of repositories.

## Current implementation status
The repository already contains a real Apps in Itunda foundation:
- partner registration API
- mini-app submission API
- human approval flow
- published catalog
- Android partner bundle loader
- Saronite runtime
- partner permission allow-list
- first-party mini-apps
- partner-facing Apps in Itunda page
- IDS semantic brand-theme contract

The next parity gates are:
1. make the catalog a first-class consumer discovery surface;
2. formalize the partner manifest and lifecycle contract;
3. expand Saronite SDK APIs behind explicit permissions;
4. add developer documentation/examples for creating an Itunda mini-app;
5. add platform analytics/growth contracts;
6. generate brand-theme contracts consistently for Web, Android, iOS and Flutter;
7. add automated accessibility and theme-parity tests for every partner app;
8. keep the parity matrix grounded in verified implementation rather than roadmap claims.

## Source boundary
Use public Toss materials to understand product patterns, APIs, documentation structure, and platform concepts. Do not copy proprietary source code, private assets, internal implementation, or branding.

The goal is Toss-level platform completeness and polish with an Itunda-native implementation and identity.