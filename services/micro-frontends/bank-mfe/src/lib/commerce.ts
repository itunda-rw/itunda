import { apiFetch } from './api';

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

export type CommerceOrderStatus = 'PLACED' | 'PACKED' | 'SHIPPED' | 'DELIVERED';

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
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ merchantId, items, deliveryAddress }),
  });

export const fetchMyOrders = () =>
  apiFetch<{ success: boolean; orders: CommerceOrder[] }>('/api/v1/orders/my-orders').then((r) => r.orders);

export const fetchMerchantOrders = () =>
  apiFetch<{ success: boolean; orders: CommerceOrder[] }>('/api/v1/orders/merchant-orders').then((r) => r.orders);

export const advanceOrderStatus = (orderId: string, status: CommerceOrderStatus) =>
  apiFetch<{ success: boolean; order: CommerceOrder }>(`/api/v1/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);
