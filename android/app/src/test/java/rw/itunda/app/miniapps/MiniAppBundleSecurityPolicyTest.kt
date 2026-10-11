package rw.itunda.app.miniapps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppBundleSecurityPolicyTest {
    @Test
    fun acceptsOnlyA64CharacterHexSha256Pin() {
        assertTrue(MiniAppBundleSecurityPolicy.isValidSha256Pin("a".repeat(64)))
        assertTrue(MiniAppBundleSecurityPolicy.isValidSha256Pin("ABCDEF0123456789".repeat(4)))
        assertFalse(MiniAppBundleSecurityPolicy.isValidSha256Pin(""))
        assertFalse(MiniAppBundleSecurityPolicy.isValidSha256Pin("a".repeat(63)))
        assertFalse(MiniAppBundleSecurityPolicy.isValidSha256Pin("a".repeat(65)))
        assertFalse(MiniAppBundleSecurityPolicy.isValidSha256Pin("g".repeat(64)))
    }

    @Test
    fun acceptsHttpsUrlWithoutEmbeddedCredentials() {
        assertTrue(MiniAppBundleSecurityPolicy.isSafeHttpsUrl("https://cdn.example.com/bundles/app.js"))
    }

    @Test
    fun rejectsHttpCredentialsAndMalformedUrls() {
        assertFalse(MiniAppBundleSecurityPolicy.isSafeHttpsUrl("http://cdn.example.com/app.js"))
        assertFalse(MiniAppBundleSecurityPolicy.isSafeHttpsUrl("https://user:secret@cdn.example.com/app.js"))
        assertFalse(MiniAppBundleSecurityPolicy.isSafeHttpsUrl("not a URL"))
        assertFalse(MiniAppBundleSecurityPolicy.isSafeHttpsUrl(""))
    }
}
