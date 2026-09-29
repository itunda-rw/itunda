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
  usable as a real design reference even without the source. **Colors and typography now
  actually sourced (2026-07-13)**, closing part of the gap `docs/ARCHITECTURE.md`'s own
  backlog named ("the rest of the token surface... matching the real documented TDS, not
  just colors") — fetched directly from the foundation sub-pages, not assumed:
  - **Colors** ([.../foundation/colors](https://tossmini-docs.toss.im/tds-mobile/foundation/colors)):
    real numbered scales (50-900) for grey/blue/red at minimum — grey50 `#f9fafb` through
    grey900 `#191f28`, blue50 `#e8f3ff` through blue900 `#194aa6` (blue500 `#3182f6` is the
    signature brand blue, already correctly used everywhere in itunda), red50 `#ffeeee`
    through red900 `#a51926`. Orange/yellow/green/teal/purple scales exist (10 steps each)
    but exact hex values weren't captured this pass.
  - **Typography** ([.../foundation/typography](https://tossmini-docs.toss.im/tds-mobile/foundation/typography)):
    a real 7-level scale, `Typography 1`-`7`: 30/40, 26/35, 22/31, 20/29, 17/25.5, 15/22.5,
    13/19.5 (font size/line height, px). A prior session's `docs/ARCHITECTURE.md` note
    (§6 item 6) explicitly said this wasn't available and only spacing/elevation were
    genuinely unsourced — that spacing/elevation conclusion still holds, but typography
    *was* findable by fetching the real sub-page directly rather than the top-level
    landing page (which itself has zero numeric values, confirmed).
  - **Found via this**: itunda's own `TdsColors`/`TdsTheme` had a real, live mislabeling
    (its `Blue600`/`blue600` constant held the real TDS's blue700 value, `Blue100`/`blue100`
    held blue50) — independently drifted identically on both Android and iOS, fixed
    2026-07-13 (`android/.../TdsColors.kt`, `ios/.../TdsTheme.swift`). itunda's existing
    semantic type scale (`Title1`/`Subtitle1`/etc.) didn't match this real scale's exact
    sizes and was deliberately left as-is at the time (a visual-hierarchy change needing
    its own live-verified pass) — the real scale was added alongside as `Typography1`-`7`/
    `typography1`-`7` reference tokens instead. That deferred decision was resolved
    2026-07-13→2026-07-21: `Title1` (24sp) was the one real outlier, and turned out
    ambiguous by size alone (equidistant from `Typography2`'s 26sp and `Typography3`'s
    22sp) until checked against iOS's already-shipped equivalent (`IDS.Typography.title`,
    `IDS.swift:126`, already 22pt) — Android's `Title1` was snapped to 22sp/31sp to match
    both. Auditing real call sites during that pass also found `Title1` had been
    overloaded for two different roles (headlines *and* hero currency amounts, e.g.
    `AgentHomeScreen.kt`'s till-cash display) where iOS already splits them
    (`IDS.Typography.largeAmount`, 34pt) — Android gained the matching `LargeAmount`
    (34sp) token rather than inheriting the merged role. `Title2`/`Subtitle1`/`Body1`/
    `Body2`'s line-heights were also snapped to the sourced scale's exact values (their
    font sizes already matched; only rounding was off).
  - **A real, more authoritative source found 2026-07-21**: the docs site above is a
    props/behavior reference (component pages like `button`/`table-row`/`list-row` document
    variant names and CSS custom-property *names*, not their default pixel/color values — no
    spacing, radius, elevation, or motion numbers are published anywhere on it, confirmed by
    checking `foundation/` directly, which only lists `colors`/`typography`). But Toss also
    publishes actual design-token *code*: `@toss/tds-colors` and `@toss/tds-typography` on the
    public npm registry (real, actively maintained — `@toss/tds-colors@0.1.0` and
    `@toss/tds-typography@0.0.3`, maintainers include `toss-build-bot`/`toss-public`, both
    updated through March 2026), consumed internally by `@toss/tds-react-native@2.0.4`.
    `@toss/tds-colors`'s `colors.light.css`/`colors.dark.css` ship the real adaptive scale as
    plain CSS custom properties — **the light scale matches this repo's already-corrected
    values exactly** (confirms the color-system-update findings above, not a new fact), but
    the **dark scale is meaningfully different from what itunda had**: real
    `--adaptiveBackground` is `#17171c` (a dark grey, not true black),
    `--adaptiveBackgroundLevel01`/`Level02` are `#202027`/`#2c2c35` (named elevation steps),
    `--adaptiveHairlineBorder` is `#3c3c47`, `--adaptiveBlue500` (dark) is `#3485fa`. itunda's
    prior dark values (`#000000` background, `#4C8FFF` brand, etc.) were eyeballed from
    screenshots on 2026-07-10 — screenshots can't reliably distinguish true black from a very
    dark grey, and turned out wrong once a real source existed. Corrected 2026-07-21 across
    `IdsSemanticColors.kt` (Android), `IDS.swift` (iOS), and `tokens.css` (web) —
    background/surface/surfaceSoft/brand/textBrand/divider/chip only, mapped by matching
    role name, not guessed. `pressed`/`success`/`warning`/`danger`/tint colors were
    deliberately left untouched: the real package only exposes the raw numbered adaptive
    scale, not which step Toss's own semantic "danger"/"pressed" roles actually point to, and
    guessing that mapping would repeat the exact mistake being fixed here. Also confirmed
    real (from `@toss/tds-react-native`'s shipped `tokens/token.js`): Toss's actual line-height
    values are unitless ratios (`1.252`/`1.35`/`1.5`) applied per font size, not fixed px — a
    structurally different (and more robust) approach than either TDS's own docs or itunda's
    typography scale, both of which hardcode a fixed line-height per size step. Not adopted
    here (would mean restructuring `IdsTypography.kt`'s whole shape, not a value swap), but
    worth knowing if that scale is revisited.
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

- **Corrected 2026-07-12** — MongoDB/Elasticsearch as part of Toss's *core banking* stack: no
  source found; still not claimable for Toss Bank, which remains documented as Oracle→MySQL
  (§1). But scoped to **Toss Securities' data/ML pipeline specifically**, both are now real and
  sourced: Elasticsearch runs a ~22TB/day, ~1.7B-entries/day log cluster
  ([toss.tech/article/slash23-data](https://toss.tech/article/slash23-data)); MongoDB stores
  ML user-clustering output, joined into ksqlDB via CDC for real-time personalization
  ([toss.tech/article/ksqldb-realtime-data](https://toss.tech/article/ksqldb-realtime-data)).
  Do not conflate this with Toss Bank's core ledger/account database.
- Specific service port numbers (3000-3011) — invented, not real.
- **Corrected 2026-07-12** — "PCI-DSS Level 1" self-declared compliance: the specific phrase
  "Level 1" is still wrong to use (it names a merchant-transaction-volume tier, not a
  certification, and no Toss source uses that phrase). But **Toss Securities holds a real,
  audited PCI-DSS v4.0 certification** from BSI Korea — the first Korean securities firm to get
  one ([toss.im/tossfeed/article/securities_PCI-DSS](https://toss.im/tossfeed/article/securities_PCI-DSS)).
  Scoped to Toss Securities only; not evidence of a Toss Bank or Toss Payments certification,
  and never claimable as itunda's own status without an actual audit.
- "1M+ concurrent users", "99.99% uptime", "Production Ready" in `IMPLEMENTATION_GUIDE.md` —
  aspirational marketing copy with no basis in this repo's actual state (no git history existed
  until this session; most surfaces are unwired prototypes — see `docs/TOSS_PARITY_MATRIX.md`).
  Re-checked 2026-07-12: still no official Toss source states a system-uptime SLA figure either
  (the only official "99.99%" found is FacePay's *facial-recognition accuracy*, an unrelated
  metric — [toss.im/tossfeed/article/facepay](https://toss.im/tossfeed/article/facepay)).
- Redux/MVI as "the" Toss mobile state approach — not sourced; Toss's real distinctive mobile
  architecture fact is the RN host+mini-app bundle split (§3), not a specific state library.

## 6. New Toss product/platform developments (found 2026-07-12)

- **FacePay** (biometric in-store payment): a real, named product not previously in this doc —
  2M+ registered users, 240,000+ participating merchants by mid-2026, first facial-payment
  product to get preliminary review from Korea's PIPC privacy regulator.
  Source: [toss.im/tossfeed/article/facepay](https://toss.im/tossfeed/article/facepay). Relevant
  to itunda's own "Face Pay terminal concept" row in `docs/TOSS_PARITY_MATRIX.md` — now has a
  real reference point for adoption scale and regulatory precedent, not just a Toss product name.
- **`toss/granite` has had no release since `0.1.34` (2026-01-12)** despite active non-release
  commits as recently as 2026-07-10 — a staleness signal worth tracking before deepening a
  dependency on it (relevant to `saronite`'s eventual migration path, §4), not a reason to avoid
  it outright.
- Toss Bank/Viva Republica's blockchain and stablecoin exploration (Solana Foundation MOU,
  an Optimism PoC, KRW-stablecoin trademark filings) and a reported Q2 2026 US IPO are real but
  **corporate-strategy facts, not architecture facts** — they don't belong in this document's
  "what to build like" scope and shouldn't be treated as an itunda roadmap item.

## 7. Toss Payments' private cloud: OpenStack + Cluster API, internally called "OKS"

Source: [토스 테크 — 레거시 인프라 작살내고 하이브리드 클라우드 만든 썰](https://toss.tech/article/payments-legacy-9)
(fetched 2026-07-13). Directly answers a question this session's own Multipass-based
active-active rehearsal raised: does Toss run OpenStack anywhere? Yes — but scoped to Toss
Payments specifically, and not as a replacement for Kubernetes, as an IaaS layer underneath it.

- Toss Payments built its own private cloud on **OpenStack**, organized as three independent
  clusters they call "Pods": **Pod0** (management — Cluster API, monitoring, security tooling,
  quorum nodes) and **Pod1**/**Pod2** (production, active-active, fully independent of each
  other — losing one OpenStack cluster shifts traffic to the others without an outage).
- Kubernetes runs on top of this via **Cluster API + CAPO** (Cluster API Provider OpenStack) +
  **OCCM** (OpenStack Cloud Controller Manager) — a deliberate choice over OpenStack's own
  Magnum PaaS offering, specifically so K8s clusters stay declaratively managed like any other
  resource. They name this whole arrangement **"OKS"** ("OpenStack Kubernetes Service"), an
  explicit naming echo of AWS EKS.
- Explicit resource-parity mapping to AWS, stated directly in the source: EC2→Nova,
  NLB→Octavia, ALB→F5 BigIP, ECR→Harbor (open-source), **EKS→Cluster API-managed K8s (OKS)**.
- Four live traffic zones in production: Live1/Live2 on AWS, Live3/Live4 on the OpenStack
  Pods — Route53 plus AWS Global Accelerator balance across all four before traffic ever
  reaches a customer-facing firewall.
- IaC via Terraform + Ansible; golden images bring instance creation down to ~10 seconds;
  **AWS Roles Anywhere** lets OpenStack-hosted workloads assume real AWS IAM roles without
  static access keys (a process-credential-provider pattern for VMs, a sidecar + K8s
  ServiceAccount pattern for pods).
- Monitoring: Zabbix/Prometheus/Mimir for metrics, Grafana for dashboards, Elasticsearch with
  Cross-Cluster Search handling 100,000–300,000 logs/second across both the AWS and OpenStack
  environments.
- They patched Octavia's own source code to change its log format — real operational
  ownership of the private-cloud stack, not a vendored black box left untouched.

**What this means for itunda, honestly**: full OKS-style Cluster API + CAPO + a real OpenStack
control plane is production infrastructure sized for a company running its own physical data
centers — not something to stand up as a local rehearsal (CAPO alone typically wants
kubeadm-class nodes with real resources per node; OpenStack's own control plane is commonly
budgeted 8GB+ RAM before any workload runs on it). The two-Multipass-VM rehearsal this session
is building targets a different, smaller claim: prototyping the *shape* of active-active
failover (MySQL replica promotion, Kafka topic mirroring) locally, not reproducing OKS itself.
If itunda ever needs the real thing, Cluster API + a cloud-agnostic infrastructure provider
(not necessarily CAPO/OpenStack specifically) is the correct pattern to converge on, matching
what's actually sourced here rather than a guess.

## 8. Gateway, resilience engineering, and paved-road tooling (found 2026-08-29)

Source: [토스는 Gateway 이렇게 씁니다](https://toss.tech/article/22910), [은행 최초 코어뱅킹 MSA 전환기](https://toss.im/career/article/tossbank-system) / SLASH23 session A1-8, [20년 레거시를 넘어](https://toss.tech/article/payments-legacy-1), [토스페이먼츠의 Open API 생태계](https://toss.tech/article/payments-legacy-4), [서버 증설 없이 처리하는 대규모 트래픽](https://toss.tech/article/monitoring-traffic), [캐시 문제 해결 가이드](https://toss.tech/article/cache-traffic-tip), [Kafka 데이터센터 이중화 #1](https://toss.tech/article/kafka-distribution-1)/[#2](https://toss.tech/article/kafka-distribution-2)/[#3](https://toss.tech/article/33121), [유연하고 안전하게 배포 Pipeline 운영하기](https://toss.tech/article/slash23-devops), [토스의 속도와 품질, 상용 도구로 충분한가 — 토션](https://toss.tech/article/tossion), [레고처럼 조립하는 토스 앱](https://toss.tech/article/slash23-iOS), [200여개 서비스 모노레포의 파이프라인 최적화](https://toss.tech/article/monorepo-pipeline) (all primary toss.tech/toss.im).

- **Gateway is a real shared cross-cutting-concern layer, not just a router.** Spring Cloud
  Gateway on Reactor-Netty + Kotlin coroutines centralizes auth, request/response encryption,
  anti-tampering signature checks, and **mTLS (X.509 SANs) between services**, plus a
  **"Passport" token** that carries resolved user/device context downstream so services don't
  each independently call a user-info API. Circuit breaking runs at **two layers**: Istio
  (infra) and Resilience4j (app, per-route) — kept deliberately separate because Istio's
  granularity alone was judged too coarse.
- **Core-banking MSA extraction is phased by traffic percentage**, not a big-bang cutover
  (internal → employees → % of users → 100%), and uses **Redis global locks + JPA `@Lock`
  pessimistic row locks together** to prevent lost updates on concurrently-touched accounts,
  with async/eventual-consistency accounting work split out through Kafka with a DLQ.
- **Toss Payments' legacy modernization is "Two-Track"**: new cloud-native work never touches
  the legacy system, legacy is hardened in place without downtime — explicitly not a rewrite.
  Real scale reached: 150+ microservices, migrations validated via 1%-increment canary shifts
  plus 450,000+ regression tests before cutover.
- **Rate limiting is framed as "the last line of defense"** against traffic spikes/attacks, not
  just throughput shaping; **traffic spikes are absorbed by Redis+Kafka write-buffering and
  local-node caching of non-user-specific data (invalidated via Redis Pub/Sub) before adding
  hardware** — a real incident (unplanned viral traffic on a live-shopping feature) was resolved
  this way, plus consolidating 3 duplicate endpoints into 1 (cut peak traffic 50%).
- **Cache-stampede protection**: jittered TTLs (randomized 0-10s spread), null-object caching
  against cache-penetration, Redis Redlock for hot-key distributed locking, and an explicit
  "essential vs. non-essential feature" split so a cache outage can't take down core paths.
- **Deployment safety net**: Toss Bank's backend runs GoCD (not custom-built) with
  Pipeline-as-Code across 400+ pipelines, and a CI check that re-renders every pipeline on
  template change specifically to catch blast radius before merge — the CI-enforcement
  equivalent of itunda's own `.dependency-cruiser.cjs`/Konsist/`ios-silo-boundary-check.py`
  boundary checks, at a much larger scale.
- **iOS "Microfeatures"** (the real Toss-published name) is Tuist + custom Stencil templates,
  splitting each feature into 5: Feature (impl), **Interface** (the only cross-feature-importable
  surface), Testing, Tests, and **Example** — a standalone per-feature mini-app that builds ~5x
  faster than the full app, usable by design/PM for review without a full build. This is the
  real source `android/settings.gradle.kts` and `docs/MULTI_AGENT_ISOLATION.md` already cite —
  **correction**: it is an iOS-specific SLASH23 talk; no primary Toss source describes an
  Android-specific equivalent module system, so itunda's own Android Microfeatures graph is a
  cross-platform extrapolation of the iOS pattern, not a directly Toss-Android-sourced one. Also
  not yet confirmed present in itunda's own iOS `Features/<Name>` split: the **Example**
  sub-app pattern specifically (fast per-feature build/design-review loop) — worth checking for.
- **Web is NOT Module Federation.** Toss's real web architecture is a **single monorepo housing
  200+ frontend services** (50-60 contributors, ~60 PRs/day, 40GB+ repo needing
  `git clone --filter=blob:none`), a 5-minute push-to-deploy via CircleCI Dynamic Config running
  isolated per-service jobs, a daily-rebuilt Docker base image with the monorepo pre-baked
  (36min → 22sec checkout), and Yarn PnP + a custom bundler shrinking SSR images ~4GB → ~200MB.
  Directly relevant: itunda's `host-app` module-federation shell is **not** the Toss-aligned
  direction (no primary source describes Toss using Module Federation for this) — the
  Toss-aligned direction is what `bank-mfe` already is (one real deployed app), with the actual
  open problem being *internal* decomposition of that one app (already tracked in
  `docs/ARCHITECTURE_GUIDELINES.md` §2's `BankDashboard.tsx` finding), not splitting into more
  federated apps.
- **Named paved-road tooling**: **Tossion** (real internal QA/TCM platform — beyond test-case
  management, it flags PRs touching files with prior-incident history as higher risk, and
  AI-generates test cases) and **Nebula** (a named real device farm for on-device test
  execution) are Toss's equivalent of itunda's own `scripts/accessibility-lint.py`/
  `scripts/file-size-lint.py`/`scripts/uncalled-endpoint-sweep.py` habit — same philosophy
  (turn a recurring problem into an enforced tool), different scale.
- **Explicitly searched for and NOT found** (don't claim these as Toss-sourced without a new
  primary source): a named chaos-engineering practice; a named feature-flag platform; on-call
  rotation/paging tooling specifics; a Toss-published article on double-entry ledger schema
  mechanics beyond the locking/saga behavior above; a Toss-published article on Android-specific
  multi-module architecture.

## Update Rule

Same rule as `FACT_CHECKED_TOSS_RWANDA_MAP.md`: update this file when new public Toss
architecture facts surface, and do not let `ARCHITECTURE.md` assert anything about "matching
Toss" that isn't backed by an entry here.
