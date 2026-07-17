package rw.itunda.core.web

import org.springframework.data.domain.Page

/**
 * The one real pagination convention this backend uses, established 2026-07-17 to close
 * the "three-plus admin/catalog endpoints return a fully unbounded `List<T>` with no cap
 * on result size" gap an earlier performance-review pass flagged and explicitly deferred
 * (see docs/TOSS_PARITY_MATRIX.md) -- before this, `grep -r "Pageable\|PageRequest"`
 * across all of services/backend returned zero hits.
 *
 * Deliberately additive, not a new envelope: every paginated endpoint keeps its existing
 * `{"success": true, "<field>": [...]}` shape (`"queue"`, `"merchants"`, `"miniApps"`) and
 * just spreads these four extra top-level keys alongside it, e.g.
 * `{"success": true, "merchants": [...], "page": 0, "size": 20, "totalElements": 42,
 * "totalPages": 3}`. A caller that only ever reads the named array field (every existing
 * frontend consumer before this pass) keeps working unchanged; a caller that never passes
 * `page`/`size` still gets a real, bounded (default 20-row) page 0 instead of an
 * unbounded dump, via each controller's `@PageableDefault(size = 20)`.
 */
fun <T> pageMeta(page: Page<T>): Map<String, Any> = mapOf(
    "page" to page.number,
    "size" to page.size,
    "totalElements" to page.totalElements,
    "totalPages" to page.totalPages,
)
