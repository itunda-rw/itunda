package rw.itunda.wallet

class WalletNotFoundException(message: String) : RuntimeException(message)
class QuoteNotFoundException(message: String) : RuntimeException(message)
class QuoteExpiredException(message: String) : RuntimeException(message)
class QuoteAlreadyUsedException(message: String) : RuntimeException(message)
