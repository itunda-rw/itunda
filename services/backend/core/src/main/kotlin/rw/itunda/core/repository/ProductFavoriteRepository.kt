package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ProductFavorite

interface ProductFavoriteRepository : JpaRepository<ProductFavorite, String> {
    fun findByUserIdAndProductId(userId: String, productId: String): ProductFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<ProductFavorite>

    fun deleteByUserIdAndProductId(userId: String, productId: String): Long
}
