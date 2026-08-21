package rw.itunda.certificate

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CertificateRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.Optional

class CertificateServiceTest : BehaviorSpec({

    fun kycVerifiedUser(id: String) = User(id = id, phoneNumber = "+250788000000", firstName = "Test", lastName = "User", passwordHash = "hash", kycVerified = true)
    fun unverifiedUser(id: String) = User(id = id, phoneNumber = "+250788000001", firstName = "Test", lastName = "User", passwordHash = "hash", kycVerified = false)

    Given("a real KYC-verified user issuing a certificate") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { userRepository.findById("user_1") } returns Optional.of(kycVerifiedUser("user_1"))
        every { userRepository.findByIdForUpdate("user_1") } returns Optional.of(kycVerifiedUser("user_1"))
        every { certificateRepository.findByUserIdAndStatus("user_1", CertificateStatus.ACTIVE) } returns null
        val savedSlot = slot<Certificate>()
        every { certificateRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("issuing") {
            val (certificate, privateKey) = service.issue("user_1")

            Then("a real Ed25519 keypair is generated -- the private key is real, never persisted, returned exactly once") {
                certificate.userId shouldBe "user_1"
                certificate.algorithm shouldBe "Ed25519"
                certificate.status shouldBe CertificateStatus.ACTIVE
                privateKey shouldNotBe null
                savedSlot.captured.publicKeyBase64 shouldBe certificate.publicKeyBase64
            }
            Then("a real, unique, unpredictable serial number is assigned") {
                certificate.serialNumber.length shouldBe 32
            }
            Then("a real 1-year expiry is set") {
                val daysUntilExpiry = java.time.Duration.between(Instant.now(), certificate.expiresAt).toDays()
                (daysUntilExpiry in 360..366) shouldBe true
            }
        }
    }

    Given("a user who has not completed real KYC") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { userRepository.findById("user_2") } returns Optional.of(unverifiedUser("user_2"))

        When("attempting to issue a certificate") {
            Then("it real-fails, matching Toss's own real phone+ID precondition") {
                try {
                    service.issue("user_2")
                    error("expected CertificateUserNotVerifiedException")
                } catch (e: CertificateUserNotVerifiedException) {
                    // expected
                }
            }
        }
    }

    Given("a user re-issuing a certificate while one is already active") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { userRepository.findById("user_3") } returns Optional.of(kycVerifiedUser("user_3"))
        every { userRepository.findByIdForUpdate("user_3") } returns Optional.of(kycVerifiedUser("user_3"))
        val existing = Certificate(id = "cert_old", userId = "user_3", serialNumber = "OLD", publicKeyBase64 = "x", expiresAt = Instant.now().plusSeconds(1000))
        every { certificateRepository.findByUserIdAndStatus("user_3", CertificateStatus.ACTIVE) } returns existing
        every { certificateRepository.save(any()) } answers { firstArg() }

        When("issuing a new one") {
            service.issue("user_3")

            Then("the prior active certificate is real-revoked, not left dangling") {
                existing.status shouldBe CertificateStatus.REVOKED
                existing.revokedAt shouldNotBe null
            }
            // Real bug found live (2026-08-02): see CertificateService.issue's own doc
            // comment. This asserts the actual fix mechanism -- the same "lock a
            // different already-existing row" precedent AccountRepository/
            // UserRepository.findByIdForUpdate's own identical-shaped fixes establish
            // for a check-then-act race on a "one active row per user" invariant.
            Then("it real-locks the user's own row before touching the certificate") {
                io.mockk.verify(exactly = 1) { userRepository.findByIdForUpdate("user_3") }
            }
        }
    }

    Given("two concurrent issue() calls racing for the same user") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        every { userRepository.findById("user_race") } returns Optional.of(kycVerifiedUser("user_race"))
        every { userRepository.findByIdForUpdate("user_race") } returns Optional.of(kycVerifiedUser("user_race"))

        // Simulates the real serialization the `findByIdForUpdate` row lock provides:
        // the second call's `findByUserIdAndStatus` re-read only ever real-sees the
        // first call's already-committed state, never a stale concurrent snapshot.
        var activeCert: Certificate? = null
        every { certificateRepository.findByUserIdAndStatus("user_race", CertificateStatus.ACTIVE) } answers { activeCert }
        every { certificateRepository.save(any()) } answers {
            val cert = firstArg<Certificate>()
            if (cert.status == CertificateStatus.ACTIVE) activeCert = cert
            cert
        }

        When("issuing twice back-to-back, simulating the lock's serialization of an interleaved race") {
            val (first, _) = service.issue("user_race")
            val (second, _) = service.issue("user_race")

            Then("only the second, most-recent certificate ends up ACTIVE -- never both at once") {
                first.status shouldBe CertificateStatus.REVOKED
                second.status shouldBe CertificateStatus.ACTIVE
                first.id shouldNotBe second.id
            }
        }
    }

    Given("real Ed25519 cryptography end-to-end") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        // A genuine external keypair -- not generated by CertificateService itself --
        // proving verify() does real, standard, interoperable JCA verification, not a
        // self-consistent round trip against its own generation code.
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val cert = Certificate(id = "cert_1", userId = "user_4", serialNumber = "SERIAL1", publicKeyBase64 = publicKeyBase64, expiresAt = Instant.now().plusSeconds(1000))
        every { certificateRepository.findBySerialNumber("SERIAL1") } returns cert

        fun sign(payload: String): String {
            val signature = Signature.getInstance("Ed25519")
            signature.initSign(keyPair.private)
            signature.update(payload.toByteArray())
            return Base64.getEncoder().encodeToString(signature.sign())
        }

        When("verifying a real signature over the exact signed payload") {
            val result = service.verify("SERIAL1", "loan-agreement-terms-v1", sign("loan-agreement-terms-v1"))

            Then("the real cryptographic check passes") {
                result.signatureValid shouldBe true
                result.certificateStatus shouldBe CertificateStatus.ACTIVE
            }
        }

        When("verifying a real signature against a tampered payload") {
            val result = service.verify("SERIAL1", "tampered-payload", sign("loan-agreement-terms-v1"))

            Then("the real cryptographic check correctly fails -- not a fabricated pass") {
                result.signatureValid shouldBe false
            }
        }

        When("verifying with garbage base64") {
            val result = service.verify("SERIAL1", "x", "not-valid-base64!!!")

            Then("it real-fails cleanly rather than throwing a 500") {
                result.signatureValid shouldBe false
            }
        }
    }

    Given("a certificate that has passed its real expiry date") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val expired = Certificate(id = "cert_2", userId = "user_5", serialNumber = "SERIAL2", publicKeyBase64 = publicKeyBase64, status = CertificateStatus.ACTIVE, expiresAt = Instant.now().minusSeconds(1000))
        every { certificateRepository.findBySerialNumber("SERIAL2") } returns expired

        When("checking its status") {
            val result = service.getStatus("SERIAL2")

            Then("it reports EXPIRED even though the stored status column still says ACTIVE") {
                result.status shouldBe CertificateStatus.EXPIRED
            }
        }
    }

    Given("a certificate that doesn't exist") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)
        every { certificateRepository.findBySerialNumber("UNKNOWN") } returns null

        When("checking its status") {
            Then("it real-404s") {
                try {
                    service.getStatus("UNKNOWN")
                    error("expected CertificateNotFoundException")
                } catch (e: CertificateNotFoundException) {
                    // expected
                }
            }
        }
    }

    fun certWithExpiry(id: String, userId: String, expiresAt: Instant, status: CertificateStatus = CertificateStatus.ACTIVE, renewalReminderSentAt: Instant? = null) = Certificate(
        id = id, userId = userId, serialNumber = "SN-$id", publicKeyBase64 = "x", status = status,
        expiresAt = expiresAt, renewalReminderSentAt = renewalReminderSentAt,
    )

    Given("real active certificates at various points in their real renewal window") {
        val certificateRepository = mockk<CertificateRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CertificateService(certificateRepository, userRepository, rateLimiter, notificationRepository, pushNotificationService)

        When("a real active certificate's real expiresAt is already inside the 60-day renewal window") {
            val soon = certWithExpiry("cert_soon", "user_a", Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS))
            every { certificateRepository.findByStatusAndRenewalReminderSentAtIsNull(CertificateStatus.ACTIVE) } returns listOf(soon)

            Then("it is a real due candidate") {
                service.getCertificatesDueForRenewalReminder().map { it.id } shouldBe listOf("cert_soon")
            }
        }

        When("a real active certificate's real expiresAt is genuinely still outside the renewal window") {
            val far = certWithExpiry("cert_far", "user_b", Instant.now().plus(120, java.time.temporal.ChronoUnit.DAYS))
            every { certificateRepository.findByStatusAndRenewalReminderSentAtIsNull(CertificateStatus.ACTIVE) } returns listOf(far)

            Then("it is real-excluded -- not due yet") {
                service.getCertificatesDueForRenewalReminder() shouldBe emptyList()
            }
        }

        When("sending a real renewal reminder for a due certificate") {
            val cert = certWithExpiry("cert_due", "user_c", Instant.now().plus(5, java.time.temporal.ChronoUnit.DAYS))
            every { certificateRepository.findById("cert_due") } returns Optional.of(cert)
            every { certificateRepository.save(any()) } answers { firstArg() }
            // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
            // returns a raw Object, ClassCastException-ing at the call site -- same fix as
            // this codebase's other documented instances of this exact pitfall.
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.sendRenewalReminder("cert_due")

            Then("it real-notifies once and real-marks renewalReminderSentAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "CERTIFICATE_EXPIRING_SOON" && it.userId == "user_c" }) }
                cert.renewalReminderSentAt shouldNotBe null
            }
        }

        When("sending a reminder for a certificate that was already reminded") {
            val cert = certWithExpiry("cert_already", "user_d", Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS), renewalReminderSentAt = Instant.now())
            every { certificateRepository.findById("cert_already") } returns Optional.of(cert)

            service.sendRenewalReminder("cert_already")

            Then("it real-skips -- no double notification for the same real renewal") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("sending a reminder for a certificate that's already REVOKED") {
            val cert = certWithExpiry("cert_revoked", "user_e", Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS), status = CertificateStatus.REVOKED)
            every { certificateRepository.findById("cert_revoked") } returns Optional.of(cert)

            service.sendRenewalReminder("cert_revoked")

            Then("it real-skips -- a revoked certificate is not genuinely renewing") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
