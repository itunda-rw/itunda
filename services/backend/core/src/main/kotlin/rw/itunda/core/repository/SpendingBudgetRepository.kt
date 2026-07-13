package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SpendingBudget

interface SpendingBudgetRepository : JpaRepository<SpendingBudget, String> {
    fun findByUserIdAndMonth(userId: String, month: String): List<SpendingBudget>
}
