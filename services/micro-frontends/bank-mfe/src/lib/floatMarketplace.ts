// Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- sourced
// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Running
// out of e-float or physical cash is a documented top-2 operational challenge for
// mobile money agents across Africa, Rwanda specifically named among affected
// countries (Financial Inclusion Insights Survey); the real existing rebalancing path
// is traveling to a central point, often impossible on a weekend when banks are
// closed. Rebalance (IDEO.org-backed) is a real existing peer-to-peer float
// marketplace proving genuine demand for exactly this shape. See the backend's
// FloatMarketplaceService.kt doc comment for the full account, including why this is
// deliberately distinct from AgentOperatorController's admin-to-agent till funding.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type FloatListingStatus = 'OPEN' | 'FULFILLED' | 'CANCELLED';
export type FloatTransferRequestStatus = 'REQUESTED' | 'ACCEPTED' | 'DECLINED';

export interface FloatListing {
  id: string;
  agentId: string;
  amount: number;
  claimedAmount: number;
  status: FloatListingStatus;
  createdAt: string;
}

export interface NearbyFloatListing {
  listing: FloatListing;
  agentDisplayName: string;
  distanceKm: number;
  remainingAmount: number;
}

export interface FloatTransferRequest {
  id: string;
  listingId: string;
  requestingAgentId: string;
  amount: number;
  status: FloatTransferRequestStatus;
  transactionId: string | null;
  createdAt: string;
}

export const postFloatListing = (amount: number) =>
  apiFetch<{ success: boolean; listing: FloatListing }>('/api/v1/float-marketplace/listings', {
    method: 'POST',
    body: JSON.stringify({ amount }),
  }).then((r) => r.listing);

export const fetchNearbyFloatListings = (latitude: number, longitude: number, radiusKm = 20) =>
  apiFetch<{ success: boolean; listings: NearbyFloatListing[] }>(
    `/api/v1/float-marketplace/listings/nearby?latitude=${latitude}&longitude=${longitude}&radiusKm=${radiusKm}`,
  ).then((r) => r.listings);

export const fetchMyFloatListings = () =>
  apiFetch<{ success: boolean; listings: FloatListing[] }>('/api/v1/float-marketplace/listings/mine').then((r) => r.listings);

export const cancelFloatListing = (listingId: string) =>
  apiFetch<{ success: boolean; listing: FloatListing }>(`/api/v1/float-marketplace/listings/${listingId}/cancel`, {
    method: 'POST',
  }).then((r) => r.listing);

export const requestFloat = (listingId: string, amount: number) =>
  apiFetch<{ success: boolean; request: FloatTransferRequest }>(`/api/v1/float-marketplace/listings/${listingId}/requests`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  }).then((r) => r.request);

export const fetchMyFloatRequests = () =>
  apiFetch<{ success: boolean; requests: FloatTransferRequest[] }>('/api/v1/float-marketplace/requests/mine').then((r) => r.requests);

export const fetchIncomingFloatRequests = () =>
  apiFetch<{ success: boolean; requests: FloatTransferRequest[] }>('/api/v1/float-marketplace/requests/incoming').then((r) => r.requests);

export const acceptFloatRequest = (requestId: string) =>
  apiFetch<{ success: boolean; request: FloatTransferRequest }>(`/api/v1/float-marketplace/requests/${requestId}/accept`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.request);

export const declineFloatRequest = (requestId: string) =>
  apiFetch<{ success: boolean; request: FloatTransferRequest }>(`/api/v1/float-marketplace/requests/${requestId}/decline`, {
    method: 'POST',
  }).then((r) => r.request);
