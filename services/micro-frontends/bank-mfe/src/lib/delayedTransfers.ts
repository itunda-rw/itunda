import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Korean 지연이체서비스 (Delayed Transfer Service) -- see backend
// P2pDelayedTransfer.kt's own doc comment for the full sourced account (a real,
// government-documented anti-voice-phishing safeguard every major Korean bank offers:
// KakaoBank/Toss/IBK/KB all let a sender opt to hold an outgoing transfer for a real
// minimum window instead of it landing instantly, so a transfer made under active
// phishing pressure -- or just a fat-fingered recipient -- can still be cancelled
// before it's irreversible). Genuinely distinct from ScheduledTransfers' own 예약송금
// (a user-chosen FUTURE send date): this is a SAFETY delay on a transfer the sender
// wants to send right now. Fully built on the backend (real hold/release/cancel
// lifecycle, real transfer-limit enforcement shared with the instant sendDirect path)
// but had zero client anywhere -- found via scripts/uncalled-endpoint-sweep.py.

export type DelayedTransferStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export interface DelayedTransfer {
  id: string;
  senderUserId: string;
  recipientUserId: string;
  amount: number;
  description: string;
  status: DelayedTransferStatus;
  releaseAt: string;
  createdAt: string;
}

export const fetchMyDelayedTransfers = () =>
  apiFetch<{ success: boolean; transfers: DelayedTransfer[] }>('/api/v1/p2p/delayed-transfers').then((r) => r.transfers);

export const sendDelayed = (recipient: string, amount: number, description: string) =>
  apiFetch<{ success: boolean; message: string; transfer: DelayedTransfer }>('/api/v1/p2p/send-delayed', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipient, amount, description }),
  }).then((r) => r.transfer);

export const cancelDelayedTransfer = (id: string) =>
  apiFetch<{ success: boolean; transfer: DelayedTransfer }>(`/api/v1/p2p/delayed-transfers/${id}/cancel`, { method: 'POST' }).then(
    (r) => r.transfer,
  );
