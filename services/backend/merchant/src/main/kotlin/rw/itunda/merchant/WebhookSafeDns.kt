package rw.itunda.merchant

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The DNS resolver [WebhookDeliveryService]'s OkHttp client is configured with.
 *
 * `WebhookUrlPolicy.parseForDelivery` already resolves a webhook hostname and rejects
 * it if any address is loopback/link-local/private/CGNAT/IPv6-ULA -- but that check and
 * the HTTP client's own connection-time resolution used to be two independent DNS
 * queries. `java.net.http.HttpClient` re-resolves a hostname URI itself when it opens
 * the connection, so a merchant who controls DNS for their webhook hostname could
 * return a safe public address for the validation query and a different, internal
 * address (loopback, a cluster-internal service, a cloud metadata endpoint) for the
 * connection query moments later -- a DNS-rebinding TOCTOU (CWE-918).
 *
 * OkHttp lets a client override DNS resolution entirely (`OkHttpClient.Builder.dns`),
 * so this class performs the SAME safety check `WebhookUrlPolicy.isPublicAddress`
 * already implements, but as the one and only resolution OkHttp uses to open the
 * connection. There is no second, independent query left for a rebinding attacker to
 * race: whatever this method returns is what OkHttp connects to.
 */
object WebhookSafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: Exception) {
            throw UnknownHostException("Webhook hostname could not be resolved: $hostname")
        }
        return rejectUnlessAllPublic(addresses)
    }

    /** Split out for direct unit testing without needing a real/mocked DNS lookup. */
    internal fun rejectUnlessAllPublic(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.isEmpty() || addresses.any { !WebhookUrlPolicy.isPublicAddress(it) }) {
            throw UnknownHostException("Webhook hostname must resolve only to public addresses")
        }
        return addresses
    }
}
