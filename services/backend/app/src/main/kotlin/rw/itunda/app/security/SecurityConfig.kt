package rw.itunda.app.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.http.HttpStatus
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Every route requires a verified JWT except register/login/health — the opposite
 * default of how the Express backend started (nothing required auth until it was added
 * route-by-route, and even then no controller used the verified identity; see
 * SECURITY.md). Stateless: no server-side session, matching the token-based design.
 */
@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    // No CORS config existed anywhere in this backend until ops-mfe (2026-07-16) --
    // bank-mfe/kyc-mfe never actually called it from a browser (mocked fetch only), so
    // the gap was never hit. Origins are the micro-frontends' Vite dev ports; override
    // via ITUNDA_CORS_ALLOWED_ORIGINS (comma-separated) for non-local environments.
    // Port 5004 added 2026-07-17 for merchant-mfe.
    @Value("\${itunda.cors.allowed-origins:http://localhost:5000,http://localhost:5001,http://localhost:5002,http://localhost:5003,http://localhost:5004}")
    private val allowedOrigins: List<String>,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        // Real bug found live (2026-07-17): merchant-mfe's card-charge screen was the
        // first browser-based flow in this repo to ever send the real Idempotency-Key
        // header itunda's own money-moving POST endpoints require -- every prior MFE
        // flow either didn't need it (QR generation) or was only ever exercised via
        // curl, not a real browser, so this CORS preflight failure was never hit before.
        // A real headless-Chromium check caught a real net::ERR_FAILED, not a guess.
        config.allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // "/health" was already permitAll here but nothing ever implemented
                    // it -- k8s readiness/liveness probes need a real endpoint, so
                    // "/actuator/health" was added alongside it (2026-07-11) rather than
                    // building a bespoke one; see infra/k8s/production/backend.yaml.
                    // "/error" added (2026-07-11, found live): sendError(403)/401 from
                    // accessDeniedHandler/authenticationEntryPoint triggers Spring Boot's
                    // BasicErrorController via an internal servlet forward to /error --
                    // which re-enters this exact same filter chain as a fresh request. If
                    // /error itself isn't permitted, it hits .anyRequest().authenticated(),
                    // fails (the forward reaches AuthorizationFilter before any per-filter
                    // JWT re-authentication resolves), and *that* failure's response is what
                    // the client actually receives -- silently overriding whatever status the
                    // original handler set. Confirmed live: a valid USER-role token denied
                    // ADMIN-only /api/v1/system/** came back 401 instead of 403 until this
                    // was added.
                    .requestMatchers("/health", "/actuator/health", "/error", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                    // Fixed (2026-07-11): previously any authenticated user -- not just an
                    // operator -- could read fraud/compliance/reconciliation data from
                    // /api/v1/system/**, exactly the gap SECURITY.md names as still open.
                    // Requires the "role":"ADMIN" JWT claim (see JwtAuthenticationFilter);
                    // there's no self-service promotion flow yet, see
                    // V4__user_role.sql's comment.
                    .requestMatchers("/api/v1/system/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            // Spring Security's default for an unauthenticated request with no configured
            // entry point is 403; Express's requireAuth returns 401 for a missing/invalid
            // token. Matching that exactly rather than leaving an incidental difference.
            //
            // Real bug found deploying this (2026-07-11): a *valid* USER-role token hitting
            // an ADMIN-only /api/v1/system/** route also came back 401, not 403 -- wrongly
            // implying "you're not logged in" to someone who very much is, just isn't
            // allowed here. Only authenticationEntryPoint was ever customized; explicitly
            // wiring accessDeniedHandler too, rather than trusting Spring Security's default
            // wiring to already separate the two cases correctly.
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                it.accessDeniedHandler(AccessDeniedHandler { _, response, _ -> response.sendError(HttpStatus.FORBIDDEN.value()) })
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
