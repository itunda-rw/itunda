package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MeetupAttendance
import rw.itunda.core.domain.MeetupSession

interface MeetupSessionRepository : JpaRepository<MeetupSession, String> {
    fun findByPostIdOrderBySequenceAsc(postId: String): List<MeetupSession>
    fun countByPostId(postId: String): Int
}

interface MeetupAttendanceRepository : JpaRepository<MeetupAttendance, String> {
    fun findBySessionIdAndUserId(sessionId: String, userId: String): MeetupAttendance?
    fun findBySessionId(sessionId: String): List<MeetupAttendance>
}
