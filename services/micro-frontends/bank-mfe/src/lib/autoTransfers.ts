import { apiFetch } from './api';
import { randomUUID } from './uuid';

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

// Real idempotency fix (item 235, found via a periodic Idempotency-Key coverage
// audit) -- corrects this file's own earlier reasoning that no key was needed here
// because create "doesn't move money immediately." That's true but not the actual
// risk: a retry created a second active recurring-transfer row to the same
// recipient, which the scheduler would then execute independently -- a real
// duplicate charge every period going forward, not just once. See
// AutoTransferController.create's own doc comment for the full account.
export const createAutoTransfer = (
  recipient: string, amount: number, frequency: AutoTransferFrequency, dayOfWeek: number | null, dayOfMonth: number | null, description: string,
) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>('/api/v1/p2p/auto-transfers', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipient, amount, frequency, dayOfWeek, dayOfMonth, description }),
  }).then((r) => r.autoTransfer);

export const pauseAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}/pause`, { method: 'POST' }).then((r) => r.autoTransfer);

export const resumeAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}/resume`, { method: 'POST' }).then((r) => r.autoTransfer);

export const cancelAutoTransfer = (id: string) =>
  apiFetch<{ success: boolean; autoTransfer: AutoTransfer }>(`/api/v1/p2p/auto-transfers/${id}`, { method: 'DELETE' }).then((r) => r.autoTransfer);
