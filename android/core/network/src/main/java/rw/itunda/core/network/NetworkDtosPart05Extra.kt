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

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.

data class HarvestAdvanceResponse(val success: Boolean, val advance: HarvestAdvanceDto)
data class HarvestAdvancesResponse(val success: Boolean, val advances: List<HarvestAdvanceDto>)

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
// microloan -- sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference
// ecosystems. VUP, run by LODA since 2008, subsidizes microloans for income-generating
// activities (farming, livestock, small business) targeted at households in poorer
// Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
// 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
// rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
// limitation: declaredUbudeheCategory is self-declared by the user, not verified
// against Rwanda's real government Ubudehe household-classification registry. Mirrors
// bank-mfe's lib/vupLoan.ts exactly.
data class VupLoanDto(
    val id: String, val userId: String, val declaredUbudeheCategory: Int, val purpose: String,
    val principalAmount: java.math.BigDecimal, val outstandingPrincipal: java.math.BigDecimal,
    val interestRate: Double, val status: String, val appliedAt: String, val disbursedAt: String?, val dueDate: String?,
)
data class ApplyForVupLoanRequest(val declaredUbudeheCategory: Int, val purpose: String, val amount: java.math.BigDecimal)
data class RepayVupLoanRequest(val amount: java.math.BigDecimal)
data class VupLoanResponse(val success: Boolean, val loan: VupLoanDto)
data class VupLoansResponse(val success: Boolean, val loans: List<VupLoanDto>)
data class VupLoanEligibilityResponse(
    val success: Boolean, val hasActiveLoan: Boolean, val canApply: Boolean,
    val minUbudeheCategory: Int, val maxUbudeheCategory: Int, val interestRate: Double, val maxAmount: java.math.BigDecimal,
)

// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- sourced
// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Rwanda has
// run a national student-loan-and-bursary scheme since Law No. 44/2015, administered by
// BRD since an October 2016 MINEDUC agreement. Real scale: RWF 221.85 billion disbursed
// to 139,925 students (through mid-2023), fixed interest rates of 11% undergraduate /
// 12% postgraduate, repayment terms of 2-10 years (brd.rw). Honest v1 limitation:
// declaredAnnualHouseholdIncome is self-declared, not verified against BRD's real
// Financial Means Testing (FMT) process, and the real 8%-of-income payroll deduction is
// only ever a SUGGESTED amount here -- itunda has no payroll/RRA-integration path to
// enforce it. Mirrors bank-mfe's lib/studentLoan.ts exactly.
data class StudentLoanDto(
    val id: String, val userId: String, val level: String, val declaredAnnualHouseholdIncome: java.math.BigDecimal,
    val principalAmount: java.math.BigDecimal, val outstandingBalance: java.math.BigDecimal, val interestRate: Double,
    val status: String, val appliedAt: String, val disbursedAt: String?, val expectedGraduationDate: String, val graceEndsAt: String?,
)
data class ApplyForStudentLoanRequest(
    val level: String, val declaredAnnualHouseholdIncome: java.math.BigDecimal, val amount: java.math.BigDecimal, val expectedGraduationDate: String,
)
data class RepayStudentLoanRequest(val amount: java.math.BigDecimal)
data class StudentLoanResponse(val success: Boolean, val loan: StudentLoanDto)
data class StudentLoansResponse(val success: Boolean, val loans: List<StudentLoanDto>)
data class StudentLoanSuggestedPaymentResponse(
    val success: Boolean, val loanId: String, val outstandingBalance: java.math.BigDecimal, val suggestedMonthlyPayment: java.math.BigDecimal, val note: String,
)

// Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
// session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
// entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14 May
// 2021 -- profiles a rider who saved for years to buy her own bike after paying daily
// rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this exact
// sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own model
// increased driver revenue 78%/month; WeeTracker/WEF coverage of the same). This
// fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
// 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
// facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
// hold, so it cannot repossess the bike or verify it was actually purchased. Mirrors
// bank-mfe's lib/motoOwnership.ts exactly.
data class MotoOwnershipPlanDto(
    val id: String, val userId: String, val bikePrice: java.math.BigDecimal, val downPaymentTarget: java.math.BigDecimal,
    val savedAmount: java.math.BigDecimal, val dailyContribution: java.math.BigDecimal, val loanOutstanding: java.math.BigDecimal,
    val status: String, val lastAutoContributionAt: String?, val createdAt: String,
)
data class CreateMotoOwnershipPlanRequest(val bikePrice: java.math.BigDecimal, val dailyContribution: java.math.BigDecimal)
data class ContributeToMotoOwnershipPlanRequest(val amount: java.math.BigDecimal)
data class RepayMotoOwnershipPlanRequest(val amount: java.math.BigDecimal)
data class MotoOwnershipPlanResponse(val success: Boolean, val plan: MotoOwnershipPlanDto)
data class MotoOwnershipPlansResponse(val success: Boolean, val plans: List<MotoOwnershipPlanDto>)

// Real KakaoBank mini-style capped starter account -- see YouthAccountService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
data class OpenYouthAccountResponse(val success: Boolean, val account: Account)
data class DepositYouthAccountRequest(val amount: java.math.BigDecimal)
data class DepositYouthAccountResponse(val success: Boolean, val id: String, val amount: java.math.BigDecimal, val completedAt: String)

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See network/OfflineActionQueue.kt for the local persisted queue this replays.
data class BatchActionRequest(
    val clientActionId: String,
    val type: String,
    val idempotencyKey: String,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchRequest(val actions: List<BatchActionRequest>)

data class BatchActionResultDto(
    val clientActionId: String,
    val type: String,
    val status: Int,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchResponse(val success: Boolean, val results: List<BatchActionResultDto>)
