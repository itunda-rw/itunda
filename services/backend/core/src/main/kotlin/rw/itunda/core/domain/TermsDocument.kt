package rw.itunda.core.domain

/**
 * Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog -- static, like
 * `BillsCatalog`/`StockCatalog`. Sourced from the real, standard Korean fintech terms
 * screen structure (a required Terms of Service + required Privacy Policy + optional
 * marketing consent, [필수]/[선택] tagged, required items listed before optional ones)
 * and the real regulatory backdrop that shape exists to satisfy: Korea's 전자상거래법
 * (e-commerce law) amendment effective 2025-02-14 bans dark-pattern consent UI --
 * pre-ticked boxes and a "confirm" button styled to look like the only option -- and a
 * 2026-09-11 penalty increase (up to 10% of annual revenue for serious violations)
 * raises the real stakes of getting this right. itunda had ZERO terms-consent step
 * anywhere before this -- `AuthService.register` created a real account and a real
 * wallet with no record the user agreed to anything.
 *
 * `required = true` items MUST be present in `RegisterRequest.acceptedTermsIds` or
 * registration real-400s -- see `AuthService.register`'s own validation. `required =
 * false` (marketing) is honestly optional: skipping it must never block account
 * creation, the same "an optional term is actually optional" rule the real dark-pattern
 * regulation exists to enforce.
 */
data class TermsDocument(val id: String, val title: String, val version: String, val required: Boolean, val summary: String)

object TermsCatalog {
    val documents = listOf(
        TermsDocument(
            id = "terms_of_service",
            title = "Terms of Service",
            version = "2026-08-18",
            required = true,
            summary = "The rules for using itunda: your responsibilities, our responsibilities, and how disputes are handled.",
        ),
        TermsDocument(
            id = "privacy_policy",
            title = "Privacy Policy",
            version = "2026-08-18",
            required = true,
            summary = "What personal and financial information itunda collects, why, and how long it's kept.",
        ),
        TermsDocument(
            id = "marketing_communications",
            title = "Marketing Communications",
            version = "2026-08-18",
            required = false,
            summary = "Occasional promotions, new features, and offers by push notification or SMS. You can opt out any time in Settings.",
        ),
    )

    fun requiredIds(): Set<String> = documents.filter { it.required }.map { it.id }.toSet()
    fun validIds(): Set<String> = documents.map { it.id }.toSet()
}
