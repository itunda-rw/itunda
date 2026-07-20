package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MenuOptionChoice
import rw.itunda.core.domain.MenuOptionGroup
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class InvalidMenuOptionGroupException(message: String) : RuntimeException(message)
class MenuOptionGroupNotFoundException(message: String) : RuntimeException(message)

data class MenuOptionChoiceRequest(val name: String, val priceDelta: BigDecimal = BigDecimal.ZERO)
data class MenuOptionGroupView(val group: MenuOptionGroup, val choices: List<MenuOptionChoice>)

/**
 * Real menu-item option groups -- the owner-management half of
 * docs/DESIGN_REFERENCES.md's Eats recommendation #3. See MenuOptionGroup.kt's own doc
 * comment for the full, honestly-scoped v1 account (required + single-select only).
 * Mirrors `MerchantProductService`'s own ownership-check + rate-limit discipline exactly
 * -- a real restaurant owner manages their own product's option groups, nothing else.
 */
@Service
class MenuOptionService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        private const val MAX_CHOICES_PER_GROUP = 10
        private const val MAX_GROUPS_PER_PRODUCT = 10
    }

    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    private fun getOwnedProduct(ownerUserId: String, productId: String): String {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        return product.id
    }

    /** Creates a whole required, single-select group + its choices in one real call --
     * a real Coupang Eats-style required option group is meaningless with zero choices,
     * so this deliberately doesn't offer a separate "add empty group then add choices
     * one at a time" API that could leave a half-built, unusable group visible to buyers
     * in between calls. */
    @Transactional
    fun addOptionGroup(ownerUserId: String, productId: String, name: String, choices: List<MenuOptionChoiceRequest>): MenuOptionGroupView {
        rateLimiter.checkLimit("merchant:menu-option-group:$ownerUserId", limit = 30, window = Duration.ofHours(1))
        val ownedProductId = getOwnedProduct(ownerUserId, productId)

        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 100) {
            throw InvalidMenuOptionGroupException("Option group name must be between 1 and 100 characters")
        }
        if (choices.isEmpty() || choices.size > MAX_CHOICES_PER_GROUP) {
            throw InvalidMenuOptionGroupException("An option group needs between 1 and $MAX_CHOICES_PER_GROUP choices")
        }
        val existingGroups = menuOptionGroupRepository.findByProductIdOrderByDisplayOrderAsc(ownedProductId)
        if (existingGroups.size >= MAX_GROUPS_PER_PRODUCT) {
            throw InvalidMenuOptionGroupException("A menu item can have at most $MAX_GROUPS_PER_PRODUCT option groups")
        }
        val trimmedChoices = choices.map {
            val choiceName = it.name.trim()
            if (choiceName.isEmpty() || choiceName.length > 100) {
                throw InvalidMenuOptionGroupException("Choice name must be between 1 and 100 characters")
            }
            // Real Coupang Eats seller-guide constraint (see MenuOptionChoice.kt's own doc
            // comment): a delta can be zero, never negative -- a negative delta would let
            // a "choice" silently undercut the base list price, the exact price-mismatch
            // this whole feature exists to prevent.
            if (it.priceDelta < BigDecimal.ZERO) {
                throw InvalidMenuOptionGroupException("Choice price delta cannot be negative")
            }
            choiceName to it.priceDelta
        }

        val group = menuOptionGroupRepository.save(
            MenuOptionGroup(
                id = "menu_option_group_${UUID.randomUUID()}", productId = ownedProductId, name = trimmedName,
                displayOrder = existingGroups.size,
            ),
        )
        val savedChoices = menuOptionChoiceRepository.saveAll(
            trimmedChoices.mapIndexed { index, (choiceName, priceDelta) ->
                MenuOptionChoice(
                    id = "menu_option_choice_${UUID.randomUUID()}", groupId = group.id, name = choiceName,
                    priceDelta = priceDelta, displayOrder = index,
                )
            },
        )
        return MenuOptionGroupView(group, savedChoices)
    }

    fun getOptionGroups(productId: String): List<MenuOptionGroupView> {
        val groups = menuOptionGroupRepository.findByProductIdOrderByDisplayOrderAsc(productId)
        if (groups.isEmpty()) return emptyList()
        val choicesByGroup = menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(groups.map { it.id }).groupBy { it.groupId }
        return groups.map { MenuOptionGroupView(it, choicesByGroup[it.id] ?: emptyList()) }
    }

    @Transactional
    fun removeOptionGroup(ownerUserId: String, productId: String, groupId: String) {
        val ownedProductId = getOwnedProduct(ownerUserId, productId)
        val group = menuOptionGroupRepository.findById(groupId)
            .orElseThrow { MenuOptionGroupNotFoundException("Option group not found") }
        if (group.productId != ownedProductId) {
            throw MenuOptionGroupNotFoundException("Option group not found")
        }
        menuOptionChoiceRepository.deleteByGroupId(groupId)
        menuOptionGroupRepository.delete(group)
    }
}
