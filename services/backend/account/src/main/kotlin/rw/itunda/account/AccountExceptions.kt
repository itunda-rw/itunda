package rw.itunda.account

class AccountNotFoundException(message: String) : RuntimeException(message)
class QuoteNotFoundException(message: String) : RuntimeException(message)
class QuoteExpiredException(message: String) : RuntimeException(message)
class QuoteAlreadyUsedException(message: String) : RuntimeException(message)
