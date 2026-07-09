package com.itunda.app.data.api

import com.itunda.app.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("auth/profile")
    suspend fun getProfile(): Response<AuthResponse>

    @GET("auth/credit-score")
    suspend fun getCreditScore(): Response<ApiMessage>

    @GET("wallet/balance")
    suspend fun getBalance(): Response<BalanceResponse>

    @GET("wallet/transactions")
    suspend fun getTransactions(): Response<TransactionsResponse>

    @POST("wallet/transfer")
    suspend fun transfer(@Body request: TransferRequest): Response<TransferResponse>

    @GET("bills/providers")
    suspend fun getBillProviders(): Response<BillProvidersResponse>

    @GET("bills/pending")
    suspend fun getPendingBills(): Response<BillsResponse>

    @POST("bills/pay")
    suspend fun payBill(@Body request: PayBillRequest): Response<PayBillResponse>

    @POST("bills/airtime")
    suspend fun buyAirtime(@Body request: AirtimeRequest): Response<PayBillResponse>

    @GET("stocks")
    suspend fun getStocks(): Response<StockListResponse>

    @GET("stocks/portfolio")
    suspend fun getPortfolio(): Response<PortfolioResponse>

    @POST("stocks/buy")
    suspend fun buyStock(@Body request: StockOrderRequest): Response<StockOrderResponse>

    @POST("stocks/sell")
    suspend fun sellStock(@Body request: StockOrderRequest): Response<StockOrderResponse>

    @GET("loans/offers")
    suspend fun getLoanOffers(): Response<LoanOffersResponse>

    @GET("loans/my-loans")
    suspend fun getMyLoans(): Response<MyLoansResponse>

    @POST("loans/apply")
    suspend fun applyLoan(@Body request: LoanApplyRequest): Response<LoanApplyResponse>

    @POST("loans/repay")
    suspend fun repayLoan(@Body request: RepayRequest): Response<RepayResponse>

    @GET("contacts")
    suspend fun getContacts(): Response<ContactsResponse>

    @POST("contacts/add")
    suspend fun addContact(@Body contact: Contact): Response<ApiMessage>
}
