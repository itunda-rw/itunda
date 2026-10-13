# Itunda Maps search-quality benchmark

## Purpose

Create a repeatable evaluation set before changing ranking. These are test queries and evaluation instructions, not claims that the current Rwanda index returns a particular place. Run them against a staging instance with a known OSM extract and record the extract date, query, locale, viewport, top 5 results, latency, and whether the intended place is present.

## Query set

| ID | Query | Locale | Evaluation focus |
| --- | --- | --- | --- |
| RW-KGL-01 | Kigali | rw,en,fr | City/settlement should be represented; no irrelevant foreign-country result |
| RW-KGL-02 | Kigali Convention Centre | en,rw,fr | Named landmark retrieval |
| RW-KGL-03 | Kigali Convention Center | en,rw,fr | US/UK spelling variation |
| RW-KGL-04 | Kigali, Rwanda | en,rw,fr | Address ordering and country constraint |
| RW-KGL-05 | ibitaro | rw,en,fr | Kinyarwanda common noun; inspect actual indexed matches |
| RW-KGL-06 | farumasi | rw,en,fr | Kinyarwanda pharmacy term; inspect actual indexed matches |
| RW-KGL-07 | pharmacy | en,rw,fr | English category term |
| RW-HUY-01 | Huye | rw,en,fr | Secondary-city coverage |
| RW-RUB-01 | Rubavu | rw,en,fr | Secondary-city coverage and local naming |
| RW-MUS-01 | Musanze | rw,en,fr | Secondary-city coverage |
| RW-GIS-01 | Gisenyi | rw,en,fr | Alternate/common place name coverage; compare with Rubavu results |
| RW-TYP-01 | Kigali Conventon Center | en,rw,fr | Typo tolerance; record whether correction is offered or a near-match appears |
| RW-ALT-01 | [a verified local alias from field research] | rw,en,fr | Alternate-name lookup, only after alias is verified with local users/data |
| RW-EMPTY-01 | xqz-no-such-place-93817 | en,rw,fr | Genuine no-match returns an empty result, not an error |
| RW-OUT-01 | Eiffel Tower | en,rw,fr | Rwanda country restriction behaves as intended; expected no-result is a hypothesis to verify against index/data |

Do not count a query as passing solely because it returned something. For every query, a reviewer must record whether the intended result exists in the local data, whether the top result is useful, and whether any result is misleading. For common nouns such as `ibitaro` and `farumasi`, Nominatim returns best matches rather than a complete category inventory; nearby-category search should be evaluated separately.

## Metrics

- **Top-1 success:** intended result is first, for queries with a verified target.
- **Top-5 recall:** intended result appears among the first five.
- **Zero-result rate:** split into expected zero-result and unexpected zero-result.
- **Latency:** p50/p95 server latency, measured separately from client debounce.
- **Locale quality:** result labels use the requested language where OSM name tags support it; never treat a translated label as proof of a better match.
- **Coverage:** report separately for Kigali and each sampled secondary city.
- **Data provenance:** save OSM object identity when available, index extract timestamp, and evaluation date so scores are reproducible.

## Evaluation process

1. Run the fixed query set on staging against a recorded data extract.
2. Capture the raw top-five results and latency; do not log end-user searches or precise locations as telemetry.
3. Have at least two Rwanda-based reviewers judge target relevance and name quality for ambiguous/local-language queries.
4. Record false positives, missing target data, alternate-name gaps, and ranking errors as separate defects.
5. Change ranking only after a baseline exists; rerun the same set and compare deltas.
6. Do not claim Naver parity from this small seed set. Expand it with verified local addresses, business POIs, village names, and rural routes before a release decision.

## Current implementation note

The Maps search response now carries Nominatim `namedetails` as optional `alternativeNames` when present. This makes aliases available to clients and future relevance work; it does not yet implement typo correction, alias-based query matching, or a ranking boost. Nominatim's documented `accept-language` controls preferred result-label language, and `namedetails=1` requests available alternate names. See the [official Search API documentation](https://nominatim.org/release-docs/latest/api/Search/).
