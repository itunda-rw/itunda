package rw.itunda.auth

class PhoneAlreadyRegisteredException(message: String) : RuntimeException(message)
class InvalidCredentialsException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)
class InvalidRefreshTokenException(message: String) : RuntimeException(message)
class ReferralCodeNotFoundException(message: String) : RuntimeException(message)
class NoEmailOnFileException(message: String) : RuntimeException(message)
class EmailAlreadyVerifiedException(message: String) : RuntimeException(message)
class InvalidVerificationTokenException(message: String) : RuntimeException(message)
