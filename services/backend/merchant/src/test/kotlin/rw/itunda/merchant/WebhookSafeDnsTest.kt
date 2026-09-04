package rw.itunda.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Proves the exact gap WebhookSafeDns's doc comment describes is closed: this is the
 * resolution OkHttp actually connects with, so if it rejects an unsafe address, there is
 * no separate, independent resolution left for a DNS-rebinding attacker to race.
 */
class WebhookSafeDnsTest : BehaviorSpec({
    Given("a webhook hostname that resolves only to public addresses") {
        When("OkHttp asks WebhookSafeDns to resolve it before connecting") {
            val addresses = WebhookSafeDns.rejectUnlessAllPublic(listOf(InetAddress.getByName("8.8.8.8")))

            Then("the connection is allowed to proceed") {
                addresses.size shouldBe 1
            }
        }
    }

    Given("a rebinding attack: the same lookup returns one public and one internal address") {
        When("OkHttp asks WebhookSafeDns to resolve it before connecting") {
            val addresses = listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("169.254.169.254"))

            Then("the whole resolution is rejected, since this is the only lookup used to connect") {
                shouldThrow<UnknownHostException> { WebhookSafeDns.rejectUnlessAllPublic(addresses) }
            }
        }
    }

    Given("a webhook hostname that resolves only to an internal address") {
        When("OkHttp asks WebhookSafeDns to resolve it before connecting") {
            val addresses = listOf(InetAddress.getByName("127.0.0.1"))

            Then("the connection is refused before any socket is opened") {
                shouldThrow<UnknownHostException> { WebhookSafeDns.rejectUnlessAllPublic(addresses) }
            }
        }
    }
})
