package rw.itunda.core.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.FileInputStream

/**
 * Picks the real `PushSender` at startup, at the exact same "real architecture,
 * simulated external boundary" line `SimulatedProviderConnector`
 * (`itunda.mtn-momo.*`)/`DemoNidaVerificationService` already draw: `RealFcmPushSender`
 * the moment `itunda.push.firebase-credentials-path` points at a real Firebase
 * service-account JSON key (downloaded from the Firebase Console -- Project Settings ->
 * Service Accounts -> Generate new private key -- something only the project owner can
 * do, so this backend can't generate one itself), `SimulatedPushSender` otherwise. Empty
 * by default, same as every other externally-gated integration's own config: never
 * required for local dev/CI, and a missing/unreadable/invalid file logs a warning and
 * falls back rather than failing application startup -- push is auxiliary everywhere
 * else in this codebase (see `PushNotificationService`'s own doc comment), and startup
 * itself is no exception.
 */
@Configuration
class PushConfig {
    private val log = LoggerFactory.getLogger(PushConfig::class.java)

    @Value("\${itunda.push.firebase-credentials-path:}")
    private lateinit var credentialsPath: String

    @Bean
    fun pushSender(): PushSender {
        val simulatedPushSender = SimulatedPushSender()
        val path = credentialsPath.trim()
        if (path.isEmpty()) {
            log.info("itunda.push.firebase-credentials-path not set -- using SimulatedPushSender (see PushSender.kt doc comment).")
            return simulatedPushSender
        }
        val credentialsFile = File(path)
        if (!credentialsFile.isFile) {
            log.warn("itunda.push.firebase-credentials-path={} does not exist -- falling back to SimulatedPushSender.", path)
            return simulatedPushSender
        }
        return try {
            val credentials = FileInputStream(credentialsFile).use { stream -> GoogleCredentials.fromStream(stream) }
            val app = if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())
            } else {
                FirebaseApp.getInstance()
            }
            // Read the project id from the credential itself, not
            // FirebaseOptions.projectId (stays null unless explicitly set via
            // .setProjectId()) or FirebaseApp.getProjectId() (package-private in this
            // SDK version, confirmed via a real compile failure while verifying this
            // against the user's own real credential) -- both looked like the "right"
            // API but neither is actually usable here.
            val projectId = (credentials as? ServiceAccountCredentials)?.projectId
            log.info("Real FCM push sender active (Firebase project {}).", projectId)
            RealFcmPushSender(FirebaseMessaging.getInstance(app))
        } catch (e: Exception) {
            log.error("Could not initialize Firebase from itunda.push.firebase-credentials-path={} -- falling back to SimulatedPushSender.", path, e)
            simulatedPushSender
        }
    }
}
