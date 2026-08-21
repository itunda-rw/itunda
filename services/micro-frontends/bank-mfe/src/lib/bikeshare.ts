import { apiFetch } from './api';

// Real Kakao T 바이크 (Kakao T BikeAsset, rw.itunda.rideshare, item 222) -- see the
// backend's BikeAssetRentalService doc comment for the full sourced account. Real
// PEER-TO-PEER bike/scooter rental pool (any user self-registers a bike they own),
// billed by elapsed TIME at rental end -- distinct from ride-hailing/designated-driver,
// which both know their fare up front. This file is the first client for an already-
// real backend that previously had zero UI anywhere.

export type BikeAssetType = 'ELECTRIC' | 'REGULAR';
export type BikeAssetRentalStatus = 'ACTIVE' | 'COMPLETED';

export interface BikeAsset {
  id: string;
  ownerUserId: string;
  accountId: string;
  type: BikeAssetType;
  currentLatitude: number;
  currentLongitude: number;
  available: boolean;
  createdAt: string;
}

export interface BikeAssetRentalSession {
  id: string;
  bikeId: string;
  riderUserId: string;
  startedAt: string;
  endedAt: string | null;
  startLatitude: number;
  startLongitude: number;
  endLatitude: number | null;
  endLongitude: number | null;
  durationMinutes: number | null;
  totalFare: number | null;
  platformFee: number | null;
  status: BikeAssetRentalStatus;
}

export const registerBikeAsset = (type: BikeAssetType, latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; bike: BikeAsset }>('/api/v1/bikeshare/bikes', {
    method: 'POST',
    body: JSON.stringify({ type, latitude, longitude }),
  }).then((r) => r.bike);

export const fetchMyBikeAssets = () =>
  apiFetch<{ success: boolean; bikes: BikeAsset[] }>('/api/v1/bikeshare/bikes/mine').then((r) => r.bikes);

export const setBikeAssetAvailability = (bikeId: string, available: boolean) =>
  apiFetch<{ success: boolean; bike: BikeAsset }>(`/api/v1/bikeshare/bikes/${bikeId}/availability`, {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.bike);

export const updateBikeAssetLocation = (bikeId: string, latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; bike: BikeAsset }>(`/api/v1/bikeshare/bikes/${bikeId}/location`, {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.bike);

export const fetchNearbyBikeAssets = (latitude: number, longitude: number, radiusKm = 5) =>
  apiFetch<{ success: boolean; bikes: BikeAsset[] }>(
    `/api/v1/bikeshare/bikes/nearby?latitude=${latitude}&longitude=${longitude}&radiusKm=${radiusKm}`,
  ).then((r) => r.bikes);

export const startBikeAssetRental = (bikeId: string, startLatitude: number, startLongitude: number) =>
  apiFetch<{ success: boolean; rental: BikeAssetRentalSession }>('/api/v1/bikeshare/rentals', {
    method: 'POST',
    body: JSON.stringify({ bikeId, startLatitude, startLongitude }),
  }).then((r) => r.rental);

export const endBikeAssetRental = (sessionId: string, endLatitude: number, endLongitude: number) =>
  apiFetch<{ success: boolean; rental: BikeAssetRentalSession }>(`/api/v1/bikeshare/rentals/${sessionId}/end`, {
    method: 'POST',
    body: JSON.stringify({ endLatitude, endLongitude }),
  }).then((r) => r.rental);

export const fetchMyBikeAssetRentalHistory = () =>
  apiFetch<{ success: boolean; rentals: BikeAssetRentalSession[] }>('/api/v1/bikeshare/rentals/my-history?size=20').then((r) => r.rentals);
