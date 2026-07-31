package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real 당근모임 (Karrot Meetups) attendance check-in -- sourced from Karrot's own real,
 * published feature (about.daangn.com's own PR archive): a regular meetup member can
 * check in to a real scheduled `MeetupSession`, distinct from having merely joined the
 * meetup's group chat (`GroupConversationMember`) -- membership is "I'm part of this
 * group," attendance is "I actually showed up to this specific session," a real,
 * separate fact Karrot's own feature exists to track.
 *
 * A real unique constraint on (`sessionId`, `userId`) at the DB layer, not just an
 * application-level check, backs the same "can't double-check-in" guarantee every other
 * once-only action in this codebase already enforces this way.
 */
@Entity
@Table(name = "meetup_attendances")
class MeetupAttendance(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "session_id", nullable = false, length = 64)
    val sessionId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "checked_in_at", nullable = false)
    val checkedInAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", sessionId = "", userId = "")
}
