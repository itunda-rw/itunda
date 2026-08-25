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

    public func linkAccount(provider: String, externalAccountNumber: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link", body: LinkAccountRequest(provider: provider, externalAccountNumber: externalAccountNumber))
    }

    public func unlinkAccount(accountId: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link/\(accountId)/unlink", body: EmptyRequest())
    }

    public func getAutoTopUpSetting(accountId: String) async throws -> GetAutoTopUpSettingResponse {
        try await get("api/v1/account/\(accountId)/auto-topup")
    }

    public func configureAutoTopUp(accountId: String, linkedAccountId: String, thresholdAmount: Double, topUpAmount: Double, dailyTriggerCap: Int = 3, enabled: Bool = true) async throws -> GetAutoTopUpSettingResponse {
        try await authenticatedPut(
            "api/v1/account/\(accountId)/auto-topup",
            body: ConfigureAutoTopUpRequest(linkedAccountId: linkedAccountId, thresholdAmount: thresholdAmount, topUpAmount: topUpAmount, dailyTriggerCap: dailyTriggerCap, enabled: enabled)
        )
    }

    public func triggerAutoTopUp(accountId: String) async throws -> TriggerAutoTopUpResponse {
        try await authenticatedPost("api/v1/account/\(accountId)/auto-topup/trigger", body: EmptyRequest())
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
        try await authenticatedPost("api/v1/loans/repay", body: RepayLoanRequest(loanId: loanId, amount: amount), idempotencyKey: UUID().uuidString)
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

    public func issueCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/issue", body: EmptyRequest())
    }

    public func getMyCard() async throws -> CardResponse { try await get("api/v1/card/my-card") }

    public func getCardTransactions() async throws -> CardTransactionsResponse { try await get("api/v1/card/transactions") }

    public func setCardLimits(dailyLimit: Double, monthlyLimit: Double) async throws -> CardResponse {
        try await authenticatedPut("api/v1/card/limits", body: SetCardLimitsRequest(dailyLimit: dailyLimit, monthlyLimit: monthlyLimit))
    }

    public func freezeCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/freeze", body: EmptyRequest())
    }

    public func unfreezeCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/unfreeze", body: EmptyRequest())
    }

    public func chargeCard(amount: Double, merchantName: String) async throws -> ChargeCardResponse {
        try await authenticatedPost("api/v1/card/charge", body: ChargeCardRequest(amount: amount, merchantName: merchantName), idempotencyKey: UUID().uuidString)
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
    public func agentCashIn(_ request: AgentCashInRequest) async throws -> AgentCashResultResponse {
        try await authenticatedPost("api/v1/agent/cash-ins", body: request, idempotencyKey: UUID().uuidString)
    }
    public func agentCashOut(_ request: AgentCashOutRequest) async throws -> AgentCashResultResponse {
        try await authenticatedPost("api/v1/agent/cash-outs", body: request, idempotencyKey: UUID().uuidString)
    }
    public func submitAgentTillCount(_ countedCash: Double) async throws -> AgentTillReconciliationResponse {
        try await authenticatedPost("api/v1/agent/till-reconciliations", body: SubmitAgentTillCountRequest(countedCash: countedCash))
    }

    // Real peer-to-peer agent float rebalancing marketplace -- see FloatMarketplaceController.kt.
    public func postFloatListing(amount: Double) async throws -> FloatListingResponse {
        try await authenticatedPost("api/v1/float-marketplace/listings", body: PostFloatListingRequest(amount: amount))
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
        try await authenticatedPost("api/v1/float-marketplace/listings/\(listingId)/cancel", body: EmptyBody())
    }

    public func requestFloat(listingId: String, amount: Double) async throws -> FloatTransferRequestResponse {
        try await authenticatedPost("api/v1/float-marketplace/listings/\(listingId)/requests", body: RequestFloatRequest(amount: amount))
    }

    public func getMyFloatRequests() async throws -> FloatTransferRequestsResponse { try await get("api/v1/float-marketplace/requests/mine") }

    public func getIncomingFloatRequests() async throws -> FloatTransferRequestsResponse { try await get("api/v1/float-marketplace/requests/incoming") }

    public func acceptFloatRequest(requestId: String) async throws -> FloatTransferRequestResponse {
        try await authenticatedPost("api/v1/float-marketplace/requests/\(requestId)/accept", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func declineFloatRequest(requestId: String) async throws -> FloatTransferRequestResponse {
        try await authenticatedPost("api/v1/float-marketplace/requests/\(requestId)/decline", body: EmptyBody())
    }

    public func issueCertificate() async throws -> IssueCertificateResponse {
        try await authenticatedPost("api/v1/certificate/issue", body: EmptyRequest())
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

    public func submitIdentity(documentType: String, documentNumber: String, documentReference: String) async throws -> SubmitIdentityResponse {
        try await authenticatedPost("api/v1/identity/submit", body: SubmitIdentityRequest(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference))
    }

    public func getIdentityStatus() async throws -> IdentityStatusResponse { try await get("api/v1/identity/status") }

    public func createSupportTicket(transactionId: String, category: String, description: String) async throws -> CreateSupportTicketResponse {
        try await authenticatedPost("api/v1/support/tickets", body: CreateSupportTicketRequest(transactionId: transactionId, category: category, description: description))
    }

    public func getSupportTickets() async throws -> SupportTicketsResponse { try await get("api/v1/support/tickets") }

    public func createSplitBill(groupConversationId: String, totalAmount: Double, description: String, participantUserIds: [String], mode: String = "EVEN", ladderVarianceLevel: Int? = nil) async throws -> CreateSplitBillResponse {
        try await authenticatedPost(
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
        try await authenticatedPost(
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
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/pay", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func attachSplitBillReceipt(splitBillId: String, imageUrl: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/receipt", body: AttachSplitBillReceiptRequest(imageUrl: imageUrl))
    }

    public func requestSplitBillNextRound(splitBillId: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/next-round", body: EmptyRequest())
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
