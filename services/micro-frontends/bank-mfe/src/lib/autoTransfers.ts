import { apiFetch } from './api';

// Real Toss Bank 자동이체 (auto-transfer) -- see backend AutoTransfer.kt's own doc
// comment. Recurring (WEEKLY/MONTHLY), genuinely distinct from ScheduledTransfer's own
// one-time 예약송금. First bank-mfe client for a backend that previously had none.

export type AutoTransferFrequency = 'WEEKLY' | 'MONTHLY';
export type AutoTransferStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';

export interface AutoTransfer {
  id: string;
  recipientIdentifier: string;
  recipientName: string;
  amount: number;
  frequency: AutoTransferFrequency;
  dayOfWeek: number | null;
  dayOfMonth: number | null;
  description: string;
  status: AutoTransferStatus;
  nextExecutionAt: string;
  createdAt: string;
  lastExecutedAt: string | null;
  executionCount: number;
  lastFailureReason: string | null;
  cancelledAt: string | null;
}

export const fetchMyAutoTransfers = () =>
  apiFetch<{ success: boolean; autoTransfers: AutoTransfer[] }>('/api/v1/p2p/auto-transfers').then((r) => r.autoTransfers);

// No Idempotency-Key here -- AutoTransferController.create doesn't declare one (it
// only schedules future recurring executions, doesn't move money immediately, unlike
// ScheduledTransfer/MerchantBilling's own create endpoints); matching the real backend
// exactly rather than sending a header that implies protection that doesn't exist.
export const createAutoTransfer = (
  recipient: string, amount: number, frequency: AutoTransferFrequency, dayOfWeek: number | null, dayOfMonth: number | null, description: string,
) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>('/api/v1/p2p/auto-transfers', {
    method: 'POST',
    body: JSON.stringify({ recipient, amount, frequency, dayOfWeek, dayOfMonth, description }),
  }).then((r) => r.autoTransfer);

export const pauseAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}/pause`, { method: 'POST' }).then((r) => r.autoTransfer);

export const resumeAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}/resume`, { method: 'POST' }).then((r) => r.autoTransfer);

export const cancelAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}`, { method: 'DELETE' }).then((r) => r.autoTransfer);
