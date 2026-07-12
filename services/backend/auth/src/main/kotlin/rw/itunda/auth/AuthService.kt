package rw.itunda.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Port of backend/src/controllers/auth.controller.ts's login/register, with one fix
 * applied from day one instead of ported as a known gap: registration provisions a real
 * MAIN wallet (zero balance) for the new user. The Express backend's SECURITY.md lists
 * "new accounts have no wallet of their own" as its #1 open remediation item — since this
 * is a fresh implementation, there's no reason to carry that gap forward.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
    private val rateLimiter: RateLimiter,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        // Stricter and longer-windowed than login: creating an account is a rarer,
        // more sensitive action than a login retry, so fewer attempts should be
        // tolerated before this starts looking like account-creation spam.
        rateLimiter.checkLimit("auth:register:${request.phoneNumber}", limit = 3, window = Duration.ofMinutes(10))
        if (userRepository.existsByPhoneNumber(request.phoneNumber)) {
            throw PhoneAlreadyRegisteredException("An account with this phone number already exists")
        }
        val user = User(
            id = "user_${UUID.randomUUID()}",
            phoneNumber = request.phoneNumber,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            passwordHash = passwordEncoder.encode(request.password),
            kycVerified = false,
            creditScore = 0,
            createdAt = Instant.now(),
        )
        userRepository.save(user)

        walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = user.id,
                accountNumber = generateAccountNumber(),
                accountName = "${user.firstName}'s Main Account",
                type = WalletType.MAIN,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )

        return issueAuthResponse(user, "Registration successful")
    }

    fun login(request: LoginRequest): AuthResponse {
        // Keyed by phoneNumber, not IP: the actual asset being brute-forced is one
        // account's password, and a real attacker rotates source IPs long before they
        // rotate target phone numbers. Checked before the password comparison so a
        // locked-out attacker can't keep spending CPU on bcrypt.
        rateLimiter.checkLimit("auth:login:${request.phoneNumber}", limit = 5, window = Duration.ofMinutes(1))
        val user = userRepository.findByPhoneNumber(request.phoneNumber)
            ?: throw InvalidCredentialsException("Invalid phone number or password")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException("Invalid phone number or password")
        }
        return issueAuthResponse(user, "Login successful")
    }

    fun getProfile(userId: String): PublicUser {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        return user.toPublic()
    }

    /**
     * Revokes the current access token and, if a refresh token is supplied, revokes
     * that too — otherwise a client could "log out" the access token while still
     * holding a live refresh token capable of silently minting a fresh one, which
     * isn't a real logout at all.
     */
    fun logout(accessToken: String, refreshToken: String?) {
        jwtService.verify(accessToken)?.let { tokenBlocklistService.blacklist(it.jti, it.expiresAt) }
        refreshToken?.let { jwtService.verify(it)?.let { decoded -> tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt) } }
    }

    /**
     * Refresh token rotation: the presented refresh token is immediately revoked and a
     * brand-new access+refresh pair is issued. A stolen-but-unused refresh token that
     * gets replayed after the legitimate client already rotated it is now a detectable,
     * blocked replay instead of a silently reusable long-lived credential.
     */
    fun refresh(refreshToken: String): AuthResponse {
        val decoded = jwtService.verify(refreshToken)
            ?: throw InvalidRefreshTokenException("Refresh token is invalid or expired")
        if (!decoded.isRefresh) throw InvalidRefreshTokenException("Not a refresh token")
        if (tokenBlocklistService.isBlacklisted(decoded.jti)) {
            throw InvalidRefreshTokenException("Refresh token has already been used or revoked")
        }
        val user = userRepository.findById(decoded.userId).orElseThrow { InvalidRefreshTokenException("User not found") }

        tokenBlocklistService.blacklist(decoded.jti, decoded.expiresAt)
        return issueAuthResponse(user, "Token refreshed")
    }

    private fun issueAuthResponse(user: User, message: String) = AuthResponse(
        message = message,
        user = user.toPublic(),
        accessToken = jwtService.issueAccessToken(user.id, user.phoneNumber, user.role),
        refreshToken = jwtService.issueRefreshToken(user.id),
    )

    private fun generateAccountNumber(): String = (2024100000L + (Math.random() * 900000).toLong()).toString()

    private fun User.toPublic() = PublicUser(
        id = id, phoneNumber = phoneNumber, email = email, firstName = firstName,
        lastName = lastName, kycVerified = kycVerified, creditScore = creditScore, createdAt = createdAt,
    )
}
