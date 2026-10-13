# Itunda Maps: Naver-level capability roadmap

## Purpose

Build a reliable, fast, locally useful mapping product for Rwanda first, then expand across Africa. Learn from the product quality and interaction patterns of Naver Maps and the engineering/product-system discipline documented by Toss. Do not copy proprietary code, assets, or branding. Keep Itunda identity and the current Itunda brand tokens.

This is a capability and verification plan, not a claim that parity already exists.

## Repository evidence observed during this audit

- Maps backend has a dedicated service and uses Nominatim geocoding and OSRM routing clients.
- The geocoding client is documented as a self-hosted Rwanda-focused Nominatim index.
- Map frontend network code exists in both `services/micro-frontends/maps-mfe/src/lib/maps.ts` and `services/micro-frontends/bank-mfe/src/lib/maps.ts`; comments state the implementations are duplicated and contract changes must be applied to both.
- The maps micro-frontend references a self-hosted Rwanda vector-tile archive served by PMTiles.
- iOS has a maps network extension with previous transit/place-detail additions; Android has a dedicated map style implementation.
- The repository contains a maps backend test suite. Extend it with explicit contract and failure-mode coverage as capabilities are added.

These observations should be rechecked against the current branch and runtime before implementation decisions are finalized.

## Priority 0 — contract and production reliability

- [ ] Publish one canonical maps API contract (OpenAPI/schema) covering search, nearby places, geocode, reverse geocode, place details, routes, bookmarks, and transit where implemented.
- [ ] Eliminate duplicated maps API clients by extracting a shared typed client or generating clients from the canonical contract. If runtime boundaries prevent sharing, enforce parity with generated fixtures and contract tests.
- [ ] Never turn upstream timeouts, unconfigured services, or malformed responses into a successful empty result. Return typed errors, actionable retry states, request IDs, and useful telemetry.
- [ ] Verify production configuration for tile, geocoding, and routing endpoints in GCP; add health/readiness checks and alerts for missing configuration.
- [ ] Add contract tests for Android, iOS, and web clients against the same fixtures.

Acceptance: all clients use the same documented contract; upstream failures remain distinguishable from genuine zero-result searches; CI tests the contract and configuration checks.

## Priority 1 — search and place discovery

- [ ] Support debounced autocomplete, recent searches, typo tolerance, transliteration and alternate names.
- [ ] Rank results using text relevance, distance, category, popularity signals where lawful, and data confidence; expose deterministic tie-breakers.
- [ ] Make nearby search viewport-aware and category-driven, with loading, no-results, offline, permission-denied, and retry states.
- [ ] Build stable place identity and deduplication across OSM identifiers, local business records, and verified community submissions.
- [ ] Localize names and UI for Kinyarwanda, English, and French; retain original names and show data provenance.
- [ ] Show hours, phone, category, address, photos, reviews, and service attributes only when available and appropriately sourced.

Acceptance: relevance evaluation set for Kigali and representative secondary cities; measured top-result quality; no fabricated place facts; graceful behavior with weak connectivity.

## Priority 2 — routing and navigation

- [ ] Define a typed routing model for walking, driving, cycling, and public transport only where the underlying data/provider supports it.
- [ ] Return route geometry, distance, duration, steps, warnings, alternatives, and data timestamp with consistent units.
- [ ] Add route alternatives and preference controls (fastest, shortest, avoid unpaved roads/tolls where supported).
- [ ] Add location-follow mode, off-route detection, rerouting, heading-aware camera, and accessible maneuver prompts.
- [ ] Communicate uncertainty honestly when road access, travel times, or transit data is incomplete.

Acceptance: route fixtures for urban, rural, disconnected-road, and no-route cases; latency and route-validity dashboards; no invented transit schedules or ETA guarantees.

## Priority 3 — map rendering and performance

- [ ] Validate vector-tile coverage, freshness, zoom levels, label density, and attribution for Rwanda.
- [ ] Ensure map gestures remain smooth during search, route rendering, and place selection; profile low-end Android devices.
- [ ] Use viewport-aware loading, caching with explicit freshness rules, request cancellation, and bounded memory.
- [ ] Provide accessible light/dark styles, legible selected-place states, and high-contrast route lines using Itunda semantic tokens.
- [ ] Add offline downloads by named area with storage estimates, versioning, expiry/update controls, and clear coverage boundaries.

Acceptance: device-class performance budgets and repeatable benchmark scenarios; tile errors and cache hit rate monitored.

## Priority 4 — place details and local trust

- [ ] Provide a consistent place-details contract and UI across Android, iOS, and web: overview, directions, hours, contact, photos, reviews, and relevant services.
- [ ] Add report-a-problem, suggest-an-edit, duplicate reporting, and business-owner verification workflows.
- [ ] Store source, update time, confidence, and moderation state for contributed data.
- [ ] Add abuse prevention, moderation tools, audit logs, and correction SLAs before enabling public reviews or edits.

Acceptance: every displayed claim has provenance; unverified submissions are clearly distinguished; moderation and rollback are testable.

## Priority 5 — Toss-inspired UX without copying Toss identity

- [ ] Keep one obvious primary action per state and use concise, contextual copy.
- [ ] Use the current Itunda brand token (`#8F89FF`) for brand actions and success emphasis where semantically appropriate; validate contrast in light and dark modes.
- [ ] Use accessible touch targets, clear bottom sheets, skeletons, retry actions, and reduced-motion support.
- [ ] Define design tokens and reusable components in IDS; avoid one-off styling across platform implementations.
- [ ] Measure task completion and time-to-directions rather than judging visual similarity alone.

Acceptance: design review across Android/iOS/web, accessibility checks, and usability tests with Rwanda-based users.

## Priority 6 — observability and rollout

- [ ] Instrument search latency, zero-result rate, geocode success, route success, tile failures, reroutes, and crash-free map sessions.
- [ ] Add privacy-preserving diagnostics; do not log precise location or search history unnecessarily.
- [ ] Roll out by feature flag and region; preserve a rollback path for search, routing, and tile changes.
- [ ] Publish an internal Maps runbook covering provider health, data refresh, cache invalidation, and incident response.

## Suggested delivery order

1. Audit current API and runtime configuration; add failing contract tests for known inconsistencies.
2. Consolidate client contracts and fix failure semantics.
3. Improve Rwanda search relevance and place identity.
4. Harden routing and navigation behavior.
5. Improve rendering, offline coverage, and performance.
6. Complete place details, community data governance, and product analytics.

## Reference material

- Toss TDS Mobile documentation: https://tossmini-docs.toss.im/tds-mobile/
- Apps in Toss developer platform: https://developers-apps-in-toss.toss.im/
- Naver Open API guide repository: https://github.com/naver/naver-openapi-guide

Use official documentation and public APIs as references; do not assume that a public sample repository is official or that an API's availability, terms, coverage, or pricing are unchanged. Verify licensing, attribution, usage limits, and data freshness before production adoption.
