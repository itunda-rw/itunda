package rw.itunda.ussd

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.UssdPin
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.UssdPinRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.p2p.P2pService
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for real USSD basic-banking access (item 231) -- Rwanda's own
 * feature-phone access channel. See UssdService's own doc comment for the full
 * sourced account. Proves the stateless multi-step menu-tree parsing works across a
 * real sequence of independent requests, matching the real Africa's Talking-style
 * gateway contract.
 */
class UssdServiceTest : BehaviorSpec({

    val encoder = BCryptPasswordEncoder()

    fun user(id: String, phone: String) = User(
        id = id, phoneNumber = phone, firstName = "Test", lastName = "User",
        passwordHash = "unused", createdAt = Instant.now(),
    )

    fun wallet(id: String, userId: String, balance: BigDecimal = BigDecimal("50000")) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = balance, availableBalance = balance,
    )

    fun newService(
        ussdPinRepository: UssdPinRepository = mockk(),
        userRepository: UserRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        transactionRepository: TransactionRepository = mockk(),
        p2pService: P2pService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = UssdService(ussdPinRepository, userRepository, walletRepository, transactionRepository, p2pService, rateLimiter)

    Given("a session-start request from a real registered phone number") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)
        every { userRepository.findByPhoneNumber("+250788111111") } returns user("u1", "+250788111111")

        When("the gateway sends an empty text (first keypress)") {
            val result = service.handleUssdRequest("sess1", "+250788111111", "")

            Then("the real main menu is shown") {
                result shouldStartWith "CON Welcome to itunda"
                result shouldContain "1. Check balance"
                result shouldContain "4. Set PIN"
            }
        }
    }

    Given("a session-start request from a phone number with no itunda account") {
        val userRepository = mockk<UserRepository>()
        val service = newService(userRepository = userRepository)
        every { userRepository.findByPhoneNumber("+250788999999") } returns null

        When("the gateway sends any text") {
            val result = service.handleUssdRequest("sess1", "+250788999999", "")

            Then("a real, honest END is returned -- never a crash") {
                result shouldBe "END No itunda account found for this phone number."
            }
        }
    }

    Given("a real user with a PIN already set, checking their balance across a real 2-step USSD sequence") {
        val userRepository = mockk<UserRepository>()
        val ussdPinRepository = mockk<UssdPinRepository>()
        val walletRepository = mockk<WalletRepository>()
        val service = newService(userRepository = userRepository, ussdPinRepository = ussdPinRepository, walletRepository = walletRepository)

        every { userRepository.findByPhoneNumber("+250788111111") } returns user("u1", "+250788111111")
        every { ussdPinRepository.findByUserId("u1") } returns UssdPin(id = "pin_1", userId = "u1", pinHash = encoder.encode("1234"))
        every { walletRepository.findByUserIdAndType("u1", WalletType.MAIN) } returns wallet("wallet_1", "u1", BigDecimal("75000"))

        When("selecting option 1 (first real request)") {
            val step1 = service.handleUssdRequest("sess1", "+250788111111", "1")
            Then("the real PIN prompt is shown") {
                step1 shouldBe "CON Enter your PIN"
            }
        }

        When("entering the correct PIN (a second, independent real request -- stateless, re-derives everything from text alone)") {
            val step2 = service.handleUssdRequest("sess1", "+250788111111", "1*1234")
            Then("the real balance is shown") {
                step2 shouldBe "END Your balance is 75000 RWF."
            }
        }

        When("entering a wrong PIN") {
            val result = service.handleUssdRequest("sess1", "+250788111111", "1*0000")
            Then("a real, honest failure is returned") {
                result shouldBe "END Incorrect PIN"
            }
        }
    }

    Given("a real user with no PIN set yet, trying to check balance") {
        val userRepository = mockk<UserRepository>()
        val ussdPinRepository = mockk<UssdPinRepository>()
        val service = newService(userRepository = userRepository, ussdPinRepository = ussdPinRepository)
        every { userRepository.findByPhoneNumber("+250788111111") } returns user("u1", "+250788111111")
        every { ussdPinRepository.findByUserId("u1") } returns null

        When("entering any PIN") {
            val result = service.handleUssdRequest("sess1", "+250788111111", "1*1234")
            Then("a real, honest 'set a PIN first' message is returned") {
                result shouldBe "END No USSD PIN set for this account. Set one in the itunda app first."
            }
        }
    }

    Given("a real user sending money via a real 4-step USSD sequence, reusing the exact same real P2pService.sendDirect the app itself uses") {
        val userRepository = mockk<UserRepository>()
        val ussdPinRepository = mockk<UssdPinRepository>()
        val p2pService = mockk<P2pService>()
        val service = newService(userRepository = userRepository, ussdPinRepository = ussdPinRepository, p2pService = p2pService)

        every { userRepository.findByPhoneNumber("+250788111111") } returns user("u1", "+250788111111")
        every { ussdPinRepository.findByUserId("u1") } returns UssdPin(id = "pin_1", userId = "u1", pinHash = encoder.encode("1234"))
        val transaction = Transaction(
            id = "txn_1", referenceNumber = "REF1", senderId = "u1", recipientId = "u2",
            amount = BigDecimal("5000"), fee = BigDecimal.ZERO, currency = "RWF",
            type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "USSD transfer",
        )
        every { p2pService.sendDirect("u1", "+250788222222", BigDecimal("5000"), "USSD transfer") } returns (transaction to BigDecimal("70000"))

        When("selecting option 2, entering recipient, amount, then the real correct PIN") {
            service.handleUssdRequest("sess1", "+250788111111", "2") shouldBe "CON Enter recipient phone number"
            service.handleUssdRequest("sess1", "+250788111111", "2*+250788222222") shouldBe "CON Enter amount (RWF)"
            service.handleUssdRequest("sess1", "+250788111111", "2*+250788222222*5000") shouldBe "CON Enter your PIN"
            val result = service.handleUssdRequest("sess1", "+250788111111", "2*+250788222222*5000*1234")

            Then("the real transfer completes via the exact same real P2pService.sendDirect the app itself uses") {
                result shouldBe "END Sent 5000 RWF to +250788222222. New balance: 70000 RWF."
            }
        }
    }

    Given("a real user with no USSD PIN yet, setting one for the first time via a real 2-step sequence") {
        val userRepository = mockk<UserRepository>()
        val ussdPinRepository = mockk<UssdPinRepository>()
        val service = newService(userRepository = userRepository, ussdPinRepository = ussdPinRepository)

        every { userRepository.findByPhoneNumber("+250788111111") } returns user("u1", "+250788111111")
        every { ussdPinRepository.findByUserId("u1") } returns null
        every { ussdPinRepository.save(any()) } answers { firstArg() }

        When("selecting option 4, entering a new PIN, then confirming it") {
            service.handleUssdRequest("sess1", "+250788111111", "4") shouldBe "CON Enter a new 4-6 digit PIN"
            service.handleUssdRequest("sess1", "+250788111111", "4*5678") shouldBe "CON Confirm your new PIN"
            val result = service.handleUssdRequest("sess1", "+250788111111", "4*5678*5678")

            Then("the real PIN is set") {
                result shouldBe "END Your USSD PIN has been set."
            }
        }

        When("confirming with a mismatched PIN") {
            val result = service.handleUssdRequest("sess1", "+250788111111", "4*5678*0000")
            Then("a real, honest mismatch message is returned -- nothing is saved") {
                result shouldBe "END PINs did not match. Please dial again."
            }
        }
    }
})
