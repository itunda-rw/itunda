package rw.itunda.messaging.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.messaging.EmoticonGiftRecipientNotFoundException
import rw.itunda.messaging.EmoticonGiftToSelfException
import rw.itunda.messaging.EmoticonNoAccountException
import rw.itunda.messaging.EmoticonNotFoundException
import rw.itunda.messaging.EmoticonPackAlreadyOwnedException
import rw.itunda.messaging.EmoticonPackNotFoundException
import rw.itunda.messaging.EmoticonPackNotOwnedException
import rw.itunda.messaging.EmoticonService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException

data class SendEmoticonRequest(val emoticonId: String)
data class GiftEmoticonPackRequest(val recipientPhoneNumber: String)

// Real KakaoTalk Emoticon Store -- see EmoticonService's own doc comment.
@RestController
@RequestMapping("/api/v1/emoticons")
class EmoticonController(private val emoticonService: EmoticonService) {

    @GetMapping("/packs")
    fun listPacks() = ResponseEntity.ok(mapOf("success" to true, "packs" to emoticonService.listPacks()))

    @GetMapping("/packs/{packId}")
    fun listPackEmoticons(@PathVariable packId: String) =
        ResponseEntity.ok(mapOf("success" to true, "emoticons" to emoticonService.listPackEmoticons(packId)))

    @GetMapping("/packs/owned")
    fun listOwnedPacks(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "packs" to emoticonService.listOwnedPacks(currentUser.userId)))

    @PostMapping("/packs/{packId}/purchase")
    fun purchasePack(@PathVariable packId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val owned = emoticonService.purchasePack(currentUser.userId, packId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "ownedPack" to owned))
    }

    // Real gifting-a-pack-to-another-user (2026-07-28) -- see EmoticonService's own doc
    // comment. Recipient identified by phone number, same convention P2pController's own
    // /send already establishes.
    @PostMapping("/packs/{packId}/gift")
    fun giftPack(
        @PathVariable packId: String,
        @RequestBody request: GiftEmoticonPackRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val gifted = emoticonService.giftPack(currentUser.userId, request.recipientPhoneNumber, packId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "giftedPack" to gifted))
    }

    // Send an owned emoticon into a real 1:1 conversation, same path family as
    // MessagingController's own /conversations/{conversationId}/messages.
    @PostMapping("/conversations/{conversationId}/send")
    fun sendEmoticon(
        @PathVariable conversationId: String,
        @RequestBody request: SendEmoticonRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = emoticonService.sendEmoticon(currentUser.userId, conversationId, request.emoticonId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    // Same, for a real group thread.
    @PostMapping("/groups/{groupId}/send")
    fun sendGroupEmoticon(
        @PathVariable groupId: String,
        @RequestBody request: SendEmoticonRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = emoticonService.sendGroupEmoticon(currentUser.userId, groupId, request.emoticonId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    @ExceptionHandler(EmoticonPackNotFoundException::class)
    fun handlePackNotFound(ex: EmoticonPackNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("EMOTICON_PACK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmoticonNotFoundException::class)
    fun handleEmoticonNotFound(ex: EmoticonNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("EMOTICON_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmoticonPackAlreadyOwnedException::class)
    fun handleAlreadyOwned(ex: EmoticonPackAlreadyOwnedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("EMOTICON_PACK_ALREADY_OWNED", ex.message ?: "Conflict"))

    @ExceptionHandler(EmoticonPackNotOwnedException::class)
    fun handleNotOwned(ex: EmoticonPackNotOwnedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("EMOTICON_PACK_NOT_OWNED", ex.message ?: "Forbidden"))

    @ExceptionHandler(EmoticonNoAccountException::class)
    fun handleNoAccount(ex: EmoticonNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmoticonGiftRecipientNotFoundException::class)
    fun handleGiftRecipientNotFound(ex: EmoticonGiftRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GIFT_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmoticonGiftToSelfException::class)
    fun handleGiftToSelf(ex: EmoticonGiftToSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GIFT_TO_SELF", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))
}
