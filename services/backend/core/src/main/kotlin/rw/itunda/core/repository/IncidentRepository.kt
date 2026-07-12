package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Incident
import rw.itunda.core.domain.IncidentStatus

interface IncidentRepository : JpaRepository<Incident, String> {
    fun findByRailIdAndStatus(railId: String, status: IncidentStatus): Incident?
    fun findAllByOrderByOpenedAtDesc(): List<Incident>
}
