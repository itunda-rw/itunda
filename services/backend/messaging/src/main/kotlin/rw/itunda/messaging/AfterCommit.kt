package rw.itunda.messaging

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Runs external delivery only after the surrounding database transaction commits.
 * A WebSocket frame or mobile push cannot be rolled back, so sending it while a
 * message transaction is still open can advertise a message that never becomes
 * durable. The fallback keeps command-line and focused unit-test callers useful
 * when they intentionally invoke a service without Spring transaction advice.
 */
internal fun runAfterCommit(action: () -> Unit) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        action()
        return
    }
    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() = action()
    })
}
