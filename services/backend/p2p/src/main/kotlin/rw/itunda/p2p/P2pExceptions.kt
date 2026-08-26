package rw.itunda.p2p

// Real fix (2026-08-26): split out of P2pService.kt once that file grew past its
// file-size-lint baseline. Pure exception declarations + one small DTO, no behavior
// -- zero-risk mechanical move, same package so no import changes anywhere.

class P2pRequestNotFoundException(message: String) : RuntimeException(message)
class P2pRequestNotPayableException(message: String) : RuntimeException(message)
class P2pSelfPaymentException(message: String) : RuntimeException(message)
class P2pNoAccountException(message: String) : RuntimeException(message)
class P2pRecipientNotFoundException(message: String) : RuntimeException(message)
class P2pInvalidAmountException(message: String) : RuntimeException(message)
class P2pTransferLimitExceededException(message: String) : RuntimeException(message)

/**
 * Real Toss/Kakao Bank-style recipient-name confirmation payload -- see
 * [P2pService.resolveRecipient]'s own doc comment for the full sourced account of why
 * this exists. Deliberately just the two fields a client needs to render "Send X RWF
 * to [displayName]?"; never leaks the recipient's phone number or account number back
 * out (the caller already knows the identifier they typed in).
 */
data class P2pRecipientPreview(val recipientUserId: String, val displayName: String)
