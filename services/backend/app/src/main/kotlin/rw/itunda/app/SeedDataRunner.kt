package rw.itunda.app

import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import rw.itunda.core.domain.Contact
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Seeds the same demo user as backend/src/services/database.ts (same phone number,
 * same "password123" login, same three wallet balances, same active loan and contacts)
 * so the two backends can be compared side by side during migration rather than
 * diverging on fixture data.
 *
 * Idempotent per entity, not gated behind a single early return: an earlier version
 * returned immediately if the demo user already existed, which meant every seed added
 * *after* someone's local database already had that user (like the loan/contacts seeds
 * added in this same change) silently never ran on existing databases. Each block now
 * checks its own existence independently.
 */
@Component
class SeedDataRunner(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val contactRepository: ContactRepository,
    private val holdingRepository: HoldingRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val interestJarRepository: InterestJarRepository,
    private val notificationRepository: NotificationRepository,
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val listingRepository: ListingRepository,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        LedgerAccount.SEED_IDS.forEach { (id, name) ->
            if (!ledgerAccountRepository.existsById(id)) {
                ledgerAccountRepository.save(LedgerAccount(id = id, name = name))
            }
        }

        val user = userRepository.findByPhoneNumber("+250788123456") ?: userRepository.save(
            User(
                id = "user_1",
                phoneNumber = "+250788123456",
                email = "demo@itunda.rw",
                firstName = "Jean",
                lastName = "Baptiste",
                passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true,
                creditScore = 720,
                createdAt = Instant.now(),
                referralCode = "ITDJEAN1",
            ),
        )

        // Demo ADMIN account so /api/v1/system/** (see SecurityConfig's hasRole("ADMIN")
        // rule) is actually reachable locally without hand-editing the database --
        // same "fake but clearly labeled" convention as the rest of this seed file, not
        // a real onboarding flow (there isn't one yet, see V4__user_role.sql).
        if (userRepository.findByPhoneNumber("+250788999000") == null) {
            userRepository.save(
                User(
                    id = "user_admin_1",
                    phoneNumber = "+250788999000",
                    email = "ops@itunda.rw",
                    firstName = "Itunda",
                    lastName = "Ops",
                    passwordHash = BCryptPasswordEncoder().encode("admin123"),
                    kycVerified = true,
                    role = "ADMIN",
                    createdAt = Instant.now(),
                ),
            )
        }

        if (walletRepository.findByUserId(user.id).isEmpty()) {
            walletRepository.saveAll(
                listOf(
                    Wallet(id = "wallet_1", userId = user.id, accountNumber = "2024100001", accountName = "Jean's Main Account", type = WalletType.MAIN, balance = BigDecimal("2450000"), availableBalance = BigDecimal("2450000")),
                    Wallet(id = "wallet_2", userId = user.id, accountNumber = "2024100002", accountName = "Jean's Savings", type = WalletType.SAVINGS, balance = BigDecimal("850000"), availableBalance = BigDecimal("850000")),
                    Wallet(id = "wallet_3", userId = user.id, accountNumber = "2024100003", accountName = "Jean's Investment", type = WalletType.INVESTMENT, balance = BigDecimal("1500000"), availableBalance = BigDecimal("1500000")),
                ),
            )
        }

        if (!loanAccountRepository.existsById("loan_active_1")) {
            loanAccountRepository.save(
                LoanAccount(
                    id = "loan_active_1", userId = user.id, walletId = "wallet_1", offerId = "loan_1",
                    principal = BigDecimal("100000"), outstanding = BigDecimal("65000"), interestRate = 5.0,
                    status = LoanStatus.ACTIVE, disbursedAt = Instant.now().minusSeconds(1_209_600),
                ),
            )
        }

        if (contactRepository.findByUserId(user.id).isEmpty()) {
            contactRepository.saveAll(
                listOf(
                    Contact(id = "c1", userId = user.id, name = "Jean Paul", bank = "Bank of Kigali", acc = "0004 1234 5678", phoneNumber = "+250788111222", color = "#F5FAFF", letter = "J"),
                    Contact(id = "c2", userId = user.id, name = "Marie Claire", bank = "MTN MoMo", acc = "0788 123 456", phoneNumber = "+250788222333", color = "#FFF4E5", letter = "M"),
                    Contact(id = "c3", userId = user.id, name = "David N.", bank = "Airtel Money", acc = "0733 908 123", phoneNumber = "+250733444555", color = "#FEECEE", letter = "D"),
                    Contact(id = "c4", userId = user.id, name = "Alice Uwimana", bank = "Equity Bank", acc = "1000 5678 9012", phoneNumber = "+250788666777", color = "#E8F8F0", letter = "A"),
                    Contact(id = "c5", userId = user.id, name = "Patrick Habimana", bank = "MTN MoMo", acc = "0788 888 999", phoneNumber = "+250788888999", color = "#F0E6FF", letter = "P"),
                ),
            )
        }

        if (holdingRepository.findByUserId(user.id).isEmpty()) {
            holdingRepository.saveAll(
                listOf(
                    Holding(id = "hold_1", userId = user.id, walletId = "wallet_3", stockId = "s1", shares = BigDecimal("1000"), avgPrice = BigDecimal("580")),
                    Holding(id = "hold_2", userId = user.id, walletId = "wallet_3", stockId = "s2", shares = BigDecimal("500"), avgPrice = BigDecimal("490")),
                    Holding(id = "hold_3", userId = user.id, walletId = "wallet_3", stockId = "s3", shares = BigDecimal("200"), avgPrice = BigDecimal("130")),
                ),
            )
        }

        if (savingsGoalRepository.findByUserId(user.id).isEmpty()) {
            savingsGoalRepository.saveAll(
                listOf(
                    SavingsGoal(id = "sg_1", userId = user.id, walletId = "wallet_2", name = "Emergency Fund", targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("320000"), monthlyContribution = BigDecimal("50000"), interestRate = 7.5, targetDate = "2025-06-01", category = "emergency", color = "#0066FF"),
                    SavingsGoal(id = "sg_2", userId = user.id, walletId = "wallet_2", name = "New Laptop", targetAmount = BigDecimal("250000"), currentAmount = BigDecimal("80000"), monthlyContribution = BigDecimal("30000"), interestRate = 7.5, targetDate = "2025-09-01", category = "tech", color = "#9C27B0"),
                ),
            )
        }

        if (!interestJarRepository.existsById(user.id)) {
            interestJarRepository.save(
                InterestJar(
                    userId = user.id, walletId = "wallet_2", balance = BigDecimal("45200"), rate = 7.5,
                    earnedThisMonth = BigDecimal("2840"), earnedTotal = BigDecimal("45200"),
                    lastPaidAt = Instant.now(), nextPayoutAt = Instant.now().plusSeconds(86400),
                ),
            )
        }

        if (notificationRepository.findByUserIdOrderByCreatedAtDesc(user.id).isEmpty()) {
            notificationRepository.saveAll(
                listOf(
                    rw.itunda.core.domain.Notification(id = "n_1", userId = user.id, type = "transaction", title = "Money Received", body = "You received 50,000 RWF from Amina K.", isRead = false, createdAt = Instant.now().minusSeconds(300), dataJson = "{\"amount\": 50000}"),
                    rw.itunda.core.domain.Notification(id = "n_2", userId = user.id, type = "credit_score", title = "Credit Score Updated", body = "Your score improved by 15 points to 735", isRead = false, createdAt = Instant.now().minusSeconds(3600), dataJson = "{\"score\": 735, \"change\": 15}"),
                    rw.itunda.core.domain.Notification(id = "n_3", userId = user.id, type = "bill", title = "Bill Due Soon", body = "REG electricity bill of 35,000 RWF due in 3 days", isRead = false, createdAt = Instant.now().minusSeconds(7200), dataJson = "{\"amount\": 35000}"),
                    rw.itunda.core.domain.Notification(id = "n_4", userId = user.id, type = "savings", title = "Interest Paid", body = "You earned 2,840 RWF interest on your savings", isRead = true, createdAt = Instant.now().minusSeconds(86400), dataJson = "{\"amount\": 2840}"),
                    rw.itunda.core.domain.Notification(id = "n_5", userId = user.id, type = "promo", title = "itunda Prime Offer", body = "Get 3 months free with annual subscription", isRead = true, createdAt = Instant.now().minusSeconds(172800), dataJson = "{}"),
                    rw.itunda.core.domain.Notification(id = "n_6", userId = user.id, type = "security", title = "New Login", body = "New sign-in detected on your account", isRead = true, createdAt = Instant.now().minusSeconds(259200), dataJson = "{}"),
                    rw.itunda.core.domain.Notification(id = "n_7", userId = user.id, type = "transaction", title = "Transfer Sent", body = "You sent 20,000 RWF to Claude M.", isRead = true, createdAt = Instant.now().minusSeconds(345600), dataJson = "{\"amount\": 20000}")
                )
            )
        }

        if (insurancePolicyRepository.findByUserId(user.id).isEmpty()) {
            insurancePolicyRepository.save(
                rw.itunda.core.domain.InsurancePolicy(
                    id = "pol_1", userId = user.id, planId = "ins_1", planName = "Health Shield",
                    category = "health", status = "active", startDate = LocalDate.of(2024, 1, 1),
                    endDate = LocalDate.of(2024, 12, 31), monthlyPremium = BigDecimal("15000"),
                    nextPaymentDate = LocalDate.of(2024, 12, 1), policyNumber = "HS-2024-780123"
                )
            )
        }

        // Real Hood/Marketplace demo sellers + listings (2026-07-24) -- until now the
        // only seeded account was `user` (id user_1), so every Karrot-style feed screen
        // showed empty "No listings yet" states with nobody to compare the new
        // photo-forward ListingCard design against. Two more sellers (not user_1) so the
        // feed reads like a real multi-neighbor market, same as Karrot's own home feed,
        // rather than one person's items. Real, appropriately-licensed Wikimedia Commons
        // photo URLs -- confirmed reachable before use, not fabricated -- same honest
        // "external URL, no upload/storage layer" scope as Listing.photoUrl's own doc
        // comment.
        val seller2 = userRepository.findByPhoneNumber("+250788234567") ?: userRepository.save(
            User(
                id = "user_seller_2",
                phoneNumber = "+250788234567",
                email = "amina@itunda.rw",
                firstName = "Amina",
                lastName = "Keza",
                passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true,
                creditScore = 690,
                createdAt = Instant.now(),
                neighborhood = "Kimironko",
            ),
        )

        val seller3 = userRepository.findByPhoneNumber("+250788345678") ?: userRepository.save(
            User(
                id = "user_seller_3",
                phoneNumber = "+250788345678",
                email = "eric@itunda.rw",
                firstName = "Eric",
                lastName = "Nshuti",
                passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true,
                creditScore = 705,
                createdAt = Instant.now(),
                neighborhood = "Remera",
            ),
        )

        if (!listingRepository.existsById("listing_seed_1")) {
            listingRepository.save(
                Listing(
                    id = "listing_seed_1", sellerId = user.id, title = "Mountain bike, barely used",
                    description = "Rode it maybe 10 times. No damage, tires still good. Selling because I moved closer to work.",
                    price = BigDecimal("85000"), category = "Sports", status = ListingStatus.ACTIVE,
                    latitude = -1.9441, longitude = 30.1136, neighborhood = "Kimironko",
                    meetingPlace = "Kimironko Market gate",
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Bicycle.jpg",
                ),
            )
        }

        if (!listingRepository.existsById("listing_seed_2")) {
            listingRepository.save(
                Listing(
                    id = "listing_seed_2", sellerId = seller2.id, title = "3-seater sofa, grey fabric",
                    description = "Moving out end of month, needs to go. Comfortable, no stains or tears.",
                    price = BigDecimal("120000"), category = "Furniture", status = ListingStatus.ACTIVE,
                    latitude = -1.9441, longitude = 30.1136, neighborhood = "Kimironko",
                    meetingPlace = "Delivery only within Kimironko",
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Sofa.jpg",
                ),
            )
        }

        if (!listingRepository.existsById("listing_seed_3")) {
            listingRepository.save(
                Listing(
                    id = "listing_seed_3", sellerId = seller2.id, title = "Android phone, good condition",
                    description = "Screen has no cracks, battery still holds a full day. Comes with charger.",
                    price = BigDecimal("95000"), category = "Electronics", status = ListingStatus.ACTIVE,
                    latitude = -1.9441, longitude = 30.1136, neighborhood = "Kimironko",
                    meetingPlace = "Kimironko Market gate",
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Mobile_phone.jpg",
                ),
            )
        }

        if (!listingRepository.existsById("listing_seed_4")) {
            listingRepository.save(
                Listing(
                    id = "listing_seed_4", sellerId = seller3.id, title = "Wooden dining table",
                    description = "Solid wood, seats 4. A few scratches on top but sturdy.",
                    price = BigDecimal("60000"), category = "Furniture", status = ListingStatus.ACTIVE,
                    latitude = -1.9578, longitude = 30.1127, neighborhood = "Remera",
                    meetingPlace = "Remera roundabout",
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Desk.jpg",
                ),
            )
        }

        if (!listingRepository.existsById("listing_seed_5")) {
            listingRepository.save(
                Listing(
                    id = "listing_seed_5", sellerId = seller3.id, title = "Laptop, works great for school",
                    description = "Used for two semesters, upgrading to a new one. No issues, includes charger.",
                    price = BigDecimal("280000"), category = "Electronics", status = ListingStatus.ACTIVE,
                    latitude = -1.9578, longitude = 30.1127, neighborhood = "Remera",
                    meetingPlace = "Remera roundabout",
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Laptop.jpg",
                ),
            )
        }
    }
}
