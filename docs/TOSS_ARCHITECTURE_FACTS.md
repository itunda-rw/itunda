# Toss Architecture Facts (Sourced)

This document records what is *actually publicly known* about how Toss builds its systems —
backend, ledger, and frontend/mobile — so `ARCHITECTURE.md` can be rewritten against real
facts instead of generic fintech-microservices boilerplate. It follows the same discipline as
[FACT_CHECKED_TOSS_RWANDA_MAP.md](FACT_CHECKED_TOSS_RWANDA_MAP.md): every claim below is
traceable to a source. If a claim about Toss's architecture cannot be sourced, it does not
belong in `ARCHITECTURE.md` as "Toss-aligned" — it should be labeled a plain engineering
default instead.

Toss is not one system. "Toss" the super-app is the product surface; underneath it are
separately regulated entities (Viva Republica / the app itself, Toss Bank, Toss Securities,
Toss Payments) with **different backends and different tech stacks**, unified by one frontend
experience. That separation is itself the first architectural fact worth copying.

## 1. Toss Bank — core banking MSA

Source: [토스뱅크 커리어 아티클 "유연하지만 견고한 은행 시스템을 만들어요"](https://toss.im/career/article/tossbank-system), [바이라인네트워크](https://byline.network/2022/06/10-234/), [SLASH23 세션](https://toss.im/slash-23/session-detail/A1-8)

- Started as a **monolith on Oracle**, split into a **channel tier** (receives customer
  traffic) and an **account/accounting tier** (transaction processing). This two-tier split
  is a real, named part of their system, not an invented layer.
- That original channel/account boundary itself became a bottleneck (deployment coupling,
  cascading failures) — so it was dissolved. Business/transaction logic was rebuilt as
  independently deployable microservices, each with its own failure domain.
- **All channel services run as containers on Kubernetes**, deployed independently via CI/CD,
  and scale out horizontally per-service rather than as one monolith.
- Database moved from Oracle to **MySQL**.
- Infrastructure runs **active-active across two data centers** — not classic
  primary/DR failover. Both sites serve live traffic simultaneously; losing one site does not
  interrupt service. This is a deliberate rejection of "just have a backup region."
- A single user-facing request can traverse **10+ microservices**; this is explicitly called
  out as an observability problem they had to solve for (distributed tracing, not just logs).

## 2. Toss Securities — hybrid legacy-core + modern edge

Source: [바이라인네트워크](https://byline.network/2022/06/10-234/), [토스증권 Kafka 이중화 아티클](https://toss.tech/article/kafka-distribution-1)

- The trading ledger itself is a **Unix/C-based legacy system** — Toss did **not** rewrite the
  core ledger in Kotlin/Java. This matters: Toss's real pattern is "modernize the edges,
  don't reflexively rewrite the system of record."
- A **Java-based Mobile Trading System runs on Kubernetes** as the application layer in front
  of that C ledger.
- **Kafka is the bridge** carrying market/quote data from the C ledger core to the Java
  applications, and from there to clients.
- Client market-data delivery is **WebSocket-first with a polling-API fallback** when
  WebSocket connections degrade or lag — a real, named fallback pattern for unreliable
  connectivity, directly relevant to Rwanda's low-bandwidth requirement already stated in
  `TOSS_RWANDA_ALIGNMENT.md`.
- Kafka itself runs **active-active across two data centers**, deliberately choosing that over
  a stretched cluster: a stretched cluster forces every write to wait on cross-DC replica
  acknowledgment (latency) and risks split-brain if the inter-DC link drops. Instead:
  producers are active-active (routed to the nearest DC via split DNS), consumers are
  **active-standby** (GSLB routes all consumption to one DC at a time) specifically to avoid
  double-processing the same message from two DCs at once. Custom tooling mirrors topics and
  syncs consumer-group offsets across the two independent clusters (1,000+ topics/groups).
- Originally on-premises infrastructure, migrating to AWS after a completed proof-of-concept.

## 3. Frontend/mobile — the actual "super app" mechanism

This is the most distinctive and most copyable part of Toss's architecture, and the part
`saronite/` in this repo is currently hand-imitating instead of building on the real thing.

Source: [toss/granite (GitHub)](https://github.com/toss/granite), [토스가 꿈꾸는 React Native 기술의 미래](https://toss.tech/article/react-native-2024), [SLASH23 — 달리는 토스 앱에 React Native 엔진 더하기](https://velog.io/@badbeoti/%ED%86%A0%EC%8A%A4%E3%85%A3SLASH-23-%EB%8B%AC%EB%A6%AC%EB%8A%94-%ED%86%A0%EC%8A%A4-%EC%95%B1%EC%97%90-React-Native-%EC%97%94%EC%A7%80-%EB%8D%94%ED%95%98%EA%B8%B0), [토스 RN Framework 팀 커리어 아티클](https://toss.im/career/article/rn_framework_2511)

- The Toss app is a **native host app** (Swift/Kotlin) that embeds many independently-built
  **React Native "mini-apps"** — this is "brownfield" RN, not a from-scratch RN rewrite of the
  whole app.
- Each feature/service is its **own RN bundle**, split from a **shared bundle**. The shared
  bundle holds `react-native` core and code every mini-app needs; each service bundle holds
  only that feature's code.
- **Bundles load dynamically at runtime**, on demand, per screen/service — the host app does
  not ship every feature's JS up front. Toss reports this cut load time by 1+ second on at
  least one real product ("daily visit mission").
- This exact pattern is now productized and **open-sourced as `toss/granite`** (Apache-2.0):
  host app + microservice mini-apps, ~200KB per mini-app bundle via ESBuild, brownfield
  integration into existing native iOS/Android apps, one-command CDN deploy via Pulumi/AWS.
  It is actively maintained (34 releases, latest Jan 2026).
- Third-party developers build on the same mechanism via **Apps-in-Toss**: external mini-apps
  embedded inside the Toss app itself, with real published examples
  ([toss/apps-in-toss-examples](https://github.com/toss/apps-in-toss-examples)) and an
  AI/CLI scaffolding toolkit ([toss/apps-in-toss-ax](https://github.com/toss/apps-in-toss-ax)).
- Frontend workspace is a **monorepo using Yarn Plug'n'Play** for install strictness/speed
  (source: 토스 프론트엔드 careers material). Notably, **itunda's own root already uses Yarn
  PnP** (`.pnp.cjs`, `.pnp.loader.mjs`, `.yarn/`) — this is a genuine, already-true point of
  alignment worth preserving, not something to invent.
- **TDS (Toss Design System)**: 40+ components, tokens, and hooks, shared language across
  design/dev/product. Not open-sourced as code, but publicly documented at
  [tossmini-docs.toss.im/tds-mobile](https://tossmini-docs.toss.im/tds-mobile/) and
  [developers-apps-in-toss.toss.im/design/components.html](https://developers-apps-in-toss.toss.im/design/components.html) —
  usable as a real design reference even without the source.
- Toss open-sources its own **frontend engineering principles** as
  [toss/frontend-fundamentals](https://github.com/toss/frontend-fundamentals): a rubric for
  code quality centered on readability/predictability/cohesion/coupling reasoning (not
  arbitrary style rules), plus bundling-behavior, accessibility, and debugging guides.

## 4. Real Toss open-source libraries directly usable here

Source: [github.com/toss](https://github.com/toss) org listing (fetched live)

| Library | What it replaces/does | Where it fits in itunda |
|---|---|---|
| [es-toolkit](https://github.com/toss/es-toolkit) | lodash, 2-3x faster, up to 97% smaller | Any frontend/Node utility usage |
| [use-funnel](https://github.com/toss/use-funnel) | Step-by-step funnel state for React | KYC onboarding, transfer quote→confirm flow, loan application steps |
| [overlay-kit](https://github.com/toss/overlay-kit) | Declarative overlay/bottom-sheet/modal handling | `KeypadBottomSheet` and similar confirmation sheets |
| [suspensive](https://github.com/toss/suspensive) | React Suspense + ErrorBoundary utilities | Data-fetching states across Home/Wallet/Invest pages |
| [granite](https://github.com/toss/granite) | Host app + RN mini-app microservice framework | Direct replacement candidate for the hand-rolled `saronite/` |
| [nestjs-aop](https://github.com/toss/nestjs-aop) | AOP for NestJS | Only relevant if `services/api-gateway` stays NestJS-based |

## 5. What is genuinely unverifiable and should not be claimed as "Toss-sourced"

To keep `ARCHITECTURE.md` honest going forward, the following are **not sourced to anything
Toss has published** and must not be presented as facts about Toss, even though they are
common generic fintech-microservices choices:

- MongoDB, Elasticsearch/Kibana as part of Toss's stack — no source found.
- Specific service port numbers (3000-3011) — invented, not real.
- "PCI-DSS Level 1" self-declared compliance — this is a real, audited certification status,
  not something a repo can claim about itself without an actual audit; must not appear as a
  compliance claim.
- "1M+ concurrent users", "99.99% uptime", "Production Ready" in `IMPLEMENTATION_GUIDE.md` —
  aspirational marketing copy with no basis in this repo's actual state (no git history existed
  until this session; most surfaces are unwired prototypes — see `docs/TOSS_PARITY_MATRIX.md`).
- Redux/MVI as "the" Toss mobile state approach — not sourced; Toss's real distinctive mobile
  architecture fact is the RN host+mini-app bundle split (§3), not a specific state library.

## Update Rule

Same rule as `FACT_CHECKED_TOSS_RWANDA_MAP.md`: update this file when new public Toss
architecture facts surface, and do not let `ARCHITECTURE.md` assert anything about "matching
Toss" that isn't backed by an entry here.
