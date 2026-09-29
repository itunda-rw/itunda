package rw.itunda.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

/**
 * Real device binding enforcement (2026-07-20) -- see
 * [rw.itunda.core.domain.TrustedDevice]'s own doc comment for the full account of why
 * this exists (modeled on Toss's own real, published Gateway/Passport architecture,
 * where a valid credential alone was never treated as sufficient -- device identity is
 * checked too). This filter is the actual enforcement point; [rw.itunda.auth.DeviceService]
 * owns recording/verifying devices.
 *
 * **Which requests are gated, and why**: every genuinely money-moving POST endpoint in
 * this backend already requires a real `Idempotency-Key` header (P2P send/request,
 * Commerce/Eats orders, Gift send/claim, Rewards claim, Insurance enroll, etc. -- an
 * established, load-bearing convention, not incidental). Reusing that exact signal here
 * means every money-moving endpoint is automatically covered without hand-maintaining a
 * second, parallel list of "sensitive" URL patterns that could silently drift out of
 * sync as new money-moving features are added.
 *
 * **Honest, deliberate gradual-rollout gap**: a request from a session with no
 * `deviceId` claim at all (an older client that hasn't been updated to send one, or a
 * token minted before this feature existed) is let through untouched -- this feature
 * can only ever apply to clients that have actually adopted it. Forcing every existing
 * mobile/web session to suddenly fail would be a real regression, not a security
 * improvement; each client opts in by starting to send a real `deviceId` at login,
 * exactly the same additive, non-breaking rollout shape this whole project uses for
 * every other optional capability (OSRM/Nominatim, hyperlocal neighborhood, etc.).
 */
@Component
class DeviceVerificationFilter(
    private val trustedDeviceRepository: TrustedDeviceRepository,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val idempotencyKey = request.getHeader("Idempotency-Key")
        val currentUser = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        val deviceId = currentUser?.deviceId

        if (idempotencyKey != null && currentUser != null && deviceId != null) {
            val device = trustedDeviceRepository.findByUserIdAndDeviceId(currentUser.userId, deviceId)
            if (device == null || !device.trusted) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    objectMapper.writeValueAsString(
                        ApiError("DEVICE_NOT_VERIFIED", "This device hasn't been verified yet -- verify it (re-enter your password) before moving money."),
                    ),
                )
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}
