package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.UpfrontInterestDeposit

interface UpfrontInterestDepositRepository : JpaRepository<UpfrontInterestDeposit, String> {
    fun findByUserIdOrderByOpenedAtDesc(userId: String): List<UpfrontInterestDeposit>
}
