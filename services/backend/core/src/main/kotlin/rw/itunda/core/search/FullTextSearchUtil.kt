package rw.itunda.core.search

/**
 * Shared MySQL BOOLEAN MODE query builder (2026-08-14) -- see
 * MerchantProductRepository.searchFullText's own doc comment for the "why" this
 * exists at all (a plain LIKE search never ranked results and only matched one
 * exact substring). Factored out once this same construction was needed a second
 * time for Marketplace/Community/Jobs/Property search, rather than copy-pasting the
 * sanitization logic across five controllers.
 */
object FullTextSearchUtil {
    /**
     * Builds a MySQL BOOLEAN MODE query string: each token is sanitized (boolean-mode
     * operator characters stripped, so a query like "c++" can't inject search syntax),
     * suffixed with `*` for prefix matching (typing "pho" already matches "phone"),
     * and required via a leading `+` (AND of terms, not OR). Returns null when no
     * token reaches MySQL's default minimum indexed token length (3 characters) --
     * FULLTEXT structurally can't match anything shorter, so callers should fall back
     * to a plain LIKE search in that case, not a bug in this function.
     */
    fun toBooleanModeQuery(q: String): String? {
        val query = q.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[+\\-><()~*\"@]"), "") }
            .filter { it.length >= 3 }
            .joinToString(" ") { "+$it*" }
        return query.ifBlank { null }
    }
}
