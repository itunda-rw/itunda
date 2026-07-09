package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Mirrors backend/src/types/index.ts Contact. */
@Entity
@Table(name = "contacts")
class Contact(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var bank: String,

    @Column(nullable = false)
    var acc: String,

    @Column(name = "phone_number", nullable = false, length = 32)
    var phoneNumber: String,

    @Column(nullable = false, length = 16)
    var color: String,

    @Column(nullable = false, length = 4)
    var letter: String,
) {
    protected constructor() : this(id = "", userId = "", name = "", bank = "", acc = "", phoneNumber = "", color = "", letter = "")
}
