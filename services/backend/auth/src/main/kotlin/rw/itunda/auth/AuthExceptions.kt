package rw.itunda.auth

class PhoneAlreadyRegisteredException(message: String) : RuntimeException(message)
class InvalidCredentialsException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)
class InvalidRefreshTokenException(message: String) : RuntimeException(message)
class ReferralCodeNotFoundException(message: String) : RuntimeException(message)
