package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/v1/analytics/events")
    suspend fun recordAnalyticsEvent(@Body request: RecordAnalyticsEventRequest): SuccessResponse

    @GET("api/v1/account")
    suspend fun getAccounts(): AccountResponse

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.account.
    // AccountService.getSpendingInsight, real since 2026-07-13) -- first Android client
    // for this feature (item 107, found backend-only via a fresh matrix scan; bank-mfe
    // ported the same day as item 106). Ledger-based, not the transactions table -- see
    // the backend's own doc comment for the full account.
    @GET("api/v1/account/spending")
    suspend fun getSpendingInsight(): SpendingInsightResponse

    // Real business expense summary (2026-08-11) -- see backend's
    // AccountService.getBusinessExpenseSummary doc comment for the real Toss Bank
    // 세금 신고용 이용내역 자동발송 (tax-filing usage summary) pattern this closes the
    // honest slice of: a categorized, period-scoped summary of the BUSINESS account's
    // own real ledger history, same categorization as getSpendingInsight above.
    @GET("api/v1/account/business-expense-summary")
    suspend fun getBusinessExpenseSummary(@Query("sinceMonthsAgo") sinceMonthsAgo: Long = 3): BusinessExpenseSummaryResponse

    // Real Toss budgets/limits equivalent (item 165/172) -- AccountService.setBudget/
    // getBudgets, exposed on the pre-existing AccountController (no dedicated
    // controller). Per-category or overall (category == null) monthly limit, with
    // a real 80%/100%-threshold in-app Notification + push (maybeNotifyBudgetThreshold).
    @GET("api/v1/account/budgets")
    suspend fun getBudgets(): GetBudgetsResponse

    @POST("api/v1/account/budgets")
    suspend fun setBudget(@Body request: SetBudgetRequest): SetBudgetResponse

    // Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/176) -- see
    // AutoTopUpService's own doc comment. getSetting real-404s (AUTO_TOPUP_SETTING_NOT_
    // FOUND) if this account has no setting configured yet -- normal, not caught here.
    @GET("api/v1/account/{accountId}/auto-topup")
    suspend fun getAutoTopUpSetting(@Path("accountId") accountId: String): GetAutoTopUpSettingResponse

    @PUT("api/v1/account/{accountId}/auto-topup")
    suspend fun configureAutoTopUp(@Path("accountId") accountId: String, @Body request: ConfigureAutoTopUpRequest): GetAutoTopUpSettingResponse

    @POST("api/v1/account/{accountId}/auto-topup/trigger")
    suspend fun triggerAutoTopUp(@Path("accountId") accountId: String, @Header("Idempotency-Key") idempotencyKey: String): TriggerAutoTopUpResponse

    // Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
    // rw.itunda.account.web.ForeignCurrencyController.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful open would previously resubmit here
    // and hit the backend's own ForeignCurrencyAccountAlreadyExistsException guard
    // on retry.
    @POST("api/v1/account/foreign-currency/accounts")
    suspend fun openForeignAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OpenForeignAccountRequest): ForeignAccountResponse

    @GET("api/v1/account/foreign-currency/accounts")
    suspend fun getForeignAccounts(): ForeignAccountsResponse

    @GET("api/v1/account/foreign-currency/rate")
    suspend fun getExchangeRate(@Query("from") from: String, @Query("to") to: String): ExchangeRateResponse

    // Idempotency-Key added 2026-09-05 -- convert genuinely moves real money
    // between the caller's own accounts with NO duplicate-prevention guard at all,
    // so a lost response after a successful conversion would previously resubmit
    // here and silently execute the SAME conversion twice.
    @POST("api/v1/account/foreign-currency/convert")
    suspend fun convertCurrency(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConvertCurrencyRequest): CurrencyConversionResponse

    @GET("api/v1/account/foreign-currency/conversions")
    suspend fun getMyConversions(): CurrencyConversionsResponse

    // SetRateAlertRequest's own doc comment.
    @POST("api/v1/account/foreign-currency/rate-alert")
    suspend fun setRateAlert(@Body request: SetRateAlertRequest): SetRateAlertResponse

    @DELETE("api/v1/account/foreign-currency/rate-alert")
    suspend fun clearRateAlert(@Query("fromCurrency") fromCurrency: String, @Query("toCurrency") toCurrency: String): SimpleSuccessResponse

    @GET("api/v1/account/foreign-currency/rate-alerts")
    suspend fun getMyRateAlerts(): RateAlertsResponse

    @GET("api/v1/discover")
    suspend fun getDiscoverItems(): DiscoverResponse

    // Real "verify with itunda" partner consent flow. IdentityVerificationService's own
    // doc comment states the itunda app "shows the user a real consent screen naming the
    // partner and exactly what will be shared" -- but no client on any platform had ever
    // called these, so a partner could create a request the user could never answer and
    // the flow could only ever expire (found 2026-08-14). getIdentityVerificationRequest
    // is deliberately unauthenticated on the backend (it names only the partner and the
    // field labels, never the user's own data); approve/decline both require the real
    // signed-in user, which is what makes the consent meaningful.
    @GET("api/v1/identity/verification/{requestId}")
    suspend fun getIdentityVerificationRequest(@Path("requestId") requestId: String): IdentityVerificationRequestResponse

    @POST("api/v1/identity/verification/{requestId}/approve")
    suspend fun approveIdentityVerification(@Path("requestId") requestId: String): SimpleSuccessResponse

    @POST("api/v1/identity/verification/{requestId}/decline")
    suspend fun declineIdentityVerification(@Path("requestId") requestId: String): SimpleSuccessResponse

    @GET("api/v1/savings/goals")
    suspend fun getSavingsGoals(): SavingsGoalsResponse

    // Real savings-goal creation. The backend endpoint and bank-mfe's own createGoal
    // have both existed for a long time, but Android had only the GET above -- so the
    // "Save & grow" goal list could never be anything but empty on this platform
    // (found 2026-08-14). Mirrors backend SavingsController.CreateGoalRequest exactly.
    @POST("api/v1/savings/goals")
    suspend fun createSavingsGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateSavingsGoalRequest): CreateSavingsGoalResponse

    @GET("api/v1/savings/goals/{id}/transactions")
    suspend fun getSavingsGoalTransactions(@Path("id") id: String): BucketTransactionsResponse

    @GET("api/v1/savings/interest-jar")
    suspend fun getInterestJar(): InterestJarResponse

    @GET("api/v1/savings/interest-jar/transactions")
    suspend fun getInterestJarTransactions(): BucketTransactionsResponse

    // Real Deposit Protection Fund status (2026-08-11) -- see backend's
    // DepositProtectionFund.kt doc comment for the full honesty framing.
    @GET("api/v1/savings/deposit-protection")
    suspend fun getDepositProtectionStatus(): DepositProtectionStatusResponse

    @POST("api/v1/account/transfer/quote")
    suspend fun quoteTransfer(@Body request: QuoteTransferRequest): QuoteTransferResponse

    @POST("api/v1/account/transfer/confirm")
    suspend fun confirmTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConfirmTransferRequest): ConfirmTransferResponse

    // Real Toss "충전하기"/"옮기기" reference (2026-09-12) -- see the backend's
    // AccountService.transferBetweenOwnAccounts doc comment. A purely internal,
    // zero-fee move between two of the caller's own real accounts -- deliberately
    // NOT quoteTransfer/confirmTransfer above (those route through an external
    // provider rail and charge a real 1% fee).
    @POST("api/v1/account/internal-transfer")
    suspend fun internalTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: InternalTransferRequest): InternalTransferResponse

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // MainViewModel.sendTransfer.
    @POST("api/v1/p2p/send")
    suspend fun sendDirect(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendDirectP2pRequest): SendDirectP2pResponse

    // Real Naver Pay "가족 공유 자산 관리" -- see SendToFamilyMemberRequest's own doc
    // comment.
    @POST("api/v1/p2p/send-to-family")
    suspend fun sendToFamilyMember(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendToFamilyMemberRequest): SendToFamilyMemberResponse

    @POST("api/v1/p2p/request")
    suspend fun generateP2pRequest(@Body request: GenerateP2pRequest): GenerateP2pRequestResponse

    @GET("api/v1/p2p/requests")
    suspend fun getMyP2pRequests(): GetP2pRequestsResponse

    @POST("api/v1/p2p/pay/{requestId}")
    suspend fun payP2pRequest(@Path("requestId") requestId: String, @Header("Idempotency-Key") idempotencyKey: String): PayP2pRequestResponse

    // Real idempotency fix (item 235, found via a periodic Idempotency-Key coverage
    // audit) -- a retry created a second active recurring-transfer row to the same
    // recipient, which the scheduler then executes independently: a real duplicate
    // charge every period going forward. See backend AutoTransferController.create's
    // own doc comment for the full account.
    @POST("api/v1/p2p/auto-transfers")
    suspend fun createAutoTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateAutoTransferRequest): AutoTransferResponse

    @GET("api/v1/p2p/auto-transfers")
    suspend fun getMyAutoTransfers(): AutoTransfersListResponse

    @POST("api/v1/p2p/auto-transfers/{id}/pause")
    suspend fun pauseAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @POST("api/v1/p2p/auto-transfers/{id}/resume")
    suspend fun resumeAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @DELETE("api/v1/p2p/auto-transfers/{id}")
    suspend fun cancelAutoTransfer(@Path("id") id: String): AutoTransferResponse

    // Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
    // ScheduledTransferDto's own doc comment. ScheduledTransferController existed fully
    // on the backend with zero client anywhere until now.
    @POST("api/v1/p2p/scheduled-transfers")
    suspend fun createScheduledTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateScheduledTransferRequest): ScheduledTransferResponse

    @GET("api/v1/p2p/scheduled-transfers")
    suspend fun getMyScheduledTransfers(): ScheduledTransfersListResponse

    @POST("api/v1/p2p/scheduled-transfers/{id}/cancel")
    suspend fun cancelScheduledTransfer(@Path("id") id: String): ScheduledTransferResponse

    // Real Korean 지연이체서비스 (Delayed Transfer Service) -- see DelayedTransferDto's
    // own doc comment. Already real on iOS/web; Android had zero client anywhere until
    // now despite the backend being fully built.
    @POST("api/v1/p2p/send-delayed")
    suspend fun sendDelayed(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendDelayedTransferRequest): DelayedTransferResponse

    @GET("api/v1/p2p/delayed-transfers")
    suspend fun getMyDelayedTransfers(): DelayedTransfersListResponse

    @POST("api/v1/p2p/delayed-transfers/{id}/cancel")
    suspend fun cancelDelayedTransfer(@Path("id") id: String): DelayedTransferResponse

    @GET("api/v1/p2p/scam-reports/check")
    suspend fun checkScamStatus(@Query("identifier") identifier: String): ScamCheckResponse

    @GET("api/v1/p2p/recipient")
    suspend fun resolveRecipient(@Query("identifier") identifier: String): ResolveRecipientResponse

    @GET("api/v1/p2p/transfer-limit")
    suspend fun getTransferLimit(): TransferLimitResponse

    @POST("api/v1/p2p/scam-reports")
    suspend fun reportScam(@Body request: ReportScamRequest): ScamReportResponse

    @GET("api/v1/p2p/scam-reports/mine")
    suspend fun getMyScamReports(): ScamReportsListResponse

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/withdraw")
    suspend fun withdrawFromGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: WithdrawRequest): WithdrawResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // No Idempotency-Key -- a settings write, not money movement itself. See
    // RoundUpController.kt's own doc comment.
    @GET("api/v1/savings/round-up")
    suspend fun getRoundUpSettings(): RoundUpSettingsResponse

    @POST("api/v1/savings/round-up")
    suspend fun setRoundUpSettings(@Body request: SetRoundUpSettingsRequest): RoundUpSettingsResponse

    @POST("api/v1/account/agent-withdrawal-authorizations")
    suspend fun createAgentWithdrawalAuthorization(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateAgentWithdrawalAuthorizationRequest,
    ): AgentWithdrawalAuthorizationResponse

    @GET("api/v1/account/agent-withdrawal-authorizations")
    suspend fun getAgentWithdrawalAuthorizations(): AgentWithdrawalAuthorizationsResponse

    @POST("api/v1/account/agent-withdrawal-authorizations/cancel")
    suspend fun cancelAgentWithdrawalAuthorization(@Body request: CancelAgentWithdrawalAuthorizationRequest): AgentWithdrawalAuthorizationResponse

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen; see services/backend/account's new AccountController endpoint.
    @GET("api/v1/account/transactions")
    suspend fun getTransactionHistory(): TransactionHistoryResponse

    // Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21) --
    // scoped to one account's own transactions, not getTransactionHistory's mix of
    // every account. See AccountService.getAccountTransactionHistory on the backend.
    @GET("api/v1/account/{id}/transactions")
    suspend fun getAccountTransactionHistory(@retrofit2.http.Path("id") id: String): TransactionHistoryResponse

    // Real Toss Bank reference (2026-09-12, "계좌 별명" -- account nickname) -- see
    // AccountService.setNickname's own doc comment on the backend.
    @PATCH("api/v1/account/{id}/nickname")
    suspend fun setAccountNickname(@retrofit2.http.Path("id") id: String, @Body request: SetAccountNicknameRequest): SetAccountNicknameResponse

    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): MarkReadResponse

    // Real offline-action-queue replay (2026-07-13) -- see network/OfflineActionQueue.kt.
    @POST("api/v1/actions/batch")
    suspend fun submitActionBatch(@Body request: BatchRequest): BatchResponse

    // Real Partner SDK catalog (2026-07-17) -- services/backend/partners's
    // MiniAppCatalogController, itunda-user-JWT-gated like every other endpoint on this
    // interface (no ADMIN role, no partner API key -- this is the public "app store"
    // surface a logged-in itunda user's own client fetches). See
    // miniapps/PartnerMiniAppLoader.kt for what actually happens when one of these is tapped.
    @GET("api/v1/mini-apps/catalog")
    suspend fun getMiniAppCatalog(@Query("category") category: String? = null, @Query("size") size: Int = 100): MiniAppCatalogResponse

    // Real 1:1 messaging (2026-07-18) -- see rw.itunda.messaging.web.MessagingController.
    @POST("api/v1/messages/conversations")
    suspend fun startConversation(@Body request: StartConversationRequest): ConversationResponse

    // Real pagination-discard fix (same systemic gap fixed on web, 2026-09-11
    // -- see project_itunda_pagination_discard_sweep memory) -- page/size
    // just weren't ever sent, silently capping the Talk conversation list at
    // its first 20 rows.
    @GET("api/v1/messages/conversations")
    suspend fun getConversations(@Query("archived") archived: Boolean = false, @Query("page") page: Int = 0, @Query("size") size: Int = 20): ConversationsResponse

    @GET("api/v1/messages/contacts")
    suspend fun getTalkContacts(): TalkContactsResponse

    // Real KakaoTalk-style "오늘의 생일" (Today's Birthday) -- ported from bank-mfe
    // (2026-09-03). Reuses the same TalkContactDto shape as getTalkContacts.
    @GET("api/v1/messages/contacts/birthdays-today")
    suspend fun getTodaysBirthdays(): TalkContactsResponse

    @GET("api/v1/messages/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): MessagesResponse

    @GET("api/v1/messages/conversations/{id}/messages/search")
    suspend fun searchMessages(@Path("id") conversationId: String, @Query("query") query: String): MessagesResponse

    @POST("api/v1/messages/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body request: SendMessageRequest): MessageResponse

    @DELETE("api/v1/messages/conversations/{id}/messages/{messageId}")
    suspend fun deleteMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    // Real Thread support (2026-08-05) -- see MessageDto.replyCount's own doc comment.
    // Root message first, then every direct reply oldest-first.
    @GET("api/v1/messages/conversations/{id}/messages/{messageId}/thread")
    suspend fun getThread(@Path("id") conversationId: String, @Path("messageId") messageId: String): MessagesResponse

    // Real message forwarding (2026-08-04) -- see ForwardMessageRequest's own doc
    // comment: MessageForwardService (backend, 2026-07-25) already fully supported
    // forwarding any 1:1 or group message to any 1:1 or group destination, with zero
    // Retrofit method anywhere -- the same "defined but uncalled" pattern this session
    // already found for group pin/photo messages. Two methods per real source (this
    // message is the source) since the response shape depends on which destination type
    // the caller picks -- both hit the identical real endpoint, Retrofit routes purely
    // by Kotlin signature, not a conflict.
    @POST("api/v1/messages/messages/{id}/forward")
    suspend fun forwardDirectMessageToConversation(@Path("id") messageId: String, @Body request: ForwardMessageRequest): MessageResponse

    @POST("api/v1/messages/messages/{id}/forward")
    suspend fun forwardDirectMessageToGroup(@Path("id") messageId: String, @Body request: ForwardMessageRequest): GroupMessageResponse

    @POST("api/v1/messages/groups/messages/{id}/forward")
    suspend fun forwardGroupMessageToConversation(@Path("id") messageId: String, @Body request: ForwardMessageRequest): MessageResponse

    @POST("api/v1/messages/groups/messages/{id}/forward")
    suspend fun forwardGroupMessageToGroup(@Path("id") messageId: String, @Body request: ForwardMessageRequest): GroupMessageResponse

    @GET("api/v1/messages/conversations/{id}/pin")
    suspend fun getPinnedConversationMessage(@Path("id") conversationId: String): PinnedMessageResponse

    @POST("api/v1/messages/conversations/{id}/pin/{messageId}")
    suspend fun pinConversationMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/pin")
    suspend fun unpinConversationMessage(@Path("id") conversationId: String): SuccessResponse

    @POST("api/v1/messages/conversations/{id}/block")
    suspend fun blockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/block")
    suspend fun unblockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @GET("api/v1/messages/conversations/{id}/quiet")
    suspend fun getConversationQuiet(@Path("id") conversationId: String): ConversationQuietResponse

    @POST("api/v1/messages/conversations/{id}/quiet")
    suspend fun setConversationQuiet(@Path("id") conversationId: String, @Body request: SetConversationQuietRequest): ConversationQuietResponse

    @GET("api/v1/messages/conversations/{id}/archive")
    suspend fun getConversationArchived(@Path("id") conversationId: String): ConversationArchivedResponse

    @POST("api/v1/messages/conversations/{id}/archive")
    suspend fun setConversationArchived(@Path("id") conversationId: String, @Body request: SetConversationArchivedRequest): ConversationArchivedResponse

    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
    // ConversationSummaryDto.pinnedToTop's own doc comment. Found fully built on the
    // backend (MessagingController POST/GET .../pin-to-top) with zero Android caller,
    // same defined-but-uncalled shape this session already found and closed for
    // bank-mfe (Section 165) -- Android/iOS were explicitly left for follow-up then.
    @GET("api/v1/messages/conversations/{id}/pin-to-top")
    suspend fun getConversationPinnedToTop(@Path("id") conversationId: String): ConversationPinnedToTopResponse

    @POST("api/v1/messages/conversations/{id}/pin-to-top")
    suspend fun setConversationPinnedToTop(@Path("id") conversationId: String, @Body request: SetConversationPinnedToTopRequest): ConversationPinnedToTopResponse

    @POST("api/v1/chat/reports")
    suspend fun reportChatMessage(@Body request: CreateChatReportRequest): SuccessResponse

    // Real toggle -- tapping an already-active reaction removes it, same semantics as
    // MessagingService.toggleReaction on the backend.
    @POST("api/v1/messages/messages/{id}/reactions")
    suspend fun toggleReaction(@Path("id") messageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    @POST("api/v1/messages/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): GroupResponse

    // Real pagination-discard fix (same systemic gap fixed on web, 2026-09-11)
    // -- page/size just weren't ever sent, silently capping the Talk
    // group-chat list at its first 20 rows.
    @GET("api/v1/messages/groups")
    suspend fun getMyGroups(@Query("page") page: Int = 0, @Query("size") size: Int = 20): GroupsResponse

    // Real KakaoTalk 오픈채팅-style open group (Talk-parity port, item 244) -- see
    // GroupMessagingService.createOpenGroup's own doc comment. bank-mfe/iOS already
    // have this; this is the Android port. Anyone with the real joinCode can join
    // without being invited by phone number first -- distinct from createGroup
    // above, which requires knowing everyone's real number up front.
    @POST("api/v1/messages/groups/open")
    suspend fun createOpenGroup(@Body request: CreateOpenGroupRequest): OpenGroupResponse

    @POST("api/v1/messages/groups/join")
    suspend fun joinGroupByCode(@Body request: JoinGroupByCodeRequest): JoinGroupResponse

    @GET("api/v1/messages/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): GroupMessagesResponse

    // Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
    // see searchMessages's own doc comment for the 1:1 equivalent this mirrors.
    @GET("api/v1/messages/groups/{id}/messages/search")
    suspend fun searchGroupMessages(@Path("id") groupId: String, @Query("query") query: String): GroupMessagesResponse

    @POST("api/v1/messages/groups/{id}/messages")
    suspend fun sendGroupMessage(@Path("id") groupId: String, @Body request: SendGroupMessageRequest): GroupMessageResponse

    @DELETE("api/v1/messages/groups/{id}/messages/{messageId}")
    suspend fun deleteGroupMessage(@Path("id") groupId: String, @Path("messageId") messageId: String): SuccessResponse

    // Real Thread support (2026-08-05) -- see getThread's own doc comment; identical
    // shape for group chat.
    @GET("api/v1/messages/groups/{id}/messages/{messageId}/thread")
    suspend fun getGroupThread(@Path("id") groupId: String, @Path("messageId") messageId: String): GroupMessagesResponse

    // Real group-chat pin (2026-08-04) -- see GroupThreadView's own doc comment: the
    // real backend (GroupMessagingController, 2026-07-26) had no Retrofit client method
    // or UI anywhere until now. Mirrors getPinnedConversationMessage/
    // pinConversationMessage/unpinConversationMessage's own 1:1 shape exactly.
    @GET("api/v1/messages/groups/{id}/pin")
    suspend fun getPinnedGroupMessage(@Path("id") groupId: String): GroupPinnedMessageResponse

    @POST("api/v1/messages/groups/{id}/pin/{messageId}")
    suspend fun pinGroupMessage(@Path("id") groupId: String, @Path("messageId") messageId: String): SuccessResponse

    @DELETE("api/v1/messages/groups/{id}/pin")
    suspend fun unpinGroupMessage(@Path("id") groupId: String): SuccessResponse

    @POST("api/v1/messages/groups/messages/{id}/reactions")
    suspend fun toggleGroupReaction(@Path("id") groupMessageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    @POST("api/v1/messages/groups/{id}/members")
    suspend fun addGroupMember(@Path("id") groupId: String, @Body request: AddGroupMemberRequest): GroupResponse

    @DELETE("api/v1/messages/groups/{id}/members/me")
    suspend fun leaveGroup(@Path("id") groupId: String): LeaveGroupResponse

    @GET("api/v1/messages/groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") groupId: String): GroupMembersResponse

    @POST("api/v1/messages/groups/{id}/photo")
    suspend fun setGroupPhotoUrl(@Path("id") groupId: String, @Body request: SetGroupPhotoUrlRequest): GroupSummaryResponse

    @POST("api/v1/messages/groups/{id}/description")
    suspend fun setGroupDescription(@Path("id") groupId: String, @Body request: SetGroupDescriptionRequest): GroupSummaryResponse

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment on the backend. Works for any set of user ids, not just 1:1
    // conversation partners -- e.g. a group thread can pass every member's id.
    @GET("api/v1/messages/presence")
    suspend fun getPresence(@Query("userIds") userIds: List<String>): PresenceResponse

    // Real photo upload (2026-07-24) -- see rw.itunda.marketplace.web.UploadController.
    @Multipart
    @POST("api/v1/uploads")
    suspend fun uploadPhoto(@Part file: okhttp3.MultipartBody.Part): UploadResponse

    // Real 당근마켓-style marketplace (2026-07-18) -- see rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings")
    suspend fun createListing(@Body request: CreateListingRequest): ListingResponse

    // Real single-listing fetch (item 270) -- a real, defined-but-uncalled backend
    // endpoint found via this session's own "check before assuming a gap needs new
    // backend work" discipline: MarketplaceController.getListing has existed since
    // 2026-07-18 with zero callers on any client (Android/iOS/web all only ever
    // called this listing's own SUB-resources -- mark-sold/boost/review -- never the
    // plain listing itself). Needed for a real "recently viewed listings" rail entry
    // to reopen a listing that's since scrolled out of the currently-loaded feed.
    @GET("api/v1/marketplace/listings/{id}")
    suspend fun getListing(@Path("id") listingId: String): ListingResponse

    // Real pagination-discard fix (same systemic gap fixed for Knowledge/
    // Community/Marketplace-web/Jobs-web/RealEstate-web, 2026-09-09 -- see
    // project_itunda_pagination_discard_sweep memory) -- page/size just
    // weren't ever sent, silently capping every Marketplace feed at its
    // first 20 listings.
    @GET("api/v1/marketplace/listings")
    suspend fun browseListings(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): ListingsResponse

    @GET("api/v1/marketplace/listings/nearby")
    suspend fun getNearbyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): ListingsResponse

    @GET("api/v1/marketplace/my-listings")
    suspend fun getMyListings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ListingsResponse

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend ListingRepository's own doc comment.
    @GET("api/v1/marketplace/my-purchases")
    suspend fun getMyPurchases(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Real 400 NEIGHBORHOOD_NOT_SET if the caller hasn't set one yet.
    @GET("api/v1/marketplace/listings/my-neighborhood")
    suspend fun getListingsMyNeighborhood(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): ListingsResponse

    // Real relevance-ranked search (2026-08-14) -- see backend MarketplaceService
    // .search's own doc comment. Not neighborhood-scoped, unlike the browse above.
    @GET("api/v1/marketplace/listings/search")
    suspend fun searchListings(@Query("q") query: String): ListingsResponse

    @POST("api/v1/marketplace/listings/{id}/mark-sold")
    suspend fun markListingSold(@Path("id") listingId: String, @Body request: MarkSoldRequest = MarkSoldRequest()): ListingResponse

    // Real 당근마켓 끌어올리기 (bump to top of feed), 2026-08-10 -- see
    // rw.itunda.marketplace.web.MarketplaceController.bumpListing. Free, no real money
    // moves, so no Idempotency-Key, unlike boostListing below.
    @POST("api/v1/marketplace/listings/{id}/bump")
    suspend fun bumpListing(@Path("id") listingId: String): ListingResponse

    // Real 가격 수정 (price edit) + Karrot 가격 하락 알림 -- see backend
    // MarketplaceService.updatePrice's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check. Not money-moving itself, so no Idempotency-Key, matching bumpListing above.
    @PATCH("api/v1/marketplace/listings/{id}/price")
    suspend fun updateListingPrice(@Path("id") listingId: String, @Body request: UpdateListingPriceRequest): ListingResponse

    // Real seller-paid sponsored placement (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.boostListing.
    @GET("api/v1/marketplace/boost-tiers")
    suspend fun getBoostTiers(): BoostTiersResponse

    @POST("api/v1/marketplace/listings/{id}/boost")
    suspend fun boostListing(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: BoostListingRequest): ListingResponse

    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings/{id}/pay-escrow")
    suspend fun payEscrow(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: PayEscrowRequest = PayEscrowRequest()): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/confirm-receipt")
    suspend fun confirmEscrowReceipt(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/dispute-escrow")
    suspend fun disputeEscrow(@Path("id") listingId: String, @Body request: DisputeEscrowRequest): MarketplaceEscrowResponse

    @GET("api/v1/marketplace/listings/{id}/escrow")
    suspend fun getEscrow(@Path("id") listingId: String): MarketplaceEscrowResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/marketplace/listings/{id}/review")
    suspend fun submitListingReview(@Path("id") listingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/marketplace/listings/{id}/review")
    suspend fun getListingReviews(@Path("id") listingId: String): HoodReviewsResponse

    @DELETE("api/v1/marketplace/listings/{id}")
    suspend fun removeListing(@Path("id") listingId: String): ListingResponse

    @POST("api/v1/marketplace/listings/{id}/contact-seller")
    suspend fun contactSeller(@Path("id") listingId: String): ContactSellerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService.
    @POST("api/v1/marketplace/listings/{id}/offers")
    suspend fun makeOffer(@Path("id") listingId: String, @Body request: MakeOfferRequest): PriceOfferResponse

    @POST("api/v1/marketplace/offers/{id}/respond")
    suspend fun respondToOffer(@Path("id") offerId: String, @Body request: RespondToOfferRequest): PriceOfferResponse

    @GET("api/v1/marketplace/conversations/{id}/offers")
    suspend fun getOffersForConversation(@Path("id") conversationId: String): PriceOffersResponse

    // Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe) -- ported here,
    // closing the "Android/iOS don't have this yet" gap that row's own doc comment
    // named. Mirrors addFavoriteRestaurant/removeFavoriteRestaurant exactly.
    @POST("api/v1/marketplace/listings/{id}/favorite")
    suspend fun addListingFavorite(@Path("id") listingId: String): AddFavoriteResponse

    @DELETE("api/v1/marketplace/listings/{id}/favorite")
    suspend fun removeListingFavorite(@Path("id") listingId: String): RemoveFavoriteResponse

    @GET("api/v1/marketplace/listings/favorites")
    suspend fun getMyFavoriteListings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): FavoriteListingsResponse

    // Real like/unlike toggle (2026-08-03) -- see backend MarketplaceController.kt's
    // own doc comment. Idempotent, matching addListingFavorite/removeListingFavorite's
    // own real toggle discipline above.
    @POST("api/v1/marketplace/listings/{id}/like")
    suspend fun toggleListingLike(@Path("id") listingId: String): ToggleLikeResponse

    // Real Karrot "이 글 숨기기" (hide this post) -- see backend ListingHideService's own
    // doc comment. Real, shipped on the backend + bank-mfe (2026-08-24) with zero
    // Android/iOS client until now -- found via a cross-platform-parity check. Mirrors
    // addListingFavorite/removeListingFavorite exactly (POST to hide, DELETE to unhide).
    @POST("api/v1/marketplace/listings/{id}/hide")
    suspend fun hideListing(@Path("id") listingId: String): HideListingResponse

    @DELETE("api/v1/marketplace/listings/{id}/hide")
    suspend fun unhideListing(@Path("id") listingId: String): AddFavoriteResponse

    // Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) -- see
    // HiddenListingDto's own doc comment.
    @GET("api/v1/marketplace/listings/hidden")
    suspend fun getMyHiddenListings(): HiddenListingsResponse

    // Real 당근마켓-style Keyword Alert (rw.itunda.marketplace.KeywordAlertService, real
    // since before this session) -- first Android client for this feature (item 115,
    // found via a content-grep sweep confirming zero client anywhere; bank-mfe ported
    // it the same day as item 114). Real, published Karrot 30-keyword-per-user cap
    // enforced server-side.
    @POST("api/v1/marketplace/keyword-alerts")
    suspend fun addKeywordAlert(@Body request: AddKeywordAlertRequest): KeywordAlertResponse

    @GET("api/v1/marketplace/keyword-alerts")
    suspend fun getKeywordAlerts(): KeywordAlertsResponse

    @DELETE("api/v1/marketplace/keyword-alerts/{id}")
    suspend fun removeKeywordAlert(@Path("id") alertId: String): SuccessResponse

    @POST("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun setKeywordAlertQuietHours(@Body request: SetKeywordAlertQuietHoursRequest): KeywordAlertQuietHoursResponse

    @GET("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun getKeywordAlertQuietHours(): KeywordAlertQuietHoursResponse

    @POST("api/v1/hood/reports")
    suspend fun reportHoodContent(@Body request: CreateHoodReportRequest): HoodReportResponse

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    @POST("api/v1/gifts/conversations/{id}")
    suspend fun sendGiftInConversation(
        @Path("id") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendGiftInConversationRequest,
    ): GiftResponse

    // Real standalone send-by-phone-number (found via an uncalled-endpoint sweep
    // 2026-08-16, backend/bank-mfe/iOS docs Section 88) -- distinct from the chat-
    // embedded call above, this is the general "gift anyone with an itunda account"
    // entry point.
    @POST("api/v1/gifts")
    suspend fun sendGift(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendGiftRequest,
    ): GiftResponse

    @GET("api/v1/gifts/{id}")
    suspend fun getGift(@Path("id") giftId: String): GiftResponse

    @POST("api/v1/gifts/{id}/claim")
    suspend fun claimGift(@Path("id") giftId: String, @Header("Idempotency-Key") idempotencyKey: String): GiftResponse

    @GET("api/v1/gifts/conversations/{id}")
    suspend fun getGiftsForConversation(@Path("id") conversationId: String): GiftsResponse

    // Real KakaoTalk Emoticon Store (item 135) -- see backend Emoticon.kt's own doc
    // comment. Mirrors bank-mfe's lib/emoticons.ts exactly (item 133, this session's
    // first client for this feature).
    @GET("api/v1/emoticons/packs")
    suspend fun getEmoticonPacks(): EmoticonPacksResponse

    @GET("api/v1/emoticons/packs/{packId}")
    suspend fun getPackEmoticons(@Path("packId") packId: String): EmoticonsResponse

    @GET("api/v1/emoticons/packs/owned")
    suspend fun getOwnedEmoticonPacks(): OwnedEmoticonPacksResponse

    @POST("api/v1/emoticons/packs/{packId}/purchase")
    suspend fun purchaseEmoticonPack(@Path("packId") packId: String, @Header("Idempotency-Key") idempotencyKey: String): OwnedEmoticonPackResponse

    @POST("api/v1/emoticons/packs/{packId}/gift")
    suspend fun giftEmoticonPack(@Path("packId") packId: String, @Body request: GiftEmoticonPackRequest, @Header("Idempotency-Key") idempotencyKey: String): GiftedEmoticonPackResponse

    @POST("api/v1/emoticons/conversations/{id}/send")
    suspend fun sendEmoticon(@Path("id") conversationId: String, @Body request: SendEmoticonRequest): MessageResponse

    @POST("api/v1/emoticons/groups/{id}/send")
    suspend fun sendGroupEmoticon(@Path("id") groupId: String, @Body request: SendEmoticonRequest): GroupMessageResponse

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    @GET("api/v1/community/categories")
    suspend fun getCommunityCategories(): CommunityCategoriesResponse

    // Real 동네생활 topic-chip filter row (2026-08-28) -- see backend
    // CommunityService.TOPICS' own doc comment.
    @GET("api/v1/community/topics")
    suspend fun getCommunityTopics(): CommunityTopicsResponse

    @POST("api/v1/community/posts")
    suspend fun createCommunityPost(@Body request: CreateCommunityPostRequest): CommunityPostResponse

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // rw.itunda.community.web.CommunityController.upcomingMeetups.
    @GET("api/v1/community/meetups/upcoming")
    suspend fun getUpcomingMeetups(): CommunityPostsResponse

    // Real pagination-discard fix (named as the systemic sibling of the
    // Knowledge gap fixed on web/Android/iOS 2026-09-09; ported to bank-mfe's
    // own HoodCommunity.tsx first, commit 4ebba547) -- page/size just weren't
    // ever sent, silently capping every Hood feed at its first 20 posts.
    @GET("api/v1/community/posts")
    suspend fun browseCommunityPosts(@Query("category") category: String? = null, @Query("topic") topic: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): CommunityPostsResponse

    @GET("api/v1/community/posts/nearby")
    suspend fun getNearbyCommunityPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): CommunityPostsResponse

    @GET("api/v1/community/my-posts")
    suspend fun getMyCommunityPosts(@Query("page") page: Int = 0, @Query("size") size: Int = 20): CommunityPostsResponse

    // Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- ported
    // from bank-mfe (2026-09-03). Scoped to MY posts only (the preference only affects
    // notifications about comments on posts the caller authored).
    @GET("api/v1/community/notification-preference")
    suspend fun getCommentNotificationsEnabled(): CommentNotificationsEnabledResponse

    @POST("api/v1/community/notification-preference")
    suspend fun setCommentNotificationsEnabled(@Body request: SetCommentNotificationsEnabledRequest): CommentNotificationsEnabledResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/community/posts/my-neighborhood")
    suspend fun getCommunityPostsMyNeighborhood(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): CommunityPostsResponse

    // Real relevance-ranked search (2026-08-14) -- see backend CommunityService
    // .search's own doc comment.
    @GET("api/v1/community/posts/search")
    suspend fun searchCommunityPosts(@Query("q") query: String): CommunityPostsResponse

    @GET("api/v1/community/posts/{id}")
    suspend fun getCommunityPost(@Path("id") postId: String): CommunityPostDetailResponse

    @DELETE("api/v1/community/posts/{id}")
    suspend fun removeCommunityPost(@Path("id") postId: String): CommunityPostResponse

    @GET("api/v1/community/posts/{id}/comments")
    suspend fun getCommunityComments(@Path("id") postId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): CommunityCommentsResponse

    @POST("api/v1/community/posts/{id}/comments")
    suspend fun addCommunityComment(@Path("id") postId: String, @Body request: AddCommunityCommentRequest): CommunityCommentResponse

    @POST("api/v1/community/posts/{id}/like")
    suspend fun toggleCommunityLike(@Path("id") postId: String): ToggleCommunityLikeResponse

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
    // CommunityService.joinMeetup's own doc comment.
    @POST("api/v1/community/posts/{id}/join")
    suspend fun joinCommunityMeetup(@Path("id") postId: String): JoinMeetupResponse

    // Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
    // rw.itunda.community.CommunityService.scheduleMeetupSessions/checkIntoSession's own
    // doc comments. bank-mfe already has this; this is the first Android client.
    @POST("api/v1/community/posts/{postId}/sessions")
    suspend fun scheduleMeetupSessions(@Path("postId") postId: String, @Body request: ScheduleMeetupSessionsRequest): MeetupSessionsResponse

    @GET("api/v1/community/posts/{postId}/sessions")
    suspend fun getMeetupSessions(@Path("postId") postId: String): MeetupSessionsResponse

    @POST("api/v1/community/sessions/{sessionId}/check-in")
    suspend fun checkIntoMeetupSession(@Path("sessionId") sessionId: String): MeetupAttendanceResponse

    @GET("api/v1/community/sessions/{sessionId}/attendance")
    suspend fun getSessionAttendance(@Path("sessionId") sessionId: String): SessionAttendanceResponse

    // Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
    // rw.itunda.community.CommunityService.finalizeGroupBuy's own doc comment. bank-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/community/posts/{postId}/finalize-group-buy")
    suspend fun finalizeGroupBuy(@Path("postId") postId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: FinalizeGroupBuyRequest): SuccessResponse

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    @GET("api/v1/jobs/categories")
    suspend fun getJobCategories(): JobCategoriesResponse

    @POST("api/v1/jobs/posts")
    suspend fun createJobPost(@Body request: CreateJobPostRequest): JobPostResponse

    // Real pagination-discard fix (same systemic gap fixed for Knowledge/
    // Community/Marketplace/Jobs-web, 2026-09-09 -- see
    // project_itunda_pagination_discard_sweep memory) -- page/size just
    // weren't ever sent, silently capping every Jobs feed at its first 20
    // posts.
    @GET("api/v1/jobs/posts")
    suspend fun browseJobPosts(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): JobPostsResponse

    @GET("api/v1/jobs/posts/nearby")
    suspend fun getNearbyJobPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): JobPostsResponse

    @GET("api/v1/jobs/my-posts")
    suspend fun getMyJobPosts(@Query("page") page: Int = 0, @Query("size") size: Int = 20): JobPostsResponse

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend JobPostRepository's own doc comment.
    @GET("api/v1/jobs/my-worked-posts")
    suspend fun getMyWorkedJobPosts(@Query("page") page: Int = 0, @Query("size") size: Int = 20): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/favorite")
    suspend fun addJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @DELETE("api/v1/jobs/posts/{id}/favorite")
    suspend fun removeJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @GET("api/v1/jobs/posts/favorites")
    suspend fun getMyFavoriteJobPosts(@Query("page") page: Int = 0, @Query("size") size: Int = 20): FavoriteJobPostsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/jobs/posts/my-neighborhood")
    suspend fun getJobPostsMyNeighborhood(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): JobPostsResponse

    // Real relevance-ranked search (2026-08-14) -- see backend JobPostService
    // .search's own doc comment.
    @GET("api/v1/jobs/posts/search")
    suspend fun searchJobPosts(@Query("q") query: String): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/mark-filled")
    suspend fun markJobPostFilled(@Path("id") jobPostId: String, @Body request: MarkFilledRequest = MarkFilledRequest()): JobPostResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/review")
    suspend fun submitJobPostReview(@Path("id") jobPostId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/jobs/posts/{id}/review")
    suspend fun getJobPostReviews(@Path("id") jobPostId: String): HoodReviewsResponse

    @DELETE("api/v1/jobs/posts/{id}")
    suspend fun removeJobPost(@Path("id") jobPostId: String): JobPostResponse

    @POST("api/v1/jobs/posts/{id}/contact-poster")
    suspend fun contactPoster(@Path("id") jobPostId: String): ContactPosterResponse

    // Real single-post detail fetch -- see JobPostController.getPost's own contract.
    @GET("api/v1/jobs/posts/{id}")
    suspend fun getJobPost(@Path("id") jobPostId: String): JobPostResponse

    // Real 당근알바-style structured application (2026-07-25) -- see backend
    // JobApplicationService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/apply")
    suspend fun applyToJob(@Path("id") jobPostId: String, @Body request: ApplyToJobRequest): JobApplicationResponse

    @GET("api/v1/jobs/posts/{id}/applications")
    suspend fun getApplicationsForJobPost(@Path("id") jobPostId: String): JobApplicationsResponse

    @GET("api/v1/jobs/my-applications")
    suspend fun getMyJobApplications(): JobApplicationsResponse

    @POST("api/v1/jobs/applications/{id}/respond")
    suspend fun respondToJobApplication(@Path("id") applicationId: String, @Body request: RespondToApplicationRequest): JobApplicationResponse

    // Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
    @GET("api/v1/realestate/property-types")
    suspend fun getPropertyTypes(): PropertyTypesResponse

    @POST("api/v1/realestate/listings")
    suspend fun createPropertyListing(@Body request: CreatePropertyListingRequest): PropertyListingResponse

    // Real pagination-discard fix (same systemic gap fixed for Knowledge/
    // Community/Marketplace/Jobs/RealEstate-web, 2026-09-09 -- see
    // project_itunda_pagination_discard_sweep memory) -- page/size just
    // weren't ever sent, silently capping every RealEstate feed at its
    // first 20 listings.
    @GET("api/v1/realestate/listings")
    suspend fun browsePropertyListings(
        @Query("listingType") listingType: String? = null,
        @Query("propertyType") propertyType: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/valuation")
    suspend fun getPropertyValuation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("propertyType") propertyType: String,
        @Query("listingType") listingType: String,
        @Query("sizeSqm") sizeSqm: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): PropertyValuationResponse

    @GET("api/v1/realestate/listings/nearby")
    suspend fun getNearbyPropertyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/my-listings")
    suspend fun getMyPropertyListings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PropertyListingsResponse

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend PropertyListingRepository's own doc comment.
    @GET("api/v1/realestate/my-acquired-listings")
    suspend fun getMyAcquiredPropertyListings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PropertyListingsResponse
    @POST("api/v1/realestate/listings/{id}/favorite") suspend fun addPropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @DELETE("api/v1/realestate/listings/{id}/favorite") suspend fun removePropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @GET("api/v1/realestate/listings/favorites") suspend fun getMyFavoritePropertyListings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): FavoritePropertyListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Deliberately not combined with listingType/propertyType filters -- an honest v1
    // scoping choice, same as the real backend endpoint this calls.
    @GET("api/v1/realestate/listings/my-neighborhood")
    suspend fun getPropertyListingsMyNeighborhood(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PropertyListingsResponse

    // Real relevance-ranked search (2026-08-14) -- see backend PropertyListingService
    // .search's own doc comment.
    @GET("api/v1/realestate/listings/search")
    suspend fun searchPropertyListings(@Query("q") query: String): PropertyListingsResponse

    @POST("api/v1/realestate/listings/{id}/mark-taken")
    suspend fun markPropertyListingTaken(@Path("id") propertyListingId: String, @Body request: MarkTakenRequest = MarkTakenRequest()): PropertyListingResponse

    // Real ownership verification (2026-07-25) -- see backend PropertyOwnershipService's
    // own doc comment. documentUrl comes from uploadPhoto() above.
    @POST("api/v1/realestate/listings/{id}/verify-ownership")
    suspend fun submitPropertyOwnershipVerification(
        @Path("id") propertyListingId: String,
        @Body request: SubmitOwnershipVerificationRequest,
    ): PropertyOwnershipSubmissionResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/realestate/listings/{id}/review")
    suspend fun submitPropertyListingReview(@Path("id") propertyListingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/realestate/listings/{id}/review")
    suspend fun getPropertyListingReviews(@Path("id") propertyListingId: String): HoodReviewsResponse

    @DELETE("api/v1/realestate/listings/{id}")
    suspend fun removePropertyListing(@Path("id") propertyListingId: String): PropertyListingResponse

    // Real Karrot(당근마켓)-style price-drop notification -- see backend
    // PropertyListingService.updatePrice's own doc comment. Real, shipped on the
    // backend + bank-mfe with zero Android client until now -- found via a
    // cross-platform-parity check, the real-estate mirror of the same fix already
    // ported to Marketplace listings.
    @POST("api/v1/realestate/listings/{id}/price")
    suspend fun updatePropertyListingPrice(@Path("id") propertyListingId: String, @Body request: UpdateListingPriceRequest): PropertyListingResponse

    @POST("api/v1/realestate/listings/{id}/contact-lister")
    suspend fun contactLister(@Path("id") propertyListingId: String): ContactListerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    @POST("api/v1/realestate/listings/{id}/offers")
    suspend fun makePropertyOffer(@Path("id") propertyListingId: String, @Body request: MakePropertyOfferRequest): PropertyPriceOfferResponse

    @POST("api/v1/realestate/offers/{id}/respond")
    suspend fun respondToPropertyOffer(@Path("id") offerId: String, @Body request: RespondToPropertyOfferRequest): PropertyPriceOfferResponse

    @GET("api/v1/realestate/conversations/{id}/offers")
    suspend fun getPropertyOffersForConversation(@Path("id") conversationId: String): PropertyPriceOffersResponse

    // Real per-merchant public product browse (2026-07-18) -- see
    // rw.itunda.merchant.web.ShoppingController.getMerchantProducts.
    @GET("api/v1/shopping/merchants")
    suspend fun getShoppingMerchants(
        @Query("category") category: String? = null,
        // Real fix (2026-08-13, direct user report against a live screenshot): Eats'
        // own restaurant list showed Electronics/Fashion merchants alongside real
        // restaurants -- both browse the exact same unfiltered Merchant directory. Pass
        // "RESTAURANT" from EatsScreen.kt, leave null (unfiltered, unchanged) from Shop.
        @Query("businessType") businessType: String? = null,
        @Query("q") q: String? = null,
        // Real browse-card enrichment (2026-07-21) -- see ShoppingMerchantDto's own doc
        // comment. Omitted (null) means no real distanceKm/deliveryTimeMinutes back.
        @Query("buyerLat") buyerLat: Double? = null,
        @Query("buyerLng") buyerLng: Double? = null,
        // Real Baemin/Coupang Eats-style "fastest delivery" sort tab (2026-08-16) --
        // only takes effect server-side when buyerLat/buyerLng are also supplied.
        @Query("sortBy") sortBy: String? = null,
    ): ShoppingMerchantsResponse

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    @GET("api/v1/maps/search")
    suspend fun searchPlaces(@Query("q") q: String): MapsSearchResponse

    // Backs the ruler (distance-measurement) tool's "what's here" label -- ported from
    // bank-mfe's own real reverseGeocode call, 2026-07-23.
    @GET("api/v1/maps/reverse")
    suspend fun reverseGeocode(@Query("lat") lat: Double, @Query("lng") lng: Double): MapsReverseGeocodeResponse

    // mode added 2026-07-22 (default "DRIVING") -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this now lets a caller reach.
    @GET("api/v1/maps/directions")
    suspend fun getDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsResponse

    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment.
    @GET("api/v1/maps/directions/alternatives")
    suspend fun getDirectionsAlternatives(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsAlternativesResponse

    @POST("api/v1/maps/directions/itinerary")
    suspend fun getItineraryDirections(
        @Body request: ItineraryDirectionsRequest,
    ): MapsItineraryResponse

    // Real Kigali GTFS transit journeys (2026-08-28) -- see TransitJourneyDto's own doc comment.
    @GET("api/v1/maps/directions/transit")
    suspend fun getTransitDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
    ): MapsTransitDirectionsResponse

    // Real consolidated place-detail (2026-08-28) -- see MapPlaceDetailDto's own doc comment.
    @GET("api/v1/maps/places/{merchantId}")
    suspend fun getMapPlaceDetail(@Path("merchantId") merchantId: String): MapPlaceDetailResponse

    // Real gap found live (uncalled-endpoint sweep, 2026-08-29) -- see
    // ToggleMerchantUpdateLikeResponse's own doc comment.
    @POST("api/v1/merchant/updates/{updateId}/like")
    suspend fun toggleMerchantUpdateLike(@Path("updateId") updateId: String): ToggleMerchantUpdateLikeResponse

    // Real, free, keyless Kigali weather (2026-08-28) -- see KigaliWeatherDto's own doc comment.
    @GET("api/v1/maps/weather")
    suspend fun getKigaliWeather(): MapsWeatherResponse

    // Real backend category list (Maps product-completeness pass, 2026-09-07) -- see
    // MAP_NEARBY_CATEGORIES's own doc comment for why this replaces that hardcoded
    // fallback as the real source of truth once fetched.
    @GET("api/v1/maps/categories")
    suspend fun getMapCategories(): MapCategoriesResponse

    // Real "nearby places" category search + bookmarked/favorite places (2026-07-19) --
    // see rw.itunda.maps.MapsService's own doc comment on the backend.
    @GET("api/v1/maps/nearby")
    suspend fun searchNearbyPlaces(
        @Query("category") category: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 2.0,
    ): MapNearbyResponse

    // Real "Smart Around"-style default map state (2026-08-04) -- see MapAroundMeResponse's
    // own doc comment.
    @GET("api/v1/maps/around-me")
    suspend fun getMapAroundMe(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 2.0,
    ): MapAroundMeResponse

    @GET("api/v1/maps/trending")
    suspend fun getMapTrending(
        @Query("days") days: Int = 7,
        @Query("limit") limit: Int = 10,
    ): MapTrendingResponse

    @GET("api/v1/agents/nearby")
    suspend fun searchNearbyAgents(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): NearbyAgentsResponse

    @GET("api/v1/maps/bookmarks")
    suspend fun getMyMapBookmarks(): MapBookmarksResponse

    @POST("api/v1/maps/bookmarks")
    suspend fun addMapBookmark(@Body request: AddMapBookmarkRequest): AddMapBookmarkResponse

    // Real "move to folder" (2026-07-22) -- see MoveMapBookmarkRequest's own doc comment.
    @PATCH("api/v1/maps/bookmarks")
    suspend fun moveMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double, @Body request: MoveMapBookmarkRequest): MoveMapBookmarkResponse

    @DELETE("api/v1/maps/bookmarks")
    suspend fun removeMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double): RemoveMapBookmarkResponse

    // Real Naver Map-style public/private folder + share (2026-08-04) -- see
    // SetMapFolderVisibilityRequest's own doc comment.
    @PATCH("api/v1/maps/bookmarks/folder-visibility")
    suspend fun setMapFolderVisibility(@Body request: SetMapFolderVisibilityRequest): SetMapFolderVisibilityResponse

    // Deliberately unauthenticated on the backend (SecurityConfig permitAll) -- whoever
    // opens a real share link doesn't need an itunda session. Reuses the same NetworkClient
    // Retrofit instance, which is harmless: a caller who IS logged in just attaches a token
    // the backend never required for this specific path.
    @GET("api/v1/maps/shared/{userId}/{folderName}")
    suspend fun getSharedMapFolder(@Path("userId") userId: String, @Path("folderName") folderName: String): SharedMapFolderResponse

    // Real Kakao Map-style "구독" (subscribe) -- the write half of sharing a folder, not
    // just viewing it. Unlike the GET above, this is authenticated (writes real rows into
    // the caller's own bookmarks).
    @POST("api/v1/maps/shared/{userId}/{folderName}/subscribe")
    suspend fun subscribeToSharedMapFolder(@Path("userId") userId: String, @Path("folderName") folderName: String): SubscribeToSharedMapFolderResponse

    // Real Kakao Map-style "친구위치" live location sharing -- see LiveLocationShareDto's
    // own doc comment.
    //
    // Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- creates a
    // brand-new share row every call with no dedup key at all, unlike a real
    // DB-unique-constraint-backed bookmark add.
    @POST("api/v1/maps/location-share")
    suspend fun startLocationShare(@Header("Idempotency-Key") idempotencyKey: String, @Body request: StartLocationShareRequest): StartLocationShareResponse

    @POST("api/v1/maps/location-share/_/update-location")
    suspend fun updateMyLocationShare(@Body request: UpdateLocationShareRequest): UpdateLocationShareResponse

    // Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- this is
    // additive (extends from the share's current expiry), so a retry without a real
    // dedup key used to silently double-extend it.
    @POST("api/v1/maps/location-share/{id}/extend")
    suspend fun extendLocationShare(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: ExtendLocationShareRequest): ExtendLocationShareResponse

    @POST("api/v1/maps/location-share/{id}/stop")
    suspend fun stopLocationShare(@Path("id") id: String): StopLocationShareResponse

    @GET("api/v1/maps/location-share/mine")
    suspend fun getMyLocationShares(): LocationSharesResponse

    @GET("api/v1/maps/location-share/shared-with-me")
    suspend fun getLocationSharesWithMe(): LocationSharesResponse

    // Real recipient-side poll -- call this on a real interval (e.g. every 15s) while
    // watching a share to see the sharer's latest pushed position.
    @GET("api/v1/maps/location-share/{id}")
    suspend fun getLocationShare(@Path("id") id: String): LocationShareResponse

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    @GET("api/v1/shopping/merchants/categories")
    suspend fun getMerchantCategories(@Query("businessType") businessType: String? = null): MerchantCategoriesResponse

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    @GET("api/v1/shopping/products/deals")
    suspend fun getShopDeals(): DealsResponse

    // Real Coupang Eats-style dish grid (2026-08-03) -- see EatsDishDto's own doc
    // comment for the sourcing.
    @GET("api/v1/eats/dishes")
    // sortBy="popular" added 2026-08-28 -- real order-count-derived ranking,
    // distinct from the default real "recommended for you" personal-history sort.
    suspend fun getEatsDishes(@Query("category") category: String? = null, @Query("maxBudget") maxBudget: Double? = null, @Query("sortBy") sortBy: String? = null): EatsDishesResponse

    // Real "frequently ordered together" cross-sell (2026-08-28) -- see backend
    // OrderItemRepository.getFrequentlyOrderedWith. A dedicated lean DTO (not
    // DealProductDto): Gson deserializes a genuinely-absent field to null, not a
    // Kotlin default, so reusing DealProductDto here would risk a silent mismatch.
    @GET("api/v1/shopping/products/{id}/frequently-ordered-with")
    suspend fun getFrequentlyOrderedWith(@Path("id") productId: String): FrequentlyOrderedWithResponse

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment.
    @GET("api/v1/time-deals")
    suspend fun getActiveTimeDeals(): TimeDealsResponse

    // Real Toss Shopping banner carousel (2026-08-12) -- see backend
    // TimeDealService.getBanners's own doc comment: every banner IS a real, currently
    // active Time Deal, never fabricated promotional content.
    @GET("api/v1/time-deals/banners")
    suspend fun getShoppingBanners(): ShoppingBannersResponse

    // Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
    // see backend ShoppingMissionService's own doc comment.
    @GET("api/v1/shopping/points")
    suspend fun getShoppingMissions(): ShoppingMissionsResponse

    @POST("api/v1/shopping/points/missions/{type}/complete")
    suspend fun completeShoppingMission(@Path("type") type: String, @Header("Idempotency-Key") idempotencyKey: String): MissionCompleteResponse

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- see
    // rw.itunda.merchant.ShoppingCashbackService's own doc comment. bank-mfe already has
    // this; this is the first Android client.
    @GET("api/v1/shopping/membership-day")
    suspend fun getMembershipDayStatus(): MembershipDayStatusResponse

    @POST("api/v1/product-subscriptions")
    suspend fun subscribeToProduct(@Body request: CreateProductSubscriptionRequest, @Header("Idempotency-Key") idempotencyKey: String): ProductSubscriptionResponse

    @GET("api/v1/product-subscriptions")
    suspend fun getMyProductSubscriptions(): ProductSubscriptionsResponse

    @POST("api/v1/product-subscriptions/{id}/pause")
    suspend fun pauseProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    @POST("api/v1/product-subscriptions/{id}/resume")
    suspend fun resumeProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    @POST("api/v1/product-subscriptions/{id}/cancel")
    suspend fun cancelProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    // Real Coupang 정기배송 "건너뛰기" (skip next) -- ported from bank-mfe (2026-09-03).
    @POST("api/v1/product-subscriptions/{id}/skip-next")
    suspend fun skipNextProductSubscriptionDelivery(@Path("id") id: String): ProductSubscriptionResponse

    @GET("api/v1/shopping/merchants/{id}/products")
    suspend fun getMerchantProducts(@Path("id") merchantId: String): MerchantProductsResponse

    // Real seller chat (2026-08-28) -- mirrors contactSeller (Marketplace) above
    // exactly: resolves the real merchant owner's userId server-side, hands off to
    // the same real shared messaging system every other vertical already uses.
    @POST("api/v1/shopping/merchants/{id}/contact-seller")
    suspend fun contactMerchantSeller(@Path("id") merchantId: String): ContactSellerResponse

    // Real cross-merchant product search (item 137) -- see ProductSearchResultDto's own
    // doc comment.
    @GET("api/v1/shopping/products/search")
    suspend fun searchProducts(@Query("q") query: String): ProductSearchResponse

    // Real KakaoTalk-style 기프티콘 gift voucher (item 137) -- see GiftVoucherDto's own
    // doc comment.
    @POST("api/v1/gift-vouchers")
    suspend fun purchaseGiftVoucher(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PurchaseGiftVoucherRequest): GiftVoucherResponse

    @GET("api/v1/gift-vouchers/conversations/{id}")
    suspend fun getGiftVouchersForConversation(@Path("id") conversationId: String): GiftVouchersResponse

    @POST("api/v1/gift-vouchers/{id}/extend")
    suspend fun extendGiftVoucherExpiry(@Path("id") voucherId: String, @Header("Idempotency-Key") idempotencyKey: String): GiftVoucherResponse

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first Android client
    // for this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't). Mirrors bank-mfe's lib/shopping.ts exactly.
    @POST("api/v1/merchant/{merchantId}/follow")
    suspend fun followMerchant(@Path("merchantId") merchantId: String): MerchantFollowResponse

    @DELETE("api/v1/merchant/{merchantId}/follow")
    suspend fun unfollowMerchant(@Path("merchantId") merchantId: String): SuccessResponse

    @GET("api/v1/merchant/follows")
    suspend fun getMyFollowedMerchants(@Query("size") size: Int = 200): FollowedMerchantsResponse

    // Real local-business appointment booking (2026-07-25) -- see
    // rw.itunda.merchant.web.MerchantBookingController.
    @GET("api/v1/merchant/{merchantId}/booking-slots")
    suspend fun getBookingSlots(@Path("merchantId") merchantId: String, @Query("serviceId") serviceId: String, @Query("date") date: String): BookingSlotsResponse

    @POST("api/v1/merchant/bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): MerchantBookingDetailResponse

    @GET("api/v1/merchant/bookings/my-bookings")
    suspend fun getMyBookings(): MerchantBookingsResponse

    @POST("api/v1/merchant/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") bookingId: String): MerchantBookingDetailResponse

    // Real customer-facing post-appointment review submission (item 143) -- see
    // rw.itunda.merchant.MerchantBookingReviewController. bank-mfe already has this;
    // this is the first native client.
    @POST("api/v1/merchant/bookings/{bookingId}/review")
    suspend fun submitBookingReview(@Path("bookingId") bookingId: String, @Body request: SubmitBookingReviewRequest): MerchantBookingReviewResponse

    @GET("api/v1/merchant/reviews/my-reviews")
    suspend fun getMyBookingReviews(@Query("size") size: Int = 50): MerchantBookingReviewsResponse

    // Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
    // sweep: both real, fully-authorized backend endpoints, but had zero client callers
    // anywhere. bank-mfe shipped this first (2026-08-05); this is the Android port. Reuses
    // MerchantCouponPreviewDto (already exact-shape-identical, from the payment-preview
    // feature) rather than declaring a duplicate DTO.
    @GET("api/v1/merchant/{merchantId}/reviews")
    suspend fun getMerchantReviews(@Path("merchantId") merchantId: String, @Query("size") size: Int = 20): MerchantReviewsWithRatingResponse

    @GET("api/v1/merchant/{merchantId}/coupons")
    suspend fun getCouponsForCustomer(@Path("merchantId") merchantId: String): MerchantCouponsForCustomerResponse

    // Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28) --
    // itunda's first ever unscoped coupon read, see MerchantCouponService.
    // browseCoupons's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    @GET("api/v1/merchant/coupons/browse")
    suspend fun browseCoupons(): CouponBrowseResponse

    @GET("api/v1/merchant/coupons/my-redemptions")
    suspend fun getMyCouponRedemptions(): CouponRedemptionsResponse

    // Real Membership-screen "Store points" row -- see MerchantLoyaltyPointsService.
    // getMyBalances's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    @GET("api/v1/merchant/loyalty/my-balances")
    suspend fun getMyLoyaltyBalances(): LoyaltyBalancesResponse

    // Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
    // (this app has no scanner), mirrors bank-mfe's lib/shopping.ts collectPayment/
    // payByStaticQr exactly. bank-mfe already has both; this is the first Android client
    // for either.
    @POST("api/v1/merchant/collect/{intentId}")
    suspend fun collectPayment(@Path("intentId") intentId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: CollectPaymentRequest): CollectPaymentResultDto

    @POST("api/v1/merchant/{merchantId}/static-qr/pay")
    suspend fun payByStaticQr(@Path("merchantId") merchantId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: StaticQrPayRequest): CollectPaymentResultDto

    // Real customer-presented payment code (2026-08-11) -- see backend's
    // MerchantService.generateCustomerPaymentCode/chargeByCustomerCode doc comments.
    // Matches real KakaoPay/Toss Pay's actual primary in-store flow: the CUSTOMER
    // opens Pay and a scannable code is already on screen, no typing on either side --
    // the reverse direction of collectPayment/payByStaticQr above.
    @POST("api/v1/merchant/pay/customer-code")
    suspend fun generateCustomerPaymentCode(@Body request: GenerateCustomerPaymentCodeRequest = GenerateCustomerPaymentCodeRequest()): CustomerPaymentCodeResponse

    @POST("api/v1/merchant/pay/charge-by-code")
    suspend fun chargeByCustomerCode(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ChargeByCustomerCodeRequest): CollectPaymentResultDto

    // Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
    // this file's own collectPayment comment previously named. bank-mfe already has this
    // (previewPaymentIntent); this is the first Android client.
    @GET("api/v1/merchant/intent/{intentId}")
    suspend fun previewPaymentIntent(@Path("intentId") intentId: String): PaymentIntentPreviewResponse

    // Real Face Pay -- see rw.itunda.merchant.FacePayService's own doc comment. The
    // backend has been fully real since 2026-07-13; bank-mfe wired it 2026-07-20; this
    // is the first Android client. Enrolling swaps Pay-by-code's own collect call to
    // this channel -- same manual code entry, just a different real ledger channel
    // label ("Face Pay" vs "QR"), matching bank-mfe's own honest scope exactly (no
    // device biometric prompt gates it on any client, itunda's own).
    @POST("api/v1/facepay/enroll")
    suspend fun enrollFacePay(): FacePayEnrollmentResponse

    @POST("api/v1/facepay/revoke")
    suspend fun revokeFacePay(): FacePayEnrollmentResponse

    @GET("api/v1/facepay/status")
    suspend fun getFacePayStatus(): FacePayStatusResponse

    @POST("api/v1/facepay/collect/{intentId}")
    suspend fun collectWithFacePay(@Path("intentId") intentId: String, @Header("Idempotency-Key") idempotencyKey: String): CollectPaymentResultDto

    // Real RewardsService task list -- see rw.itunda.rewards.web.RewardsController's own
    // doc comment: the Saronite reward-tasks mini-app's native bridge has called
    // /api/v1/rewards/tasks since it was built, but this Compose-native surface (PayTab's
    // real Toss Pay home "Get more rewards" preview, 2026-08-22) never had a direct
    // client. Mirrors bank-mfe's lib/rewards.ts RewardTasksResult exactly.
    @GET("api/v1/rewards/tasks")
    suspend fun getRewardTasks(): RewardTasksResponse

    @POST("api/v1/rewards/claim")
    suspend fun claimRewardTask(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ClaimRewardTaskRequest): ClaimRewardTaskResponse

    // Real Coupang-style multi-item checkout (2026-07-18) -- see rw.itunda.commerce.web.OrderController.
    @POST("api/v1/orders")
    suspend fun placeOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceOrderRequest): OrderDetailResponse

    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
    // c6e470fb -- see project_itunda_pagination_discard_sweep memory) --
    // getMyOrders/getMerchantOrders are real Pageable-backed on the backend,
    // but page was never sent, silently capping order history at 20 rows.
    @GET("api/v1/orders/my-orders")
    suspend fun getMyOrders(@Query("page") page: Int = 0, @Query("size") size: Int = 20): OrdersResponse

    @GET("api/v1/orders/{id}")
    suspend fun getOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
    // sibling-consistency audit against bank-mfe's own MerchantOrdersView/
    // MerchantReturnQueueView, which have had this since before this session: a real
    // itunda user who also runs a merchant storefront could manage their store's
    // orders on bank-mfe but had zero client anywhere on Android or iOS. Mirrors
    // bank-mfe's own COMMERCE_STATUS_CHAIN (PLACED -> PACKED -> SHIPPED -> DELIVERED)
    // exactly.
    @GET("api/v1/orders/merchant-orders")
    suspend fun getMerchantOrders(@Query("page") page: Int = 0, @Query("size") size: Int = 20): OrdersResponse

    @POST("api/v1/orders/{orderId}/status")
    suspend fun updateOrderStatus(@Path("orderId") orderId: String, @Body request: UpdateOrderStatusRequest): OrderDetailResponse

    @GET("api/v1/orders/returns/merchant-queue")
    suspend fun getMerchantReturnQueue(): OrderReturnRequestsResponse

    @POST("api/v1/orders/returns/{returnRequestId}/decide")
    suspend fun decideOrderReturn(@Path("returnRequestId") returnRequestId: String, @Body request: DecideOrderReturnRequest, @Header("Idempotency-Key") idempotencyKey: String): OrderReturnRequestResponse

    // Real live rider-location tracking for Commerce orders (item 230) -- found via a
    // defined-but-uncalled-endpoint sweep: mirrors getEatsRiderLocation exactly (same
    // RiderLocationDto shape, same real "available: false" while genuinely nothing to
    // show), but had zero client callers on any platform until now. bank-mfe shipped
    // this first (SimpleLiveRiderMap.tsx, 2026-08-05); this is the Android port. Honest
    // v1 scope-down: Commerce's OrderDto has no delivery-coordinate fields, so this is a
    // single rider marker, not a route.
    @GET("api/v1/orders/{orderId}/rider-location")
    suspend fun getOrderRiderLocation(@Path("orderId") orderId: String): EatsRiderLocationResponse

    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) --
    // see AffiliateLinkDto's own doc comment.
    @POST("api/v1/affiliate/links")
    suspend fun createAffiliateLink(@Body request: CreateAffiliateLinkRequest): AffiliateLinkResponse

    @GET("api/v1/affiliate/links/my-links")
    suspend fun getMyAffiliateLinks(): AffiliateLinksResponse

    @GET("api/v1/affiliate/commissions/my-commissions")
    suspend fun getMyAffiliateCommissions(): AffiliateCommissionsResponse

    // Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    // See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    @POST("api/v1/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real Coupang-style post-delivery Return & Exchange requests (item 166/174) -- see
    // OrderReturnService's own doc comment. A real 7-day window from delivery; an
    // approved RETURN triggers a real refund via reversed ledger legs, an approved
    // EXCHANGE moves no money. Merchant-side approve/reject queue closed item 234 above.
    @POST("api/v1/orders/{orderId}/return")
    suspend fun requestOrderReturn(@Path("orderId") orderId: String, @Body request: RequestOrderReturnRequest, @Header("Idempotency-Key") idempotencyKey: String): OrderReturnRequestResponse

    @GET("api/v1/orders/returns/my-requests")
    suspend fun getMyReturnRequests(): OrderReturnRequestsResponse

    // Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    @POST("api/v1/orders/items/{id}/review")
    suspend fun submitProductReview(@Path("id") orderItemId: String, @Body request: SubmitProductReviewRequest): ProductReviewResponse

    @GET("api/v1/orders/products/{id}/rating")
    suspend fun getProductRating(@Path("id") productId: String): ProductRatingResponse

    @GET("api/v1/orders/products/{id}/reviews")
    suspend fun getProductReviews(@Path("id") productId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): ProductReviewsResponse

    // Real Coupang/Naver-style "helpful" idempotent toggle (2026-08-25) -- see
    // ProductReviewService.toggleHelpful's own doc comment on the backend.
    @POST("api/v1/orders/reviews/{id}/helpful")
    suspend fun toggleProductReviewHelpful(@Path("id") reviewId: String): ToggleHelpfulReviewResponse

    // Real Coupang-style pre-purchase product Q&A (상품문의) -- see
    // rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct
    // from a review: no order/purchase required at all. bank-mfe already has the
    // buyer-side ask/view flow; this is the first Android client. The seller-answer
    // flow (2026-08-04) now lives on the merchant app -- see that app's
    // ProductInquiryAnswerRow.
    @POST("api/v1/orders/products/{id}/inquiries")
    suspend fun askProductInquiry(@Path("id") productId: String, @Body request: AskProductInquiryRequest): ProductInquiryResponse

    @GET("api/v1/orders/products/{id}/inquiries")
    suspend fun getProductInquiries(@Path("id") productId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): ProductInquiriesResponse

    // Real "my questions across every product I've ever asked about" (2026-08-04) --
    // OrderController.getMyInquiries existed on the backend with zero client anywhere;
    // ShopScreen's ProductInquirySection only ever showed one product's Q&A at a time.
    @GET("api/v1/orders/inquiries/my-questions")
    suspend fun getMyProductInquiries(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ProductInquiriesResponse

    // Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing --
    // see rw.itunda.merchant.MerchantBillingService's own doc comment. Customer-facing
    // half only (browse a merchant's own plans, subscribe, view/cancel), matching
    // bank-mfe's own scope -- plan creation is merchant-owner-only, a merchant-mfe/
    // merchant-app concern, not built here. bank-mfe already has this; this is the
    // first Android client.
    @GET("api/v1/merchant/{merchantId}/billing-plans")
    suspend fun getMerchantBillingPlans(@Path("merchantId") merchantId: String): MerchantBillingPlansResponse

    @POST("api/v1/merchant/billing-plans/{planId}/subscribe")
    suspend fun subscribeToBillingPlan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MerchantBillingSubscriptionResponse

    @GET("api/v1/merchant/billing-subscriptions/my")
    suspend fun getMyBillingSubscriptions(): MerchantBillingSubscriptionsResponse

    @POST("api/v1/merchant/billing-subscriptions/{subscriptionId}/cancel")
    suspend fun cancelBillingSubscription(@Path("subscriptionId") subscriptionId: String): MerchantBillingSubscriptionResponse

    // Real recurring-payment ("subscription") detection over a user's own real
    // transaction history -- see rw.itunda.account.SubscriptionDetectionService's own
    // doc comment. bank-mfe already has this; this is the first Android client.
    @GET("api/v1/account/subscriptions")
    suspend fun getDetectedSubscriptions(): DetectedSubscriptionsResponse

    // Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- the
    // customer-facing browse half (merchant-mfe owns the paid create/extend side).
    // "Pull" discovery, same as every other nearby() in this codebase: the caller's
    // live coordinate is a request param, not a stored location itunda doesn't keep.
    // bank-mfe already has this; this is the first Android client.
    @GET("api/v1/merchant/ads/nearby")
    suspend fun getNearbyMerchantAds(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double): NearbyMerchantAdsResponse

    // Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
    // "it should look 100% like toss pay UI/UX features everything") -- the reference's
    // own "345 stores nearby where you can earn rewards" banner. Distinct from
    // getNearbyMerchantAds above -- that's paid ad placements (a subset), this is every
    // real ACTIVE merchant nearby (MerchantDiscoveryService.kt), which is what "how many
    // stores can I actually pay near me" honestly means. Every merchant earns the payer
    // real cashback on collect() (ShoppingCashbackService), so "earn cashback" is a true
    // claim for all of them, not just FacePay-enrolled ones.
    @GET("api/v1/merchant/nearby")
    suspend fun getNearbyMerchants(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double, @Query("radiusKm") radiusKm: Double = 5.0): NearbyMerchantsResponse

    // Real Shop product wishlist (2026-07-24) -- backend shipped 2026-07-20
    // (ProductFavoriteService), bank-mfe wired the same day; this closes the
    // Android-side gap. Mirrors addListingFavorite/addJobPostFavorite exactly.
    @POST("api/v1/orders/products/{id}/favorite")
    suspend fun addProductFavorite(@Path("id") productId: String): SuccessResponse

    @DELETE("api/v1/orders/products/{id}/favorite")
    suspend fun removeProductFavorite(@Path("id") productId: String): SuccessResponse

    @GET("api/v1/orders/products/favorites")
    suspend fun getMyFavoriteProducts(): FavoriteProductsResponse

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    @POST("api/v1/eats/orders")
    suspend fun placeEatsOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceEatsOrderRequest): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/my-orders")
    suspend fun getMyEatsOrders(@Query("page") page: Int = 0, @Query("size") size: Int = 20): EatsOrdersResponse

    // Real 배달의민족 함께주문 (Baemin "Together Order") -- see GroupEatsOrderDto's own
    // doc comment.
    @POST("api/v1/eats/group-orders")
    suspend fun createGroupEatsOrder(@Body request: CreateGroupEatsOrderRequest): GroupEatsOrderResponse

    @POST("api/v1/eats/group-orders/join")
    suspend fun joinGroupEatsOrder(@Body request: JoinGroupEatsOrderRequest): GroupEatsOrderResponse

    @GET("api/v1/eats/group-orders/{id}")
    suspend fun getGroupEatsOrder(@Path("id") id: String): GroupEatsOrderDetailResponse

    @POST("api/v1/eats/group-orders/{id}/items")
    suspend fun setGroupEatsOrderItems(@Path("id") id: String, @Body request: SetGroupEatsOrderItemsRequest): GroupEatsOrderDetailResponse

    @POST("api/v1/eats/group-orders/{id}/finalize")
    suspend fun finalizeGroupEatsOrder(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): FinalizeGroupEatsOrderResponse

    @POST("api/v1/eats/group-orders/{id}/cancel")
    suspend fun cancelGroupEatsOrder(@Path("id") id: String): GroupEatsOrderResponse

    // Real order detail, including items -- backs the real "Reorder" button
    // (2026-07-19): a buyer can re-populate a cart from a past order's real items
    // rather than retyping their whole order from scratch.
    @GET("api/v1/eats/orders/{id}")
    suspend fun getEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    // Real address-search autocomplete (2026-07-18) -- see EatsController.searchDeliveryAddress.
    @GET("api/v1/eats/geocode/search")
    suspend fun searchDeliveryAddress(@Query("q") query: String): AddressSearchResponse

    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    @POST("api/v1/eats/orders/{id}/review")
    suspend fun submitEatsReview(@Path("id") orderId: String, @Body request: SubmitEatsReviewRequest): EatsReviewResponse

    // Real Uber Eats post-delivery tip -- see EatsOrderDto.tipAmount's own doc comment.
    // Real Idempotency-Key required, matching tipRideDriver's own identical fix (2026-09-03).
    @POST("api/v1/eats/orders/{id}/tip")
    suspend fun tipEatsOrderRider(@Path("id") orderId: String, @Body request: TipEatsOrderRequest, @Header("Idempotency-Key") idempotencyKey: String): TipEatsOrderResponse

    @GET("api/v1/eats/restaurants/{id}/rating")
    suspend fun getRestaurantRating(@Path("id") restaurantId: String): EatsRatingResponse

    // Real written-review list + owner-reply (item 184/185) -- see
    // EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe already has
    // this (item 184); this is the first Android client.
    @GET("api/v1/eats/restaurants/{id}/reviews")
    suspend fun getRestaurantReviews(@Path("id") restaurantId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): EatsReviewsResponse

    @POST("api/v1/eats/reviews/{reviewId}/reply")
    suspend fun replyToRestaurantReview(@Path("reviewId") reviewId: String, @Body request: ReplyToEatsReviewRequest): EatsReviewResponse

    // Real Coupang/Naver-style "도움돼요" (helpful) idempotent toggle -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check.
    @POST("api/v1/eats/reviews/{reviewId}/helpful")
    suspend fun toggleEatsReviewHelpful(@Path("reviewId") reviewId: String): ToggleEatsReviewHelpfulResponse

    // Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
    // EatsReviewService.reportReview's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via the same
    // cross-platform-parity check. Genuinely NOT covered by the generic
    // HoodReportButton mechanism (no REVIEW target exists there), same reasoning
    // bank-mfe's own lib/eats.ts doc comment already established.
    @POST("api/v1/eats/reviews/{reviewId}/report")
    suspend fun reportEatsReview(@Path("reviewId") reviewId: String, @Body request: ReportEatsReviewRequest): SuccessResponse

    // Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    // only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    @POST("api/v1/eats/orders/{id}/cancel")
    suspend fun cancelEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
    // a553b127 -- see project_itunda_pagination_discard_sweep memory) -- both
    // real Pageable endpoints' page was never sent, silently capping each
    // list at 20 rows.
    @GET("api/v1/eats/orders/rider-deliveries")
    suspend fun getRiderDeliveries(@Query("page") page: Int = 0, @Query("size") size: Int = 20): EatsOrdersResponse

    @GET("api/v1/eats/orders/available")
    suspend fun getAvailableDeliveries(@Query("page") page: Int = 0, @Query("size") size: Int = 20): EatsOrdersResponse

    @POST("api/v1/eats/orders/{id}/claim")
    suspend fun claimDelivery(@Path("id") orderId: String, @Header("Idempotency-Key") idempotencyKey: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/rider-status")
    suspend fun updateRiderOrderStatus(@Path("id") orderId: String, @Body request: UpdateEatsOrderStatusRequest): EatsOrderDetailResponse

    // Real live rider-location tracking (2026-07-19 backend, first mobile client 2026-07-29,
    // item 182) -- "the defining 'watch your order arrive' moment," see EatsOrderService.
    // getRiderLocation's own doc comment. bank-mfe has had this since 2026-07-20
    // (LiveRiderMap.tsx); Android/iOS main apps never did. `available: false` (not an
    // error) is the real, honest response whenever there's genuinely nothing to show yet.
    @GET("api/v1/eats/orders/{orderId}/rider-location")
    suspend fun getEatsRiderLocation(@Path("orderId") orderId: String): EatsRiderLocationResponse

    @POST("api/v1/eats/riders/register")
    suspend fun registerRider(): RiderResponse

    @GET("api/v1/eats/riders/me")
    suspend fun getMyRiderProfile(): RiderResponse

    @POST("api/v1/eats/riders/availability")
    suspend fun setRiderAvailability(@Body request: SetRiderAvailabilityRequest): RiderResponse

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    @POST("api/v1/eats/restaurants/{id}/favorite")
    suspend fun addFavoriteRestaurant(@Path("id") restaurantId: String): AddFavoriteResponse

    @DELETE("api/v1/eats/restaurants/{id}/favorite")
    suspend fun removeFavoriteRestaurant(@Path("id") restaurantId: String): RemoveFavoriteResponse

    @GET("api/v1/eats/favorites")
    suspend fun getMyFavoriteRestaurants(@Query("page") page: Int = 0, @Query("size") size: Int = 20): FavoriteRestaurantsResponse

    // Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- backend
    // real since 2026-07-26, bank-mfe client since 2026-07-28 (item 102); this is the
    // first Android client. See lib/eats.ts's own doc comment (bank-mfe) for the full
    // sourced account: free delivery only at a restaurant that has itself opted in,
    // never a blanket waiver.
    @GET("api/v1/eats/membership/me")
    suspend fun getMyEatsMembership(): EatsMembershipResponse

    @POST("api/v1/eats/membership/subscribe")
    suspend fun subscribeEatsMembership(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SubscribeEatsMembershipRequest): EatsMembershipResponse

    // Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
    // PlatformMembershipDto's own doc comment.
    @GET("api/v1/eats/platform-membership/me")
    suspend fun getMyPlatformMembership(): PlatformMembershipResponse

    @POST("api/v1/eats/platform-membership/subscribe")
    suspend fun subscribePlatformMembership(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SubscribePlatformMembershipRequest): PlatformMembershipResponse

    // Real 배민오더-style table/QR in-store ordering (2026-07-25) -- see
    // rw.itunda.eats.web.DineInOrderController.
    @POST("api/v1/eats/dine-in/orders")
    suspend fun placeDineInOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceDineInOrderRequest): DineInOrderDetailResponse

    @GET("api/v1/eats/dine-in/orders/my-orders")
    suspend fun getMyDineInOrders(@Query("page") page: Int = 0, @Query("size") size: Int = 50): DineInOrdersResponse

    @GET("api/v1/eats/dine-in/orders/{id}")
    suspend fun getDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    @POST("api/v1/eats/dine-in/orders/{id}/cancel")
    suspend fun cancelDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    // Real Toss Securities-style stock investing (rw.itunda.stocks) -- this is the
    // first mobile UI this feature has ever had (bank-mfe just got its own real UI the
    // same session, closing what had been a zero-client-UI gap even for the original
    // pre-existing buy/sell/portfolio backend). See StockCatalog.kt's own doc comment
    // on the backend for why day-over-day movement/history is a real deterministic
    // simulation, not fabricated randomness or live RSE data.
    @GET("api/v1/stocks")
    suspend fun getStocks(): StocksResponse

    @GET("api/v1/stocks/{id}/history")
    suspend fun getStockHistory(@Path("id") stockId: String, @Query("days") days: Int = 14): StockHistoryResponse

    @GET("api/v1/stocks/portfolio")
    suspend fun getStockPortfolio(): StockPortfolioResponse

    @GET("api/v1/stocks/portfolio/history")
    suspend fun getPortfolioHistory(@Query("days") days: Int = 30): PortfolioHistoryResponse

    @POST("api/v1/stocks/fund")
    suspend fun fundInvestmentAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: FundInvestmentRequest): FundInvestmentResponse

    @POST("api/v1/stocks/buy")
    suspend fun buyStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @POST("api/v1/stocks/sell")
    suspend fun sellStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @GET("api/v1/stocks/watchlist")
    suspend fun getStockWatchlist(): StocksResponse

    @POST("api/v1/stocks/{id}/watch")
    suspend fun watchStock(@Path("id") stockId: String): WatchStockResponse

    @DELETE("api/v1/stocks/{id}/watch")
    suspend fun unwatchStock(@Path("id") stockId: String): UnwatchStockResponse

    // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- see
    // SetPriceAlertRequest's own doc comment.
    @GET("api/v1/stocks/{id}/price-alert")
    suspend fun getPriceAlert(@Path("id") stockId: String): PriceAlertResponse

    @POST("api/v1/stocks/{id}/price-alert")
    suspend fun setPriceAlert(@Path("id") stockId: String, @Body request: SetPriceAlertRequest): SetPriceAlertResponse

    @DELETE("api/v1/stocks/{id}/price-alert")
    suspend fun clearPriceAlert(@Path("id") stockId: String): SetPriceAlertResponse

    @GET("api/v1/overview")
    suspend fun getOverview(): OverviewResponse

    // Real Toss "자산 변화" (asset change over time) reference (2026-09-12, direct
    // user-supplied total-assets screenshots) -- see the backend's NetWorthSnapshot
    // doc comment for why liquidTotal is deliberately narrower than
    // OverviewResponse.netWorth (account balances + reward points only).
    @GET("api/v1/overview/net-worth-history")
    suspend fun getNetWorthHistory(): NetWorthHistoryResponse

    // Idempotency-Key added 2026-09-07 (Overview product-completeness pass) -- a lost
    // response after a real successful link previously created a genuine duplicate
    // LinkedAccount row and burned a second real simulated ProviderConnector call.
    @POST("api/v1/accounts/link")
    suspend fun linkAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: LinkAccountRequest): LinkAccountResponse

    @GET("api/v1/accounts/linked")
    suspend fun getLinkedAccounts(): LinkedAccountsResponse

    @POST("api/v1/accounts/link/{accountId}/unlink")
    suspend fun unlinkAccount(@Path("accountId") accountId: String): LinkAccountResponse

    @GET("api/v1/loans/offers")
    suspend fun getLoanOffers(@Query("lenderId") lenderId: String? = null): LoanOffersResponse

    @GET("api/v1/loans/lenders")
    suspend fun getLenders(): LendersResponse

    @GET("api/v1/loans/my-loans")
    suspend fun getMyLoans(): MyLoansResponse

    @POST("api/v1/loans/apply")
    suspend fun applyForLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ApplyLoanRequest): ApplyLoanResponse

    @POST("api/v1/loans/repay")
    suspend fun repayLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RepayLoanRequest): RepayLoanResponse

    @POST("api/v1/loans/refinance")
    suspend fun refinanceLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RefinanceLoanRequest): RefinanceResult

    @GET("api/v1/loans/overdraft")
    suspend fun getMyOverdraft(): OverdraftAccountResponse

    @POST("api/v1/loans/overdraft/open")
    suspend fun openOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OpenOverdraftRequest): OverdraftAccountResponse

    @POST("api/v1/loans/overdraft/draw")
    suspend fun drawOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OverdraftAmountRequest): OverdraftDrawResponse

    @POST("api/v1/loans/overdraft/repay")
    suspend fun repayOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OverdraftAmountRequest): OverdraftRepayResponse

    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
    // 2026-07-31) -- first Android client for this feature, mirroring bank-mfe's
    // lib/loans.ts and Android's own OverdraftPanel shape exactly.
    @GET("api/v1/loans/postpaid-credit")
    suspend fun getMyPostpaidCredit(): PostpaidCreditLineResponse

    @POST("api/v1/loans/postpaid-credit/apply")
    suspend fun applyForPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String): PostpaidCreditLineResponse

    @POST("api/v1/loans/postpaid-credit/spend")
    suspend fun spendPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PostpaidCreditAmountRequest): PostpaidCreditActionResponse

    @POST("api/v1/loans/postpaid-credit/repay")
    suspend fun repayPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PostpaidCreditAmountRequest): PostpaidCreditActionResponse

    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory) --
    // a lost response after a successful issue would previously resubmit here and
    // hit the backend's own CardAlreadyIssuedException guard on retry.
    @POST("api/v1/card/issue")
    suspend fun issueCard(@Header("Idempotency-Key") idempotencyKey: String, @Body request: IssueCardRequest): CardResponse

    @GET("api/v1/card/my-card")
    suspend fun getMyCard(): CardResponse

    @GET("api/v1/card/transactions")
    suspend fun getCardTransactions(@Query("page") page: Int = 0, @Query("size") size: Int = 20): CardTransactionsResponse

    @PUT("api/v1/card/limits")
    suspend fun setCardLimits(@Body request: SetCardLimitsRequest): CardResponse

    @POST("api/v1/card/freeze")
    suspend fun freezeCard(): CardResponse

    @POST("api/v1/card/unfreeze")
    suspend fun unfreezeCard(): CardResponse

    @POST("api/v1/card/report-lost")
    suspend fun reportCardLost(): CardResponse

    @POST("api/v1/card/close")
    suspend fun closeCard(): CardResponse

    // Real, live bug found 2026-09-07 (Certificate product-completeness pass, incidental
    // discovery): the backend's /api/v1/card/reissue requires Idempotency-Key with no
    // default, but this call had none -- every real reissue-card tap on Android has been
    // hard-failing with 400 IDEMPOTENCY_KEY_REQUIRED since the Card pass added the
    // backend requirement. Same fix web (lib/card.ts) and iOS (NetworkClient+
    // CoreServices.swift) needed too.
    @POST("api/v1/card/reissue")
    suspend fun reissueCard(@Header("Idempotency-Key") idempotencyKey: String): CardResponse

    @PUT("api/v1/card/pin")
    suspend fun setCardPin(@Body request: SetCardPinRequest): CardResponse

    @POST("api/v1/card/charge")
    suspend fun chargeCard(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ChargeCardRequest): ChargeCardResponse

    @GET("api/v1/transit/balance")
    suspend fun getMyTransitBalance(): TransitBalanceResponse

    @GET("api/v1/transit/trips")
    suspend fun getTransitTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): TransitTripsResponse

    @POST("api/v1/transit/topup")
    suspend fun topUpTransit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TopUpTransitRequest): TransitBalanceResponse

    @POST("api/v1/transit/tap")
    suspend fun tapTransitFare(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TapFareRequest): TapFareResponse

    @POST("api/v1/transit/tap-by-code")
    suspend fun tapTransitFareByCode(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TapFareByCodeRequest): TapFareByCodeResponse

    @POST("api/v1/moto-fare/collect")
    suspend fun collectMotoFare(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CollectMotoFareRequest): CollectMotoFareResponse

    @GET("api/v1/moto-fare/earnings")
    suspend fun getMyMotoFareEarnings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): MotoFareEarningsResponse

    // Real gap found live (uncalled-endpoint sweep, 2026-09-02): the backend's own
    // MotoFareController.getMyTripsAsRider ("/trips") is the exact symmetric
    // counterpart of getMyTripsAsDriver ("/earnings") above -- same MotoFareTripDto
    // shape, same pagination -- but had zero caller on any platform since the feature
    // shipped 2026-08-27. A rider who tapped to pay a moto-taxi fare had no way to see
    // their own trip history, only the driver side of this same feature was ever wired.
    @GET("api/v1/moto-fare/trips")
    suspend fun getMyMotoFareTripsAsRider(@Query("page") page: Int = 0, @Query("size") size: Int = 20): MotoFareEarningsResponse

    @POST("api/v1/support/tickets")
    suspend fun createSupportTicket(@Body request: CreateSupportTicketRequest): CreateSupportTicketResponse

    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(): SupportTicketsResponse

    @POST("api/v1/ussd/pin")
    suspend fun setUssdPin(@Body request: SetUssdPinRequest): SuccessResponse

    @GET("api/v1/credit-score")
    suspend fun getCreditScore(): CreditScoreResponse

    @GET("api/v1/credit-score/suggestions")
    suspend fun getCreditScoreSuggestions(): CreditScoreSuggestionsResponse

    @GET("api/v1/trust-score")
    suspend fun getTrustScore(): TrustScoreResponse

    // Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own
    // doc comment. bank-mfe already has this; this is the first native client.
    @GET("api/v1/agent/till")
    suspend fun getAgentTill(): AgentTillResponse

    @GET("api/v1/agent/activity")
    suspend fun getAgentActivity(@Query("limit") limit: Int = 30): AgentActivityResponse

    @POST("api/v1/agent/cash-ins")
    suspend fun agentCashIn(@Header("Idempotency-Key") idempotencyKey: String, @Body request: AgentCashInRequest): AgentCashResultResponse

    @POST("api/v1/agent/cash-outs")
    suspend fun agentCashOut(@Header("Idempotency-Key") idempotencyKey: String, @Body request: AgentCashOutRequest): AgentCashResultResponse

    @POST("api/v1/agent/till-reconciliations")
    suspend fun submitAgentTillCount(@Body request: SubmitTillCountRequest): AgentTillReconciliationResponse

    // Real gap found live (uncalled-endpoint sweep, 2026-08-16) -- see backend
    // AgentService.setLocationForOperator's own doc comment. The real customer-facing
    // "nearby agents" feature depends entirely on this; no real agent had any way to
    // report it before.
    @POST("api/v1/agent/location")
    suspend fun setAgentLocation(@Body request: SetAgentLocationRequest): AgentResponse

    // Real peer-to-peer agent float rebalancing marketplace -- see FloatMarketplaceController.kt.
    @POST("api/v1/float-marketplace/listings")
    suspend fun postFloatListing(@Body request: PostFloatListingRequest): FloatListingResponse

    @GET("api/v1/float-marketplace/listings/nearby")
    suspend fun getNearbyFloatListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 20.0,
    ): NearbyFloatListingsResponse

    @GET("api/v1/float-marketplace/listings/mine")
    suspend fun getMyFloatListings(): FloatListingsResponse

    @POST("api/v1/float-marketplace/listings/{listingId}/cancel")
    suspend fun cancelFloatListing(@Path("listingId") listingId: String): FloatListingResponse

    @POST("api/v1/float-marketplace/listings/{listingId}/requests")
    suspend fun requestFloat(@Path("listingId") listingId: String, @Body request: RequestFloatRequest): FloatTransferRequestResponse

    @GET("api/v1/float-marketplace/requests/mine")
    suspend fun getMyFloatRequests(): FloatTransferRequestsResponse

    @GET("api/v1/float-marketplace/requests/incoming")
    suspend fun getIncomingFloatRequests(): FloatTransferRequestsResponse

    @POST("api/v1/float-marketplace/requests/{requestId}/accept")
    suspend fun acceptFloatRequest(@Path("requestId") requestId: String, @Header("Idempotency-Key") idempotencyKey: String): FloatTransferRequestResponse

    @POST("api/v1/float-marketplace/requests/{requestId}/decline")
    suspend fun declineFloatRequest(@Path("requestId") requestId: String): FloatTransferRequestResponse

    // Idempotency-Key added 2026-09-07 (Certificate product-completeness pass) -- issue
    // is the one endpoint where a lost response causes irreversible harm: the private
    // key is returned exactly once and never persisted (CertificateService.kt's own
    // doc comment). A retry after a timeout previously created a brand-new certificate
    // (silently revoking the one just issued), permanently orphaning a private key the
    // user may never have actually received.
    @POST("api/v1/certificate/issue")
    suspend fun issueCertificate(@Header("Idempotency-Key") idempotencyKey: String): IssueCertificateResponse

    @GET("api/v1/certificate/me")
    suspend fun getMyCertificate(): MyCertificateResponse

    @POST("api/v1/certificate/revoke")
    suspend fun revokeCertificate(): RevokeCertificateResponse

    // Real public certificate status/verify (2026-08-04) -- found via a fresh
    // "defined but uncalled" endpoint sweep: real, working, deliberately unauthenticated
    // endpoints (see CertificateController's own doc comment on why /status and /verify
    // are permitAll, unlike /issue-/me/-revoke) with zero client anywhere, including
    // this app which already wires the other three. Lets any itunda user check whether
    // a certificate serial number a counterpart shared with them is still valid, and
    // verify a signed payload against it -- the actual "does this signed thing check
    // out" use case a personal signing certificate exists for.
    @GET("api/v1/certificate/status/{serialNumber}")
    suspend fun getCertificateStatus(@Path("serialNumber") serialNumber: String): CertificateStatusResponse

    @POST("api/v1/certificate/verify")
    suspend fun verifyCertificateSignature(@Body request: VerifyCertificateSignatureRequest): VerifyCertificateSignatureResponse

    @POST("api/v1/identity/submit")
    suspend fun submitIdentity(@Body request: SubmitIdentityRequest): SubmitIdentityResponse

    @GET("api/v1/identity/status")
    suspend fun getIdentityStatus(): IdentityStatusResponse

    @POST("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun createSplitBill(
        @Path("groupConversationId") groupConversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateSplitBillRequest,
    ): CreateSplitBillResponse

    @GET("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun getSplitBillsForGroup(@Path("groupConversationId") groupConversationId: String): SplitBillsForGroupResponse

    @GET("api/v1/split-bills/{id}")
    suspend fun getSplitBill(@Path("id") splitBillId: String): SplitBillResponse

    @POST("api/v1/split-bills/{id}/pay")
    suspend fun paySplitBillShare(@Path("id") splitBillId: String, @Header("Idempotency-Key") idempotencyKey: String): PaySplitBillShareResponse

    @POST("api/v1/split-bills/{id}/receipt")
    suspend fun attachSplitBillReceipt(@Path("id") splitBillId: String, @Body request: AttachSplitBillReceiptRequest): SplitBillOnlyResponse

    // Real 1:1-chat split-bill entry point (2026-08-09) -- see backend
    // SplitBillService.createDirectSplitBill's own doc comment: resolves a hidden
    // 2-person group between the caller and otherUserId first, so this never needs an
    // existing named group the way createSplitBill above does.
    @POST("api/v1/split-bills/direct/{otherUserId}")
    suspend fun createDirectSplitBill(
        @Path("otherUserId") otherUserId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateDirectSplitBillRequest,
    ): CreateSplitBillResponse

    // Real read-only counterpart -- never creates a hidden group as a side effect of
    // just viewing this tab; see backend SplitBillService.getDirectSplitBills's own
    // doc comment.
    @GET("api/v1/split-bills/direct/{otherUserId}")
    suspend fun getDirectSplitBills(@Path("otherUserId") otherUserId: String): SplitBillsForGroupResponse

    @POST("api/v1/split-bills/{id}/next-round")
    suspend fun requestSplitBillNextRound(@Path("id") splitBillId: String, @Header("Idempotency-Key") idempotencyKey: String): SplitBillOnlyResponse

    @GET("api/v1/contacts")
    suspend fun getContacts(): ContactsResponse

    @POST("api/v1/contacts")
    suspend fun addContact(@Body request: AddContactRequest): AddContactResponse

    // Real 26-week savings plan (2026-07-21) -- see WeeklySavingsPlanDto's own doc
    // comment. No Idempotency-Key header on any of these -- WeeklySavingsController
    // genuinely doesn't declare that header for this feature, unlike deposit/
    // claimInterest/sendDirect above.
    @GET("api/v1/weekly-savings/plans")
    suspend fun getWeeklySavingsPlans(): WeeklySavingsPlansResponse

    @GET("api/v1/weekly-savings/plans/{id}")
    suspend fun getWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsPlanDetailResponse

    @GET("api/v1/weekly-savings/plans/{id}/transactions")
    suspend fun getWeeklySavingsPlanTransactions(@Path("id") id: String): BucketTransactionsResponse

    @POST("api/v1/weekly-savings/plans")
    suspend fun createWeeklySavingsPlan(@Body request: CreateWeeklySavingsPlanRequest): CreateWeeklySavingsPlanResponse

    @POST("api/v1/weekly-savings/plans/{id}/cancel")
    suspend fun cancelWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    @POST("api/v1/weekly-savings/plans/{id}/withdraw")
    suspend fun withdrawWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    // Real 31-day daily savings plan (2026-08-12) -- see Grow31SavingsPlanDto's own doc
    // comment. Unlike weekly-savings above, create/depositToday/cancel/withdraw all
    // genuinely require the Idempotency-Key header -- Grow31SavingsController declares
    // it on every state-changing endpoint.
    @GET("api/v1/grow31-savings/plans")
    suspend fun getGrow31SavingsPlans(): Grow31SavingsPlansResponse

    @GET("api/v1/grow31-savings/plans/{id}")
    suspend fun getGrow31SavingsPlan(@Path("id") id: String): Grow31SavingsPlanDetailResponse

    @GET("api/v1/grow31-savings/plans/{id}/transactions")
    suspend fun getGrow31SavingsPlanTransactions(@Path("id") id: String): BucketTransactionsResponse

    @POST("api/v1/grow31-savings/plans")
    suspend fun createGrow31SavingsPlan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateGrow31SavingsPlanRequest): CreateGrow31SavingsPlanResponse

    @POST("api/v1/grow31-savings/plans/{id}/deposit-today")
    suspend fun depositGrow31SavingsToday(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    @POST("api/v1/grow31-savings/plans/{id}/cancel")
    suspend fun cancelGrow31SavingsPlan(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    @POST("api/v1/grow31-savings/plans/{id}/withdraw")
    suspend fun withdrawGrow31SavingsPlan(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
    // equivalent (2026-07-25) -- see backend UpfrontInterestDeposit's own doc comment.
    @GET("api/v1/upfront-deposits")
    suspend fun getUpfrontDeposits(): UpfrontDepositsResponse

    @POST("api/v1/upfront-deposits")
    suspend fun openUpfrontDeposit(@Body request: OpenUpfrontDepositRequest): UpfrontDepositResponse

    @POST("api/v1/upfront-deposits/{id}/withdraw")
    suspend fun withdrawUpfrontDeposit(@Path("id") id: String): UpfrontDepositResponse

    @GET("api/v1/upfront-deposits/{id}/transactions")
    suspend fun getUpfrontDepositTransactions(@Path("id") id: String): BucketTransactionsResponse

    // Real KakaoBank mini-style capped starter account (rw.itunda.account.
    // YouthAccountService, 2026-07-28) -- first mobile client for this feature (item 100),
    // mirroring bank-mfe's lib/miniAccount.ts equivalent added one item earlier.
    @POST("api/v1/account/youth/open")
    suspend fun openYouthAccount(): OpenYouthAccountResponse

    @POST("api/v1/account/youth/deposit")
    suspend fun depositYouthAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositYouthAccountRequest): DepositYouthAccountResponse

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first Android client for this feature (item 109, found via a fresh matrix scan:
    // bank-mfe has had it since the same day, Android/iOS never did). Mirrors
    // bank-mfe's lib/rideshare.ts exactly.
    @POST("api/v1/rides/drivers/register")
    suspend fun registerAsRideDriver(@Body request: RegisterRideDriverRequest): RideDriverResponse

    @GET("api/v1/rides/drivers/me")
    suspend fun getMyRideDriverProfile(): RideDriverResponse

    @POST("api/v1/rides/drivers/availability")
    suspend fun setRideDriverAvailability(@Body request: SetRideDriverAvailabilityRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/location")
    suspend fun updateRideDriverLocation(@Body request: UpdateRideDriverLocationRequest): RideDriverResponse

    // Real Uber "Destination Filter" + driver earnings report (uncalled-endpoint
    // sweep follow-up, item 246) -- see RideDriverService.setDestination/
    // RideTripService.getMyEarnings's own doc comments. Both real, fully-built
    // backend endpoints found with zero client anywhere; bank-mfe already has this
    // (2026-08-21); this is the Android port. A driver heading somewhere real
    // (e.g. home) sets it here and only gets offered trips heading that direction.
    @POST("api/v1/rides/drivers/destination")
    suspend fun setRideDriverDestination(@Body request: SetRideDriverDestinationRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/destination/clear")
    suspend fun clearRideDriverDestination(): RideDriverResponse

    @GET("api/v1/rides/trips/my-earnings")
    suspend fun getMyRideEarnings(@Query("from") from: String? = null, @Query("to") to: String? = null): RideEarningsResponse

    @POST("api/v1/rides/trips")
    suspend fun requestRideTrip(@Body request: RequestRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

    @GET("api/v1/rides/trips/available")
    suspend fun getAvailableRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-trips")
    suspend fun getMyRideTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): RideTripsPageResponse

    @GET("api/v1/rides/trips/my-driver-trips")
    suspend fun getMyRideDriverTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): RideTripsPageResponse

    @POST("api/v1/rides/trips/{tripId}/accept")
    suspend fun acceptRideTrip(
        @Path("tripId") tripId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/decline")
    suspend fun declineRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real driver-side cancel-after-acceptance (Rideshare product-completeness pass,
    // 2026-09-06) -- see RideTripService.driverCancelTrip's own doc comment.
    @POST("api/v1/rides/trips/{tripId}/driver-cancel")
    suspend fun driverCancelRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Uber "Verify Your Ride" PIN (uber.com/pl/en/blog/pin-number) -- the driver
    // must enter the exact 4-digit code the passenger reads aloud before the trip (and
    // the fare clock) actually starts.
    @POST("api/v1/rides/trips/{tripId}/start")
    suspend fun startRideTrip(@Path("tripId") tripId: String, @Body request: StartRideTripRequest): RideTripResponse

    // Real passenger-only PIN lookup -- a stranger, or even the trip's own driver, gets
    // a real 404 from the backend.
    @GET("api/v1/rides/trips/{tripId}/pin")
    suspend fun getRideTripPin(@Path("tripId") tripId: String): RideTripPinResponse

    // Real live driver-location tracking (Rideshare product-completeness pass,
    // 2026-09-06) -- see RideTripService.getDriverLocation's own doc comment.
    @GET("api/v1/rides/trips/{tripId}/location")
    suspend fun getRideDriverLocation(@Path("tripId") tripId: String): RideDriverLocationResponse

    @POST("api/v1/rides/trips/{tripId}/complete")
    suspend fun completeRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Uber post-trip tipping -- see RideTripDto.tipAmount's own doc comment. Real
    // Idempotency-Key required -- a tip is a real account-to-account transfer.
    @POST("api/v1/rides/trips/{tripId}/tip")
    suspend fun tipRideDriver(@Path("tripId") tripId: String, @Body request: TipRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/cancel")
    suspend fun cancelRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
    // driver rating (item 213) -- first Android client for these three, backend and
    // bank-mfe real since 2026-07-31. Mirrors bank-mfe's lib/rideshare.ts exactly.
    @GET("api/v1/rides/trips/{tripId}/stops")
    suspend fun getRideTripStops(@Path("tripId") tripId: String): RideTripStopsResponse

    @POST("api/v1/rides/trips/{tripId}/stops/arrive")
    suspend fun arriveAtRideStop(@Path("tripId") tripId: String): RideTripStopResponse

    @POST("api/v1/rides/trips/{tripId}/review")
    suspend fun submitRideReview(@Path("tripId") tripId: String, @Body request: SubmitRideReviewRequest): RideTripReviewResponse

    @GET("api/v1/rides/drivers/{driverId}/rating")
    suspend fun getRideDriverRating(@Path("driverId") driverId: String): RideDriverRatingResponse

    // Real "meet your driver" rating + reviews during an active trip (item 233) --
    // found via the uncalled-endpoint sweep, see bank-mfe's lib/rideshare.ts own doc
    // comment on fetchDriverReviews for the full sourced account. bank-mfe shipped
    // this first (2026-08-05); this is the Android port.
    @GET("api/v1/rides/drivers/{driverId}/reviews")
    suspend fun getRideDriverReviews(@Path("driverId") driverId: String, @Query("size") size: Int = 10): RideDriverReviewsResponse

    // Real Uber Safety "Trusted Contacts" (help.uber.com -- riders pre-select up to 5
    // trusted contacts once in settings, then one tap sends their live trip status to
    // all of them). See the backend's RideTrustedContact.kt doc comment for the full
    // sourced account. Found via the uncalled-endpoint sweep (item 159, 2026-08-18):
    // RideController already had a complete, tested list/add/remove/send-status
    // implementation with zero client callers on any platform -- bank-mfe closed this
    // first, this is the first Android client. Mirrors bank-mfe's lib/rideshare.ts
    // fetchTrustedContacts/addTrustedContact/removeTrustedContact/
    // sendStatusToTrustedContacts exactly.
    @GET("api/v1/rides/trusted-contacts")
    suspend fun getRideTrustedContacts(): RideTrustedContactsResponse

    @POST("api/v1/rides/trusted-contacts")
    suspend fun addRideTrustedContact(@Body request: AddRideTrustedContactRequest): RideTrustedContactResponse

    @DELETE("api/v1/rides/trusted-contacts/{contactId}")
    suspend fun removeRideTrustedContact(@Path("contactId") contactId: String): SuccessResponse

    // Real Uber "Send Status" -- one tap fans a trip's live status out to every trusted
    // contact at once (distinct from shareRideTripStatus's single-conversation pick).
    // Returns how many contacts were actually messaged so the client can show a real
    // "Sent to N contacts" confirmation, matching Uber's own toast.
    @POST("api/v1/rides/trips/{tripId}/send-status")
    suspend fun sendStatusToRideTrustedContacts(@Path("tripId") tripId: String): SendStatusToTrustedContactsResponse

    // Real Kakao T 대리운전 (designated driver, item 221) -- first Android client for
    // this feature. bank-mfe already has this; mirrors lib/designatedDriver.ts exactly.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful register would previously resubmit
    // here and hit the backend's own DesignatedDriverAlreadyRegisteredException
    // guard on retry.
    @POST("api/v1/designated-driver/drivers/register")
    suspend fun registerAsDesignatedDriver(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RegisterDesignatedDriverRequest): DesignatedDriverResponse

    @GET("api/v1/designated-driver/drivers/me")
    suspend fun getMyDesignatedDriverProfile(): DesignatedDriverResponse

    @POST("api/v1/designated-driver/drivers/availability")
    suspend fun setDesignatedDriverAvailability(@Body request: SetDesignatedDriverAvailabilityRequest): DesignatedDriverResponse

    @POST("api/v1/designated-driver/drivers/location")
    suspend fun updateDesignatedDriverLocation(@Body request: UpdateDesignatedDriverLocationRequest): DesignatedDriverResponse

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // DesignatedDriverController.requestTrip doc comment for the full account.
    @POST("api/v1/designated-driver/trips")
    suspend fun requestDesignatedDriverTrip(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RequestDesignatedDriverTripRequest): DesignatedDriverTripResponse

    @GET("api/v1/designated-driver/trips/available")
    suspend fun getAvailableDesignatedDriverTrips(): DesignatedDriverTripsResponse

    @GET("api/v1/designated-driver/trips/my-trips")
    suspend fun getMyDesignatedDriverTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): DesignatedDriverTripsResponse

    @GET("api/v1/designated-driver/trips/my-driver-trips")
    suspend fun getMyDesignatedDriverDriverTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): DesignatedDriverTripsResponse

    @POST("api/v1/designated-driver/trips/{tripId}/accept")
    suspend fun acceptDesignatedDriverTrip(
        @Path("tripId") tripId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/start-driving")
    suspend fun startDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/complete")
    suspend fun completeDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/cancel")
    suspend fun cancelDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    // Real Kakao T 바이크 (Kakao T Bike, item 222) -- real peer-to-peer bike/scooter
    // rental, billed by elapsed time (not a pre-known fare like ride-hailing/designated-
    // driver). First Android client. bank-mfe already has this; mirrors lib/bikeshare.ts
    // exactly.
    @POST("api/v1/bikeshare/bikes")
    suspend fun registerBike(@Body request: RegisterBikeRequest): BikeResponse

    @GET("api/v1/bikeshare/bikes/mine")
    suspend fun getMyBikes(): BikesResponse

    @POST("api/v1/bikeshare/bikes/{bikeId}/availability")
    suspend fun setBikeAvailability(@Path("bikeId") bikeId: String, @Body request: SetBikeAvailabilityRequest): BikeResponse

    @POST("api/v1/bikeshare/bikes/{bikeId}/location")
    suspend fun updateBikeLocation(@Path("bikeId") bikeId: String, @Body request: UpdateBikeLocationRequest): BikeResponse

    @GET("api/v1/bikeshare/bikes/nearby")
    suspend fun getNearbyBikes(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): BikesResponse

    @POST("api/v1/bikeshare/rentals")
    suspend fun startBikeRental(@Body request: StartBikeRentalRequest, @Header("Idempotency-Key") idempotencyKey: String): BikeRentalResponse

    @POST("api/v1/bikeshare/rentals/{sessionId}/end")
    suspend fun endBikeRental(@Path("sessionId") sessionId: String, @Body request: EndBikeRentalRequest, @Header("Idempotency-Key") idempotencyKey: String): BikeRentalResponse

    @GET("api/v1/bikeshare/rentals/my-history")
    suspend fun getMyBikeRentalHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 20): BikeRentalsResponse

    // Real Kakao T 주차 (Kakao T Parking, item 223) -- real peer-to-peer parking-spot
    // rental, billed by elapsed hours (not a pre-known fare, same "settle at checkout"
    // shape Bike already establishes). First Android client. bank-mfe already has this;
    // mirrors lib/parking.ts exactly.
    @POST("api/v1/parking/spots")
    suspend fun registerParkingSpot(@Body request: RegisterParkingSpotRequest): ParkingSpotResponse

    @GET("api/v1/parking/spots/mine")
    suspend fun getMyParkingSpots(): ParkingSpotsResponse

    @POST("api/v1/parking/spots/{spotId}/availability")
    suspend fun setParkingSpotAvailability(@Path("spotId") spotId: String, @Body request: SetParkingSpotAvailabilityRequest): ParkingSpotResponse

    @GET("api/v1/parking/spots/nearby")
    suspend fun getNearbyParkingSpots(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): ParkingSpotsResponse

    @POST("api/v1/parking/sessions")
    suspend fun startParkingSession(@Body request: StartParkingSessionRequest, @Header("Idempotency-Key") idempotencyKey: String): ParkingSessionResponse

    @POST("api/v1/parking/sessions/{sessionId}/end")
    suspend fun endParkingSession(@Path("sessionId") sessionId: String, @Header("Idempotency-Key") idempotencyKey: String): ParkingSessionResponse

    @GET("api/v1/parking/sessions/my-history")
    suspend fun getMyParkingHistory(@Query("page") page: Int = 0, @Query("size") size: Int = 20): ParkingSessionsResponse

    // Real Kakao T 시외버스 (intercity bus booking, item 224) -- real peer-to-peer
    // coach-operator trip pool, fare known and charged in full at booking time (unlike
    // Parking/Bike's settle-at-checkout shape). First Android client. bank-mfe already
    // has this; mirrors lib/bus.ts exactly.
    @POST("api/v1/bus/trips")
    suspend fun postBusTrip(@Body request: PostBusTripRequest): BusTripResponse

    @GET("api/v1/bus/trips/mine")
    suspend fun getMyBusTrips(): BusTripsResponse

    @GET("api/v1/bus/trips/{tripId}/bookings")
    suspend fun getBusTripBookings(@Path("tripId") tripId: String): BusBookingsResponse

    @GET("api/v1/bus/trips/search")
    suspend fun searchBusTrips(
        @Query("origin") origin: String? = null,
        @Query("destination") destination: String? = null,
    ): BusTripsResponse

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // BusController.bookSeats doc comment for the full account.
    @POST("api/v1/bus/bookings")
    suspend fun bookBusSeats(@Header("Idempotency-Key") idempotencyKey: String, @Body request: BookBusSeatsRequest): BusBookingResponse

    @POST("api/v1/bus/bookings/{bookingId}/cancel")
    suspend fun cancelBusBooking(@Path("bookingId") bookingId: String, @Header("Idempotency-Key") idempotencyKey: String): BusBookingResponse

    @GET("api/v1/bus/bookings/my-history")
    suspend fun getMyBusBookings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): BusBookingsResponse

    // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a
    // genuinely different shape from the trip/rental features above: no account
    // movement, no location, just a real question -> competing answers ->
    // asker-adopts-one-best-answer content flow. First Android client. bank-mfe
    // already has this; mirrors lib/knowledge.ts exactly.
    @GET("api/v1/knowledge/categories")
    suspend fun getKnowledgeCategories(): KnowledgeCategoriesResponse

    @POST("api/v1/knowledge/questions")
    suspend fun postKnowledgeQuestion(@Body request: PostKnowledgeQuestionRequest): KnowledgeQuestionResponse

    // Real pagination-discard fix (Knowledge product-completeness pass named this
    // systemic; ported to web first, 2026-09-09, commit 134758cf) -- the backend's
    // real Pageable/pageMeta convention was always there; page/size just weren't
    // ever sent, silently capping every Knowledge list at its first 20 rows.
    @GET("api/v1/knowledge/questions")
    suspend fun getKnowledgeQuestions(@Query("category") category: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): KnowledgeQuestionsResponse

    @GET("api/v1/knowledge/questions/my-questions")
    suspend fun getMyKnowledgeQuestions(@Query("page") page: Int = 0, @Query("size") size: Int = 20): KnowledgeQuestionsResponse

    @GET("api/v1/knowledge/answers/my-answers")
    suspend fun getMyKnowledgeAnswers(@Query("page") page: Int = 0, @Query("size") size: Int = 20): KnowledgeAnswersResponse

    @GET("api/v1/knowledge/reputation/me")
    suspend fun getMyKnowledgeReputation(): KnowledgeReputationResponse

    @GET("api/v1/knowledge/questions/{questionId}")
    suspend fun getKnowledgeQuestion(@Path("questionId") questionId: String): KnowledgeQuestionResponse

    @GET("api/v1/knowledge/questions/{questionId}/answers")
    suspend fun getKnowledgeAnswers(@Path("questionId") questionId: String): KnowledgeAnswersResponse

    @POST("api/v1/knowledge/questions/{questionId}/answers")
    suspend fun postKnowledgeAnswer(@Path("questionId") questionId: String, @Body request: PostKnowledgeAnswerRequest): KnowledgeAnswerResponse

    @POST("api/v1/knowledge/questions/{questionId}/answers/{answerId}/adopt")
    suspend fun adoptKnowledgeAnswer(@Path("questionId") questionId: String, @Path("answerId") answerId: String): KnowledgeAnswerResponse

    // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
    // rw.itunda.marketplace.VehicleInspectionService's own doc comment. A buyer books
    // and 100%-prepays a real mechanic to inspect a real Marketplace used-car listing
    // before purchase. bank-mfe already has this; this is the first Android client.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful register would previously resubmit
    // here and hit the backend's own MechanicAlreadyRegisteredException guard on
    // retry.
    @POST("api/v1/marketplace/inspections/mechanics/register")
    suspend fun registerAsInspectionMechanic(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RegisterInspectionMechanicRequest): VehicleInspectionMechanicResponse

    @GET("api/v1/marketplace/inspections/mechanics/me")
    suspend fun getMyInspectionMechanicProfile(): VehicleInspectionMechanicOrNullResponse

    @GET("api/v1/marketplace/inspections/mechanics")
    suspend fun getAvailableInspectionMechanics(): VehicleInspectionMechanicsResponse

    @POST("api/v1/marketplace/inspections/mechanics/availability")
    suspend fun setInspectionMechanicAvailability(@Body request: SetInspectionMechanicAvailabilityRequest): VehicleInspectionMechanicResponse

    @POST("api/v1/marketplace/inspections")
    suspend fun requestVehicleInspection(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RequestVehicleInspectionRequest): VehicleInspectionBookingResponse

    @GET("api/v1/marketplace/inspections/my-bookings")
    suspend fun getMyInspectionBookings(): VehicleInspectionBookingsResponse

    @GET("api/v1/marketplace/inspections/my-mechanic-bookings")
    suspend fun getMyInspectionMechanicBookings(): VehicleInspectionBookingsResponse

    @POST("api/v1/marketplace/inspections/{bookingId}/accept")
    suspend fun acceptVehicleInspection(@Path("bookingId") bookingId: String): VehicleInspectionBookingResponse

    // Idempotency-Key added (Hood product-completeness pass, 2026-09-07) -- posts a
    // real ledger payout to the mechanic, same class of real-money mutation
    // registerAsInspectionMechanic/requestVehicleInspection above already require it for.
    @POST("api/v1/marketplace/inspections/{bookingId}/complete")
    suspend fun completeVehicleInspection(@Path("bookingId") bookingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: CompleteVehicleInspectionRequest): VehicleInspectionBookingResponse

    // Idempotency-Key added (Hood product-completeness pass, 2026-09-07) -- same real
    // ledger-refund reasoning as completeVehicleInspection above.
    @POST("api/v1/marketplace/inspections/{bookingId}/cancel")
    suspend fun cancelVehicleInspection(@Path("bookingId") bookingId: String, @Header("Idempotency-Key") idempotencyKey: String): VehicleInspectionBookingResponse

    // Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
    // rw.itunda.vehicle.VehicleValuationService's own doc comment. bank-mfe already has
    // this; this is the first Android client.
    // Idempotency-Key added 2026-09-07 (Vehicle product-completeness pass) -- same bug
    // class registerAsMechanic (marketplace) already got fixed for: a lost response
    // after a successful registration previously resubmitted here and created a real
    // duplicate vehicle row (no uniqueness constraint exists).
    @POST("api/v1/vehicles")
    suspend fun registerVehicle(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RegisterVehicleRequest): VehicleResponse

    @GET("api/v1/vehicles")
    suspend fun getMyVehicles(): VehiclesResponse

    @GET("api/v1/vehicles/{id}/valuation")
    suspend fun getVehicleValuation(@Path("id") id: String): VehicleValuationResponse

    @POST("api/v1/vehicles/{id}/mileage")
    suspend fun updateVehicleMileage(@Path("id") id: String, @Body request: UpdateVehicleMileageRequest): VehicleResponse

    @DELETE("api/v1/vehicles/{id}")
    suspend fun removeVehicle(@Path("id") id: String): SuccessResponse

    // Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
    // rw.itunda.family.FamilyLinkService's own doc comment. Honest scope boundary: real
    // read-only spending oversight only, no new allowance mechanism (point an existing
    // AutoTransfer/ScheduledTransfer at the child's phone number instead). bank-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/family/invite")
    suspend fun inviteFamilyChild(@Body request: InviteChildRequest): FamilyLinkResponse

    @GET("api/v1/family/invites")
    suspend fun getMyFamilyInvites(): FamilyLinksResponse

    @POST("api/v1/family/invites/{id}/respond")
    suspend fun respondToFamilyInvite(@Path("id") id: String, @Body request: RespondToInviteRequest): FamilyLinkResponse

    @GET("api/v1/family/children")
    suspend fun getMyFamilyChildren(): FamilyLinkViewsResponse

    @GET("api/v1/family/guardians")
    suspend fun getMyFamilyGuardians(): FamilyLinkViewsResponse

    @GET("api/v1/family/children/{childUserId}/overview")
    suspend fun getChildOverview(@Path("childUserId") childUserId: String): ChildOverviewResponse

    @POST("api/v1/family/links/{id}/revoke")
    suspend fun revokeFamilyLink(@Path("id") id: String): FamilyLinkResponse

    // Real spend-limit enforcement (2026-07-27) -- see FamilyLinkDto.dailySpendLimit's
    // own doc comment. FamilyLinkController.setSpendLimit existed on the backend, already
    // real-enforced, with zero client anywhere until now. Pass null to clear the limit.
    @POST("api/v1/family/children/{childUserId}/spend-limit")
    suspend fun setFamilySpendLimit(@Path("childUserId") childUserId: String, @Body request: SetSpendLimitRequest): FamilyLinkResponse

    // Real Kakao Bank 모임통장 (group/shared account) equivalent -- first Android client
    // for this feature (item 104, found via a fresh matrix scan: zero client on either
    // mobile platform despite being real and live since well before this session).
    // Mirrors bank-mfe's lib/groupAccounts.ts exactly.
    @POST("api/v1/group-accounts")
    suspend fun createGroupAccount(@Body request: CreateGroupAccountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts")
    suspend fun getMyGroupAccounts(): GroupAccountsResponse

    @GET("api/v1/group-accounts/{id}")
    suspend fun getGroupAccount(@Path("id") id: String): GroupAccountDetailResponse

    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful invite would previously resubmit here
    // and hit the backend's own GroupAccountAlreadyMemberException guard on retry.
    @POST("api/v1/group-accounts/{id}/members")
    suspend fun inviteGroupAccountMember(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: InviteMemberRequest): InviteMemberResponse

    @POST("api/v1/group-accounts/{id}/deposit")
    suspend fun depositToGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @POST("api/v1/group-accounts/{id}/withdraw")
    suspend fun withdrawFromGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @PUT("api/v1/group-accounts/{id}/dues")
    suspend fun setGroupAccountDuesAmount(@Path("id") id: String, @Body request: SetDuesAmountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts/{id}/dues")
    suspend fun getGroupAccountDues(@Path("id") id: String): GroupAccountDuesResponse

    @POST("api/v1/group-accounts/{id}/dues/remind")
    suspend fun requestUnpaidGroupAccountDues(@Path("id") id: String): RemindUnpaidDuesResponse

    // Real ikimina (Rwanda's own rotating savings & credit association) -- see
    // IkiminaDto's own doc comment. Genuinely distinct from every Toss/Kakao/Naver/
    // Coupang-sourced feature in this backend.
    @POST("api/v1/ikiminas")
    suspend fun createIkimina(@Body request: CreateIkiminaRequest): CreateIkiminaResponse

    @GET("api/v1/ikiminas")
    suspend fun getMyIkiminas(): IkiminasResponse

    @GET("api/v1/ikiminas/{id}")
    suspend fun getIkimina(@Path("id") id: String): IkiminaDetailResponse

    @POST("api/v1/ikiminas/{id}/members")
    suspend fun inviteIkiminaMember(
        @Path("id") id: String,
        @Body request: InviteIkiminaMemberRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): InviteIkiminaMemberResponse

    @POST("api/v1/ikiminas/{id}/start")
    suspend fun startIkiminaCycle(@Path("id") id: String): CreateIkiminaResponse

    @POST("api/v1/ikiminas/{id}/contribute")
    suspend fun contributeToIkimina(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): CreateIkiminaResponse

    @POST("api/v1/ikiminas/{id}/payout")
    suspend fun triggerIkiminaPayout(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): IkiminaPayoutResponse

    // Real Umurenge SACCO-style shares & dividends -- see SaccoShareholdingDto's own
    // doc comment. Genuinely distinct from every Toss/Kakao/Naver/Coupang-sourced
    // feature in this backend and from Ikimina (rotating-pot ROSCA).
    @POST("api/v1/sacco/shares/buy")
    suspend fun buySaccoShares(@Body request: SaccoAmountRequest, @Header("Idempotency-Key") idempotencyKey: String): SaccoShareholdingResponse

    @POST("api/v1/sacco/shares/redeem")
    suspend fun redeemSaccoShares(@Body request: SaccoAmountRequest, @Header("Idempotency-Key") idempotencyKey: String): SaccoShareholdingResponse

    @GET("api/v1/sacco/shares/me")
    suspend fun getMySaccoShareholding(): SaccoShareholdingResponse

    @GET("api/v1/sacco/dividends/me")
    suspend fun getMySaccoDividendHistory(): SaccoDividendPayoutsResponse

    // Real Rwanda coffee-cooperative harvest-advance / input financing -- see
    // HarvestAdvanceDto's own doc comment. The third feature in this backend not
    // sourced from Toss/Kakao/Naver/Coupang. A direct itunda-to-farmer lending
    // relationship (loan_payable), not a cooperative-pool redistribution.
    @POST("api/v1/cooperatives")
    suspend fun registerCooperative(@Body request: RegisterCooperativeRequest): CooperativeResponse

    @POST("api/v1/cooperatives/{cooperativeId}/join")
    suspend fun joinCooperative(@Path("cooperativeId") cooperativeId: String): CooperativeMembershipResponse

    @GET("api/v1/cooperatives/my-memberships")
    suspend fun getMyCooperativeMemberships(): CooperativeMembershipsResponse

    // Real member-facing cooperative detail (2026-08-04) -- CooperativeController.
    // getCooperativeOverview existed on the backend (real 403 via NotMemberException for
    // a non-member) with zero client anywhere: a member could request/repay advances but
    // never actually saw their own cooperative's name, crop type, or member count.
    @GET("api/v1/cooperatives/{cooperativeId}/overview")
    suspend fun getCooperativeOverview(@Path("cooperativeId") cooperativeId: String): CooperativeOverviewResponse

    // Idempotency-Key added 2026-09-05 -- matching disburse/repay below. Worse than a
    // mere confusing-error risk without it: CooperativeService.requestAdvance has no
    // "already pending" guard at all, so a lost response after a successful request
    // would previously resubmit here and silently create a SECOND harvest advance
    // (see CooperativeController.requestAdvance's own doc comment for the full gap).
    @POST("api/v1/cooperatives/advances")
    suspend fun requestHarvestAdvance(@Body request: RequestAdvanceRequest, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

    @POST("api/v1/cooperatives/advances/{advanceId}/disburse")
    suspend fun disburseHarvestAdvance(@Path("advanceId") advanceId: String, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

    // amount must exactly equal the advance's own principalAmount -- real bug caught
    // and fixed before this feature shipped: a free-form amount let a token repayment
    // silently forgive the rest of a real debt. No amount is user-editable client-side.
    @POST("api/v1/cooperatives/advances/{advanceId}/repay")
    suspend fun repayHarvestAdvance(@Path("advanceId") advanceId: String, @Body request: RepayAdvanceRequest, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

    @GET("api/v1/cooperatives/advances/my-advances")
    suspend fun getMyHarvestAdvances(): HarvestAdvancesResponse

    // Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
    // microloan -- see VupLoanDto's own doc comment. Correction, 2026-09-05: the
    // "no Idempotency-Key on apply, not money movement" reasoning below missed the
    // actual risk -- VupLoanService.applyForLoan's own VupLoanAlreadyActiveException
    // guard fires on a legitimate lost-response retry regardless of whether money
    // moved yet (see VupLoanController.apply's own doc comment for the full gap) --
    // now protected, same convention as every other money-moving call in this file.
    @POST("api/v1/loans/vup/apply")
    suspend fun applyForVupLoan(@Body request: ApplyForVupLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

    @POST("api/v1/loans/vup/{loanId}/disburse")
    suspend fun disburseVupLoan(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

    @POST("api/v1/loans/vup/{loanId}/repay")
    suspend fun repayVupLoan(@Path("loanId") loanId: String, @Body request: RepayVupLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

    @GET("api/v1/loans/vup/my")
    suspend fun getMyVupLoans(): VupLoansResponse

    @GET("api/v1/loans/vup/eligibility")
    suspend fun getVupLoanEligibility(): VupLoanEligibilityResponse

    @GET("api/v1/loans/vup/{loanId}")
    suspend fun getVupLoan(@Path("loanId") loanId: String): VupLoanResponse

    // Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan --
    // see StudentLoanDto's own doc comment. Correction, 2026-09-05: apply is now
    // Idempotency-Key protected, same reasoning correction as applyForVupLoan above
    // (a lost-response retry hits StudentLoanAlreadyActiveException regardless of
    // whether money moved yet). declare-graduated below has the same class of gap
    // (a retry after success hits StudentLoanNotDisbursedException, since the loan
    // is no longer DISBURSED) but is lower-frequency/lower-stakes -- a disclosed,
    // not-yet-fixed follow-up, not addressed in this pass. disburse/repay both
    // require Idempotency-Key, matching every other money-moving call in this file.
    @POST("api/v1/loans/student/apply")
    suspend fun applyForStudentLoan(@Body request: ApplyForStudentLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/disburse")
    suspend fun disburseStudentLoan(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/declare-graduated")
    // Idempotency-Key added 2026-09-05 -- a lost response after a successful
    // declare-graduated would previously resubmit here and hit the backend's own
    // StudentLoanNotDisbursedException (the loan is no longer DISBURSED) on retry.
    suspend fun declareStudentLoanGraduated(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/repay")
    suspend fun repayStudentLoan(@Path("loanId") loanId: String, @Body request: RepayStudentLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @GET("api/v1/loans/student/my")
    suspend fun getMyStudentLoans(): StudentLoansResponse

    @GET("api/v1/loans/student/{loanId}/suggested-payment")
    suspend fun getStudentLoanSuggestedPayment(@Path("loanId") loanId: String): StudentLoanSuggestedPaymentResponse

    // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
    // MotoOwnershipPlanDto's own doc comment for the full sourced account.
    // Correction, 2026-09-05: the "no Idempotency-Key on create, not money movement"
    // reasoning missed the actual risk -- MotoOwnershipService.createPlan's own
    // MotoOwnershipPlanAlreadyActiveException guard fires on a legitimate
    // lost-response retry regardless of whether money moved yet (see
    // MotoOwnershipController.createPlan's own doc comment for the full gap) -- now
    // protected, same convention as every other money-moving call in this file.
    @POST("api/v1/moto-ownership/plans")
    suspend fun createMotoOwnershipPlan(@Body request: CreateMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/contribute")
    suspend fun contributeToMotoOwnershipPlan(@Path("planId") planId: String, @Body request: ContributeToMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/cancel")
    suspend fun cancelMotoOwnershipPlan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/convert-to-loan")
    suspend fun convertMotoOwnershipPlanToLoan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/repay")
    suspend fun repayMotoOwnershipPlan(@Path("planId") planId: String, @Body request: RepayMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @GET("api/v1/moto-ownership/plans/me")
    suspend fun getMyMotoOwnershipPlans(): MotoOwnershipPlansResponse

    @GET("api/v1/moto-ownership/plans/{planId}")
    suspend fun getMotoOwnershipPlan(@Path("planId") planId: String): MotoOwnershipPlanResponse
}
