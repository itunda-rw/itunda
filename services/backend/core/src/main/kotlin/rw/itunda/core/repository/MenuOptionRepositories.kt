package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MenuOptionChoice
import rw.itunda.core.domain.MenuOptionGroup

/** See MenuOptionGroup.kt's own doc comment for the real gap this closes. */
interface MenuOptionGroupRepository : JpaRepository<MenuOptionGroup, String> {
    fun findByProductIdOrderByDisplayOrderAsc(productId: String): List<MenuOptionGroup>

    // Real batched read (2026-07-21) -- backs the buyer-facing menu browse enrichment
    // (ShoppingController.getMerchantProducts) where a restaurant's whole menu is shown
    // at once; one query per product here would be the same real N+1 shape this
    // project's own sweeps have already fixed elsewhere (PayrollService.runPayroll,
    // GroupMessagingService.createGroup).
    fun findByProductIdInOrderByDisplayOrderAsc(productIds: List<String>): List<MenuOptionGroup>

    fun deleteByProductId(productId: String)
}

interface MenuOptionChoiceRepository : JpaRepository<MenuOptionChoice, String> {
    fun findByGroupIdOrderByDisplayOrderAsc(groupId: String): List<MenuOptionChoice>

    // Real batched read (2026-07-21) -- same discipline as
    // MenuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc above.
    fun findByGroupIdInOrderByDisplayOrderAsc(groupIds: List<String>): List<MenuOptionChoice>

    fun deleteByGroupId(groupId: String)
}
