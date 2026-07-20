import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { Transaction } from './wallet';

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- see
// P2pService.sendDirect's own doc comment. A real recipient (by phone number or account
// number) in one step, no pre-existing payment request needed and no quote step (unlike
// WalletService.confirmTransfer, there's no external rail decision to quote -- the
// recipient is either a real itunda account or a real, honest 404).

export const sendDirect = (recipient: string, amount: number, description: string) =>
  apiFetch<{ success: boolean; message: string; transaction: Transaction; newBalance: number }>('/api/v1/p2p/send', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipient, amount, description }),
  });
