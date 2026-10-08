# Itunda Platform Blueprint — Toss-Inspired, Itunda-Native

> Status: active architecture direction.
> This document translates publicly observable Toss patterns into Itunda's own architecture.
> Itunda must not copy proprietary code, private infrastructure, branding, or internal systems.

## 1. Benchmark findings

Public Toss material shows a platform made of several layers rather than a single product:
- a super-app host with independently delivered feature/mini-app bundles;
- a developer platform with SDKs, examples, testing/devtools and a partner console;
- a design-system platform with shared tokens, components, accessibility and theme variants;
- public engineering libraries and tooling;
- API/documentation infrastructure built from machine-readable contracts;
- operational/debugging tooling around distributed applications.

Toss's public GitHub currently exposes Granite, frontend-fundamentals, es-toolkit, overlay-kit, suspensive, Necto and other platform tooling. Apps in Toss also has public examples, SDK examples, a Unity SDK, and an agent/MCP project.

## 2. Itunda target platform

### Consumer platform
- `itunda.im` — consumer product/brand
- `app.itunda.im` — consumer web app
- Android/iOS native shells
- server-driven navigation and independently delivered mini-apps
- Saronite host/bridge
- payments, wallet, identity, marketplace, communication and future mini-games

### Business platform
- `business.itunda.im` — business product
- `business-app.itunda.im` — business application
- merchant onboarding
- QR/payment acceptance
- settlement/reconciliation
- customer messaging
- staff/roles
- analytics and operations

### Developer platform
- `developers.itunda.im`
- API reference generated from runtime OpenAPI
- SDKs
- Saronite mini-app SDK
- CLI
- examples
- sandbox
- web mock host
- DevTools
- app manifest/permissions
- partner/app review
- release channels

### Engineering platform
- `tech-blog.itunda.im`
- IDS design system
- token code generation
- shared TypeScript utilities
- native Android/iOS foundations
- observability
- CI/CD
- contract testing
- dependency/architecture governance

### Internal platform
- Ops MFE
- compliance
- fraud/risk
- support
- reconciliation
- settlement
- release management
- feature flags
- audit/event inspection
- service health
- mini-app review

## 3. Architecture rules

1. One super-app shell; many independently evolvable feature surfaces.
2. Bounded contexts own their data and business rules.
3. Public APIs are gateway contracts, never internal implementation details.
4. Runtime OpenAPI is the source of truth; human documentation is generated/validated against it.
5. SDK contracts are versioned separately from host application versions.
6. Mini-app capabilities are permissioned and explicitly declared.
7. Web development must have a mock host so SDK features can be exercised without the native app.
8. Production bundles must never contain development-only debugging infrastructure.
9. Design tokens are semantic and generated for Web, Android and iOS from one source.
10. Light/dark/theme variants are first-class, not hand-tuned per screen.
11. Accessibility belongs in component contracts, not individual product screens.
12. Money movement is server-authoritative, idempotent and auditable.
13. Internal service URLs never appear in public contracts.
14. No undocumented production mutation is exposed through SDKs or APIs.
15. Every public capability has an example, test path and observable failure state.

## 4. Saronite evolution

Saronite is the Itunda equivalent of the public Granite layer.

Target capability domains:
- auth
- navigation
- environment
- permissions
- storage
- location
- camera/photos where permitted
- contacts where permitted
- clipboard
- haptics
- payment
- notification
- analytics
- partner/accessory actions
- events
- games/mini-games

Every capability must have:
- TypeScript API
- protocol definition
- Android adapter
- iOS adapter
- browser mock
- permission/error model
- example
- automated contract test

The protocol version must remain independent of the SDK semantic version.

## 5. Developer sandbox

The Itunda sandbox should provide:
- test API keys only;
- deterministic test identities;
- fake but stateful money rails;
- webhook inspection;
- request/response logs;
- idempotency replay;
- configurable provider success/failure/latency;
- test clock;
- test data reset;
- OpenAPI explorer;
- SDK examples;
- browser mini-app host;
- permission simulation;
- device/environment simulation.

Sandbox must never be presented as production and must not accept production credentials.

## 6. API platform

Public API layers:
- `api.itunda.im` — gateway
- `/openapi.json` — runtime aggregate contract
- `/llms.txt` — concise agent/developer map
- `/llms-full.txt` — full machine-readable developer guidance
- SDK-generated clients where appropriate
- webhook/event schemas
- version/change policy

Contract pipeline:
1. service controllers/DTOs/security
2. service-local `/v3/api-docs`
3. gateway aggregate `/openapi.json`
4. contract validation
5. developer documentation
6. SDK/client generation
7. examples and sandbox tests

No manually invented OpenAPI document may enter the repository as the claimed source of truth.

## 7. Design system

IDS should evolve toward:
- primitive tokens
- semantic tokens
- component tokens
- platform-specific generated outputs
- theme variants
- accessibility contracts
- interaction/motion contracts
- component examples
- visual regression
- token migration tooling
- design-to-code workflows

The canonical Itunda brand remains `#7472F4` and the supplied brand package remains the source of truth for logos/assets.

## 8. Release model

Every platform artifact should support:
- development
- preview
- sandbox
- beta
- production

Mini-app releases additionally need:
- manifest version
- capability permissions
- bundle SHA-256
- icon SHA-256 where applicable
- review state
- rollout percentage
- rollback
- minimum host version
- protocol compatibility range

## 9. Internal tools

Create one internal platform rather than isolated admin screens:
- Operations
- Risk
- Compliance
- Reconciliation
- Settlement
- Support
- Developer/partner review
- Mini-app releases
- Feature flags
- Service health
- Audit logs

The same design system and authorization model should power all internal surfaces.

## 10. What “100% Toss-like” means for Itunda

It does not mean copying Toss's proprietary implementation.

It means matching the publicly observable platform maturity:
- product simplicity
- independently deployable features
- host + mini-app model
- SDK ecosystem
- developer console
- sandbox
- examples
- mock/devtools
- design-system infrastructure
- machine-readable APIs
- internal operational tooling
- strong release and observability systems

The implementation, branding, APIs, domain model and Rwanda-specific rails remain Itunda's own.