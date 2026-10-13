# Rwanda Maps Search Benchmark Runbook

This runbook defines how to turn the seed cases in PR #53 into a repeatable, evidence-based benchmark. It does not claim the benchmark has already run or that any provider has achieved a particular score.

## Required fixture format

Store reviewed fixtures as version-controlled JSON Lines. One JSON object per query:

```json
{
  "id": "kigali-landmark-001",
  "query": "Kigali Convention Centre",
  "language": "en",
  "intent": "place",
  "region": "Kigali",
  "expectedPlaceIds": [],
  "expectedNamesForReview": ["Kigali Convention Centre"],
  "expectedZeroResults": false,
  "reviewStatus": "needs-human-review",
  "source": "OpenStreetMap",
  "notes": "Confirm the canonical OSM object and duplicate/venue naming before scoring."
}
```

The example is a schema illustration, not a scored test case. Never treat an empty `expectedPlaceIds` array as an assertion that no result should exist. Only fixtures marked `reviewStatus: approved` may contribute to headline metrics.

## Fixture review rules

- Include Kigali and secondary cities, then rural/locality coverage; do not overfit the benchmark to central Kigali.
- Cover Kinyarwanda, English, and French, alternate names, spelling variants, common typos, categories, ambiguous names, and reviewed zero-result cases.
- Prefer stable source identifiers (e.g. OSM type + ID) over display names alone. Display names can change and multiple venues can share names.
- Store provenance and review date. Recheck cases when source data changes materially.
- Do not include a person's home, live location traces, or other sensitive location data.
- Keep expected candidates independent of the search implementation under test. A reviewer must establish relevance without relying on the current top result.

## Metrics

For each approved query, preserve the raw ranked result IDs and latency so the aggregate can be reproduced.

- Recall@5 / Recall@10: fraction of known relevant candidates present in the top 5 / 10.
- MRR: reciprocal rank of the first relevant result, averaged over queries with a known relevant candidate.
- NDCG@k: ranking quality with graded relevance where reviewers provide graded labels.
- Alias/typo success: fraction of approved alias/typo queries retrieving a relevant candidate in the top k.
- Zero-result precision: fraction of searches returned as empty that are approved true-zero cases. Also report zero-result recall so the system cannot game precision by never returning empty.
- Latency: p50 and p95 measured separately for warm/cold requests and by provider.
- Reliability: timeout, rate-limit, upstream failure and malformed-response rates, separate from valid zero-result responses.

Always report sample size and confidence caveats. Exclude unreviewed fixtures from the score, but report their count.

## Execution sequence

1. Confirm the benchmark runner and provider adapter used by the current Maps implementation; do not create a second independent search implementation just for scoring.
2. Approve the initial fixture set with stable IDs and expected candidates.
3. Capture a baseline against the current PR head and record commit SHA, fixture version, provider, locale settings, and timestamp.
4. Make one ranking change at a time and rerun the exact same approved fixture set.
5. Compare metrics and inspect regressions by language, region, intent, and query type.
6. Publish raw result artifacts and a summary; never describe fixture creation as a benchmark pass.

## Release gate

Do not claim “Naver Maps level” or a search-quality improvement until the baseline and candidate runs are reproducible and show measured results. A successful compile/test run validates code behavior, not map-data completeness. Document provider coverage, usage terms, attribution, and known rural coverage gaps separately from ranking metrics.
