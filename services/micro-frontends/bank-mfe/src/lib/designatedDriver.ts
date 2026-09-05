import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Kakao T 대리운전 (designated driver, rw.itunda.rideshare, item 221) -- see the
// backend's DesignatedDriverService doc comment for the full sourced account. A
// professional driver comes to the customer's location and drives the CUSTOMER'S OWN
// CAR home for them -- distinct from ride-hailing (RidesView above), where the driver
// uses their own vehicle. This file is the first client for an already-real backend
// that previously had zero UI anywhere.

export type DesignatedDriverTripStatus = 'REQUESTED' | 'ACCEPTED' | 'DRIVING' | 'COMPLETED' | 'CANCELLED';

export interface DesignatedDriverTrip {
  id: string;
  customerId: string;
  driverId: string | null;
  pickupAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropoffAddress: string;
  dropoffLatitude: number;
  dropoffLongitude: number;
  vehicleMake: string;
  vehicleModel: string;
  vehiclePlate: string;
  distanceKm: number;
  fare: number;
  platformFee: number;
  status: DesignatedDriverTripStatus;
  createdAt: string;
}

export interface DesignatedDriver {
  id: string;
  userId: string;
  accountId: string;
  licenseNumber: string;
  available: boolean;
  currentLatitude: number | null;
  currentLongitude: number | null;
  createdAt: string;
}

// Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory) -- a
// lost response after a successful register would previously resubmit here and hit
// the backend's own DesignatedDriverAlreadyRegisteredException guard on retry.
export const registerAsDesignatedDriver = (licenseNumber: string) =>
  apiFetch<{ success: boolean; driver: DesignatedDriver }>('/api/v1/designated-driver/drivers/register', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ licenseNumber }),
  }).then((r) => r.driver);

export const fetchMyDesignatedDriverProfile = () =>
  apiFetch<{ success: boolean; driver: DesignatedDriver | null }>('/api/v1/designated-driver/drivers/me').then((r) => r.driver);

export const setDesignatedDriverAvailability = (available: boolean) =>
  apiFetch<{ success: boolean; driver: DesignatedDriver }>('/api/v1/designated-driver/drivers/availability', {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.driver);

export const updateDesignatedDriverLocation = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; driver: DesignatedDriver }>('/api/v1/designated-driver/drivers/location', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.driver);

export const requestDesignatedDriverTrip = (
  pickupAddress: string, pickupLatitude: number, pickupLongitude: number,
  dropoffAddress: string, dropoffLatitude: number, dropoffLongitude: number,
  vehicleMake: string, vehicleModel: string, vehiclePlate: string,
) =>
  apiFetch<{ success: boolean; trip: DesignatedDriverTrip }>('/api/v1/designated-driver/trips', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({
      pickupAddress, pickupLatitude, pickupLongitude, dropoffAddress, dropoffLatitude, dropoffLongitude,
      vehicleMake, vehicleModel, vehiclePlate,
    }),
  }).then((r) => r.trip);

export const fetchAvailableDesignatedDriverTrips = () =>
  apiFetch<{ success: boolean; trips: DesignatedDriverTrip[] }>('/api/v1/designated-driver/trips/available').then((r) => r.trips);

export const fetchMyDesignatedDriverTrips = () =>
  apiFetch<{ success: boolean; trips: DesignatedDriverTrip[] }>('/api/v1/designated-driver/trips/my-trips?size=20').then((r) => r.trips);

export const fetchMyDesignatedDriverDriverTrips = () =>
  apiFetch<{ success: boolean; trips: DesignatedDriverTrip[] }>('/api/v1/designated-driver/trips/my-driver-trips?size=20').then((r) => r.trips);

export const acceptDesignatedDriverTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: DesignatedDriverTrip }>(`/api/v1/designated-driver/trips/${tripId}/accept`, { method: 'POST' }).then((r) => r.trip);

export const startDesignatedDriverTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: DesignatedDriverTrip }>(`/api/v1/designated-driver/trips/${tripId}/start-driving`, { method: 'POST' }).then((r) => r.trip);

export const completeDesignatedDriverTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: DesignatedDriverTrip }>(`/api/v1/designated-driver/trips/${tripId}/complete`, { method: 'POST' }).then((r) => r.trip);

export const cancelDesignatedDriverTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: DesignatedDriverTrip }>(`/api/v1/designated-driver/trips/${tripId}/cancel`, { method: 'POST' }).then((r) => r.trip);
