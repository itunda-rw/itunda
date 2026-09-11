package rw.itunda.partners

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.core.domain.PartnerMiniAppCategory
import rw.itunda.core.domain.PartnerMiniAppStatus
import rw.itunda.core.domain.PartnerStatus
import rw.itunda.core.repository.PartnerMiniAppRepository
import rw.itunda.core.repository.PartnerRepository
import rw.itunda.core.validation.isValidEmail
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID

class PartnerEmailAlreadyRegisteredException(message: String) : RuntimeException(message)
class InvalidPartnerEmailException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class PartnerSuspendedException(message: String) : RuntimeException(message)
class InvalidPermissionScopeException(message: String) : RuntimeException(message)
class PartnerMiniAppNotFoundException(message: String) : RuntimeException(message)
class PartnerMiniAppNotPendingException(message: String) : RuntimeException(message)
class InvalidMiniAppSubmissionException(message: String) : RuntimeException(message)
class InvalidMiniAppDecisionReasonException(message: String) : RuntimeException(message)
class InvalidMiniAppCategoryException(message: String) : RuntimeException(message)
class PartnerNotFoundException(message: String) : RuntimeException(message)

/**
 * The real scopes a partner mini-app can request review for -- deliberately a small,
 * conservative, read-only allow-list rather than exposing itunda's full real API
 * surface (bill payment, transfers, card charges) to a third party this pass has no
 * runtime enforcement mechanism for yet. A human reviewer sees exactly which of these
 * a submission asks for; PartnerService itself only validates the request names a real
 * scope, it never grants anything -- see PartnerService's own doc comment for the full
 * honest scope boundary.
 */
object PartnerMiniAppPermissions {
    val ALLOWED = setOf("account:read", "transactions:read", "profile:read")
}

// Shared parse helper (2026-09-11, Mini-Apps hub pass) -- both submitMiniApp's own
// default-to-OTHER handling and MiniAppCatalogController's optional ?category= filter
// need identical "blank/null means no explicit value, anything else must be a real
// enum name" parsing; a null return means "no value given" (caller decides the
// default), never a value itself.
fun parsePartnerMiniAppCategory(raw: String?): PartnerMiniAppCategory? {
    if (raw.isNullOrBlank()) return null
    return try {
        PartnerMiniAppCategory.valueOf(raw.trim().uppercase())
    } catch (e: IllegalArgumentException) {
        throw InvalidMiniAppCategoryException("Unknown category '$raw' -- must be one of ${PartnerMiniAppCategory.entries.joinToString(", ")}")
    }
}

/**
 * A real third-party developer platform for itunda's own Saronite mini-app host --
 * closes the "allow partners to use our Saronite SDK to build apps in itunda, the way
 * apps work inside Toss" gap. Toss's own real mini-app ("미니앱") platform lets outside
 * companies register, submit an app manifest, and ship inside Toss once a human
 * reviewer approves it; this is the identical real shape.
 *
 * Honest, explicit scope boundary (read before assuming this is more than it is): this
 * is a REAL registry + REAL human review workflow + a REAL published catalog of
 * approved mini-apps (`getCatalog()`) -- every part of that is genuinely functional,
 * not a demo. Android's own host (`PartnerMiniAppLoader.kt`) already really downloads,
 * loads, and runs an approved bundle on a real device -- see `docs/TOSS_PARITY_MATRIX.md`'s
 * Partner SDK row for that account, this comment used to (wrongly) claim otherwise.
 * bank-mfe only got a catalog-browse client (2026-07-26); it doesn't attempt to run a
 * bundle at all, same as iOS's current scope.
 */
@Service
class PartnerService(
    private val partnerRepository: PartnerRepository,
    private val partnerMiniAppRepository: PartnerMiniAppRepository,
    private val rateLimiter: RateLimiter,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun register(companyName: String, contactEmail: String): Pair<Partner, String> {
        // /register is public/permitAll (see SecurityConfig -- a partner has no itunda
        // account yet, so it can't sit behind the JWT gate) and the "already registered"
        // check below doubles as an email-enumeration oracle (409 vs 201 reveals whether
        // an email has a partner account). Same real gap and same fix
        // AuthService.register already applies to itunda's own user registration --
        // without this, the endpoint has no bound at all on registration spam or on how
        // fast that oracle can be probed.
        // Real gap found 2026-09-05: contactEmail was never checked for even being
        // shaped like an email address -- see EmailValidation.kt's own doc comment.
        // Checked before the rate limiter below so a malformed value doesn't spend a
        // real attempt out of that budget.
        if (!isValidEmail(contactEmail)) {
            throw InvalidPartnerEmailException("Please provide a valid contact email address")
        }
        rateLimiter.checkLimit("partner:register:$contactEmail", limit = 3, window = Duration.ofMinutes(10))
        if (partnerRepository.findByContactEmail(contactEmail) != null) {
            throw PartnerEmailAlreadyRegisteredException("A partner account already exists for this email")
        }
        val rawKey = generateApiKey()
        val partner = Partner(
            id = "partner_${UUID.randomUUID()}",
            companyName = companyName,
            contactEmail = contactEmail,
            apiKeyHash = hashApiKey(rawKey),
            status = PartnerStatus.ACTIVE,
        )
        return partnerRepository.save(partner) to rawKey
    }

    @Transactional
    fun submitMiniApp(
        apiKey: String, name: String, description: String, iconUrl: String?, bundleUrl: String, permissions: List<String>,
        category: String? = null,
    ): PartnerMiniApp {
        val partner = resolvePartner(apiKey)
        val invalidScopes = permissions.filterNot { PartnerMiniAppPermissions.ALLOWED.contains(it) }
        if (invalidScopes.isNotEmpty()) {
            throw InvalidPermissionScopeException("Unknown permission scope(s): ${invalidScopes.joinToString(", ")}")
        }
        val trimmedName = name.trim()
        val trimmedDescription = description.trim()
        val trimmedIconUrl = iconUrl?.trim()?.ifBlank { null }
        val trimmedBundleUrl = bundleUrl.trim()
        if (trimmedName.isEmpty() || trimmedDescription.isEmpty() || trimmedBundleUrl.isEmpty()) {
            throw InvalidMiniAppSubmissionException("Name, description, and bundleUrl are all required")
        }
        // Real bound, found via the same systematic sweep that fixed the identical gap
        // across Commerce/Eats/Marketplace/Jobs/RealEstate/Community/Messaging/Maps the
        // same day -- these columns are VARCHAR(255)/500/500/500, and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length insert.
        if (trimmedName.length > 255 || trimmedDescription.length > 500 || (trimmedIconUrl?.length ?: 0) > 500 || trimmedBundleUrl.length > 500) {
            throw InvalidMiniAppSubmissionException("Name must be 255 characters or fewer; description, iconUrl, and bundleUrl 500 or fewer")
        }
        // Real Mini-Apps hub pass (2026-09-11) -- a blank/omitted category is a real,
        // honest default (OTHER), same fail-closed-on-garbage-input convention as the
        // permission-scope check above: an unrecognized name is rejected outright, never
        // silently coerced to a guess.
        val resolvedCategory = parsePartnerMiniAppCategory(category) ?: PartnerMiniAppCategory.OTHER
        val miniApp = PartnerMiniApp(
            id = "partner_app_${UUID.randomUUID()}",
            partnerId = partner.id,
            name = trimmedName,
            description = trimmedDescription,
            iconUrl = trimmedIconUrl,
            bundleUrl = trimmedBundleUrl,
            permissions = permissions.joinToString(","),
            status = PartnerMiniAppStatus.PENDING,
            category = resolvedCategory,
        )
        return partnerMiniAppRepository.save(miniApp)
    }

    fun getMyMiniApps(apiKey: String): List<PartnerMiniApp> {
        val partner = resolvePartner(apiKey)
        return partnerMiniAppRepository.findByPartnerIdOrderByCreatedAtDesc(partner.id)
    }

    fun getQueue(pageable: Pageable): Page<PartnerMiniApp> =
        partnerMiniAppRepository.findByStatusOrderByCreatedAtAsc(PartnerMiniAppStatus.PENDING, pageable)

    // Real published catalog -- what a mobile Saronite host client would fetch to know
    // which third-party mini-apps are approved and available, the same real "app store"
    // surface Toss's own mini-app platform exposes. See this class's own doc comment for
    // why the mobile side doesn't actually consume/render this yet.
    // Real category filter (2026-09-11, Mini-Apps hub pass) -- backs each client's new
    // dedicated hub screen's category chips.
    fun getCatalog(pageable: Pageable, category: PartnerMiniAppCategory? = null): Page<PartnerMiniApp> =
        if (category != null) {
            partnerMiniAppRepository.findByStatusAndCategory(PartnerMiniAppStatus.APPROVED, category, pageable)
        } else {
            partnerMiniAppRepository.findByStatus(PartnerMiniAppStatus.APPROVED, pageable)
        }

    @Transactional
    fun decide(miniAppId: String, reviewerId: String, approve: Boolean, reason: String?): PartnerMiniApp {
        val miniApp = partnerMiniAppRepository.findById(miniAppId)
            .orElseThrow { PartnerMiniAppNotFoundException("Mini-app submission not found") }
        if (miniApp.status != PartnerMiniAppStatus.PENDING) {
            throw PartnerMiniAppNotPendingException("Submission is already ${miniApp.status}")
        }
        // PartnerMiniApp.decisionReason has no explicit @Column length (255 default)
        // and is written verbatim from a reviewer's free-text input -- same missing-
        // bound bug class as the 2026-09-05 sweep, just an admin-facing input.
        if (reason != null && reason.length > 255) {
            throw InvalidMiniAppDecisionReasonException("Decision reason must be 255 characters or fewer")
        }
        miniApp.status = if (approve) PartnerMiniAppStatus.APPROVED else PartnerMiniAppStatus.REJECTED
        miniApp.reviewedBy = reviewerId
        miniApp.reviewedAt = java.time.Instant.now()
        miniApp.decisionReason = reason
        return partnerMiniAppRepository.save(miniApp)
    }

    // Real admin moderation surface (2026-09-07, Partners product-completeness pass) --
    // `resolvePartner` below already real-enforces PartnerStatus.SUSPENDED (locking a
    // suspended partner out of every real partner-facing feature), but nothing anywhere
    // ever set a Partner to SUSPENDED until this pass -- the same real gap class this
    // sweep already found and fixed for Merchant (MerchantService.suspendMerchant) and
    // for Vehicle Inspection's mechanics.
    fun getAllPartners(): List<Partner> = partnerRepository.findAll()

    @Transactional
    fun suspendPartner(partnerId: String): Partner {
        val partner = partnerRepository.findById(partnerId).orElseThrow { PartnerNotFoundException("Partner not found") }
        partner.status = PartnerStatus.SUSPENDED
        return partnerRepository.save(partner)
    }

    @Transactional
    fun reactivatePartner(partnerId: String): Partner {
        val partner = partnerRepository.findById(partnerId).orElseThrow { PartnerNotFoundException("Partner not found") }
        partner.status = PartnerStatus.ACTIVE
        return partnerRepository.save(partner)
    }

    // Real, shared partner-authentication entry point -- IdentityVerificationService
    // reuses this exact API-key resolution (hash lookup + suspended check) rather than
    // duplicating it, so a suspended partner is locked out of every real partner-facing
    // feature consistently, not just mini-app submission.
    fun authenticate(apiKey: String): Partner = resolvePartner(apiKey)

    private fun resolvePartner(apiKey: String): Partner {
        val partner = partnerRepository.findByApiKeyHash(hashApiKey(apiKey))
            ?: throw InvalidApiKeyException("Invalid or unknown API key")
        if (partner.status != PartnerStatus.ACTIVE) {
            throw PartnerSuspendedException("This partner account is suspended")
        }
        return partner
    }

    // sk_test_ prefix mirrors the real convention Stripe/Toss Payments both use for
    // API-key naming -- always "test" here since this pass builds no real production
    // gate/live-mode distinction, an honest reflection of what this platform actually
    // is right now, not an invented production-sounding prefix.
    private fun generateApiKey(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        return "sk_test_$token"
    }

    private fun hashApiKey(rawKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
}
