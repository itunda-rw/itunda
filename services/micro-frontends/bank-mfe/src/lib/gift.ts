import { apiFetch } from './api';

// Real KakaoTalk-style "선물하기" money gift (rw.itunda.gift, 2026-07-20) -- see
// GiftService's own doc comment. Money leaves the sender's wallet into a real escrow
// account the moment a gift is sent, and only reaches the recipient's wallet once they
// explicitly claim it (or is auto-refunded after 7 days). Rendered inline as a gift
// bubble in the Talk thread, same "special message body" convention PriceOfferService
// established for price offers.

export type GiftStatus = 'PENDING' | 'CLAIMED' | 'EXPIRED';

export interface Gift {
  id: string;
  senderId: string;
  recipientId: string;
  conversationId: string;
  messageId: string;
  amount: number;
  note: string | null;
  status: GiftStatus;
  holdTransactionId: string;
  claimTransactionId: string | null;
  expiresAt: string;
  claimedAt: string | null;
  createdAt: string;
}

// Real chat-embedded send -- the recipient is resolved automatically as whichever
// participant in the conversation isn't the caller, no phone number re-typed.
export const sendGiftInConversation = (conversationId: string, amount: number, note?: string) =>
  apiFetch<{ success: boolean; gift: Gift }>(`/api/v1/gifts/conversations/${conversationId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ amount, note: note?.trim() || null }),
  }).then((r) => r.gift);

export const claimGift = (giftId: string) =>
  apiFetch<{ success: boolean; gift: Gift }>(`/api/v1/gifts/${giftId}/claim`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }).then((r) => r.gift);

// Real per-thread gift history -- fetched alongside a conversation's messages so the
// Talk thread can render gift bubbles for whichever messages carry one.
export const fetchGiftsForConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; gifts: Gift[] }>(`/api/v1/gifts/conversations/${conversationId}`).then((r) => r.gifts);
