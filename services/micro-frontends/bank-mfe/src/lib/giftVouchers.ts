import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real KakaoTalk-style "선물하기" 기프티콘 (mobile gift voucher, item 134) -- see backend
// GiftVoucher.kt's own doc comment: distinct from the money-envelope Gift (gift.ts) --
// redeemable at ONE specific real merchant, either for a specific product snapshot or
// a flat amount, not money that lands in the recipient's own wallet. Rendered inline
// in the Talk thread, same "special message body" convention Gift/PriceOffer use.

export type GiftVoucherStatus = 'ACTIVE' | 'REDEEMED' | 'EXPIRED';

export interface GiftVoucher {
  id: string;
  purchaserId: string;
  recipientId: string;
  conversationId: string;
  messageId: string;
  merchantId: string;
  merchantProductId: string | null;
  productNameSnapshot: string | null;
  amount: number;
  status: GiftVoucherStatus;
  holdTransactionId: string;
  redeemTransactionId: string | null;
  refundTransactionId: string | null;
  expiresAt: string;
  redeemedAt: string | null;
  extended: boolean;
  createdAt: string;
}

// v1 scope: product-based vouchers only (search-and-pick a real product, same real
// Kakao gifticon UX of searching for what to send rather than browsing a merchant
// first) -- the flat-cash-amount-at-a-merchant path the backend also supports is a
// real, separate, deliberately deferred follow-up.
export const purchaseGiftVoucher = (recipientPhoneNumber: string, merchantId: string, merchantProductId: string) =>
  apiFetch<{ success: boolean; voucher: GiftVoucher }>('/api/v1/gift-vouchers', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipientPhoneNumber, merchantId, merchantProductId }),
  }).then((r) => r.voucher);

export const fetchGiftVouchersForConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; vouchers: GiftVoucher[] }>(`/api/v1/gift-vouchers/conversations/${conversationId}`).then((r) => r.vouchers);

export const extendGiftVoucherExpiry = (voucherId: string) =>
  apiFetch<{ success: boolean; voucher: GiftVoucher }>(`/api/v1/gift-vouchers/${voucherId}/extend`, { method: 'POST' }).then((r) => r.voucher);

// Real merchant-side redemption UI (2026-08-16) -- the terminal step of this feature
// had zero client anywhere: a recipient could receive a voucher but no merchant could
// ever actually redeem it. See GiftVoucherService.redeemVoucher's own doc comment for
// why this is merchant-authenticated (the recipient presents the voucher id in person),
// never a self-serve redeem the recipient could fake.
export const redeemGiftVoucher = (voucherId: string) =>
  apiFetch<{ success: boolean; voucher: GiftVoucher }>(`/api/v1/gift-vouchers/${voucherId}/redeem`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.voucher);
