package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantFollow

interface MerchantFollowRepository : JpaRepository<MerchantFollow, String> {
    fun findByUserIdAndMerchantId(userId: String, merchantId: String): MerchantFollow?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<MerchantFollow>

    fun deleteByUserIdAndMerchantId(userId: String, merchantId: String): Long

    fun countByMerchantId(merchantId: String): Long

    // Real broadcast fan-out query -- every real follower of one merchant, unpaged
    // since a broadcast must reach everyone, not just one page.
    fun findByMerchantId(merchantId: String): List<MerchantFollow>
}
