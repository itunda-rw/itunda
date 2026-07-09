package rw.itunda.core.security

/**
 * The Spring Security principal set by the JWT filter (see :app's JwtAuthenticationFilter)
 * once a token has been verified. Equivalent to Express's `req.userId` set by
 * `requireAuth` in backend/src/middleware/auth.middleware.ts — every controller should
 * resolve the caller's identity from this, never from a hardcoded id.
 */
data class CurrentUser(val userId: String)
