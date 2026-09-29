package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Ikimina
import rw.itunda.core.domain.IkiminaContribution
import rw.itunda.core.domain.IkiminaMember

interface IkiminaRepository : JpaRepository<Ikimina, String>

interface IkiminaMemberRepository : JpaRepository<IkiminaMember, String> {
    fun findByIkiminaId(ikiminaId: String): List<IkiminaMember>
    fun findByUserId(userId: String): List<IkiminaMember>
    fun findByIkiminaIdAndUserId(ikiminaId: String, userId: String): IkiminaMember?
    fun countByIkiminaId(ikiminaId: String): Long
    fun findByIkiminaIdAndPayoutOrder(ikiminaId: String, payoutOrder: Int): IkiminaMember?
    fun existsByIkiminaIdAndPayoutOrder(ikiminaId: String, payoutOrder: Int): Boolean
}

interface IkiminaContributionRepository : JpaRepository<IkiminaContribution, String> {
    fun findByIkiminaIdAndRound(ikiminaId: String, round: Int): List<IkiminaContribution>
    fun findByIkiminaIdAndMemberIdAndRound(ikiminaId: String, memberId: String, round: Int): IkiminaContribution?
}
