package rw.itunda.eats

import org.springframework.stereotype.Service
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.WalletRepository
import java.util.UUID

class RiderNoWalletException(message: String) : RuntimeException(message)
class RiderAlreadyRegisteredException(message: String) : RuntimeException(message)
class RiderNotRegisteredException(message: String) : RuntimeException(message)

/**
 * Real rider registration for Coupang Eats-style delivery -- any itunda user can opt in,
 * no separate onboarding/KYC gate beyond the account they already have (matching how
 * light this session's other self-service registrations are, e.g. marketplace listing
 * creation). Reuses the rider's own existing MAIN wallet as their payout destination --
 * the same real WALLET-to-WALLET disbursement precedent `PayrollService` already
 * established, no new wallet type or external payout rail needed.
 */
@Service
class RiderService(
    private val riderRepository: RiderRepository,
    private val walletRepository: WalletRepository,
) {
    fun register(userId: String): Rider {
        if (riderRepository.findByUserId(userId) != null) {
            throw RiderAlreadyRegisteredException("This account is already registered as a rider")
        }
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw RiderNoWalletException("No wallet found for this account")
        return riderRepository.save(Rider(id = "rider_${UUID.randomUUID()}", userId = userId, walletId = wallet.id))
    }

    fun getMyRiderProfile(userId: String): Rider =
        riderRepository.findByUserId(userId) ?: throw RiderNotRegisteredException("This account is not registered as a rider")

    fun setAvailability(userId: String, available: Boolean): Rider {
        val rider = riderRepository.findByUserId(userId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        rider.available = available
        return riderRepository.save(rider)
    }
}
