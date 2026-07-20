import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Coupang-style multi-item commerce (rw.itunda.commerce, 2026-07-18) -- merchant
// browsing reuses the existing shopping catalog endpoint (see ShopView's own comment in
// BankDashboard.tsx); only order placement/tracking is genuinely new here.

export interface CommerceProduct {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
}

export const fetchMerchantProducts = (merchantId: string) =>
  apiFetch<{ success: boolean; merchant: { id: string; businessName: string }; products: CommerceProduct[] }>(
    `/api/v1/shopping/merchants/${merchantId}/products`,
  );

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

export const placeOrder = (merchantId: string, items: { productId: string; quantity: number }[], deliveryAddress: string) =>
  apiFetch<{ success: boolean; order: CommerceOrder; items: CommerceOrderItem[] }>('/api/v1/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ merchantId, items, deliveryAddress }),
  });

export const fetchOrderDetail = (orderId: string) =>
  apiFetch<{ success: boolean; order: CommerceOrder; items: CommerceOrderItem[] }>(`/api/v1/orders/${orderId}`);

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

export const fetchProductRating = (productId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/orders/products/${productId}/rating`);

// Real product wishlist (2026-07-20) -- the real "찜하기"/wishlist every real Coupang/
// Naver/Kakao/Toss Shopping-style app has. See ProductFavoriteService's own doc comment.
export interface FavoriteProduct {
  productId: string;
  merchantId: string;
  name: string;
  price: number;
  businessName: string;
  favoritedAt: string;
}

export const addProductFavorite = (productId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/orders/products/${productId}/favorite`, { method: 'POST' });

export const removeProductFavorite = (productId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/orders/products/${productId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoriteProducts = () =>
  apiFetch<{ success: boolean; favorites: FavoriteProduct[] }>('/api/v1/orders/products/favorites').then((r) => r.favorites);
