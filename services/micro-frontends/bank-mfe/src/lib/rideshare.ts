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
  // Real gap found 2026-08-16: the backend has always returned this (RideTrip.kt's own
  // transactionId), but this client type never declared it, so it was silently
  // discarded on every response -- see the "report a trip issue" feature's own need to
  // pre-fill SupportTicket.transactionId with a specific completed ride's payment.
  transactionId: string;
  // Same gap, found 2026-08-20: the backend has always returned this too
  // (RideTrip.tipAmount) -- null until tipDriver() below is called once, never again
  // after (RideTripAlreadyTippedException). Declared here so the client can actually
  // tell whether a completed trip has already been tipped.
  tipAmount: number | null;
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

// Real Uber "Verify Your Ride" PIN -- see backend RideTrip.pin's own doc comment. The
// driver must enter the exact 4-digit code the passenger reads aloud before the trip
// (and the fare clock) actually starts.
export const startRideTrip = (tripId: string, pin: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/start`, {
    method: 'POST',
    body: JSON.stringify({ pin }),
  }).then((r) => r.trip);

// Real passenger-only PIN lookup -- a stranger, or even the trip's own driver, gets a
// real 404 from the backend.
export const fetchRideTripPin = (tripId: string) =>
  apiFetch<{ success: boolean; pin: string }>(`/api/v1/rides/trips/${tripId}/pin`).then((r) => r.pin);

export const completeRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/complete`, { method: 'POST' }).then((r) => r.trip);

// Real Uber "Share Trip Status" (2026-08-16, help.uber.com/en/riders/article/
// sharing-your-trip-status-faq) -- see backend RideTripService.shareTripStatus's own
// doc comment for why this sends into a real Talk conversation rather than an
// unauthenticated public link (itunda has no such surface).
export const shareRideTripStatus = (tripId: string, conversationId: string) =>
  apiFetch<{ success: boolean; message: { id: string } }>(`/api/v1/rides/trips/${tripId}/share`, {
    method: 'POST',
    body: JSON.stringify({ conversationId }),
  }).then((r) => r.message);

export const cancelRideTrip = (tripId: string) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/cancel`, { method: 'POST' }).then((r) => r.trip);

// Real Uber post-trip tipping -- see backend RideTripService.tipDriver's own doc
// comment. Found via scripts/uncalled-endpoint-sweep.py: fully built on the backend
// (real TIP_WINDOW, real already-tipped guard, real wallet-to-wallet ledger legs)
// with zero client anywhere.
export const tipDriver = (tripId: string, amount: number) =>
  apiFetch<{ success: boolean; trip: RideTrip }>(`/api/v1/rides/trips/${tripId}/tip`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.trip);

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

// Real driver written-review browsing during an active trip (item 233) -- found while
// triaging the uncalled-endpoint sweep: RideController.getDriverReviews was real
// (distinct from the aggregate rating above, already shown to the driver's own
// dashboard) but had zero client callers on any platform, because no passenger-facing
// surface ever showed *which* driver they were matched with. `RideTrip.driverId` was
// already available client-side the whole time (used for post-trip review submission)
// -- this just reads it during the active trip too. Honest v1: `RideDriver` has no
// name/vehicle field on the backend at all, so this shows the driver's real rating +
// reviews only, never a name that doesn't exist in the data model.
export const fetchDriverReviews = (driverId: string) =>
  apiFetch<{ success: boolean; reviews: RideTripReview[] }>(`/api/v1/rides/drivers/${driverId}/reviews?size=10`)
    .then((r) => r.reviews);

// Real Uber Safety "Trusted Contacts" (help.uber.com -- riders pre-select up to 5
// trusted contacts once in settings, then one tap sends their live trip status to all
// of them). See the backend's RideTrustedContact.kt doc comment for the full sourced
// account. Found while triaging the uncalled-endpoint sweep: RideController already had
// a complete, tested list/add/remove/send-status implementation (RideTrustedContactService,
// RideTrustedContactServiceTest) with zero client callers on any platform -- this closes
// that gap for bank-mfe, distinct from shareRideTripStatus's own one-off per-share pick.
export interface RideTrustedContact {
  id: string;
  userId: string;
  contactUserId: string;
  contactName: string;
  createdAt: string;
}

export const fetchTrustedContacts = () =>
  apiFetch<{ success: boolean; contacts: RideTrustedContact[] }>('/api/v1/rides/trusted-contacts').then((r) => r.contacts);

export const addTrustedContact = (phoneNumber: string, name: string) =>
  apiFetch<{ success: boolean; contact: RideTrustedContact }>('/api/v1/rides/trusted-contacts', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, name }),
  }).then((r) => r.contact);

export const removeTrustedContact = (contactId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/rides/trusted-contacts/${contactId}`, { method: 'DELETE' }).then(() => undefined);

// Real Uber "Send Status" -- one tap fans a trip's live status out to every trusted
// contact at once (distinct from shareRideTripStatus's single conversation pick above).
// Returns how many contacts were actually messaged so the client can show a real
// "Sent to N contacts" confirmation, matching Uber's own toast.
export const sendStatusToTrustedContacts = (tripId: string) =>
  apiFetch<{ success: boolean; sentCount: number }>(`/api/v1/rides/trips/${tripId}/send-status`, { method: 'POST' }).then((r) => r.sentCount);
