import { apiFetch } from './api';

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

function toPagedQueue<T>(response: { queue: T[]; totalElements: number; totalPages: number; page: number }): PagedQueue<T> {
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
  category: 'GENERAL' | 'PAYMENT_DISPUTE' | 'ACCOUNT_TAKEOVER';
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
