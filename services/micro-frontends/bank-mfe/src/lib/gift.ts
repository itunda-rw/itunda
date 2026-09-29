import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real KakaoTalk-style "선물하기" money gift (rw.itunda.gift, 2026-07-20) -- see
// GiftService's own doc comment. Money leaves the sender's account into a real escrow
// account the moment a gift is sent, and only reaches the recipient's account once they
// explicitly claim it (or is auto-refunded after 7 days). Rendered inline as a gift
// bubble in the Talk thread, same "special message body" convention PriceOfferService
// established for price offers.

export type GiftStatus = 'PENDING' | 'CLAIMED' | 'EXPIRED';

// Real KakaoPay 송금봉투 (money envelope) themed presets (backend since 2026-07-26,
// GiftTheme's own doc comment) -- exactly these 4 real, sourced presets, optional and
// additive alongside the free-text note. Had zero client anywhere until now.
export type GiftTheme = 'CONGRATULATIONS' | 'HEARTFELT' | 'GOOD_LUCK' | 'SETTLE_UP';

export const GIFT_THEME_LABELS: Record<GiftTheme, string> = {
  CONGRATULATIONS: '🎉 Congratulations',
  HEARTFELT: '💌 From the heart',
  GOOD_LUCK: '🍀 Good luck',
  SETTLE_UP: '🧾 Settling up',
};

export interface Gift {
  id: string;
  senderId: string;
  recipientId: string;
  conversationId: string;
  messageId: string;
  amount: number;
  note: string | null;
  theme: GiftTheme | null;
  status: GiftStatus;
  holdTransactionId: string;
  claimTransactionId: string | null;
  expiresAt: string;
  claimedAt: string | null;
  createdAt: string;
}

// Real chat-embedded send -- the recipient is resolved automatically as whichever
// participant in the conversation isn't the caller, no phone number re-typed.
export const sendGiftInConversation = (conversationId: string, amount: number, note?: string, theme?: GiftTheme | null) =>
  apiFetch<{ success: boolean; gift: Gift }>(`/api/v1/gifts/conversations/${conversationId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount, note: note?.trim() || null, theme: theme || null }),
  }).then((r) => r.gift);

// Real standalone send-by-phone-number -- the general "gift anyone with an itunda
// account" entry point (GiftController's own POST /api/v1/gifts), distinct from the
// chat-embedded flow above. Was fully built server-side (idempotent, rate-limited)
// with zero client caller anywhere until now -- a user could only gift someone they
// were already chatting with.
export const sendGift = (recipientPhoneNumber: string, amount: number, note?: string, theme?: GiftTheme | null) =>
  apiFetch<{ success: boolean; gift: Gift }>('/api/v1/gifts', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipientPhoneNumber: recipientPhoneNumber.trim(), amount, note: note?.trim() || null, theme: theme || null }),
  }).then((r) => r.gift);

export const fetchGift = (giftId: string) =>
  apiFetch<{ success: boolean; gift: Gift }>(`/api/v1/gifts/${giftId}`).then((r) => r.gift);

export const claimGift = (giftId: string) =>
  apiFetch<{ success: boolean; gift: Gift }>(`/api/v1/gifts/${giftId}/claim`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.gift);

// Real per-thread gift history -- fetched alongside a conversation's messages so the
// Talk thread can render gift bubbles for whichever messages carry one.
export const fetchGiftsForConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; gifts: Gift[] }>(`/api/v1/gifts/conversations/${conversationId}`).then((r) => r.gifts);
