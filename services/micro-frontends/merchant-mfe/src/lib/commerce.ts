import { apiFetch } from './api';

// Real Coupang-style Shop/Commerce order fulfillment queue -- see
// services/backend's OrderService.kt doc comment for the full account. Until now the
// only client for a merchant's real incoming Commerce orders was bank-mfe's
// ShopOrdersAndWishlist.tsx; merchant-mfe (the actual merchant-facing app) had none at
// all, the same real gap EatsOrdersScreen.tsx already closed for Eats orders -- this
// mirrors that exact fix for Commerce.

export type CommerceOrderStatus = 'PLACED' | 'PACKED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

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

export const fetchCommerceMerchantOrders = () =>
  apiFetch<{ success: boolean; orders: CommerceOrder[] }>('/api/v1/orders/merchant-orders').then((r) => r.orders);

export const advanceCommerceOrder = (orderId: string, status: CommerceOrderStatus) =>
  apiFetch<{ success: boolean; order: CommerceOrder }>(`/api/v1/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);
