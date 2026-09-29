// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well", confirmed via AskUserQuestion to mean
// moto-taxi fare tap-collection). See the backend's MotoFareTrip.kt doc comment for
// the full sourced account of Kigali's real smart-metered moto-taxi fares and the
// honest boundary this simulates -- itunda has no partnership with the real app-based
// alternative, Yego Moto, and this feature is never named after it. Reuses the same
// CustomerPaymentCode every user already generates and shows as a QR via "My payment
// code" (lib/shopping.ts's generateCustomerPaymentCode) -- the driver here IS a real
// itunda user, so this is a direct, fee-free payment into the driver's own account,
// not a simulated expense the way Transit's external bus operator is.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real sourced Kigali moto-taxi fare range (newtimes.co.rw's own smart-meter
// fare-schedule reporting: RWF 400 base for the first 2km + RWF 117/km thereafter):
// observed real door-to-door fares run roughly 400 RWF (a short hop) to 6,000 RWF (a
// long cross-city ride) -- matches the backend's MotoFareTrip.MIN_FARE/MAX_FARE.
export const MOTO_FARE_MIN = 400;
export const MOTO_FARE_MAX = 6000;
export const MOTO_FARE_STEP = 100;

export interface MotoFareCollectResult {
  fare: number;
  collectedAt: string;
}

export const collectMotoFare = (code: string, fare: number) =>
  apiFetch<{ success: boolean; collected: MotoFareCollectResult }>('/api/v1/moto-fare/collect', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ code, fare }),
  }).then((r) => r.collected);

// Real gap found live (uncalled-endpoint sweep, 2026-08-29): the collect flow above
// existed with zero way for a driver to ever see what they'd collected -- the backend's
// own MotoFareController.getMyTripsAsDriver ("/earnings") had zero caller anywhere on
// any platform since the feature shipped 2026-08-27. Same real shape as MotoFareTrip
// on the backend, not re-declared: driverUserId/riderUserId are real user ids, not
// display names -- no name-lookup endpoint exists for either role on this feature.
export interface MotoFareTrip {
  id: string;
  riderUserId: string;
  driverUserId: string;
  fare: number;
  createdAt: string;
}

export const fetchMyMotoFareEarnings = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; trips: MotoFareTrip[]; totalElements: number; totalPages: number }>(
    `/api/v1/moto-fare/earnings?page=${page}&size=${size}`,
  );

// Real gap found live (uncalled-endpoint sweep, 2026-09-02): the backend's own
// MotoFareController.getMyTripsAsRider ("/trips") is the exact symmetric counterpart
// of getMyTripsAsDriver ("/earnings") above -- same MotoFareTrip shape, same
// pagination -- but had zero caller anywhere on any platform since the feature
// shipped 2026-08-27. A rider who tapped to pay a moto-taxi fare had no way to see
// their own trip history, only the driver side of this same feature ever got wired.
export const fetchMyMotoFareTripsAsRider = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; trips: MotoFareTrip[]; totalElements: number; totalPages: number }>(
    `/api/v1/moto-fare/trips?page=${page}&size=${size}`,
  );
