package rw.itunda.system

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudFlagDecision
import rw.itunda.core.repository.FraudFlagRepository
import java.time.Instant

class FraudFlagNotFoundException(message: String) : RuntimeException(message)
class FraudFlagAlreadyReviewedException(message: String) : RuntimeException(message)

@Service
class FraudReviewService(private val fraudFlagRepository: FraudFlagRepository) {

    fun getQueue(pageable: Pageable): Page<FraudFlag> = fraudFlagRepository.findByReviewedFalseOrderByCreatedAtAsc(pageable)

    @Transactional
    fun decide(flagId: String, reviewerId: String, decision: FraudFlagDecision): FraudFlag {
        val flag = fraudFlagRepository.findById(flagId).orElseThrow { FraudFlagNotFoundException("Fraud flag not found") }
        if (flag.reviewed) {
            throw FraudFlagAlreadyReviewedException("This flag has already been reviewed")
        }
        flag.reviewed = true
        flag.decision = decision
        flag.reviewedBy = reviewerId
        flag.reviewedAt = Instant.now()
        return fraudFlagRepository.save(flag)
    }
}
