package rw.itunda.discover.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/discover")
class DiscoverController {

    private val discoverItems = listOf(
        mapOf("id" to "d_1", "category" to "government", "title" to "Irembo Services", "subtitle" to "Pay government fees instantly", "description" to "Access 100+ government services: passport, driving license, land title, RRA taxes", "color" to "#0066FF", "isNew" to false, "badge" to null),
        mapOf("id" to "d_2", "category" to "government", "title" to "RRA Tax Payment", "subtitle" to "Rwanda Revenue Authority", "description" to "Pay income tax, VAT, and other obligations directly from itunda", "color" to "#34C0AC", "isNew" to false, "badge" to "Due Soon"),
        mapOf("id" to "d_3", "category" to "rewards", "title" to "itunda Points", "subtitle" to "Earn on every transaction", "description" to "Earn 1 point per 100 RWF spent. Redeem for cashback, airtime, or vouchers", "color" to "#FFB300", "isNew" to false, "badge" to "1,240 pts"),
        mapOf("id" to "d_4", "category" to "rewards", "title" to "Referral Bonus", "subtitle" to "Invite friends, earn together", "description" to "Earn 5,000 RWF for each friend who signs up and completes first transfer", "color" to "#FF9500", "isNew" to false, "badge" to null),
        mapOf("id" to "d_5", "category" to "lifestyle", "title" to "Yego Vouchers", "subtitle" to "Exclusive partner deals", "description" to "Discounts at Nakumatt, Simba Supercentre, KFC, Java House and 200+ partners", "color" to "#E91E63", "isNew" to true, "badge" to "Hot"),
        mapOf("id" to "d_6", "category" to "lifestyle", "title" to "itunda Walk", "subtitle" to "Earn by staying active", "description" to "Walk 10,000 steps daily and earn up to 500 RWF. Connected to your health data", "color" to "#00C853", "isNew" to true, "badge" to null),
        mapOf("id" to "d_7", "category" to "credit", "title" to "Credit Builder", "subtitle" to "Improve your credit score", "description" to "Take a small secured loan to build credit history and access better rates", "color" to "#5856D6", "isNew" to false, "badge" to null),
        mapOf("id" to "d_8", "category" to "social", "title" to "Request Money", "subtitle" to "Split bills with friends", "description" to "Create payment requests and share with anyone. Split restaurant bills easily", "color" to "#34C0AC", "isNew" to false, "badge" to null)
    )

    private val banners = listOf(
        mapOf("id" to "b_1", "title" to "Earn 7.5% on savings", "subtitle" to "Open Interest Jar today", "color" to "#0066FF", "cta" to "Start Saving"),
        mapOf("id" to "b_2", "title" to "Free health insurance", "subtitle" to "First month on us with Prime", "color" to "#00C853", "cta" to "Get Prime"),
        mapOf("id" to "b_3", "title" to "RSE stocks from 1,000 RWF", "subtitle" to "Invest in Rwanda's top companies", "color" to "#5856D6", "cta" to "Invest Now")
    )

    @GetMapping
    fun getDiscoverItems(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "items" to discoverItems,
            "banners" to banners
        ))
    }

    @GetMapping("/{category}")
    fun getDiscoverByCategory(@PathVariable category: String): ResponseEntity<Map<String, Any>> {
        val filtered = discoverItems.filter { it["category"] == category }
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "items" to filtered
        ))
    }
}
