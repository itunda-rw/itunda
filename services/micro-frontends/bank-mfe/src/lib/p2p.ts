import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { Transaction } from './account';

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- see
// P2pService.sendDirect's own doc comment. A real recipient (by phone number or account
// number) in one step, no pre-existing payment request needed and no quote step (unlike
// AccountService.confirmTransfer, there's no external rail decision to quote -- the
// recipient is either a real itunda account or a real, honest 404).

// Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") -- see
// backend P2pController's own doc comment. Resolves a phone/account identifier to the
// real account holder's name before the amount screen renders it, matching the
// reference Toss screenshots' "To [name]" display -- this endpoint already existed on
// the backend (P2pService.resolveRecipient) but had no client anywhere until now.
export interface P2pRecipientPreview {
  recipientUserId: string;
  displayName: string;
}

export const resolveRecipient = (identifier: string) =>
  apiFetch<{ success: boolean; recipient: P2pRecipientPreview }>(`/api/v1/p2p/recipient?identifier=${encodeURIComponent(identifier)}`).then((r) => r.recipient);

// Real gap found live (2026-08-31, direct user reference of their own Toss app showing
// a "which account should the money come from" picker on every send): this always sent
// from the sender's MAIN account with no way to choose another one. `fromAccountId` is
// optional and omitted entirely (not sent as null) when absent, matching the backend's
// own default-to-MAIN behavior for every pre-existing caller.
// Real gap found live (2026-08-31, Toss security research thread): matches Toss's own
// real "Fraud Suspicion Siren" (사기의심 사이렌) -- a real, informational warning shown
// to the sender when a transfer trips a fraud heuristic (new recipient / high value /
// velocity), never a silent block. Backend FraudRuleEngine.evaluate's result existed
// since the engine shipped but was discarded at every call site -- see
// P2pService.sendDirect's own doc comment. fraudWarnings is always present (an empty
// array for the overwhelming common case of a clean transfer), never undefined, so
// callers can render it unconditionally.
export const sendDirect = (recipient: string, amount: number, description: string, fromAccountId?: string) =>
  apiFetch<{ success: boolean; message: string; transaction: Transaction; newBalance: number; fraudWarnings: string[] }>('/api/v1/p2p/send', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify(fromAccountId ? { recipient, amount, description, fromAccountId } : { recipient, amount, description }),
  });

// Real fixed-amount person-to-person payment request (item 167, found via the
// entity-cross-reference discovery sweep) -- the person-to-person counterpart to a
// merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's own doc comment).
// A requester generates a real, 15-minute-expiring request; anyone who has the code
// can pay it directly, real account-to-account, no fee. Real (rate-limited, tested,
// live-verified against a real backend in a past session) but had zero client
// anywhere until now.
export type P2pPaymentRequestStatus = 'PENDING' | 'COMPLETED' | 'EXPIRED';

export interface P2pPaymentRequestDto {
  id: string;
  requesterUserId: string;
  amount: number;
  description: string;
  status: P2pPaymentRequestStatus;
  expiresAt: string;
  completedTransactionId: string | null;
  paidByUserId: string | null;
  createdAt: string;
}

export const generateP2pRequest = (amount: number, description: string) =>
  apiFetch<{ success: boolean; request: P2pPaymentRequestDto }>('/api/v1/p2p/request', {
    method: 'POST',
    body: JSON.stringify({ amount, description }),
  }).then((r) => r.request);

export const fetchMyP2pRequests = () =>
  apiFetch<{ success: boolean; requests: P2pPaymentRequestDto[] }>('/api/v1/p2p/requests').then((r) => r.requests);

export const payP2pRequest = (requestId: string) =>
  apiFetch<{ success: boolean; message: string; transaction: Transaction; newBalance: number }>(`/api/v1/p2p/pay/${requestId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

// Real Naver Pay "가족 공유 자산 관리" (family shared asset management) -- instant
// transfer to a linked family member straight from the Family card, see
// P2pService.sendToFamilyMember's own doc comment on the backend.
export const sendToFamilyMember = (childUserId: string, amount: number, description: string) =>
  apiFetch<{ success: boolean; message: string; transaction: Transaction; newBalance: number }>('/api/v1/p2p/send-to-family', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ childUserId, amount, description }),
  });
