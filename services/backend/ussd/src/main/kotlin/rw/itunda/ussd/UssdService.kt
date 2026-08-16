package rw.itunda.ussd

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.UssdPin
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.UssdPinRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.merchant.MerchantNoWalletException
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantService
import rw.itunda.merchant.PaymentIntentNotFoundException
import rw.itunda.merchant.PaymentIntentNotPayableException
import rw.itunda.merchant.SelfPaymentException
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoWalletException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.P2pService
import java.math.BigDecimal
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.UUID

class UssdPinNotSetException(message: String) : RuntimeException(message)
class UssdInvalidPinException(message: String) : RuntimeException(message)

/**
 * Real USSD basic-banking access (item 231) -- see `UssdPin.kt`'s own doc comment for
 * the full sourced account. Reuses `P2pService.sendDirect` directly for real send-money
 * rather than reimplementing money movement -- the same real, already-proven
 * wallet-to-wallet transfer logic every other client uses, just from a different real
 * entry point.
 *
 * Real, standard East African USSD gateway contract (Africa's Talking-style, the real
 * regional standard): one `POST` per keypress with `sessionId`/`phoneNumber`/`text`
 * (the FULL accumulated input across the whole session, e.g. `"2*+250788*5000*1234"`),
 * responding with `CON <menu>` (session continues) or `END <message>` (session
 * terminates). Deliberately STATELESS -- no server-side session table. The entire
 * conversation is reconstructed by re-parsing `text` fresh on every single request
 * (split on `*`), which is why every branch below re-derives its own state (e.g.
 * whether a PIN already exists) from real, current data rather than trusting anything
 * cached from an earlier step -- there IS no earlier step to trust, only what a fresh
 * read of `text` and the database says right now.
 *
 * Honest scope, named explicitly (same class as NIDA/real-PSP elsewhere in this
 * backend): the real telco short-code + gateway partnership (an actual `*123#`-style
 * dial connecting a real subscriber to this webhook) requires an external MNO/gateway
 * relationship this self-hosted repo has no path to obtain. This webhook itself, the
 * real menu state machine, real PIN authentication, and real money movement are not
 * simulated -- only the telco connection in front of them is missing.
 */
@Service
class UssdService(
    private val ussdPinRepository: UssdPinRepository,
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val p2pService: P2pService,
    private val rateLimiter: RateLimiter,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val merchantService: MerchantService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.of("Africa/Kigali"))

    @Transactional
    fun setPin(userId: String, pin: String): UssdPin {
        rateLimiter.checkLimit("ussd:pin:set:$userId", limit = 10, window = Duration.ofHours(1))
        validatePinFormat(pin)
        val existing = ussdPinRepository.findByUserId(userId)
        if (existing != null) {
            existing.pinHash = passwordEncoder.encode(pin)
            existing.updatedAt = java.time.Instant.now()
            return ussdPinRepository.save(existing)
        }
        return ussdPinRepository.save(UssdPin(id = "ussdpin_${UUID.randomUUID()}", userId = userId, pinHash = passwordEncoder.encode(pin)))
    }

    private fun validatePinFormat(pin: String) {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            throw UssdInvalidPinException("PIN must be 4-6 digits")
        }
    }

    /** Real-throws on a wrong/missing PIN rather than returning a boolean -- every
     * caller needs a distinct, honest USSD message for "no PIN set yet" vs "wrong
     * PIN", not a single generic failure. Real per-user rate limit on VERIFICATION
     * attempts specifically (distinct from, and tighter than, general USSD session
     * volume) -- a 4-6 digit PIN has at most 1,000,000 real combinations, genuinely
     * brute-forceable without a real lockout, the same "an unauthenticated endpoint
     * still needs real abuse protection" lesson this session already found and fixed
     * twice (AffiliateController.resolveLink, ScamReportService.checkScamStatus). */
    private fun verifyPin(userId: String, pin: String) {
        rateLimiter.checkLimit("ussd:pin:verify:$userId", limit = 5, window = Duration.ofHours(1))
        val record = ussdPinRepository.findByUserId(userId)
            ?: throw UssdPinNotSetException("No USSD PIN set for this account. Set one in the itunda app first.")
        if (!passwordEncoder.matches(pin, record.pinHash)) {
            throw UssdInvalidPinException("Incorrect PIN")
        }
    }

    fun handleUssdRequest(sessionId: String, phoneNumber: String, text: String): String {
        rateLimiter.checkLimit("ussd:session:$phoneNumber", limit = 60, window = Duration.ofHours(1))
        val parts = if (text.isBlank()) emptyList() else text.split("*")
        val user = userRepository.findByPhoneNumber(phoneNumber.trim())
            ?: return "END No itunda account found for this phone number."

        if (parts.isEmpty()) {
            return "CON Welcome to itunda\n1. Check balance\n2. Send money\n3. Mini statement\n4. Set PIN\n5. Pay a merchant"
        }
        return try {
            when (parts[0]) {
                "1" -> handleCheckBalance(user.id, parts)
                "2" -> handleSendMoney(user.id, parts)
                "3" -> handleMiniStatement(user.id, parts)
                "4" -> handleSetPinFlow(user.id, parts)
                "5" -> handlePayMerchant(user.id, parts)
                else -> "END Invalid selection. Please dial again."
            }
        } catch (e: UssdPinNotSetException) {
            "END ${e.message}"
        } catch (e: UssdInvalidPinException) {
            "END ${e.message}"
        }
    }

    // Real Toss Payments ARS결제-style USSD payment completion -- see
    // PaymentIntent.ussdCode's own doc comment. Reuses MerchantService.collect
    // directly, the exact same real payment-collection logic every other channel (QR
    // scan, Face Pay, static QR) already uses -- this is purely a new real entry point
    // into it, not a second money-movement implementation.
    private fun handlePayMerchant(userId: String, parts: List<String>): String {
        return when (parts.size) {
            1 -> "CON Enter the payment code given to you"
            2 -> "CON Enter your PIN"
            else -> {
                val ussdCode = parts[1].trim()
                val pin = parts[2]
                verifyPin(userId, pin)
                val intent = paymentIntentRepository.findByUssdCode(ussdCode)
                    ?: return "END Payment code not found."
                try {
                    val result = merchantService.collect(userId, intent.id, channel = "USSD")
                    val newBalance = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)?.balance
                    "END Paid ${formatAmount(result["amount"] as BigDecimal)} RWF to ${result["merchantName"]}." +
                        (newBalance?.let { " New balance: ${formatAmount(it)} RWF." } ?: "")
                } catch (e: PaymentIntentNotFoundException) {
                    "END Payment code not found."
                } catch (e: PaymentIntentNotPayableException) {
                    "END ${e.message}"
                } catch (e: MerchantNotFoundException) {
                    "END Merchant not found."
                } catch (e: SelfPaymentException) {
                    "END ${e.message}"
                } catch (e: MerchantNoWalletException) {
                    "END ${e.message}"
                } catch (e: InsufficientFundsException) {
                    "END Insufficient balance for this payment."
                }
            }
        }
    }

    private fun handleCheckBalance(userId: String, parts: List<String>): String {
        if (parts.size == 1) return "CON Enter your PIN"
        val pin = parts[1]
        verifyPin(userId, pin)
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: return "END No wallet found for this account."
        return "END Your balance is ${formatAmount(wallet.balance)} RWF."
    }

    private fun handleSendMoney(userId: String, parts: List<String>): String {
        return when (parts.size) {
            1 -> "CON Enter recipient phone number"
            2 -> "CON Enter amount (RWF)"
            3 -> "CON Enter your PIN"
            else -> {
                val recipient = parts[1]
                val amount = parts[2].toBigDecimalOrNull()
                    ?: return "END Invalid amount."
                val pin = parts[3]
                verifyPin(userId, pin)
                try {
                    val (transaction, newBalance) = p2pService.sendDirect(userId, recipient, amount, "USSD transfer")
                    "END Sent ${formatAmount(transaction.amount)} RWF to $recipient. New balance: ${formatAmount(newBalance)} RWF."
                } catch (e: P2pRecipientNotFoundException) {
                    "END No itunda account found for $recipient."
                } catch (e: P2pSelfPaymentException) {
                    "END You cannot send money to your own account."
                } catch (e: P2pNoWalletException) {
                    "END ${e.message}"
                } catch (e: P2pInvalidAmountException) {
                    "END ${e.message}"
                } catch (e: InsufficientFundsException) {
                    "END Insufficient balance for this transfer."
                }
            }
        }
    }

    private fun handleMiniStatement(userId: String, parts: List<String>): String {
        if (parts.size == 1) return "CON Enter your PIN"
        val pin = parts[1]
        verifyPin(userId, pin)
        val transactions = transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId).take(5)
        if (transactions.isEmpty()) return "END No recent transactions."
        val lines = transactions.joinToString("\n") { t ->
            val sign = if (t.senderId == userId) "-" else "+"
            "${dateFormatter.format(t.createdAt)} $sign${formatAmount(t.amount)}"
        }
        return "END Last ${transactions.size} transactions:\n$lines"
    }

    /** Setting a fresh PIN requires entering it twice (a real confirmation step, since
     * there's no visual field to double-check on a real USSD screen); changing an
     * existing PIN requires the real old PIN once, then the new one once -- both
     * branches re-derive `hasExistingPin` fresh at every step rather than trusting
     * anything from an earlier request, matching this whole handler's stateless
     * design. */
    private fun handleSetPinFlow(userId: String, parts: List<String>): String {
        val hasExistingPin = ussdPinRepository.findByUserId(userId) != null
        return if (hasExistingPin) {
            when (parts.size) {
                1 -> "CON Enter your current PIN"
                2 -> {
                    verifyPin(userId, parts[1])
                    "CON Enter your new 4-6 digit PIN"
                }
                else -> {
                    verifyPin(userId, parts[1])
                    val newPin = parts[2]
                    validatePinFormat(newPin)
                    setPin(userId, newPin)
                    "END Your USSD PIN has been updated."
                }
            }
        } else {
            when (parts.size) {
                1 -> "CON Enter a new 4-6 digit PIN"
                2 -> {
                    validatePinFormat(parts[1])
                    "CON Confirm your new PIN"
                }
                else -> {
                    val newPin = parts[1]
                    val confirmPin = parts[2]
                    validatePinFormat(newPin)
                    if (newPin != confirmPin) return "END PINs did not match. Please dial again."
                    setPin(userId, newPin)
                    "END Your USSD PIN has been set."
                }
            }
        }
    }

    private fun formatAmount(amount: BigDecimal): String = amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
}
