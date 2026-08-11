package rw.itunda.core.push

import org.slf4j.LoggerFactory
import rw.itunda.core.domain.DeviceToken

/**
 * Real push-notification delivery, at the exact same "real architecture, simulated
 * external boundary" line this codebase already draws for every other feature genuinely
 * blocked on a real third-party credential it has no path to obtain in this environment
 * (`SimulatedProviderConnector` for MTN MoMo/Airtel Money/utility rails,
 * `DemoNidaVerificationService`/`DemoCardAuthorizationService` for NIDA/card
 * processing). Real FCM (Android/Web) and real APNs (iOS) both require a live
 * Firebase project / Apple Developer account this backend has no access to -- the
 * [SimulatedPushSender] below is the honest v1: everything up to and including "which
 * real device tokens should receive this push, with what real title/body" is fully
 * real; only the actual network hop to Google/Apple's own push gateway is simulated,
 * behind this interface so a real implementation can be swapped in later with zero
 * change to any caller.
 *
 * **Real FCM implementation added** -- see [RealFcmPushSender] and `PushConfig`'s own
 * doc comment for how the real one now gets selected the moment a real Firebase
 * project's credentials are configured. Not `@Component`-annotated here anymore
 * (`PushConfig.pushSender()` constructs it directly) so exactly one `PushSender` bean
 * exists at a time -- Spring would otherwise see two candidates (this one, plus
 * whichever `PushConfig` picks) and fail every `PushNotificationService` injection with
 * `NoUniqueBeanDefinitionException`.
 */
interface PushSender {
    fun send(deviceToken: DeviceToken, title: String, body: String, data: Map<String, String>): Boolean
}

class SimulatedPushSender : PushSender {
    private val log = LoggerFactory.getLogger(SimulatedPushSender::class.java)

    override fun send(deviceToken: DeviceToken, title: String, body: String, data: Map<String, String>): Boolean {
        log.info(
            "SIMULATED PUSH -> platform={} token={} title=\"{}\" body=\"{}\" data={}",
            deviceToken.platform, deviceToken.token.take(12) + "...", title, body, data,
        )
        return true
    }
}
