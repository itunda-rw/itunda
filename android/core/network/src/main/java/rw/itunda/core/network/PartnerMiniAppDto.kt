package rw.itunda.core.network

data class PartnerMiniAppDto(
    val id: String,
    val partnerId: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val bundleUrl: String,
    val releaseId: String? = null,
    val manifestSha256: String? = null,
    val bundleSha256: String? = null,
    val bundleSizeBytes: Long? = null,
    val permissions: String,
    val status: String,
    val category: String,
    val createdAt: String,
)
