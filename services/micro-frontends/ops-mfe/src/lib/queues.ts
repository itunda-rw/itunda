import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Field shapes match services/backend's real domain entities exactly (FraudFlag,
// KycSubmission, Incident, SupportTicket) -- see docs/TOSS_PARITY_MATRIX.md's Ops
// Queues row for the source-of-truth controllers this talks to.

// Real pagination (2026-07-17, see rw.itunda.core.web.pageMeta): the fraud, compliance,
// partners, and support queue endpoints now return a bounded page (20 by default), not
// an unbounded dump -- a queue past 20 pending items would otherwise be silently
// truncated with no way for a reviewer to reach the rest. `usePagedQueue` (hooks/
// useQueue.ts) is what actually surfaces `totalElements`/`hasMore` to each queue view.
export interface PagedQueue<T> {
  items: T[];
  totalElements: number;
  hasMore: boolean;
}

// Exported so lib/loansQueues.ts (extracted 2026-09-08 once this file crossed the
// 500-line guideline) can reuse the exact same pagination shape.
export function toPagedQueue<T>(response: { queue: T[]; totalElements: number; totalPages: number; page: number }): PagedQueue<T> {
  return { items: response.queue, totalElements: response.totalElements, hasMore: response.page + 1 < response.totalPages };
}

export interface FraudFlag {
  id: string;
  userId: string;
  transactionId: string;
  rule: 'HIGH_VALUE' | 'VELOCITY' | 'NEW_RECIPIENT';
  description: string;
  amount: number;
  reviewed: boolean;
  decision: 'CLEARED' | 'CONFIRMED' | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export interface KycSubmission {
  id: string;
  userId: string;
  documentType: string;
  documentNumber: string;
  documentReference: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
  // Real demo NIDA structural pre-check (2026-07-17) -- see
  // DemoNidaVerificationService's own doc comment. Never auto-decides; shown here so the
  // human reviewer sees it before deciding, same as a real KYC dashboard would surface an
  // automated pre-check result.
  autoVerificationStatus: 'MATCHED' | 'NOT_FOUND' | 'INVALID_FORMAT' | 'UNSUPPORTED_DOCUMENT_TYPE' | null;
  autoVerificationDetail: string | null;
}

export interface Incident {
  id: string;
  railId: string;
  railDisplayName: string;
  description: string;
  failureCount: number;
  status: 'OPEN' | 'RESOLVED';
  openedAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

// Real 당근페이 안심결제-style optional Marketplace escrow (item 125) -- a buyer who
// flags a real problem disputes it for real human admin review (see
// MarketplaceEscrow.kt's own doc comment); this queue had zero client anywhere until
// now, found via a fresh endpoint-coverage sweep across ops-mfe's queues vs the real
// backend @RequestMapping paths under /api/v1/system.
export interface MarketplaceEscrowDispute {
  id: string;
  listingId: string;
  buyerId: string;
  sellerId: string;
  amount: number;
  fee: number;
  status: 'HELD' | 'RELEASED' | 'REFUNDED' | 'DISPUTED';
  disputeReason: string | null;
  createdAt: string;
  updatedAt: string;
}

// Real property-ownership document verification for RealEstate listings (item 128) --
// `PropertyOwnershipAdminController` had zero client anywhere, the last of the 4 gaps
// found by the item-125 endpoint-coverage sweep. Same real human-review-queue shape as
// KycSubmission above (a submitted document, MATCHED/pending, approve or reject with
// an optional reason).
export interface PropertyOwnershipSubmission {
  id: string;
  listingId: string;
  userId: string;
  documentUrl: string;
  status: string;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
}

export const fetchPropertyOwnershipQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: PropertyOwnershipSubmission[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/property-verification/queue?page=${page}`,
  ).then(toPagedQueue);

export const decidePropertyOwnership = (submissionId: string, approve: boolean, reason?: string) =>
  apiFetch(`/api/v1/system/property-verification/${submissionId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ approve, reason }),
  });

// Real Bank product-completeness pass (2026-09-06/2026-09-08) -- the VUP loan and
// Harvest Advance admin review queues moved to lib/loansQueues.ts once this file
// crossed the 500-line file-size-lint guideline (see that file's own header).

// Real Hood (Marketplace/Community/Jobs/PropertyListing/messaging) content-moderation
// report queue (item 127) -- `HoodReportAdminController` had zero client anywhere
// despite the customer-facing "report" creation endpoint being real and in use; found
// via the same endpoint-coverage sweep that found items 125/126.
export interface HoodReport {
  id: string;
  reporterUserId: string;
  targetType: 'MARKETPLACE_LISTING' | 'COMMUNITY_POST' | 'JOB_POST' | 'PROPERTY_LISTING' | 'DIRECT_MESSAGE' | 'GROUP_MESSAGE';
  targetId: string;
  reason: string;
  status: 'OPEN' | 'RESOLVED';
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export const fetchHoodReportsQueue = (page = 0) =>
  apiFetch<{ success: boolean; reports: HoodReport[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/hood-reports?page=${page}`,
  ).then((r) => ({ items: r.reports, totalElements: r.totalElements, hasMore: r.page + 1 < r.totalPages }));

export const resolveHoodReport = (reportId: string) =>
  apiFetch(`/api/v1/system/hood-reports/${reportId}/resolve`, { method: 'POST' });

export const removeHoodReportTarget = (reportId: string) =>
  apiFetch(`/api/v1/system/hood-reports/${reportId}/remove-target`, { method: 'POST' });

// Real Talk (1:1 chat) message-report queue -- `ChatReportController` had a real,
// live create endpoint (`POST /api/v1/chat/reports`) but no admin surface ever
// consumed the resulting rows; found during the Talk product-completeness pass.
export interface ChatReport {
  id: string;
  reporterUserId: string;
  messageId: string;
  reason: string;
  status: 'OPEN' | 'RESOLVED';
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export const fetchChatReportsQueue = (page = 0) =>
  apiFetch<{ success: boolean; reports: ChatReport[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/chat-reports?page=${page}`,
  ).then((r) => ({ items: r.reports, totalElements: r.totalElements, hasMore: r.page + 1 < r.totalPages }));

export const resolveChatReport = (reportId: string) =>
  apiFetch(`/api/v1/system/chat-reports/${reportId}/resolve`, { method: 'POST' });

export const removeChatReportMessage = (reportId: string) =>
  apiFetch(`/api/v1/system/chat-reports/${reportId}/remove-message`, { method: 'POST' });

// Real MTN MoMo/Airtel Money-style physical cash-in/cash-out agent network -- Agent
// management (item 129: register/list/suspend an agent, fund a real till float), the
// rest of this module's admin surface item 126 deliberately left open. Cash-in/
// cash-out/operator-assignment stay a separate, not-yet-started follow-up -- those are
// real teller actions best suited to a dedicated agent-operator client, not this
// admin-facing register/status/float view.
export interface Agent {
  id: string;
  displayName: string;
  cashAccountId: string;
  status: 'ACTIVE' | 'SUSPENDED';
  dailyCashInLimit: number;
  dailyCashOutLimit: number;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
}

export const fetchAgents = () =>
  apiFetch<{ success: boolean; agents: Agent[] }>('/api/v1/system/agents').then((r) => r.agents);

export const registerAgent = (displayName: string, dailyCashInLimit: number, dailyCashOutLimit: number) =>
  apiFetch<{ success: boolean; agent: Agent }>('/api/v1/system/agents', {
    method: 'POST',
    body: JSON.stringify({ displayName, dailyCashInLimit, dailyCashOutLimit }),
  }).then((r) => r.agent);

export const setAgentStatus = (agentId: string, status: 'ACTIVE' | 'SUSPENDED') =>
  apiFetch<{ success: boolean; agent: Agent }>(`/api/v1/system/agents/${agentId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.agent);

export const fundAgentTill = (agentId: string, amount: number, reference: string) =>
  apiFetch(`/api/v1/system/agents/${agentId}/float`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount, reference }),
  });

// Real operator management (item 132) -- closes item 129's own honestly-named gap:
// assign/activate an operator by userId was real, but there was never a way to list
// who's currently assigned to a given agent till first. New GET .../operators backend
// endpoint added the same pass.
export interface AgentOperator {
  id: string;
  agentId: string;
  userId: string;
  isActive: boolean;
  createdAt: string;
}

export const fetchAgentOperators = (agentId: string) =>
  apiFetch<{ success: boolean; operators: AgentOperator[] }>(`/api/v1/system/agents/${agentId}/operators`).then((r) => r.operators);

export const assignAgentOperator = (agentId: string, userId: string) =>
  apiFetch<{ success: boolean; operator: AgentOperator }>(`/api/v1/system/agents/${agentId}/operators`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  }).then((r) => r.operator);

export const setAgentOperatorStatus = (agentId: string, userId: string, isActive: boolean) =>
  apiFetch<{ success: boolean; operator: AgentOperator }>(`/api/v1/system/agents/${agentId}/operators/${userId}/status`, {
    method: 'POST',
    body: JSON.stringify({ isActive }),
  }).then((r) => r.operator);

// Real MTN MoMo/Airtel Money-style physical cash-in/cash-out agent network (item 126)
// -- a till reconciliation this out of balance needs real human admin review before
// the variance is written off, same "real human review, not auto-resolved" discipline
// this file's own MarketplaceEscrowDispute queue already established. Found zero
// client anywhere via the same endpoint-coverage sweep that found item 125.
export interface AgentTillReconciliation {
  id: string;
  agentId: string;
  businessDate: string;
  expectedCash: number;
  countedCash: number;
  variance: number;
  submittedByUserId: string;
  status: 'MATCHED' | 'PENDING_REVIEW' | 'RESOLVED';
  reviewedByUserId: string | null;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

// Real agent till reconciliation report-by-date-range (item 133) -- the one real,
// still-open follow-up this file's own TOSS_PARITY_MATRIX.md Agent Network row named:
// AgentService.reconciliationReport/the matching GET endpoint were already real, tested
// backend code with zero ops-mfe client anywhere.
export interface AgentReconciliationReport {
  from: string;
  to: string;
  reconciliations: AgentTillReconciliation[];
  pendingReviewCount: number;
  totalVariance: number;
}

export interface ReconciliationRow {
  railId: string;
  displayName: string;
  totalAttempts: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  avgLatencyMs: number;
}

export interface TwoSidedReconciliationRow {
  railId: string;
  displayName: string;
  itundaSuccessCount: number;
  externalSettledCount: number;
  matched: boolean;
  discrepancy: number;
  isExternalCountDemo: boolean;
}

// Real third-party developer platform (see PartnerService.kt's own doc comment) --
// closes the "allow partners to build apps in itunda like apps in Toss" gap.
export interface PartnerMiniAppSubmission {
  id: string;
  partnerId: string;
  name: string;
  description: string;
  iconUrl: string | null;
  bundleUrl: string;
  permissions: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  createdAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
}

export interface InsuranceClaim {
  id: string;
  policyId: string;
  userId: string;
  description: string;
  amount: number;
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  decisionReason: string | null;
}

export interface SupportTicket {
  id: string;
  userId: string;
  transactionId: string | null;
  category: 'GENERAL' | 'PAYMENT_DISPUTE' | 'ACCOUNT_TAKEOVER' | 'RIDE_ISSUE' | 'EATS_ORDER_ISSUE';
  description: string;
  status: 'OPEN' | 'RESOLVED';
  resolution: 'REFUNDED' | 'REJECTED' | null;
  resolutionNotes: string | null;
  refundTransactionId: string | null;
  frozeWalletId: string | null;
  dueBy: string;
  reviewedBy: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export const fetchFraudQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: FraudFlag[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/fraud/queue?page=${page}`,
  ).then(toPagedQueue);

export const decideFraud = (flagId: string, decision: 'CLEARED' | 'CONFIRMED') =>
  apiFetch(`/api/v1/system/fraud/${flagId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  });

export const fetchComplianceQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: KycSubmission[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/compliance/queue?page=${page}`,
  ).then(toPagedQueue);

export const decideCompliance = (submissionId: string, approve: boolean, reason?: string) =>
  apiFetch(`/api/v1/system/compliance/${submissionId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ approve, reason }),
  });

export const fetchIncidents = () =>
  apiFetch<{ success: boolean; incidents: Incident[] }>('/api/v1/system/incidents').then((r) => r.incidents);

export const resolveIncident = (incidentId: string) =>
  apiFetch(`/api/v1/system/incidents/${incidentId}/resolve`, { method: 'POST' });

export const fetchReconciliation = (date?: string) =>
  apiFetch<{ success: boolean; rails: ReconciliationRow[] }>(
    `/api/v1/system/reconciliation${date ? `?date=${date}` : ''}`,
  ).then((r) => r.rails);

export const fetchTwoSidedReconciliation = (date?: string) =>
  apiFetch<{ success: boolean; rails: TwoSidedReconciliationRow[] }>(
    `/api/v1/system/reconciliation/two-sided${date ? `?date=${date}` : ''}`,
  ).then((r) => r.rails);

export const fetchPartnersQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: PartnerMiniAppSubmission[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/partners/queue?page=${page}`,
  ).then(toPagedQueue);

export const decidePartnerMiniApp = (miniAppId: string, approve: boolean, reason?: string) =>
  apiFetch(`/api/v1/system/partners/${miniAppId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ approve, reason }),
  });

// Real admin moderation lever (Partners product-completeness pass, 2026-09-07) --
// PartnerService.resolvePartner already real-enforces PartnerStatus.SUSPENDED, but
// there was no admin endpoint anywhere to actually set a Partner to SUSPENDED before
// this. Naturally small-cardinality (every registered partner, not a paged
// sub-selection) -- a plain list, not paginated, same shape
// lib/merchantAdminQueues.ts's own fetchFeeWaiverCandidates already establishes.
export interface PartnerAccount {
  partnerId: string;
  companyName: string;
  contactEmail: string;
  status: 'ACTIVE' | 'SUSPENDED';
  createdAt: string;
}

export const fetchPartnerAccounts = () =>
  apiFetch<{ success: boolean; partners: PartnerAccount[] }>('/api/v1/system/partners').then((r) => r.partners);

export const suspendPartnerAccount = (partnerId: string) =>
  apiFetch<{ success: boolean; partner: PartnerAccount }>(`/api/v1/system/partners/${partnerId}/suspend`, { method: 'POST' });

export const reactivatePartnerAccount = (partnerId: string) =>
  apiFetch<{ success: boolean; partner: PartnerAccount }>(`/api/v1/system/partners/${partnerId}/reactivate`, { method: 'POST' });

export const fetchInsuranceClaimsQueue = () =>
  apiFetch<{ success: boolean; queue: InsuranceClaim[] }>('/api/v1/system/insurance-claims/queue').then((r) => r.queue);

export const decideInsuranceClaim = (claimId: string, approve: boolean, reason?: string) =>
  apiFetch(`/api/v1/system/insurance-claims/${claimId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ approve, reason }),
  });

export const fetchSupportQueue = (page = 0) =>
  apiFetch<{ success: boolean; queue: SupportTicket[]; page: number; totalElements: number; totalPages: number }>(
    `/api/v1/system/support/queue?page=${page}`,
  ).then(toPagedQueue);

export const fetchPendingTillReconciliations = () =>
  apiFetch<{ success: boolean; reconciliations: AgentTillReconciliation[] }>('/api/v1/system/agents/till-reconciliations/pending').then((r) => r.reconciliations);

export const resolveTillReconciliation = (reconciliationId: string, note: string) =>
  apiFetch(`/api/v1/system/agents/till-reconciliations/${reconciliationId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  });

export const fetchAgentReconciliationReport = (from: string, to: string) =>
  apiFetch<{ success: boolean; report: AgentReconciliationReport }>(
    `/api/v1/system/agents/till-reconciliations?from=${from}&to=${to}`,
  ).then((r) => r.report);

export const fetchMarketplaceEscrowDisputes = () =>
  apiFetch<{ success: boolean; disputes: MarketplaceEscrowDispute[] }>('/api/v1/system/marketplace-escrow/disputes').then((r) => r.disputes);

export const resolveMarketplaceEscrowDispute = (escrowId: string, release: boolean) =>
  apiFetch(`/api/v1/system/marketplace-escrow/${escrowId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ release }),
  });

export const resolveSupportTicket = (ticketId: string, resolution: 'REFUNDED' | 'REJECTED', notes?: string) =>
  apiFetch(`/api/v1/system/support/${ticketId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, notes }),
  });

export const broadcastNotification = (title: string, body: string) =>
  apiFetch<{ success: boolean; sentCount: number }>('/api/v1/system/notifications/broadcast', {
    method: 'POST',
    body: JSON.stringify({ title, body }),
  });

// SystemDashboard/PaymentRail/MtnMomoConnectivity/AutoPaySweep (OverviewView.tsx's
// own concern) moved to lib/system.ts (2026-09-07, Bills product-completeness pass)
// once this file crossed the file-size-lint 500-line guideline -- see that file's
// own doc comment.
