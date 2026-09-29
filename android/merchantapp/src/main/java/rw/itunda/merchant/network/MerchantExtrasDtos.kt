package rw.itunda.merchant.network

// Real gaps found live (uncalled-endpoint sweep, 2026-08-29/30): both features have
// been fully built on the backend + live on merchant-mfe since, and both are already
// displayed to customers via Android/iOS's real place-detail Maps tabs, but the
// native merchantapp had zero UI for either. New file (not added to ApiService.kt,
// which was already at its file-size-lint baseline) matching PayrollDtos.kt's own
// same-package-visibility split.
data class SetMerchantPhotoUrlsRequest(val photoUrls: List<String>)

data class MerchantUpdateDto(
    val id: String, val merchantId: String, val label: String, val title: String, val body: String,
    val periodStart: String?, val periodEnd: String?, val likeCount: Long, val createdAt: String,
)
data class PostMerchantUpdateRequest(val label: String, val title: String, val body: String)
data class MerchantUpdateResponse(val success: Boolean, val update: MerchantUpdateDto)
data class MerchantUpdatesResponse(val success: Boolean, val updates: List<MerchantUpdateDto>)
