package rw.itunda.app.miniapps

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Small, pure policy checks for remote executable mini-app bundles.
 * Keep these independently testable: remote code must have a valid digest pin
 * and use HTTPS without embedded URL credentials, including after redirects.
 */
internal object MiniAppBundleSecurityPolicy {
    fun isValidSha256Pin(value: String): Boolean =
        value.matches(Regex("(?i)[0-9a-f]{64}"))

    fun isSafeHttpsUrl(value: String): Boolean {
        val parsed = value.toHttpUrlOrNull() ?: return false
        return isSafeHttpsUrl(parsed)
    }

    fun isSafeHttpsUrl(url: HttpUrl): Boolean =
        url.isHttps && url.username.isEmpty() && url.password.isEmpty()
}
