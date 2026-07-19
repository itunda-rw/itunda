package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.StockWatchlist

interface StockWatchlistRepository : JpaRepository<StockWatchlist, String> {
    fun findByUserIdAndStockId(userId: String, stockId: String): StockWatchlist?

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<StockWatchlist>

    fun deleteByUserIdAndStockId(userId: String, stockId: String): Long
}
