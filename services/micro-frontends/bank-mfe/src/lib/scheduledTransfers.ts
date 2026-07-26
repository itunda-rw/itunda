import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see backend
// ScheduledTransfer.kt's own doc comment for the full sourced account. Genuinely
// distinct from a recurring auto-transfer (which has no bank-mfe client yet either):
// this always runs exactly once, on a single future calendar date.

export type ScheduledTransferStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED' | 'FAILED';

export interface ScheduledTransfer {
  id: string;
  recipientIdentifier: string;
  recipientName: string;
  amount: number;
  description: string;
  scheduledDate: string;
  status: ScheduledTransferStatus;
  createdAt: string;
  executedAt: string | null;
  transactionId: string | null;
  failureReason: string | null;
  cancelledAt: string | null;
}

export const fetchMyScheduledTransfers = () =>
  apiFetch<{ success: boolean; scheduledTransfers: ScheduledTransfer[] }>('/api/v1/p2p/scheduled-transfers').then((r) => r.scheduledTransfers);

export const createScheduledTransfer = (recipient: string, amount: number, scheduledDate: string, description: string) =>
  apiFetch<{ success: boolean; scheduledTransfer: ScheduledTransfer }>('/api/v1/p2p/scheduled-transfers', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipient, amount, scheduledDate, description }),
  }).then((r) => r.scheduledTransfer);

export const cancelScheduledTransfer = (id: string) =>
  apiFetch<{ success: boolean; scheduledTransfer: ScheduledTransfer }>(`/api/v1/p2p/scheduled-transfers/${id}/cancel`, { method: 'POST' }).then(
    (r) => r.scheduledTransfer,
  );
