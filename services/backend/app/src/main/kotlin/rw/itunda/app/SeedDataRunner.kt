package rw.itunda.app

import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import rw.itunda.core.domain.Contact
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.EmoticonPackRepository
import rw.itunda.core.repository.EmoticonRepository
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
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
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val emoticonPackRepository: EmoticonPackRepository,
    private val emoticonRepository: EmoticonRepository,
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

        // Real Eats demo restaurants (2026-07-24) -- until now the only merchants in
        // this database were test-automation fixtures ("Fraud Test Cafe", "Retry Test
        // Shop", etc, all category=NULL, no photo) plus two real-named but otherwise
        // empty entries -- the same "empty product, can't judge the redesign" gap
        // Marketplace had before its own seed above. Each restaurant is a real owner
        // User + Wallet + Merchant, matching the real onboarding shape (no shortcut
        // schema), with a real category and real Wikimedia food photos -- same "real
        // external URL, no upload pipeline" honesty as Merchant.photoUrl's own doc
        // comment.
        val restaurantOwner1 = userRepository.findByPhoneNumber("+250788456789") ?: userRepository.save(
            User(
                id = "user_restaurant_1", phoneNumber = "+250788456789", email = "aline@itunda.rw",
                firstName = "Aline", lastName = "Umutoni", passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true, creditScore = 700, createdAt = Instant.now(), neighborhood = "Kimironko",
            ),
        )
        val restaurantOwner2 = userRepository.findByPhoneNumber("+250788567890") ?: userRepository.save(
            User(
                id = "user_restaurant_2", phoneNumber = "+250788567890", email = "eric.h@itunda.rw",
                firstName = "Eric", lastName = "Habimana", passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true, creditScore = 680, createdAt = Instant.now(), neighborhood = "Remera",
            ),
        )
        val restaurantOwner3 = userRepository.findByPhoneNumber("+250788678901") ?: userRepository.save(
            User(
                id = "user_restaurant_3", phoneNumber = "+250788678901", email = "grace.m@itunda.rw",
                firstName = "Grace", lastName = "Mukamana", passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true, creditScore = 710, createdAt = Instant.now(), neighborhood = "Kacyiru",
            ),
        )

        listOf(
            restaurantOwner1.id to Wallet(id = "wallet_restaurant_1", userId = restaurantOwner1.id, accountNumber = "2024200001", accountName = "Aline's Business Account", type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            restaurantOwner2.id to Wallet(id = "wallet_restaurant_2", userId = restaurantOwner2.id, accountNumber = "2024200002", accountName = "Eric's Business Account", type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            restaurantOwner3.id to Wallet(id = "wallet_restaurant_3", userId = restaurantOwner3.id, accountNumber = "2024200003", accountName = "Grace's Business Account", type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
        ).forEach { (ownerId, wallet) ->
            if (walletRepository.findByUserId(ownerId).isEmpty()) walletRepository.save(wallet)
        }

        if (merchantRepository.findByOwnerUserId(restaurantOwner1.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_1", ownerUserId = restaurantOwner1.id, walletId = "wallet_restaurant_1",
                    businessName = "Heaven Kigali", category = "Rwandan", kybVerified = true,
                    latitude = -1.9441, longitude = 30.1136,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Brochettes.jpg",
                    minOrderAmount = BigDecimal("3000"),
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(restaurantOwner2.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_2", ownerUserId = restaurantOwner2.id, walletId = "wallet_restaurant_2",
                    businessName = "Kigali Grill House", category = "Fast Food", kybVerified = true,
                    latitude = -1.9578, longitude = 30.1127,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Hamburger.jpg",
                    minOrderAmount = BigDecimal("2000"),
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(restaurantOwner3.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_3", ownerUserId = restaurantOwner3.id, walletId = "wallet_restaurant_3",
                    businessName = "Inzozi Coffee & Bakery", category = "Coffee & Bakery", kybVerified = true,
                    latitude = -1.9346, longitude = 30.0906,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Cappuccino.jpg",
                    minOrderAmount = BigDecimal("1500"),
                ),
            )
        }

        if (merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_seed_1").isEmpty()) {
            merchantProductRepository.saveAll(
                listOf(
                    MerchantProduct(id = "product_seed_1", merchantId = "merchant_seed_1", name = "Beef brochettes (5 skewers)", price = BigDecimal("3500"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Brochettes.jpg", description = "Grilled beef skewers, a Rwandan favorite."),
                    MerchantProduct(id = "product_seed_2", merchantId = "merchant_seed_1", name = "Grilled tilapia with ugali", price = BigDecimal("5000"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Grilled_tilapia.jpg", description = "Whole grilled tilapia, served with ugali."),
                ),
            )
        }
        if (merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_seed_2").isEmpty()) {
            merchantProductRepository.saveAll(
                listOf(
                    MerchantProduct(id = "product_seed_3", merchantId = "merchant_seed_2", name = "Hamburger with chips", price = BigDecimal("4000"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Hamburger.jpg", description = "Beef burger with a side of fries."),
                    MerchantProduct(id = "product_seed_4", merchantId = "merchant_seed_2", name = "Roast chicken (half)", price = BigDecimal("4500"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Roast_chicken.jpg", description = "Half roast chicken, seasoned and grilled."),
                    MerchantProduct(id = "product_seed_5", merchantId = "merchant_seed_2", name = "French fries", price = BigDecimal("1500"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/French_fries.jpg"),
                ),
            )
        }
        if (merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_seed_3").isEmpty()) {
            merchantProductRepository.saveAll(
                listOf(
                    MerchantProduct(id = "product_seed_6", merchantId = "merchant_seed_3", name = "Cappuccino", price = BigDecimal("1800"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Cappuccino.jpg"),
                    MerchantProduct(id = "product_seed_7", merchantId = "merchant_seed_3", name = "Espresso", price = BigDecimal("1200"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Espresso.jpg"),
                    MerchantProduct(id = "product_seed_8", merchantId = "merchant_seed_3", name = "Croissant", price = BigDecimal("1500"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Croissant.jpg"),
                    MerchantProduct(id = "product_seed_9", merchantId = "merchant_seed_3", name = "Banana bread slice", price = BigDecimal("1300"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Banana_bread.jpg"),
                ),
            )
        }

        // Real Shop demo retail merchants (2026-07-24) -- Shop and Eats browse the
        // same Merchant directory (see ShoppingController.getEligibleMerchants, called
        // identically by both screens) with no category split at the API level, so the
        // 3 restaurants seeded above already appear in Shop's own browse list too, but
        // Shop needs real non-food retail merchants of its own to actually be a fair
        // Coupang-style shopping test rather than just a mirror of Eats.
        val retailOwner1 = userRepository.findByPhoneNumber("+250788789012") ?: userRepository.save(
            User(
                id = "user_retail_1", phoneNumber = "+250788789012", email = "jean.claude@itunda.rw",
                firstName = "Jean Claude", lastName = "Nkurunziza", passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true, creditScore = 690, createdAt = Instant.now(), neighborhood = "Nyamirambo",
            ),
        )
        val retailOwner2 = userRepository.findByPhoneNumber("+250788890123") ?: userRepository.save(
            User(
                id = "user_retail_2", phoneNumber = "+250788890123", email = "diane@itunda.rw",
                firstName = "Diane", lastName = "Ingabire", passwordHash = BCryptPasswordEncoder().encode("password123"),
                kycVerified = true, creditScore = 705, createdAt = Instant.now(), neighborhood = "Kicukiro",
            ),
        )

        listOf(
            retailOwner1.id to Wallet(id = "wallet_retail_1", userId = retailOwner1.id, accountNumber = "2024200004", accountName = "Jean Claude's Business Account", type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            retailOwner2.id to Wallet(id = "wallet_retail_2", userId = retailOwner2.id, accountNumber = "2024200005", accountName = "Diane's Business Account", type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
        ).forEach { (ownerId, wallet) ->
            if (walletRepository.findByUserId(ownerId).isEmpty()) walletRepository.save(wallet)
        }

        if (merchantRepository.findByOwnerUserId(retailOwner1.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_4", ownerUserId = retailOwner1.id, walletId = "wallet_retail_1",
                    businessName = "Kigali Electronics Hub", category = "Electronics", kybVerified = true,
                    latitude = -1.9723, longitude = 30.0428,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Smartphone.jpg",
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(retailOwner2.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_5", ownerUserId = retailOwner2.id, walletId = "wallet_retail_2",
                    businessName = "Umutima Fashion", category = "Fashion", kybVerified = true,
                    latitude = -1.9878, longitude = 30.1094,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/T-shirt.jpg",
                ),
            )
        }

        if (merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_seed_4").isEmpty()) {
            merchantProductRepository.saveAll(
                listOf(
                    MerchantProduct(id = "product_seed_10", merchantId = "merchant_seed_4", name = "Smartphone (mid-range)", price = BigDecimal("180000"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Smartphone.jpg", description = "New, sealed box, 1-year warranty."),
                ),
            )
        }
        if (merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_seed_5").isEmpty()) {
            merchantProductRepository.saveAll(
                listOf(
                    MerchantProduct(id = "product_seed_11", merchantId = "merchant_seed_5", name = "Cotton T-shirt", price = BigDecimal("8000"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/T-shirt.jpg"),
                    MerchantProduct(id = "product_seed_12", merchantId = "merchant_seed_5", name = "Sneakers", price = BigDecimal("35000"), imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Sneakers.jpg"),
                ),
            )
        }

        // Real Emoticon Store catalog seed (2026-07-26) -- server-managed, not
        // user-generated, so it's seeded here the same way LedgerAccount.SEED_IDS is,
        // not created through any real user-facing endpoint.
        if (!emoticonPackRepository.existsById("pack_seed_1")) {
            emoticonPackRepository.save(
                EmoticonPack(
                    id = "pack_seed_1", title = "Sunny Days", artistName = "itunda Art",
                    thumbnailUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Emoji_u1f600.svg",
                    price = BigDecimal("500"),
                ),
            )
            emoticonRepository.saveAll(
                listOf(
                    Emoticon(id = "emoticon_seed_1", packId = "pack_seed_1", imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Emoji_u1f600.svg", sortOrder = 0),
                    Emoticon(id = "emoticon_seed_2", packId = "pack_seed_1", imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Emoji_u1f602.svg", sortOrder = 1),
                    Emoticon(id = "emoticon_seed_3", packId = "pack_seed_1", imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Emoji_u1f970.svg", sortOrder = 2),
                ),
            )
        }
    }
}
