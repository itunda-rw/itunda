package rw.itunda.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import rw.itunda.core.account.AccountNumberGenerator
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.User
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.time.Instant

class PayAccountBackfillRunnerTest {
    private val userRepository = mock(UserRepository::class.java)
    private val accountRepository = mock(AccountRepository::class.java)
    private val accountNumberGenerator = mock(AccountNumberGenerator::class.java)
    private val runner = PayAccountBackfillRunner(userRepository, accountRepository, accountNumberGenerator)

    private fun user(id: String) = User(
        id = id,
        phoneNumber = "+25078800$id",
        firstName = "Test",
        lastName = "User",
        passwordHash = "hash",
        createdAt = Instant.now(),
    )

    @Test
    fun `provisions a PAY account only for the users missing one`() {
        val withPay = user("user_1")
        val withoutPay = user("user_2")
        `when`(userRepository.findAll()).thenReturn(listOf(withPay, withoutPay))
        `when`(accountRepository.findByUserIdInAndType(listOf("user_1", "user_2"), AccountType.PAY))
            .thenReturn(listOf(Account(id = "account_existing", userId = "user_1", accountNumber = "1", accountName = "x", type = AccountType.PAY, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)))
        `when`(accountNumberGenerator.generate(2024100000L)).thenReturn("2024100001")

        runner.run()

        val captor = ArgumentCaptor.forClass(Account::class.java)
        verify(accountRepository, times(1)).save(captor.capture())
        assertEquals("user_2", captor.value.userId)
        assertEquals(AccountType.PAY, captor.value.type)
        assertEquals(BigDecimal.ZERO, captor.value.balance)
    }

    @Test
    fun `does nothing when every user already has a PAY account`() {
        val withPay = user("user_1")
        `when`(userRepository.findAll()).thenReturn(listOf(withPay))
        `when`(accountRepository.findByUserIdInAndType(listOf("user_1"), AccountType.PAY))
            .thenReturn(listOf(Account(id = "account_existing", userId = "user_1", accountNumber = "1", accountName = "x", type = AccountType.PAY, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)))

        runner.run()

        verify(accountRepository, times(1)).findByUserIdInAndType(listOf("user_1"), AccountType.PAY)
        verifyNoMoreInteractions(accountRepository)
    }

    @Test
    fun `does nothing when there are no users at all`() {
        `when`(userRepository.findAll()).thenReturn(emptyList())

        runner.run()

        verifyNoInteractions(accountRepository)
    }
}
