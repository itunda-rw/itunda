// Real Kigali public-transit stored-value balance -- see the backend's
// TransitBalance.kt doc comment for the full sourced account of Kigali's real Tap&Go
// fare system (AC Group Ltd, Kigali Bus Services, Royal Express) and the honest
// boundary this simulates: itunda has no real partnership with any of them, so this is
// itunda's own product, never named after theirs.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface TransitBalance {
  balance: number;
  createdAt: string;
}

export interface TransitTrip {
  id: string;
  userId: string;
  operator: string;
  fare: number;
  ledgerTransactionId: string;
  createdAt: string;
}

// The 2 real Kigali bus operators Tap&Go actually covers (acgroup.rw). A plain
// whitelist, matching exactly what the backend's TransitOperator.ALL accepts.
export const TRANSIT_OPERATORS = ['Kigali Bus Services', 'Royal Express'] as const;

// Kigali's real sourced fare range (kigalibusservices.rw/smartcards, distance-based
// fare reporting): journeys run roughly 200-500 RWF depending on distance. itunda has
// no real GPS-derived distance to compute an exact fare from, so a rider picks a real
// fare within this sourced range -- matching the backend's TransitTrip.MIN_FARE/MAX_FARE.
export const TRANSIT_MIN_FARE = 200;
export const TRANSIT_MAX_FARE = 500;
export const TRANSIT_FARE_STEP = 50;

export const fetchTransitBalance = () =>
  apiFetch<{ success: boolean; balance: TransitBalance }>('/api/v1/transit/balance').then((r) => r.balance);

export const fetchTransitTrips = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; trips: TransitTrip[]; totalElements: number; totalPages: number }>(
    `/api/v1/transit/trips?page=${page}&size=${size}`,
  );

export const topUpTransit = (amount: number) =>
  apiFetch<{ success: boolean; balance: TransitBalance }>('/api/v1/transit/topup', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.balance);

export const tapTransitFare = (operator: string, fare: number) =>
  apiFetch<{ success: boolean; trip: TransitTrip; balance: TransitBalance }>('/api/v1/transit/tap', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ operator, fare }),
  });
