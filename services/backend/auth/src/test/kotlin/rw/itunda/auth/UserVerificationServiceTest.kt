package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * Split out of AuthServiceTest.kt (2026-08-21), following UserVerificationService.kt's
 * own extraction out of AuthService.kt -- see that class's own doc comment. Own,
 * smaller fixture (not the full AuthServiceTest.kt registration/login mock set) since
 * this class only ever needs 6 of AuthService's original 15 dependencies.
 */
class UserVerificationServiceTest : BehaviorSpec({

    val passwordEncoder = BCryptPasswordEncoder()

    Given("a fresh UserVerificationService") {
        val userRepository = mockk<UserRepository>()
        val emailVerificationTokenRepository = mockk<EmailVerificationTokenRepository>()
        every { emailVerificationTokenRepository.invalidateUnusedByUserId(any()) } returns 0
        val phoneVerificationTokenRepository = mockk<PhoneVerificationTokenRepository>()
        every { phoneVerificationTokenRepository.invalidateUnusedByUserId(any()) } returns 0
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val userVerificationService = UserVerificationService(
            userRepository, emailVerificationTokenRepository, phoneVerificationTokenRepository,
            notificationRepository, pushNotificationService, rateLimiter,
        )

        When("requesting email verification with no email on file") {
            val user = User(
                id = "user_7", phoneNumber = "+250788000009", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = null, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_7") } returns Optional.of(user)

            Then("it throws NoEmailOnFileException before creating any token or notification") {
                try {
                    userVerificationService.requestEmailVerification("user_7")
                    error("expected NoEmailOnFileException")
                } catch (e: NoEmailOnFileException) {
                    verify(exactly = 0) { emailVerificationTokenRepository.save(any()) }
                    verify(exactly = 0) { notificationRepository.save(any()) }
                }
            }
        }

        When("requesting email verification when it's already verified") {
            val user = User(
                id = "user_8", phoneNumber = "+250788000013", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean@itunda.rw", emailVerified = true, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_8") } returns Optional.of(user)

            Then("it throws EmailAlreadyVerifiedException") {
                try {
                    userVerificationService.requestEmailVerification("user_8")
                    error("expected EmailAlreadyVerifiedException")
                } catch (e: EmailAlreadyVerifiedException) {
                    verify(exactly = 0) { emailVerificationTokenRepository.save(any()) }
                }
            }
        }

        When("requesting email verification with a real, unverified email on file") {
            val user = User(
                id = "user_9", phoneNumber = "+250788000014", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean@itunda.rw", emailVerified = false, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_9") } returns Optional.of(user)
            val tokenSlot = mutableListOf<EmailVerificationToken>()
            every { emailVerificationTokenRepository.save(capture(tokenSlot)) } answers { firstArg() }
            val notificationSlot = mutableListOf<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

            userVerificationService.requestEmailVerification("user_9")

            Then("it saves a real single-use token and delivers it via a real in-app notification, never in this call's own return value") {
                tokenSlot.single().userId shouldBe "user_9"
                tokenSlot.single().usedAt shouldBe null
                val notification = notificationSlot.single()
                notification.userId shouldBe "user_9"
                notification.type shouldBe "PROFILE_EMAIL_VERIFICATION"
                passwordEncoder.matches(Regex("[a-f0-9]{32}").find(notification.body)!!.value, tokenSlot.single().token) shouldBe true
                verify(exactly = 1) { emailVerificationTokenRepository.invalidateUnusedByUserId("user_9") }
                verify(exactly = 1) { rateLimiter.checkLimit("auth:email-verify-request:user_9", 3, Duration.ofMinutes(15)) }
            }
        }

        When("an email-verification request is still inside its transaction") {
            val user = User(
                id = "user_after_commit", phoneNumber = "+250788000099", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean@itunda.rw", emailVerified = false, createdAt = Instant.now(),
            )
            every { userRepository.findById(user.id) } returns Optional.of(user)
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            TransactionSynchronizationManager.initSynchronization()
            try {
                userVerificationService.requestEmailVerification(user.id)

                Then("the token and durable notification exist, while the OTP push is withheld") {
                    verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
                    verify(exactly = 1) { notificationRepository.save(any()) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any()) }
                }

                Then("the OTP push is sent only after the transaction commits") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) { pushNotificationService.sendToUser(user.id, "Verify your email", any()) }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }

        When("confirming email verification with the real token just issued") {
            val user = User(
                id = "user_12", phoneNumber = "+250788000015", firstName = "Jean", lastName = "B",
                passwordHash = "unused", email = "jean12@itunda.rw", emailVerified = false, createdAt = Instant.now(),
            )
            val tokenRecord = EmailVerificationToken(
                id = "evt_1", userId = "user_12", token = passwordEncoder.encode("realtoken123"),
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_12") } returns tokenRecord
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById("user_12") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = userVerificationService.confirmEmailVerification("user_12", "realtoken123")

            Then("it flips emailVerified to true and marks the token used, once") {
                result.emailVerified shouldBe true
                tokenRecord.usedAt shouldNotBe null
                verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            }
        }

        When("confirming email verification with a token that belongs to a different user") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_2", userId = "user_other", token = passwordEncoder.encode("stolentoken"),
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_13") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- ownership is checked, not just token validity") {
                try {
                    userVerificationService.confirmEmailVerification("user_13", "stolentoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("confirming email verification with an expired token") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_3", userId = "user_14", token = passwordEncoder.encode("expiredtoken"),
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_14") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- a stale token can't verify an email") {
                try {
                    userVerificationService.confirmEmailVerification("user_14", "expiredtoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("confirming email verification with an already-used token") {
            val tokenRecord = EmailVerificationToken(
                id = "evt_4", userId = "user_15", token = passwordEncoder.encode("usedtoken"),
                expiresAt = Instant.now().plusSeconds(1800), usedAt = Instant.now().minusSeconds(60),
            )
            every { emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_15") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- a token verifies an email exactly once") {
                try {
                    userVerificationService.confirmEmailVerification("user_15", "usedtoken")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("requesting phone verification when it's already verified") {
            val user = User(
                id = "user_16", phoneNumber = "+250788000016", firstName = "Jean", lastName = "B",
                passwordHash = "unused", phoneVerified = true, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_16") } returns Optional.of(user)

            Then("it throws PhoneAlreadyVerifiedException before sending a new code") {
                try {
                    userVerificationService.requestPhoneVerification("user_16")
                    error("expected PhoneAlreadyVerifiedException")
                } catch (e: PhoneAlreadyVerifiedException) {
                    verify(exactly = 0) { phoneVerificationTokenRepository.save(any()) }
                }
            }
        }

        When("requesting a real resend of an unverified phone's code") {
            val user = User(
                id = "user_17", phoneNumber = "+250788000017", firstName = "Jean", lastName = "B",
                passwordHash = "unused", phoneVerified = false, createdAt = Instant.now(),
            )
            every { userRepository.findById("user_17") } returns Optional.of(user)
            val tokenSlot = mutableListOf<PhoneVerificationToken>()
            every { phoneVerificationTokenRepository.save(capture(tokenSlot)) } answers { firstArg() }
            val notificationSlot = mutableListOf<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

            userVerificationService.requestPhoneVerification("user_17")

            Then("it saves a real single-use 6-digit code and delivers it via a real in-app notification") {
                tokenSlot.single().userId shouldBe "user_17"
                tokenSlot.single().token.startsWith("\$2") shouldBe true
                verify(exactly = 1) { phoneVerificationTokenRepository.invalidateUnusedByUserId("user_17") }
                verify(exactly = 1) { rateLimiter.checkLimit("auth:phone-verify-request:user_17", 3, Duration.ofMinutes(15)) }
                val notification = notificationSlot.single()
                notification.userId shouldBe "user_17"
                notification.type shouldBe "PHONE_VERIFICATION"
                val deliveredCode = Regex("\\d{6}").find(notification.body)!!.value
                passwordEncoder.matches(deliveredCode, tokenSlot.single().token) shouldBe true
            }
        }

        When("confirming phone verification with the real code just issued") {
            val user = User(
                id = "user_18", phoneNumber = "+250788000018", firstName = "Jean", lastName = "B",
                passwordHash = "unused", phoneVerified = false, createdAt = Instant.now(),
            )
            val tokenRecord = PhoneVerificationToken(
                id = "pvt_1", userId = "user_18", token = passwordEncoder.encode("654321"),
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { phoneVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_18") } returns tokenRecord
            every { phoneVerificationTokenRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById("user_18") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = userVerificationService.confirmPhoneVerification("user_18", "654321")

            Then("it flips phoneVerified to true and marks the code used, once") {
                result.phoneVerified shouldBe true
                tokenRecord.usedAt shouldNotBe null
                verify(exactly = 1) { phoneVerificationTokenRepository.save(any()) }
                verify(exactly = 1) { rateLimiter.checkLimit("auth:phone-verify:user_18", 5, Duration.ofMinutes(15)) }
            }
        }

        When("confirming phone verification with a code that belongs to a different user") {
            val tokenRecord = PhoneVerificationToken(
                id = "pvt_2", userId = "user_other", token = passwordEncoder.encode("111111"),
                expiresAt = Instant.now().plusSeconds(1800),
            )
            every { phoneVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_19") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- ownership is checked, not just code validity") {
                try {
                    userVerificationService.confirmPhoneVerification("user_19", "111111")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }

        When("confirming phone verification with an expired code") {
            val tokenRecord = PhoneVerificationToken(
                id = "pvt_3", userId = "user_20", token = passwordEncoder.encode("222222"),
                expiresAt = Instant.now().minusSeconds(60),
            )
            every { phoneVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc("user_20") } returns tokenRecord

            Then("it throws InvalidVerificationTokenException -- a stale code can't verify a phone number") {
                try {
                    userVerificationService.confirmPhoneVerification("user_20", "222222")
                    error("expected InvalidVerificationTokenException")
                } catch (e: InvalidVerificationTokenException) {
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
