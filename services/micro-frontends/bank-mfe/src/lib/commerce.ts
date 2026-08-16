import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Coupang-style multi-item commerce (rw.itunda.commerce, 2026-07-18) -- merchant
// browsing reuses the existing shopping catalog endpoint (see ShopView's own comment in
// BankDashboard.tsx); only order placement/tracking is genuinely new here.

// imageUrl/originalPrice/discountPercent added 2026-07-21, closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #4 -- see backend
// MerchantProduct.kt's own doc comment for the full account (merchant-supplied external
// URL, no upload/storage layer; discountPercent is server-computed, never client-set).
// description added 2026-07-21, backing the new dedicated product-detail screen (closes
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6) -- merchant-entered free text,
// optional.
export interface CommerceProduct {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
  imageUrl?: string | null;
  originalPrice?: number | null;
  discountPercent?: number | null;
  description?: string | null;
  stockQuantity?: number | null;
  // Real bookable-service marker (see MerchantBooking.kt's own doc comment) -- a
  // product with durationMinutes set is bookable via lib/booking.ts; requiresPrepay
  // means booking it holds a real deposit from the customer's wallet automatically.
  durationMinutes?: number | null;
  requiresPrepay?: boolean;
  // Real Coupang WING 상품분석 (product analytics) view count (2026-08-16) -- see
  // backend MerchantProduct.viewCount's own doc comment. Only populated by
  // fetchProduct's own real single-product fetch, same "list item is stale, detail
  // fetch is fresh" shape ListingCard's own freshViewCount already established for
  // Marketplace.
  viewCount?: number;
}

export const fetchMerchantProducts = (merchantId: string) =>
  apiFetch<{ success: boolean; merchant: { id: string; businessName: string }; products: CommerceProduct[] }>(
    `/api/v1/shopping/merchants/${merchantId}/products`,
  );

// Real Coupang WING 상품분석 (product analytics) view trigger (2026-08-16) -- see
// backend ShoppingController.getProduct's own doc comment. ProductDetailView
// previously rendered straight off the already-fetched catalog list with zero real
// per-product fetch anywhere; this gives it one to call on mount.
export const fetchProduct = (productId: string) =>
  apiFetch<{ success: boolean; product: CommerceProduct }>(`/api/v1/shopping/products/${productId}`).then((r) => r.product);

export type CommerceOrderStatus = 'PLACED' | 'PACKED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface CommerceOrderItem {
  id: string;
  orderId: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
}

export interface CommerceOrder {
  id: string;
  buyerId: string;
  merchantId: string;
  deliveryAddress: string;
  totalAmount: number;
  fee: number;
  transactionId: string;
  status: CommerceOrderStatus;
  createdAt: string;
  updatedAt: string;
}

// referralCode added for the real 쿠팡파트너스 (Coupang Partners)-style affiliate
// program, item 229 -- see lib/affiliate.ts's own doc comment. Omitted/unknown/
// self-referral all fall through to a normal order with no commission paid.
export const placeOrder = (merchantId: string, items: { productId: string; quantity: number }[], deliveryAddress: string, referralCode?: string) =>
  apiFetch<{ success: boolean; order: CommerceOrder; items: CommerceOrderItem[] }>('/api/v1/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ merchantId, items, deliveryAddress, referralCode }),
  });

export const fetchOrderDetail = (orderId: string) =>
  apiFetch<{ success: boolean; order: CommerceOrder; items: CommerceOrderItem[] }>(`/api/v1/orders/${orderId}`);

// Real live rider-location tracking (2026-08-05) -- found via a defined-but-uncalled-
// endpoint sweep: OrderService.getRiderLocation (backend, 2026-07-26) mirrors
// EatsOrderService.getRiderLocation exactly (same DTO shape, same buyer/seller/rider
// authorization, same real "null while genuinely nothing to show" semantics), and
// Eats' own equivalent has had a real LiveRiderMap client since 2026-07-20 -- this one
// had zero client anywhere on any platform until now. Honest v1 scope-down from Eats'
// own richer version: Commerce's own `Order` entity has no delivery-coordinate fields
// (only a free-text deliveryAddress), so there's no real "to" endpoint to draw a route
// toward -- this shows the rider's own live position only, not a route line.
export interface OrderRiderLocation {
  latitude: number;
  longitude: number;
  updatedAt: string;
}

export const fetchOrderRiderLocation = (orderId: string) =>
  apiFetch<{ success: boolean; available: boolean; location: OrderRiderLocation | null }>(`/api/v1/orders/${orderId}/rider-location`);

export const fetchMyOrders = () =>
  apiFetch<{ success: boolean; orders: CommerceOrder[] }>('/api/v1/orders/my-orders').then((r) => r.orders);

export const fetchMerchantOrders = () =>
  apiFetch<{ success: boolean; orders: CommerceOrder[] }>('/api/v1/orders/merchant-orders').then((r) => r.orders);

export const advanceOrderStatus = (orderId: string, status: CommerceOrderStatus) =>
  apiFetch<{ success: boolean; order: CommerceOrder }>(`/api/v1/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

// Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only. See
// OrderService.cancelOrder's own doc comment for the full backend account.
export const cancelOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: CommerceOrder }>(`/api/v1/orders/${orderId}/cancel`, {
    method: 'POST',
  }).then((r) => r.order);

// Real Coupang-style post-delivery Return & Exchange requests (반품/교환 신청) (item 166,
// found via the domain-entity discovery sweep) -- see OrderReturnService's own doc
// comment. Genuinely distinct from cancelOrder above (PLACED orders only, before real
// fulfillment work starts): this is the separate real event only possible once an order
// is DELIVERED. A real 7-day window from delivery; an approved RETURN triggers a real
// refund (reverses the original transaction's own ledger legs), an approved EXCHANGE
// moves no money (a recorded "the seller will ship a replacement" agreement, fulfilled
// manually). Fully real (including real push notifications both ways) but had zero
// client anywhere on any platform.
export type OrderReturnType = 'RETURN' | 'EXCHANGE';
export type OrderReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';
export const ORDER_RETURN_REASON_CODES = ['DEFECTIVE', 'WRONG_ITEM', 'NOT_AS_DESCRIBED', 'NO_LONGER_NEEDED', 'SIZE_FIT', 'OTHER'] as const;

export interface OrderReturnRequestDto {
  id: string;
  orderId: string;
  buyerId: string;
  merchantId: string;
  type: OrderReturnType;
  reasonCode: string;
  reasonNote: string | null;
  status: OrderReturnStatus;
  refundTransactionId: string | null;
  requestedAt: string;
  decidedAt: string | null;
}

export const requestOrderReturn = (orderId: string, type: OrderReturnType, reasonCode: string, reasonNote?: string) =>
  apiFetch<{ success: boolean; returnRequest: OrderReturnRequestDto }>(`/api/v1/orders/${orderId}/return`, {
    method: 'POST',
    body: JSON.stringify({ type, reasonCode, reasonNote }),
  }).then((r) => r.returnRequest);

export const fetchMyReturnRequests = () =>
  apiFetch<{ success: boolean; returnRequests: OrderReturnRequestDto[] }>('/api/v1/orders/returns/my-requests?size=50').then((r) => r.returnRequests);

export const fetchMerchantReturnQueue = () =>
  apiFetch<{ success: boolean; returnRequests: OrderReturnRequestDto[] }>('/api/v1/orders/returns/merchant-queue?size=50').then((r) => r.returnRequests);

export const decideOrderReturn = (returnRequestId: string, approve: boolean) =>
  apiFetch<{ success: boolean; returnRequest: OrderReturnRequestDto }>(`/api/v1/orders/returns/${returnRequestId}/decide`, {
    method: 'POST',
    body: JSON.stringify({ approve }),
  }).then((r) => r.returnRequest);

// Real post-delivery product reviews (2026-07-20), mirroring Eats' own restaurant/rider
// review pattern -- see ProductReviewService's own doc comment for the full backend
// account. One real review per real delivered order line item.
export interface ProductReview {
  id: string;
  orderItemId: string;
  orderId: string;
  buyerId: string;
  productId: string;
  merchantId: string;
  rating: number;
  comment: string | null;
  // Real Coupang/Naver Smart Store-style seller reply (2026-07-26) -- see
  // ProductReviewService.replyToProductReview's own doc comment on the backend.
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
}

export const submitProductReview = (orderItemId: string, rating: number, comment?: string) =>
  apiFetch<{ success: boolean; review: ProductReview }>(`/api/v1/orders/items/${orderItemId}/review`, {
    method: 'POST',
    body: JSON.stringify({ rating, comment }),
  }).then((r) => r.review);

export const fetchProductReviews = (productId: string) =>
  apiFetch<{ success: boolean; reviews: ProductReview[] }>(`/api/v1/orders/products/${productId}/reviews`).then(
    (r) => r.reviews,
  );

// Real Coupang-style pre-purchase product Q&A (상품문의) (2026-07-26) -- see
// ProductInquiryService's own doc comment on the backend. Genuinely distinct from a
// review above: no order/purchase required at all, a real pre-purchase question.
export interface ProductInquiry {
  id: string;
  productId: string;
  merchantId: string;
  buyerId: string;
  question: string;
  answer: string | null;
  answeredAt: string | null;
  createdAt: string;
}

export const askProductInquiry = (productId: string, question: string) =>
  apiFetch<{ success: boolean; inquiry: ProductInquiry }>(`/api/v1/orders/products/${productId}/inquiries`, {
    method: 'POST',
    body: JSON.stringify({ question }),
  }).then((r) => r.inquiry);

export const fetchProductInquiries = (productId: string) =>
  apiFetch<{ success: boolean; inquiries: ProductInquiry[] }>(`/api/v1/orders/products/${productId}/inquiries`).then(
    (r) => r.inquiries,
  );

export const fetchProductRating = (productId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/orders/products/${productId}/rating`);

// Real bulk/wholesale pricing -- see the backend's ProductPriceTier doc comment.
// Buyer-facing read half: merchant-mfe already has the owner-config half
// (PosScreen.tsx's "Pricing" panel). Real checkout money impact, not cosmetic --
// OrderService applies the highest-qualifying tier automatically once the buyer's
// order quantity meets minQuantity, so this is a real "buy more, pay less per unit"
// preview, not a label.
export interface PriceTier {
  minQuantity: number;
  unitPrice: number;
}

export const fetchPriceTiers = (productId: string) =>
  apiFetch<{ success: boolean; tiers: PriceTier[] }>(`/api/v1/merchant/products/${productId}/price-tiers`).then((r) => r.tiers);

// Real product wishlist (2026-07-20) -- the real "찜하기"/wishlist every real Coupang/
// Naver/Kakao/Toss Shopping-style app has. See ProductFavoriteService's own doc comment.
export interface FavoriteProduct {
  productId: string;
  merchantId: string;
  name: string;
  price: number;
  businessName: string;
  favoritedAt: string;
  imageUrl?: string | null;
  originalPrice?: number | null;
  discountPercent?: number | null;
  description?: string | null;
  // Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- true once the
  // real current price has dropped below what it was the last time the backend's
  // ProductPriceDropScheduler checked. See ProductFavorite.kt's own doc comment.
  priceDropped?: boolean;
}

export const addProductFavorite = (productId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/orders/products/${productId}/favorite`, { method: 'POST' });

export const removeProductFavorite = (productId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/orders/products/${productId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoriteProducts = () =>
  apiFetch<{ success: boolean; favorites: FavoriteProduct[] }>('/api/v1/orders/products/favorites').then((r) => r.favorites);
