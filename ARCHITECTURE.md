# Itunda System Architecture

This document describes itunda's technical architecture and how it maps to Toss's *actual*,
sourced architecture. Every "Toss does X" claim here must trace to
[docs/TOSS_ARCHITECTURE_FACTS.md](docs/TOSS_ARCHITECTURE_FACTS.md). For the product/bounded-context
model see [docs/TOSS_RWANDA_ALIGNMENT.md](docs/TOSS_RWANDA_ALIGNMENT.md); for implementation
status see [docs/TOSS_PARITY_MATRIX.md](docs/TOSS_PARITY_MATRIX.md).

Status labels below follow the repo convention: `real` (exists and runs), `demo` (exists,
mocked/local data), `target` (designed, not built), `stub` (scaffold with little/no logic),
`superseded` (duplicate, not canonical).

## 0. The one architectural idea to copy first

Toss is not one backend wearing one app's face. "Toss," Toss Bank, Toss Securities, and Toss
Payments are separately regulated systems with **different backends and different tech
stacks** (Toss Bank: Kotlin/MySQL/Kubernetes; Toss Securities: legacy C ledger + Java edge),
unified by **one frontend mechanism**: a native host app that dynamically loads independent
React Native mini-app bundles per feature (§3 of the facts doc, productized as
[toss/granite](https://github.com/toss/granite)).

That reframes what "100% Toss-like" should mean for itunda: **not** one monolithic set of 12
generically-named microservices sharing one repo, but (a) a small number of real bounded-context
backends that can evolve independently, unified by (b) one real super-app shell that loads
feature bundles independently. Itunda currently has neither cleanly — it has fragments of both,
scattered across duplicate trees. This document's job is to say which fragment is the real one.

## 1. Backend architecture

### What Toss actually does (sourced, see facts doc §1-2)

- Toss Bank: monolith → MSA, Kotlin/Spring-style services, **MySQL**, all channel/business
  services containerized on **Kubernetes**, independent per-service CI/CD deploy and scaling,
  **active-active dual-datacenter** (not primary/DR).
- Toss Securities: does **not** rewrite its system of record reflexively — keeps a legacy C
  ledger core, puts a Java/Kubernetes edge in front of it, bridges the two with **Kafka**.

### Itunda's current state

| Layer | Directory | Status | Notes |
|---|---|---|---|
| Core ledger + money-moving backend | `spring_workspace/spring-backend` | **real** | Kotlin + Spring Boot + Spring Data JPA + MySQL + Spring Security JWT. ~4,300 LOC across auth/wallet/transfer/bills/loans/contacts/stocks/savings/insurance/notifications/discover/system, verified live against real MySQL with per-user ownership checks and a transactional idempotency store. This is the one piece that is genuinely on Toss's real stack (Kotlin, Spring, MySQL) — see `docs/TOSS_PARITY_MATRIX.md` for the verified detail. **This is the canonical backend.** |
| Earlier Kotlin scaffolds | `spring_workspace/payment-service`, `spring_workspace/ledger-service` | **superseded** | 9 and 11 `.kt` files respectively, thin, predate `spring-backend`'s consolidation. Candidates to delete once confirmed nothing in them is undone in `spring-backend`. |
| API gateway | `node_workspace/apps/api-gateway` | **stub** | Express + http-proxy-middleware, essentially just `index.js`. Not a real gateway yet — no rate limiting, no auth, no routing table beyond a proxy. |
| Micro-frontends | `node_workspace/apps/micro-frontends/{host-app,bank-mfe,kyc-mfe}` | **demo** | Real Vite+React scaffolds (~1,500 LOC), but this is a *web* micro-frontend split, which is not the architecture Toss is actually known for (§3 below is). Keep only if the goal is a web admin/BFF surface distinct from the consumer super-app. |
| Earlier Express demo API | referenced in README/docs as `backend/` | **gone** | Does not exist in the tree; docs still reference it. Superseded by `spring-backend`. Any doc still pointing at `backend/` for product-surface coverage is stale — `spring-backend` is where that coverage actually lives now. |

**Decision this implies:** `spring_workspace/spring-backend` is the single backend of record.
`payment-service`, `ledger-service`, and the API-gateway stub are either archived or folded in,
not developed in parallel. Kafka is not yet present anywhere in the repo — it is the real gap
between "itunda has a working monolith-shaped Spring app" and "itunda has the MSA event backbone
Toss actually runs" (transfer.confirmed / payment.provider_succeeded / ledger.posted events
listed in `docs/TOSS_RWANDA_ALIGNMENT.md`'s event model are designed but not wired to any broker).

## 2. Frontend / super-app architecture

### What Toss actually does (sourced, see facts doc §3)

Native host app + independently-built, independently-deployed **React Native mini-app
bundles**, split into one **shared bundle** (RN core + common code) and many **service
bundles** (one per feature), **loaded dynamically at runtime** rather than shipped up front.
Open-sourced as `toss/granite`. Third-party version of the same mechanism is **Apps-in-Toss**.

### Itunda's current state

| Layer | Directory | Status | Notes |
|---|---|---|---|
| Hand-rolled super-app shell | `saronite/` | **stub, wrong shape** | React Native host app + 4 "mini-apps" (`wallet-balance`, `pay-bills`, `reward-tasks`, `insurance_mini_app`), but only 1-3 files each (~930 LOC total) and no dynamic bundle loading, no shared/service bundle split, no CDN deploy path. It approximates Granite's *idea* without the mechanism that makes it real. |
| Consumer web app | root `package.json` / `dist/` | **broken** | Root `package.json` has no matching `src/`, `index.html`, or Vite config — it references a deleted app. `dist/` is a stale build artifact from that deleted source. Nothing currently builds from repo root. |
| Earliest web skeleton | `web-prototype/` | **stub** | 3 files, 139 LOC, not wired to anything. |
| Design system | none | **target** | TDS itself isn't open-source, but its components are documented (facts doc §3) and should be the literal reference for itunda's design tokens, not an invented "Itunda Design System (aligned with Toss Design System)" placeholder with no actual token file behind it. `ios/Core/DesignSystem` and `android/core/designsystem` exist as directories but were not verified to contain a real token set. |

**Decision this implies:** the highest-leverage single frontend move is replacing `saronite/`'s
hand-rolled shell with a real build on `toss/granite` (or, at minimum, rebuilding it to match
Granite's actual host/shared-bundle/service-bundle/dynamic-load mechanism instead of a flat
React Native app with a few screens in folders). Until that happens, calling `saronite/`
"Toss-aligned" in any doc is the same kind of unsourced claim §5 of the facts doc warns against.

## 3. Mobile native shells

| Directory | Status | Notes |
|---|---|---|
| `android/` (top-level: `core/{ledger,identity,network,consent,risk,designsystem,testing}`, `features/{credit,payments,wealth,engagement,banking,bills,assets,insurance,merchant}`) | **demo, more complete** | Feature-modularized, mirrors the bounded-context list in `docs/TOSS_RWANDA_ALIGNMENT.md`. 17 files / ~1,360 LOC. |
| `mobile_clients/android/` | **superseded** | Simpler/older structure, same rough file count, no clear reason to keep both. |
| `ios/` (top-level, Core/Features/SDK, Tuist microfeatures) | **demo, more complete** | Mirrors `android/`'s structure. 64 files / ~810 LOC. |
| `mobile_clients/ios/` | **superseded** | Older/thinner. |
| `mobile_clients/itunda-pay-sdk` | **reference only** | This is a **vendored git clone of the real `tosspayments/payment-sdk-android`**, not itunda code — its own `.git` history is genuine Toss Payments commits. Keep as a reference for how Toss actually structures a payment SDK; do not treat it as part of itunda's app surface. |

**Decision this implies:** `android/` and `ios/` (top-level) are canonical; `mobile_clients/android`
and `mobile_clients/ios` are archived once confirmed there's nothing uniquely valuable in them.
These native shells should eventually become **thin hosts that load Granite-style mini-app
bundles**, per §2, rather than growing their own parallel feature implementations long-term —
otherwise itunda ends up building three copies of every feature (web, native-Android, native-iOS)
instead of one shared bundle loaded into all three, which is the entire point of the Toss pattern.

## 4. Infrastructure

- `infrastructure/` has both `k8s/` and `kubernetes/` — pick one, they should not coexist.
- Kubernetes itself is a real point of alignment (Toss Bank channel services run on K8s) —
  keep it. Kafka is not yet present and is the real gap (§1).
- Active-active dual-datacenter, 1,000+ topic Kafka mirroring, and sub-200ms real-time
  ledger writes (facts doc §1-2) are **not targets for itunda's current stage** — they are
  what Toss does at real-money, real-scale, regulated-bank operation. Naming them in a doc as
  something itunda "has" or will build next would repeat the same fabrication problem this
  document exists to fix. They belong in this facts doc as context, not on itunda's near-term
  roadmap.
- No claim of PCI-DSS Level 1, SOC 2, or similar should appear anywhere in itunda's docs
  without an actual audit behind it (facts doc §5). `SECURITY.md` was already rewritten once to
  remove exactly this kind of fiction — `IMPLEMENTATION_GUIDE.md` still has it and needs the
  same treatment.

## 5. Immediate architecture backlog

In priority order, each item closes a specific gap identified above:

1. Delete/archive `spring_workspace/payment-service`, `spring_workspace/ledger-service`,
   `mobile_clients/android`, `mobile_clients/ios`, `web-prototype/` once confirmed nothing
   unique lives only there — stop maintaining parallel copies of the same thing.
2. Fix the broken root `package.json`/`dist/` — either rebuild a real consumer web app or
   remove the orphaned root build config so `npm run dev` at repo root isn't a lie.
3. Rebuild `saronite/` on the real host+shared-bundle+service-bundle+dynamic-load mechanism
   (study/adopt `toss/granite` directly) instead of the current flat-folder approximation.
4. Introduce Kafka as the actual event backbone for the event model already designed in
   `docs/TOSS_RWANDA_ALIGNMENT.md` (`transfer.confirmed`, `payment.provider_succeeded`,
   `ledger.posted`, etc.) — currently those events are documented but not emitted anywhere.
5. Replace the placeholder "Itunda Design System (aligned with Toss Design System)" with an
   actual token set derived from the publicly documented TDS components (facts doc §3).
6. Remove PCI-DSS/SOC2/1M-user/"Production Ready" language from `IMPLEMENTATION_GUIDE.md`.
7. Consolidate `infrastructure/k8s` and `infrastructure/kubernetes` into one directory.
