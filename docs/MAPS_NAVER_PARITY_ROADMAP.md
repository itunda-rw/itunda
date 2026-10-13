# Itunda Maps: Naver-level quality roadmap

Status: implementation plan; not a claim of feature parity. Keep Itunda's identity and Rwanda-first coverage. Use Naver Maps as a quality benchmark, not as a provider to copy blindly.

## Evidence and reference implementations

- Toss's public repositories: https://github.com/toss
  - `toss/granite`: public React Native framework for modular, brownfield applications.
  - `toss/frontend-fundamentals`: engineering guidance and frontend quality practices.
  - `toss/es-toolkit`: example of maintaining a focused, tested shared utility library.
- Naver Maps public SDK links are listed by NAVER Developers: https://developers.naver.com/docs/sdktool/Open-API-SDK.md
  - JS SDK: https://github.com/navermaps/maps.js
  - Android SDK: https://github.com/naver/navermap-android-api
  - iOS SDK: https://github.com/naver/navermap-ios-api
- Naver Maps JS geocoding tutorial: https://navermaps.github.io/maps.js.en/docs/tutorial-Geocoder-Geocoding.html
- Naver local search API docs: https://developers.naver.com/docs/serviceapi/search/local/local.md
- Nominatim Search API: https://nominatim.org/release-docs/latest/api/Search/

Naver's Korean local-search dataset and commercial APIs are not automatically a fit for Rwanda. Provider selection must follow coverage, terms, quotas, attribution, privacy, and deployment constraints. Do not ship Naver credentials in clients or scrape the consumer website.

## Product quality bar

### Search
- Accept Kinyarwanda, English, and French queries; normalize whitespace and Unicode without destroying meaningful diacritics.
- Search official/local names, alternate names, transliterations, and common spelling variants where source data supports them.
- Rank exact name before prefix/name match, localized aliases, category, and fuzzy/typo matches. Distance is a configurable tie-breaker, not a reason to bury a clear exact match.
- Return stable place IDs, coordinates, display name, address/admin hierarchy, category, source, and optional alternate names.
- Distinguish a valid empty result from invalid input, rate limiting, timeout, and upstream outage. Never turn an upstream failure into “no places found.”
- Add debounce, cancellation, request IDs, bounded timeouts, and safe pagination; cache only according to provider terms and freshness requirements.

### Rwanda search-quality benchmark
Maintain a versioned, reviewed fixture set for Kigali and secondary cities. Include Kinyarwanda/English/French queries, official and alternate names, common typos, category searches, nearby intent, ambiguous names, and known zero-result cases.
Measure:
- Recall@5 and Recall@10 for expected places
- MRR / NDCG for ranking quality
- typo and alias success rate
- empty-result precision
- p50/p95 latency and upstream error rate
- language coverage and coordinate validity

Record the dataset version, provider/source, expected candidates, and baseline before changing ranking. Do not claim improvement until benchmark runs produce measured results. Avoid storing personal location traces in fixtures.

### Map and navigation
- Pan/zoom smoothly; cluster dense markers; preserve selected-place state while the map moves.
- Provide current location only after permission, with a clear permission-denied state and manual search fallback.
- Validate route geometry and distance/duration before drawing; retain the last valid route when a refresh fails.
- Expose route alternatives only when the routing provider supports them. Label estimates and data freshness honestly.
- Make loading, empty, offline, permission-denied, timeout, and retry states accessible and localized.
- Support accessible hit targets, screen readers, dynamic type, reduced motion, and light/dark themes using Itunda Brand v3 tokens.

### API and SDK contract
- Keep one canonical place/search/route contract shared by backend, web, Android, and iOS.
- Version additive response fields; keep old payloads backward-compatible and test persisted legacy search results.
- Keep provider-specific fields behind adapters. Never leak provider secrets to clients.
- Use typed error codes, request correlation IDs, bounded retries, rate limits, and provider circuit breakers.
- Document attribution, caching, retention, and provider fallback rules before enabling a new data source.

## Delivery gates

1. Contract parity: backend, web, Android, and iOS agree on search result fields and typed failures.
2. Tests: query validation, aliases/locales, duplicate aliases, provider outage, legacy payload decoding, route geometry validation, and ranking edge cases.
3. Baseline: run the versioned benchmark and publish metrics; fixture creation alone is not a benchmark result.
4. Performance: measure real-device map interactions and backend p50/p95 latency; set targets only after collecting a baseline.
5. Coverage: validate places and routes across Kigali, secondary cities, and rural areas; document gaps rather than implying nationwide parity.
6. Release: all required CI checks pass, platform builds pass, and no regressions in permission/privacy or Brand v3 behavior.

## Next implementation sequence

1. Finish and verify PR #53's alternate-name and error-contract changes.
2. Run the Rwanda search benchmark and implement alias-aware matching/ranking from measured failures.
3. Verify iOS decoding and end-to-end response parity, then run backend and platform tests.
4. Audit route provider coverage, routing metrics, offline behavior, clustering, and accessibility against the quality bar above.
5. Publish a Maps quality dashboard with benchmark version, measured metrics, provider status, and known coverage gaps.

A “Naver Maps level” outcome is an ongoing measurable target, not something established by matching a few UI elements or adding API fields.
