package rw.itunda.app

import org.springframework.stereotype.Component
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.User
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.EmoticonPackRepository
import rw.itunda.core.repository.EmoticonRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.UserRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// Real fix (2026-08-26): split out of SeedDataRunner.kt once that file grew past
// its file-size-lint baseline. Marketplace/Eats/Shop-commerce demo-data seeding is
// a real, independent concern from the demo user/account/loan/contacts seeding
// SeedDataRunner.kt itself keeps -- called explicitly from SeedDataRunner.run()
// (passing the demo user's own id for the one listing that needs it) rather than
// as a second CommandLineRunner bean, since Spring doesn't guarantee execution
// order between multiple CommandLineRunner beans and this seed genuinely depends
// on the demo user already existing.
@Component
class CommerceSeedDataRunner(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val listingRepository: ListingRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val timeDealRepository: rw.itunda.core.repository.TimeDealRepository,
    private val emoticonPackRepository: EmoticonPackRepository,
    private val emoticonRepository: EmoticonRepository,
) {
    fun seed(demoUserId: String) {
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
                    id = "listing_seed_1", sellerId = demoUserId, title = "Mountain bike, barely used",
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
        // User + Account + Merchant, matching the real onboarding shape (no shortcut
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
            restaurantOwner1.id to Account(id = "account_restaurant_1", userId = restaurantOwner1.id, accountNumber = "2024200001", accountName = "Aline's Business Account", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            restaurantOwner2.id to Account(id = "account_restaurant_2", userId = restaurantOwner2.id, accountNumber = "2024200002", accountName = "Eric's Business Account", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            restaurantOwner3.id to Account(id = "account_restaurant_3", userId = restaurantOwner3.id, accountNumber = "2024200003", accountName = "Grace's Business Account", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
        ).forEach { (ownerId, account) ->
            if (accountRepository.findByUserId(ownerId).isEmpty()) accountRepository.save(account)
        }

        if (merchantRepository.findByOwnerUserId(restaurantOwner1.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_1", ownerUserId = restaurantOwner1.id, accountId = "account_restaurant_1",
                    businessName = "Heaven Kigali", category = "Rwandan", businessType = MerchantBusinessType.RESTAURANT, kybVerified = true,
                    latitude = -1.9441, longitude = 30.1136,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Brochettes.jpg",
                    minOrderAmount = BigDecimal("3000"),
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(restaurantOwner2.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_2", ownerUserId = restaurantOwner2.id, accountId = "account_restaurant_2",
                    businessName = "Kigali Grill House", category = "Fast Food", businessType = MerchantBusinessType.RESTAURANT, kybVerified = true,
                    latitude = -1.9578, longitude = 30.1127,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Hamburger.jpg",
                    minOrderAmount = BigDecimal("2000"),
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(restaurantOwner3.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_3", ownerUserId = restaurantOwner3.id, accountId = "account_restaurant_3",
                    businessName = "Inzozi Coffee & Bakery", category = "Coffee & Bakery", businessType = MerchantBusinessType.RESTAURANT, kybVerified = true,
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
            retailOwner1.id to Account(id = "account_retail_1", userId = retailOwner1.id, accountNumber = "2024200004", accountName = "Jean Claude's Business Account", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
            retailOwner2.id to Account(id = "account_retail_2", userId = retailOwner2.id, accountNumber = "2024200005", accountName = "Diane's Business Account", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO),
        ).forEach { (ownerId, account) ->
            if (accountRepository.findByUserId(ownerId).isEmpty()) accountRepository.save(account)
        }

        if (merchantRepository.findByOwnerUserId(retailOwner1.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_4", ownerUserId = retailOwner1.id, accountId = "account_retail_1",
                    businessName = "Kigali Electronics Hub", category = "Electronics", businessType = MerchantBusinessType.SHOP, kybVerified = true,
                    latitude = -1.9723, longitude = 30.0428,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Smartphone.jpg",
                ),
            )
        }
        if (merchantRepository.findByOwnerUserId(retailOwner2.id) == null) {
            merchantRepository.save(
                Merchant(
                    id = "merchant_seed_5", ownerUserId = retailOwner2.id, accountId = "account_retail_2",
                    businessName = "Umutima Fashion", category = "Fashion", businessType = MerchantBusinessType.SHOP, kybVerified = true,
                    latitude = -1.9878, longitude = 30.1094,
                    photoUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/T-shirt.jpg",
                ),
            )
        }

        // Real backfill (2026-08-13) -- the two `findByOwnerUserId == null` guards above
        // only ever INSERT once; an environment that already ran this seeder before
        // MerchantBusinessType existed has these 5 merchants with businessType still
        // NULL, and re-running the seeder wouldn't touch them (the guard sees they
        // already exist and skips). Explicit, idempotent, ID-targeted -- not a broad
        // sweep -- since these are the only 5 real merchants this seeder itself created.
        mapOf(
            "merchant_seed_1" to MerchantBusinessType.RESTAURANT, "merchant_seed_2" to MerchantBusinessType.RESTAURANT,
            "merchant_seed_3" to MerchantBusinessType.RESTAURANT, "merchant_seed_4" to MerchantBusinessType.SHOP,
            "merchant_seed_5" to MerchantBusinessType.SHOP,
        ).forEach { (id, type) ->
            merchantRepository.findById(id).ifPresent { m ->
                if (m.businessType == null) merchantRepository.save(m.apply { businessType = type })
            }
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

        // Real Coupang 타임특가 (Time Deal) seed (2026-08-25, direct user follow-up: "our
        // shopping doesn't look like online shopping at all"). TimeDealService's own real
        // banner carousel/"Recommended for you" grid on the Shop home screen was already
        // built (2026-08-12) but derives every banner/deal card directly from real,
        // currently-active TimeDeal rows -- deliberately never fabricated placeholder
        // content (see TimeDealService.getBanners's own doc comment). TimeDeals are a
        // real merchant-created, self-service row (TimeDealService.createTimeDeal), not
        // something auto-generated for any product, so a fresh environment with no
        // merchant having ever run one shows an honest empty Shop home -- exactly what
        // the live device showed here. Seeding a handful of real, valid deals (dealPrice
        // genuinely under the seeded product's own price, matching createTimeDeal's own
        // validation) on already-seeded real products gives that already-built UI
        // something real to render, instead of rebuilding it. endsAt 30 days out so
        // this stays active through a normal test/demo session without needing to be
        // re-seeded.
        //
        // Real fix, same pass (direct user follow-up: "we need everything separated to
        // avoid confusion, that's toss style, clear isolation"): the first version of
        // this seed put deals on merchant_seed_1/merchant_seed_3 -- both real
        // RESTAURANT-type merchants -- which would have shown up in Shop's now-isolated
        // SHOP-only banner rail as a genuine data-level isolation leak, not just a UI
        // one. Scoped to only the two real SHOP-type merchants that exist in this seed
        // file (merchant_seed_4/5) -- itunda's only 3 seeded SHOP products, so this
        // deliberately leaves nothing left over for a separate non-deal "browse" grid;
        // a real gap to close before Shop's own visual redesign, not solved here.
        if (timeDealRepository.count() == 0L) {
            val dealStart = Instant.now()
            val dealEnd = dealStart.plusSeconds(60L * 60 * 24 * 30)
            timeDealRepository.saveAll(
                listOf(
                    rw.itunda.core.domain.TimeDeal(id = "timedeal_seed_3", merchantId = "merchant_seed_4", productId = "product_seed_10", dealPrice = BigDecimal("149000"), originalPrice = BigDecimal("180000"), totalQuantity = 20, remainingQuantity = 12, startsAt = dealStart, endsAt = dealEnd),
                    rw.itunda.core.domain.TimeDeal(id = "timedeal_seed_4", merchantId = "merchant_seed_5", productId = "product_seed_11", dealPrice = BigDecimal("5600"), originalPrice = BigDecimal("8000"), totalQuantity = 80, remainingQuantity = 51, startsAt = dealStart, endsAt = dealEnd),
                    rw.itunda.core.domain.TimeDeal(id = "timedeal_seed_5", merchantId = "merchant_seed_5", productId = "product_seed_12", dealPrice = BigDecimal("27000"), originalPrice = BigDecimal("35000"), totalQuantity = 30, remainingQuantity = 9, startsAt = dealStart, endsAt = dealEnd),
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
