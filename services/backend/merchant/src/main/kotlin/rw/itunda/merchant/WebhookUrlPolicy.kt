package rw.itunda.merchant

import java.net.InetAddress
import java.net.URI

/**
 * Webhook destinations are merchant-controlled input but requests originate inside
 * Itunda's private network. Restrict them to HTTPS hostnames so configuration cannot
 * directly target loopback, link-local, or private-IP services.
 *
 * DNS names are deliberately not resolved during configuration: a merchant should be
 * able to save an endpoint while it is temporarily offline. Delivery resolves every
 * hostname and rejects non-public results before creating the HTTP request.
 */
object WebhookUrlPolicy {
    fun parse(url: String): URI {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw InvalidWebhookUrlException("Webhook URL must be a valid HTTPS URL")
        }
        val host = uri.host?.lowercase() ?: throw InvalidWebhookUrlException("Webhook URL must include a hostname")
        if (uri.scheme?.lowercase() != "https") {
            throw InvalidWebhookUrlException("Webhook URL must use HTTPS")
        }
        if (uri.userInfo != null) {
            throw InvalidWebhookUrlException("Webhook URL must not contain credentials")
        }

        val normalizedHost = host.removePrefix("[").removeSuffix("]")
        val directIpLiteral = normalizedHost.all { it.isDigit() || it == '.' || it == ':' } ||
            normalizedHost.matches(Regex("0[xX][0-9a-fA-F]+"))
        if (normalizedHost == "localhost" || normalizedHost.endsWith(".localhost") || directIpLiteral) {
            throw InvalidWebhookUrlException("Webhook URL must use a public HTTPS hostname")
        }
        return uri
    }

    fun parseForDelivery(url: String): URI {
        val uri = parse(url)
        val addresses = try {
            InetAddress.getAllByName(uri.host)
        } catch (_: Exception) {
            throw InvalidWebhookUrlException("Webhook hostname could not be resolved")
        }
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
            throw InvalidWebhookUrlException("Webhook hostname must resolve only to public addresses")
        }
        return uri
    }

    /** Safe for logs: endpoint query strings often contain merchant-issued secrets. */
    fun displayTarget(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host ?: return@runCatching "invalid-webhook-url"
        buildString {
            append(uri.scheme?.lowercase() ?: "unknown")
            append("://")
            append(host)
            if (uri.port >= 0) append(":${uri.port}")
        }
    }.getOrDefault("invalid-webhook-url")

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            // 100.64.0.0/10 is shared carrier-grade NAT space, not public Internet.
            if (first == 100 && second in 64..127) return false
        } else if (bytes.size == 16) {
            // fc00::/7 is IPv6 unique-local address space.
            if ((bytes[0].toInt() and 0xfe) == 0xfc) return false
        }
        return true
    }
}
