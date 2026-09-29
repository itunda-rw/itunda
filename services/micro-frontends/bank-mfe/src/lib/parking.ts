import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Kakao T 주차 (Kakao T Parking, rw.itunda.rideshare, item 223) -- see the
// backend's ParkingService doc comment for the full sourced account. Real
// PEER-TO-PEER parking-spot rental pool (any user self-lists a spot they own/control),
// billed by elapsed TIME at checkout, rounded up to the next full hour -- distinct
// from Bike (per-minute) and ride-hailing/designated-driver (known fare up front).
// This file is the first client for an already-real backend that previously had zero
// UI anywhere.

export type ParkingSessionStatus = 'ACTIVE' | 'COMPLETED';

export interface ParkingSpot {
  id: string;
  ownerUserId: string;
  accountId: string;
  address: string;
  latitude: number;
  longitude: number;
  hourlyRate: number;
  available: boolean;
  createdAt: string;
}

export interface ParkingSession {
  id: string;
  spotId: string;
  renterUserId: string;
  startedAt: string;
  endedAt: string | null;
  durationMinutes: number | null;
  totalFare: number | null;
  platformFee: number | null;
  status: ParkingSessionStatus;
}

export const registerParkingSpot = (address: string, latitude: number, longitude: number, hourlyRate: number) =>
  apiFetch<{ success: boolean; spot: ParkingSpot }>('/api/v1/parking/spots', {
    method: 'POST',
    body: JSON.stringify({ address, latitude, longitude, hourlyRate }),
  }).then((r) => r.spot);

export const fetchMyParkingSpots = () =>
  apiFetch<{ success: boolean; spots: ParkingSpot[] }>('/api/v1/parking/spots/mine').then((r) => r.spots);

export const setParkingSpotAvailability = (spotId: string, available: boolean) =>
  apiFetch<{ success: boolean; spot: ParkingSpot }>(`/api/v1/parking/spots/${spotId}/availability`, {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.spot);

export const fetchNearbyParkingSpots = (latitude: number, longitude: number, radiusKm = 5) =>
  apiFetch<{ success: boolean; spots: ParkingSpot[] }>(
    `/api/v1/parking/spots/nearby?latitude=${latitude}&longitude=${longitude}&radiusKm=${radiusKm}`,
  ).then((r) => r.spots);

export const startParkingSession = (spotId: string) =>
  apiFetch<{ success: boolean; session: ParkingSession }>('/api/v1/parking/sessions', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ spotId }),
  }).then((r) => r.session);

export const endParkingSession = (sessionId: string) =>
  apiFetch<{ success: boolean; session: ParkingSession }>(`/api/v1/parking/sessions/${sessionId}/end`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.session);

// Real pagination-discard fix (2026-09-11, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- this real Pageable endpoint's page just wasn't ever sent,
// silently capping parking history at the most recent 20 sessions.
export const fetchMyParkingHistory = (page = 0) =>
  apiFetch<{ success: boolean; sessions: ParkingSession[]; page: number; totalPages: number }>(
    `/api/v1/parking/sessions/my-history?page=${page}&size=20`,
  );
