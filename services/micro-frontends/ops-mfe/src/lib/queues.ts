import { apiFetch } from './api';

// Field shapes match services/backend's real domain entities exactly (FraudFlag,
// KycSubmission, Incident, SupportTicket) -- see docs/TOSS_PARITY_MATRIX.md's Ops
// Queues row for the source-of-truth controllers this talks to.

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

export interface ReconciliationRow {
  railId: string;
  displayName: string;
  totalAttempts: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  avgLatencyMs: number;
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

export const fetchFraudQueue = () =>
  apiFetch<{ success: boolean; queue: FraudFlag[] }>('/api/v1/system/fraud/queue').then((r) => r.queue);

export const decideFraud = (flagId: string, decision: 'CLEARED' | 'CONFIRMED') =>
  apiFetch(`/api/v1/system/fraud/${flagId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  });

export const fetchComplianceQueue = () =>
  apiFetch<{ success: boolean; queue: KycSubmission[] }>('/api/v1/system/compliance/queue').then((r) => r.queue);

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

export const fetchSupportQueue = () =>
  apiFetch<{ success: boolean; queue: SupportTicket[] }>('/api/v1/system/support/queue').then((r) => r.queue);

export const resolveSupportTicket = (ticketId: string, resolution: 'REFUNDED' | 'REJECTED', notes?: string) =>
  apiFetch(`/api/v1/system/support/${ticketId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, notes }),
  });
