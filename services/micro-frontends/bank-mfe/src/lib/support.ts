import { apiFetch } from './api';

// Real customer support tickets, tied to a specific transaction (rw.itunda.support) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere. category
// must be one of GENERAL/PAYMENT_DISPUTE/ACCOUNT_TAKEOVER -- see SupportTicket.kt's
// own doc comment on why a ticket is always tied to a specific transaction, not a
// free-floating complaint.

export type SupportTicketCategory = 'GENERAL' | 'PAYMENT_DISPUTE' | 'ACCOUNT_TAKEOVER';

export interface SupportTicket {
  id: string;
  userId: string;
  transactionId: string;
  category: string;
  description: string;
  status: string;
  resolution: string | null;
  resolutionNotes: string | null;
  refundTransactionId: string | null;
  frozeWalletId: string | null;
  dueBy: string;
  reviewedBy: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export const createSupportTicket = (transactionId: string, category: SupportTicketCategory, description: string) =>
  apiFetch<{ success: boolean; ticket: SupportTicket }>('/api/v1/support/tickets', {
    method: 'POST',
    body: JSON.stringify({ transactionId, category, description }),
  }).then((r) => r.ticket);

export const fetchSupportTickets = () =>
  apiFetch<{ success: boolean; tickets: SupportTicket[] }>('/api/v1/support/tickets').then((r) => r.tickets);
