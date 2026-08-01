package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.UssdPin

interface UssdPinRepository : JpaRepository<UssdPin, String> {
    fun findByUserId(userId: String): UssdPin?
}
