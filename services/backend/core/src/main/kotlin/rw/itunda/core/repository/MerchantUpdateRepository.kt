package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantUpdate
import rw.itunda.core.domain.MerchantUpdateLike

interface MerchantUpdateRepository : JpaRepository<MerchantUpdate, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<MerchantUpdate>
}

interface MerchantUpdateLikeRepository : JpaRepository<MerchantUpdateLike, String> {
    fun findByUpdateIdAndUserId(updateId: String, userId: String): MerchantUpdateLike?
}
