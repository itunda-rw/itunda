import Foundation

// Real fix (2026-08-26): split out of NetworkClient.swift once that file grew past
// its file-size-lint baseline. This is a straight house-moving split of one
// contiguous extension block (Overview/AutoTopUp/Loans/Overdraft/Postpaid/Card/
// CreditScore/Agent/Float/Certificate/Identity/Support/SplitBill/Contacts/Group
// management endpoints) -- not a re-architected per-feature taxonomy, since these
// grew as one undifferentiated block across many sessions with no natural single-
// concern seam. Their DTOs stay defined earlier in NetworkClient.swift; same module
// (CoreNetwork), so no import changes needed anywhere.

extension NetworkClient {
    public func getOverview() async throws -> OverviewResponse { try await get("api/v1/overview") }

    public func getLinkedAccounts() async throws -> LinkedAccountsResponse { try await get("api/v1/accounts/linked") }

    // Idempotency-Key added 2026-09-07 (Overview product-completeness pass) -- a lost
    // response after a real successful link previously created a genuine duplicate
    // LinkedAccount row and burned a second real simulated ProviderConnector call.
    public func linkAccount(provider: String, externalAccountNumber: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link", body: LinkAccountRequest(provider: provider, externalAccountNumber: externalAccountNumber), idempotencyKey: UUID().uuidString)
    }

    public func unlinkAccount(accountId: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link/\(accountId)/unlink", body: EmptyRequest())
    }

    public func getAutoTopUpSetting(accountId: String) async throws -> GetAutoTopUpSettingResponse {
        try await get("api/v1/account/\(accountId)/auto-topup")
    }

    // Real gap found 2026-09-04 (device step-up/error-message-gap sweep): configure/
    // trigger both went through the plain authenticatedPut/authenticatedPost, so
    // AutoTopUpScreen.swift's catch blocks could only show TalkScreen.errorMessage's
    // generic per-status bucket -- swallowing AutoTopUpService's real, specific
    // messages ("Threshold amount cannot be negative", "Top-up amount must be
    // greater than zero", "Daily trigger cap must be at least 1", "This linked
    // account is not currently LINKED"), all real validation failures a multi-field
    // settings form like this one hits often.
    public func configureAutoTopUp(accountId: String, linkedAccountId: String, thresholdAmount: Double, topUpAmount: Double, dailyTriggerCap: Int = 3, enabled: Bool = true) async throws -> GetAutoTopUpSettingResponse {
        try await authenticatedPutWithMessage(
            "api/v1/account/\(accountId)/auto-topup",
            body: ConfigureAutoTopUpRequest(linkedAccountId: linkedAccountId, thresholdAmount: thresholdAmount, topUpAmount: topUpAmount, dailyTriggerCap: dailyTriggerCap, enabled: enabled)
        )
    }

    public func triggerAutoTopUp(accountId: String) async throws -> TriggerAutoTopUpResponse {
        try await authenticatedPostWithCode("api/v1/account/\(accountId)/auto-topup/trigger", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func getLoanOffers(lenderId: String? = nil) async throws -> LoanOffersResponse {
        if let lenderId { return try await get("api/v1/loans/offers", query: [URLQueryItem(name: "lenderId", value: lenderId)]) }
        return try await get("api/v1/loans/offers")
    }

    public func getLenders() async throws -> LendersResponse { try await get("api/v1/loans/lenders") }

    public func getMyLoans() async throws -> MyLoansResponse { try await get("api/v1/loans/my-loans") }

    public func applyForLoan(loanId: String, amount: Double) async throws -> ApplyLoanResponse {
        try await authenticatedPost("api/v1/loans/apply", body: ApplyLoanRequest(loanId: loanId, amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayLoan(loanId: String, amount: Double) async throws -> RepayLoanResponse {
        try await authenticatedPostWithCode("api/v1/loans/repay", body: RepayLoanRequest(loanId: loanId, amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func refinanceLoan(loanId: String) async throws -> RefinanceResult {
        try await authenticatedPost("api/v1/loans/refinance", body: RefinanceLoanRequest(loanId: loanId), idempotencyKey: UUID().uuidString)
    }

    public func getMyOverdraft() async throws -> OverdraftAccountResponse { try await get("api/v1/loans/overdraft") }

    public func openOverdraft(requestedLimit: Double) async throws -> OverdraftAccountResponse {
        try await authenticatedPost("api/v1/loans/overdraft/open", body: OpenOverdraftRequest(requestedLimit: requestedLimit), idempotencyKey: UUID().uuidString)
    }

    public func drawOverdraft(amount: Double) async throws -> OverdraftDrawResponse {
        try await authenticatedPost("api/v1/loans/overdraft/draw", body: OverdraftAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayOverdraft(amount: Double) async throws -> OverdraftRepayResponse {
        try await authenticatedPost("api/v1/loans/overdraft/repay", body: OverdraftAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
    // 2026-07-31) -- first iOS client for this feature, mirroring bank-mfe's
    // lib/loans.ts and Android's ApiService.kt exactly.
    public func getMyPostpaidCredit() async throws -> PostpaidCreditLineResponse { try await get("api/v1/loans/postpaid-credit") }

    public func applyForPostpaidCredit() async throws -> PostpaidCreditLineResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/apply", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func spendPostpaidCredit(amount: Double) async throws -> PostpaidCreditActionResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/spend", body: PostpaidCreditAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayPostpaidCredit(amount: Double) async throws -> PostpaidCreditActionResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/repay", body: PostpaidCreditAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    // Real gap found 2026-09-04: CardService has a genuinely rich, actionable real
    // message surface across every action (invalid design, limit bounds, "This card
    // was reported lost or stolen. Reissue a new card...", "This purchase would
    // exceed your daily/monthly card limit. $X RWF remaining" -- a dynamic real
    // remaining-limit figure, "Incorrect current password or PIN") that
    // CardScreenView.swift's bare `catch {}` blocks each flattened into one static
    // per-action string.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful issue would previously resubmit here
    // and hit the backend's own CardAlreadyIssuedException guard on retry.
    // authenticatedPostWithCode (not authenticatedPostWithMessage, which has no
    // idempotency support) is this module's internal-scoped POST helper reachable
    // from an extension file like this one -- postP2p in the main NetworkClient.swift
    // is fileprivate to that file only.
    public func issueCard(design: String) async throws -> CardResponse {
        try await authenticatedPostWithCode("api/v1/card/issue", body: IssueCardRequest(design: design), idempotencyKey: UUID().uuidString)
    }

    public func getMyTransitBalance() async throws -> TransitBalanceResponse { try await get("api/v1/transit/balance") }

    public func getTransitTrips() async throws -> TransitTripsResponse { try await get("api/v1/transit/trips") }

    public func topUpTransit(amount: Double) async throws -> TransitBalanceResponse {
        try await authenticatedPost("api/v1/transit/topup", body: TopUpTransitRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func tapTransitFare(operatorName: String, fare: Double) async throws -> TapFareResponse {
        try await authenticatedPost("api/v1/transit/tap", body: TapFareRequest(operatorName: operatorName, fare: fare), idempotencyKey: UUID().uuidString)
    }

    // Real gap found 2026-09-04: TransitService.chargeFare/MotoFareService's tap-
    // collect real messages ("This code has already been used", "This code has
    // expired -- ask the rider to refresh their Pay screen", "That's your own code",
    // "Your transit balance is too low for this fare. Top up and try again.") are
    // each a genuinely different real-world tap-to-pay failure the operator needs to
    // act on differently -- both screens' bare `catch {}` flattened all of them into
    // one static "Could not collect this fare." `postP2p` is fileprivate to
    // NetworkClient.swift, so this cross-file extension reuses the internal
    // postSavingsGoal instead (same idempotency-keyed/message-carrying/
    // DEVICE_NOT_VERIFIED-checked shape).
    public func tapTransitFareByCode(code: String, operatorName: String, fare: Double) async throws -> TapFareByCodeResponse {
        try await postSavingsGoal("api/v1/transit/tap-by-code", body: TapFareByCodeRequest(code: code, operatorName: operatorName, fare: fare), idempotencyKey: UUID().uuidString)
    }

    public func collectMotoFare(code: String, fare: Double) async throws -> CollectMotoFareResponse {
        try await postSavingsGoal("api/v1/moto-fare/collect", body: CollectMotoFareRequest(code: code, fare: fare), idempotencyKey: UUID().uuidString)
    }

    public func getMyMotoFareEarnings(page: Int = 0, size: Int = 20) async throws -> MotoFareEarningsResponse {
        try await get("api/v1/moto-fare/earnings?page=\(page)&size=\(size)")
    }

    // Real gap found live (uncalled-endpoint sweep, 2026-09-02): the backend's own
    // MotoFareController.getMyTripsAsRider ("/trips") is the exact symmetric
    // counterpart of getMyTripsAsDriver ("/earnings") above -- same MotoFareTripDto
    // shape, same pagination -- but had zero caller on any platform since the feature
    // shipped 2026-08-27. A rider who tapped to pay a moto-taxi fare had no way to see
    // their own trip history, only the driver side of this same feature was ever wired.
    public func getMyMotoFareTripsAsRider(page: Int = 0, size: Int = 20) async throws -> MotoFareEarningsResponse {
        try await get("api/v1/moto-fare/trips?page=\(page)&size=\(size)")
    }

    public func getMyCard() async throws -> CardResponse { try await get("api/v1/card/my-card") }

    public func getCardTransactions() async throws -> CardTransactionsResponse { try await get("api/v1/card/transactions") }

    public func setCardLimits(dailyLimit: Double, monthlyLimit: Double) async throws -> CardResponse {
        try await authenticatedPutWithMessage("api/v1/card/limits", body: SetCardLimitsRequest(dailyLimit: dailyLimit, monthlyLimit: monthlyLimit))
    }

    public func freezeCard() async throws -> CardResponse {
        try await authenticatedPostWithMessage("api/v1/card/freeze", body: EmptyRequest())
    }

    public func unfreezeCard() async throws -> CardResponse {
        try await authenticatedPostWithMessage("api/v1/card/unfreeze", body: EmptyRequest())
    }

    // Real "분실신고" (report lost or stolen) -- a distinct, one-way backend state
    // from the ordinary freeze/unfreeze toggle above (DebitCard.kt's own doc
    // comment). Closes the same gap web/Android's identical CardView already
    // closed this session (commits 378b8e4b/d3facc7b).
    public func reportCardLost() async throws -> CardResponse {
        try await authenticatedPostWithMessage("api/v1/card/report-lost", body: EmptyRequest())
    }

    // Real "카드 해지하기" (close card) -- also one-way; only reissueCard() below
    // can recover from it.
    public func closeCard() async throws -> CardResponse {
        try await authenticatedPostWithMessage("api/v1/card/close", body: EmptyRequest())
    }

    // Real "카드 재발급" (reissue) -- only allowed once a card is lost or closed;
    // regenerates last4 and clears the old PIN in place.
    // Real, live bug found 2026-09-07 (Certificate product-completeness pass, incidental
    // discovery): the backend requires Idempotency-Key with no default, but this call
    // had none -- every real reissue-card tap on iOS has been hard-failing with 400
    // IDEMPOTENCY_KEY_REQUIRED since the Card pass added the backend requirement.
    public func reissueCard() async throws -> CardResponse {
        try await authenticatedPostWithMessage("api/v1/card/reissue", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    // Real "카드 비밀번호 변경" (change card PIN) -- a real, separate 4-digit
    // debit-card PIN, distinct from the login password/PIN. Requires the current
    // login credential as step-up auth.
    public func setCardPin(newPin: String, currentCredential: String) async throws -> CardResponse {
        try await authenticatedPutWithMessage("api/v1/card/pin", body: SetCardPinRequest(newPin: newPin, currentCredential: currentCredential))
    }

    public func chargeCard(amount: Double, merchantName: String, fundingAccountType: String = "MAIN") async throws -> ChargeCardResponse {
        try await postSavingsGoal("api/v1/card/charge", body: ChargeCardRequest(amount: amount, merchantName: merchantName, fundingAccountType: fundingAccountType), idempotencyKey: UUID().uuidString)
    }

    public func getCreditScore() async throws -> CreditScoreResponse { try await get("api/v1/credit-score") }
    public func getCreditScoreSuggestions() async throws -> CreditScoreSuggestionsResponse { try await get("api/v1/credit-score/suggestions") }
    public func getTrustScore() async throws -> TrustScoreResponse { try await get("api/v1/trust-score") }

    // Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own
    // doc comment. bank-mfe/Android already have this; this is the first iOS client.
    public func getAgentTill() async throws -> AgentTillResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("api/v1/agent/till"))
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "AGENT_OPERATOR_NOT_AUTHORIZED" {
                throw NetworkError.agentOperatorNotAuthorized
            }
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(AgentTillResponse.self, from: data)
    }
    public func getAgentActivity(limit: Int = 30) async throws -> AgentActivityResponse {
        try await get("api/v1/agent/activity", query: [URLQueryItem(name: "limit", value: String(limit))])
    }
    public func setAgentLocation(latitude: Double, longitude: Double) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/agent/location", body: SetAgentLocationRequest(latitude: latitude, longitude: longitude))
    }
    public func agentCashIn(_ request: AgentCashInRequest) async throws -> AgentCashResultResponse {
        try await authenticatedPost("api/v1/agent/cash-ins", body: request, idempotencyKey: UUID().uuidString)
    }
    public func agentCashOut(_ request: AgentCashOutRequest) async throws -> AgentCashResultResponse {
        try await authenticatedPost("api/v1/agent/cash-outs", body: request, idempotencyKey: UUID().uuidString)
    }
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- matching agentCashIn/agentCashOut just above (authenticatedPost's
    // idempotencyKey param was already there, just unused here). A lost response
    // after a successful submission would previously resubmit this request and hit
    // TillReconciliationAlreadySubmittedException on the retry, a real
    // cash-handling confusion risk (highest-priority item this thread names).
    public func submitAgentTillCount(_ countedCash: Double) async throws -> AgentTillReconciliationResponse {
        try await authenticatedPost("api/v1/agent/till-reconciliations", body: SubmitAgentTillCountRequest(countedCash: countedCash), idempotencyKey: UUID().uuidString)
    }

    // Real peer-to-peer agent float rebalancing marketplace -- see FloatMarketplaceController.kt.
    // Real gap found 2026-09-04: FloatMarketplaceService has a rich, genuinely
    // distinct-per-case real message surface ("An agent cannot request float from
    // their own listing", "This float listing is no longer open", "Requested amount
    // exceeds what remains available", "This request has already been resolved",
    // "This agent's real till no longer has enough cash on hand to fulfill this
    // request" -- the last one entirely un-guarded client-side) that
    // FloatMarketplaceScreenView.swift's 5 bare `catch {}` blocks each flattened
    // into one static per-action string.
    public func postFloatListing(amount: Double) async throws -> FloatListingResponse {
        try await authenticatedPostWithMessage("api/v1/float-marketplace/listings", body: PostFloatListingRequest(amount: amount))
    }

    public func getNearbyFloatListings(latitude: Double, longitude: Double, radiusKm: Double = 20) async throws -> NearbyFloatListingsResponse {
        try await get("api/v1/float-marketplace/listings/nearby", query: [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    public func getMyFloatListings() async throws -> FloatListingsResponse { try await get("api/v1/float-marketplace/listings/mine") }

    public func cancelFloatListing(listingId: String) async throws -> FloatListingResponse {
        try await authenticatedPostWithMessage("api/v1/float-marketplace/listings/\(listingId)/cancel", body: EmptyBody())
    }

    public func requestFloat(listingId: String, amount: Double) async throws -> FloatTransferRequestResponse {
        try await authenticatedPostWithMessage("api/v1/float-marketplace/listings/\(listingId)/requests", body: RequestFloatRequest(amount: amount))
    }

    public func getMyFloatRequests() async throws -> FloatTransferRequestsResponse { try await get("api/v1/float-marketplace/requests/mine") }

    public func getIncomingFloatRequests() async throws -> FloatTransferRequestsResponse { try await get("api/v1/float-marketplace/requests/incoming") }

    public func acceptFloatRequest(requestId: String) async throws -> FloatTransferRequestResponse {
        try await postSavingsGoal("api/v1/float-marketplace/requests/\(requestId)/accept", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func declineFloatRequest(requestId: String) async throws -> FloatTransferRequestResponse {
        try await authenticatedPostWithMessage("api/v1/float-marketplace/requests/\(requestId)/decline", body: EmptyBody())
    }

    // Real "cash out at an agent" -- see AgentWithdrawalAuthorizationDto's own doc
    // comment. A fresh Idempotency-Key on create only: cancel is not idempotency-
    // keyed on the backend (AccountController.cancelAgentWithdrawalAuthorization),
    // matching bank-mfe's lib/agentWithdrawal.ts's identical omission.
    public func createAgentWithdrawalAuthorization(amount: Double) async throws -> AgentWithdrawalAuthorizationResponse {
        try await authenticatedPost("api/v1/account/agent-withdrawal-authorizations", body: CreateAgentWithdrawalAuthorizationRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getAgentWithdrawalAuthorizations() async throws -> AgentWithdrawalAuthorizationsResponse {
        try await get("api/v1/account/agent-withdrawal-authorizations")
    }

    public func cancelAgentWithdrawalAuthorization(code: String) async throws -> AgentWithdrawalAuthorizationResponse {
        try await authenticatedPost("api/v1/account/agent-withdrawal-authorizations/cancel", body: CancelAgentWithdrawalAuthorizationRequest(code: code))
    }

    // Idempotency-Key added 2026-09-07 (Certificate product-completeness pass) -- issue
    // is the one endpoint where a lost response causes irreversible harm: the private
    // key is returned exactly once and never persisted (CertificateService.kt's own
    // doc comment). A retry after a timeout previously created a brand-new certificate
    // (silently revoking the one just issued), permanently orphaning a private key the
    // user may never have actually received.
    public func issueCertificate() async throws -> IssueCertificateResponse {
        try await authenticatedPost("api/v1/certificate/issue", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func getMyCertificate() async throws -> MyCertificateResponse { try await get("api/v1/certificate/me") }

    public func revokeCertificate() async throws -> RevokeCertificateResponse {
        try await authenticatedPost("api/v1/certificate/revoke", body: EmptyRequest())
    }

    public func getCertificateStatus(serialNumber: String) async throws -> CertificateStatusResponse {
        // Unlike every other raw-interpolated GET path in this file, this value comes
        // directly from a free-text field the user types into (VerifyCertificateCard),
        // not a server-issued id -- percent-encode it as a single path SEGMENT (so a
        // pasted "/" is escaped too, not left to reshape the request path into extra
        // segments the way .urlPathAllowed alone would let it).
        var segmentAllowed = CharacterSet.urlPathAllowed
        segmentAllowed.remove(charactersIn: "/")
        let encoded = serialNumber.addingPercentEncoding(withAllowedCharacters: segmentAllowed) ?? serialNumber
        return try await get("api/v1/certificate/status/\(encoded)")
    }

    public func verifyCertificateSignature(serialNumber: String, payload: String, signature: String) async throws -> VerifyCertificateSignatureResponse {
        try await authenticatedPost("api/v1/certificate/verify", body: VerifyCertificateSignatureRequest(serialNumber: serialNumber, payload: payload, signature: signature))
    }

    // Real "verify with itunda" identity-verification-for-partners flow (Partners
    // product-completeness pass, 2026-09-07) -- ported from Android's own real,
    // already-live-verified ItundaAppScreen.kt IdentityVerificationConsentScreen.
    // Genuinely distinct from submitIdentity/getIdentityStatus above (personal KYC,
    // rw.itunda.identity): this is itunda vouching for a user's real identity TO a
    // third-party partner, reached via an itunda://verify/{requestId} deep link a
    // partner's own site shows, never itunda's own IdentityScreenView.
    public func getIdentityVerificationRequest(_ requestId: String) async throws -> IdentityVerificationRequestResponse {
        try await get("api/v1/identity/verification/\(requestId)")
    }

    public func approveIdentityVerification(_ requestId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/identity/verification/\(requestId)/approve", body: EmptyRequest())
    }

    public func declineIdentityVerification(_ requestId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/identity/verification/\(requestId)/decline", body: EmptyRequest())
    }

    public func getMiniAppCatalog(category: String? = nil, size: Int = 100) async throws -> MiniAppCatalogResponse {
        var query = [URLQueryItem(name: "size", value: String(size))]
        if let category { query.append(URLQueryItem(name: "category", value: category)) }
        return try await get("api/v1/mini-apps/catalog", query: query)
    }

    public func submitIdentity(documentType: String, documentNumber: String, documentReference: String) async throws -> SubmitIdentityResponse {
        try await authenticatedPost("api/v1/identity/submit", body: SubmitIdentityRequest(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference))
    }

    public func getIdentityStatus() async throws -> IdentityStatusResponse { try await get("api/v1/identity/status") }

    public func createSupportTicket(transactionId: String, category: String, description: String) async throws -> CreateSupportTicketResponse {
        try await authenticatedPost("api/v1/support/tickets", body: CreateSupportTicketRequest(transactionId: transactionId, category: category, description: description))
    }

    public func getSupportTickets() async throws -> SupportTicketsResponse { try await get("api/v1/support/tickets") }

    public func setUssdPin(_ pin: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/ussd/pin", body: SetUssdPinRequest(pin: pin))
    }

    // Real gap found 2026-09-04: SplitBillService has a genuinely rich real message
    // surface ("A split bill needs at least one other real participant", "Every
    // participant must be a real member of this group", "This share has already been
    // paid", real insufficient-funds, "This split bill is already settled", "Already
    // at the maximum of N settlement rounds") that TalkSplitBills.swift's bare
    // `catch {}` blocks each flattened into one static per-action string.
    public func createSplitBill(groupConversationId: String, totalAmount: Double, description: String, participantUserIds: [String], mode: String = "EVEN", ladderVarianceLevel: Int? = nil) async throws -> CreateSplitBillResponse {
        try await postSavingsGoal(
            "api/v1/split-bills/conversations/\(groupConversationId)",
            body: CreateSplitBillRequest(totalAmount: totalAmount, description: description, participantUserIds: participantUserIds, mode: mode, ladderVarianceLevel: ladderVarianceLevel),
            idempotencyKey: UUID().uuidString
        )
    }

    public func getSplitBillsForGroup(groupConversationId: String) async throws -> SplitBillsForGroupResponse {
        try await get("api/v1/split-bills/conversations/\(groupConversationId)")
    }

    // Real 1:1-chat split-bill entry point (2026-08-09) -- see backend
    // SplitBillService.createDirectSplitBill's own doc comment: resolves a hidden
    // 2-person group between the caller and otherUserId first, so this never needs an
    // existing named group the way createSplitBill above does.
    public func createDirectSplitBill(otherUserId: String, totalAmount: Double, description: String, mode: String = "EVEN", ladderVarianceLevel: Int? = nil) async throws -> CreateSplitBillResponse {
        try await postSavingsGoal(
            "api/v1/split-bills/direct/\(otherUserId)",
            body: CreateDirectSplitBillRequest(totalAmount: totalAmount, description: description, mode: mode, ladderVarianceLevel: ladderVarianceLevel),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real read-only counterpart -- never creates a hidden group as a side effect of
    // just viewing this tab; see backend SplitBillService.getDirectSplitBills's own
    // doc comment.
    public func getDirectSplitBills(otherUserId: String) async throws -> SplitBillsForGroupResponse {
        try await get("api/v1/split-bills/direct/\(otherUserId)")
    }

    public func paySplitBillShare(splitBillId: String) async throws -> PaySplitBillShareResponse {
        try await authenticatedPostWithCode("api/v1/split-bills/\(splitBillId)/pay", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func attachSplitBillReceipt(splitBillId: String, imageUrl: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPostWithMessage("api/v1/split-bills/\(splitBillId)/receipt", body: AttachSplitBillReceiptRequest(imageUrl: imageUrl))
    }

    public func requestSplitBillNextRound(splitBillId: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPostWithCode("api/v1/split-bills/\(splitBillId)/next-round", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func getContacts() async throws -> ContactsResponse { try await get("api/v1/contacts") }

    public func addContact(name: String, phoneNumber: String, bank: String? = nil) async throws -> AddContactResponse {
        try await authenticatedPost("api/v1/contacts", body: AddContactRequest(name: name, bank: bank, phoneNumber: phoneNumber))
    }

    // Real leave-group/add-member (found 2026-07-22 fully built on the backend with
    // zero UI anywhere, despite group chat itself being fully wired).
    public func addGroupMember(groupId: String, userId: String) async throws -> GroupResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/members", body: AddGroupMemberRequest(userId: userId))
    }

    public func leaveGroup(groupId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/groups/\(groupId)/members/me")
    }

    public func setGroupPhotoUrl(groupId: String, photoUrl: String) async throws -> GroupSummaryResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/photo", body: SetGroupPhotoUrlRequest(photoUrl: photoUrl))
    }

    public func setGroupDescription(groupId: String, description: String) async throws -> GroupSummaryResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/description", body: SetGroupDescriptionRequest(description: description))
    }
}
