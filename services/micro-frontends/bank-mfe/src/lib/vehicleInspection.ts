// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// the backend's VehicleInspectionMechanic.kt/VehicleInspectionBooking.kt doc comments
// for the full sourced account. A buyer books and 100%-prepays a real mechanic to
// inspect a real Marketplace used-car listing before purchase.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface VehicleInspectionMechanic {
  id: string;
  userId: string;
  accountId: string;
  businessName: string;
  available: boolean;
  createdAt: string;
}

export type VehicleInspectionStatus = 'REQUESTED' | 'ACCEPTED' | 'COMPLETED' | 'CANCELLED';

export interface VehicleInspectionBooking {
  id: string;
  listingId: string;
  buyerId: string;
  mechanicId: string;
  fee: number;
  platformFee: number;
  scheduledFor: string;
  status: VehicleInspectionStatus;
  findings: string | null;
  createdAt: string;
}

// Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory) -- a
// lost response after a successful register would previously resubmit here and hit
// the backend's own MechanicAlreadyRegisteredException guard on retry.
export const registerAsMechanic = (businessName: string) =>
  apiFetch<{ success: boolean; mechanic: VehicleInspectionMechanic }>('/api/v1/marketplace/inspections/mechanics/register', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ businessName }),
  }).then((r) => r.mechanic);

export const fetchMyMechanicProfile = () =>
  apiFetch<{ success: boolean; mechanic: VehicleInspectionMechanic | null }>('/api/v1/marketplace/inspections/mechanics/me').then((r) => r.mechanic);

export const fetchAvailableMechanics = () =>
  apiFetch<{ success: boolean; mechanics: VehicleInspectionMechanic[] }>('/api/v1/marketplace/inspections/mechanics').then((r) => r.mechanics);

export const setMechanicAvailability = (available: boolean) =>
  apiFetch<{ success: boolean; mechanic: VehicleInspectionMechanic }>('/api/v1/marketplace/inspections/mechanics/availability', {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.mechanic);

export const requestInspection = (listingId: string, mechanicId: string, fee: number, scheduledFor: string) =>
  apiFetch<{ success: boolean; booking: VehicleInspectionBooking }>('/api/v1/marketplace/inspections', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ listingId, mechanicId, fee, scheduledFor }),
  }).then((r) => r.booking);

export const fetchMyInspectionBookings = () =>
  apiFetch<{ success: boolean; bookings: VehicleInspectionBooking[] }>('/api/v1/marketplace/inspections/my-bookings').then((r) => r.bookings);

export const fetchMyMechanicBookings = () =>
  apiFetch<{ success: boolean; bookings: VehicleInspectionBooking[] }>('/api/v1/marketplace/inspections/my-mechanic-bookings').then((r) => r.bookings);

export const acceptInspection = (bookingId: string) =>
  apiFetch<{ success: boolean; booking: VehicleInspectionBooking }>(`/api/v1/marketplace/inspections/${bookingId}/accept`, { method: 'POST' }).then((r) => r.booking);

// Idempotency-Key added (Hood product-completeness pass, 2026-09-07) -- posts a real
// ledger payout to the mechanic, the same real-money-mutation class
// registerAsMechanic/requestInspection above already require it for.
export const completeInspection = (bookingId: string, findings?: string) =>
  apiFetch<{ success: boolean; booking: VehicleInspectionBooking }>(`/api/v1/marketplace/inspections/${bookingId}/complete`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ findings }),
  }).then((r) => r.booking);

// Idempotency-Key added (Hood product-completeness pass, 2026-09-07) -- same real
// ledger-refund reasoning as completeInspection above.
export const cancelInspection = (bookingId: string) =>
  apiFetch<{ success: boolean; booking: VehicleInspectionBooking }>(`/api/v1/marketplace/inspections/${bookingId}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.booking);
