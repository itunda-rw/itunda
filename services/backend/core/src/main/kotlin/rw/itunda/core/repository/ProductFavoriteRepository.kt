package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ProductFavorite

interface ProductFavoriteRepository : JpaRepository<ProductFavorite, String> {
    fun findByUserIdAndProductId(userId: String, productId: String): ProductFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<ProductFavorite>

    fun deleteByUserIdAndProductId(userId: String, productId: String): Long

    // Real Coupang/Naver Shopping 재입고 알림 (restock notification) support -- see
    // MerchantProductService.setSoldOut's own doc comment. Finds every real wishlist
    // row for a given product so a merchant un-marking it sold-out can fan out a real
    // push to everyone who favorited it, not just the one caller who happened to be
    // looking at it at that moment.
    fun findByProductId(productId: String): List<ProductFavorite>
}
