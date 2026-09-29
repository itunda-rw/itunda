package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.net.InetAddress

class WebhookUrlPolicyTest : BehaviorSpec({
    Given("a merchant HTTPS hostname endpoint") {
        When("it has no embedded credentials") {
            val url = WebhookUrlPolicy.parse("https://merchant.example/webhooks/itunda")

            Then("it is accepted") {
                url.host shouldBe "merchant.example"
            }
        }
    }

    Given("a webhook URL with a secret query token") {
        When("it is prepared for logging") {
            val target = WebhookUrlPolicy.displayTarget("https://merchant.example/hooks?token=merchant-secret#fragment")

            Then("only its origin is retained") {
                target shouldBe "https://merchant.example"
            }
        }
    }

    listOf(
        "http://merchant.example/hooks" to "HTTPS",
        "https://localhost/hooks" to "localhost",
        "https://127.0.0.1/hooks" to "IPv4",
        "https://[::1]/hooks" to "IPv6",
        "https://user:secret@merchant.example/hooks" to "credentials",
        "not a URL" to "malformed input",
    ).forEach { (url, reason) ->
        Given("a webhook URL using $reason") {
            When("a merchant configures it") {
                val result = runCatching { WebhookUrlPolicy.parse(url) }

                Then("it is rejected before it can become an outbound request") {
                    (result.exceptionOrNull() is InvalidWebhookUrlException) shouldBe true
                }
            }
        }
    }

    Given("addresses resolved at webhook delivery time") {
        listOf(
            "127.0.0.1" to false,
            "10.0.0.1" to false,
            "100.64.0.1" to false,
            "169.254.169.254" to false,
            "fc00::1" to false,
            "8.8.8.8" to true,
        ).forEach { (address, expectedPublic) ->
            When("the destination is $address") {
                val isPublic = WebhookUrlPolicy.isPublicAddress(InetAddress.getByName(address))

                Then("its public-network status is classified safely") {
                    isPublic shouldBe expectedPublic
                }
            }
        }
    }
})
