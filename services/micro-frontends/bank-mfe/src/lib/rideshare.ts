import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Kakao T-style ride-hailing (rw.itunda.rideshare, 2026-07-26 backend) -- see
// RideTripService's own doc comment on the backend for the full sourced account
// (kakaomobility.com/contents/taxi-dispatch: real dispatch ranks candidate drivers by
// acceptance-prediction, daily completions, rating, acceptance rate, and ETA). This
// file is the first bank-mfe client for an already-real backend that previously had
// zero web UI -- closing that gap, not adding new backend behavior.

export type RideTripStatus = 'REQUESTED' | 'DRIVER_ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface RideTrip {
  id: string;
  passengerId: string;
  driverId: string | null;
  pickupAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropoffAddress: string;
  dropoffLatitude: number;
  dropoffLongitude: number;
  distanceKm: number;
  fare: number;
  platformFee: number;
  status: RideTripStatus;
  // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null means an ASAP
  // request, unchanged from before. See the backend's RideTrip.scheduledFor doc comment.
  scheduledFor: string | null;
  createdAt: string;
}

// Real Kakao T-style multi-stop rides (item 214) -- see the backend's RideTripStop.kt
// doc comment for the full sourced account.
export interface RideTripStop {
  id: string;
  tripId: string;
  sequence: number;
  address: string;
  latitude: number;
  longitude: number;
  arrivedAt: string | null;
}

export type RideDriverStatus = 'ACTIVE' | 'SUSPENDED';

export interface RideDriver {
  id: string;
  userId: string;
  walletId: string;
  status: RideDriverStatus;
  available: boolean;
  currentLatitude: number | null;
  currentLongitude: number | null;
  locationUpdatedAt: string | null;
}

export const registerAsDriver = () =>
  apiFetch<{ success: boolean; driver: RideDriver }>('/api/v1/rides/drivers/register', { method: 'POST' }).then((r) => r.driver);

export const fetchMyDriverProfile = () =>
  apiFetch<{ success: boolean; driver: RideDriver }>('/api/v1/rides/drivers/me').then((r) => r.driver);

export const setDriverAvailability = (available: boolean) =>
  apiFetch<{ success: boolean; driver: RideDriver }>('/api/v1/rides/drivers/availability', {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.driver);

export const updateDriverLocation = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; driver: RideDriver }>('/api/v1/rides/drivers/location', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.driver);

export const requestRideTrip = (
  pickupAddress: string, pickupLatitude: number, pickupLongitude: number,
  dropoffAddress: string, dropoffLatitude: number, dropoffLongitude: number,
  // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- omitted/undefined means
  // ASAP, unchanged from before.
  scheduledFor?: string,
  // Real Kakao T-style multi-stop rides (item 214) -- omitted/empty means a direct
  // pickup-to-dropoff trip, unchanged from before. Up to 3 extra stops.
  stops?: { address: string; latitude: number; longitude: number }[],
) =>
  apiFetch<{ success: boolean; trip: RideTrip }>('/api/v1/rides/trips', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ pickupAddress, pickupLatitude, pickupLongitude, dropoffAddress, dropoffLatitude, dropoffLongitude, scheduledFor, stops }),
  }).then((r) => r.trip);

export const fetchTripStops = (tripId: string) =>
  apiFetch<{ success: boolean; stops: RideTripStop[] }>(`/api/v1/rides/trips/${tripId}/stops`).then((r) => r.stops);

export const arriveAtRideStop = (tripId: string) =>
  apiFetch<{ success: boolean; stop: RideTripStop }>(`/api/v1/rides/trips/${tripId}/stops/arrive`, { method: 'POST' }).then((r) => r.stop);

export const fetchAvailableTrips = () =>
  apiFetch<{ success: boolean; trips: RideTrip[] }>('/api/v1/rides/trips/available').then((r) => r.trips);

export const fetchMyTrips = () =>
  apiFetch<{ success: boolean; trips: RideTrip[] }>('/api/v1/rides/trips/my-trips?size=20').then((r) => r.trips);

export const fetchMyDriverTrips = () =>
  apiFetch<{ success: boolean; trips: RideTrip[] }>('/api/v1/rides/trips/my-driver-trips?size=20').then((r) => r.trips);

export const acceptRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/accept`, { method: 'POST' }).then((r) => r.trip);

export const declineRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/decline`, { method: 'POST' }).then((r) => r.trip);

export const startRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/start`, { method: 'POST' }).then((r) => r.trip);

export const completeRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/complete`, { method: 'POST' }).then((r) => r.trip);

export const cancelRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/cancel`, { method: 'POST' }).then((r) => r.trip);

// Real Kakao T-style post-trip driver rating (item 213) -- see the backend's
// RideTripReview.kt doc comment for the full sourced account.
export interface RideTripReview {
  id: string;
  tripId: string;
  passengerId: string;
  driverId: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface RideDriverRating {
  average: number | null;
  count: number;
}

export const submitRideReview = (tripId: string, rating: number, comment?: string) =>
  apiFetch<{ success: boolean; review: RideTripReview }>(`/api/v1/rides/trips/${tripId}/review`, {
    method: 'POST',
    body: JSON.stringify({ rating, comment }),
  }).then((r) => r.review);

export const fetchDriverRating = (driverId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/rides/drivers/${driverId}/rating`)
    .then((r) => ({ average: r.average, count: r.count }));
