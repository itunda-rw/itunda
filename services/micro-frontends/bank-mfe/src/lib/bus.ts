import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Kakao T 시외버스 (intercity bus booking, rw.itunda.rideshare, item 224) -- see
// the backend's BusService doc comment for the full sourced account. Real PEER-TO-PEER
// coach-operator pool (any user self-registers as an operator and posts a scheduled
// trip), fare known and charged in full at booking time -- distinct from bike/parking,
// which both settle at session end. This file is the first client for an already-real
// backend that previously had zero UI anywhere.

export type BusBookingStatus = 'BOOKED' | 'CANCELLED';

export interface BusTrip {
  id: string;
  operatorUserId: string;
  walletId: string;
  origin: string;
  destination: string;
  departureTime: string;
  totalSeats: number;
  availableSeats: number;
  farePerSeat: number;
  createdAt: string;
}

export interface BusBooking {
  id: string;
  tripId: string;
  riderUserId: string;
  seatCount: number;
  totalFare: number;
  platformFee: number;
  paymentTransactionId: string;
  status: BusBookingStatus;
  refundTransactionId: string | null;
  createdAt: string;
}

export const postBusTrip = (origin: string, destination: string, departureTime: string, totalSeats: number, farePerSeat: number) =>
  apiFetch<{ success: boolean; trip: BusTrip }>('/api/v1/bus/trips', {
    method: 'POST',
    body: JSON.stringify({ origin, destination, departureTime, totalSeats, farePerSeat }),
  }).then((r) => r.trip);

export const fetchMyBusTrips = () =>
  apiFetch<{ success: boolean; trips: BusTrip[] }>('/api/v1/bus/trips/mine').then((r) => r.trips);

export const fetchBusTripBookings = (tripId: string) =>
  apiFetch<{ success: boolean; bookings: BusBooking[] }>(`/api/v1/bus/trips/${tripId}/bookings`).then((r) => r.bookings);

export const searchBusTrips = (origin?: string, destination?: string) => {
  const params = new URLSearchParams();
  if (origin) params.set('origin', origin);
  if (destination) params.set('destination', destination);
  const qs = params.toString();
  return apiFetch<{ success: boolean; trips: BusTrip[] }>(`/api/v1/bus/trips/search${qs ? `?${qs}` : ''}`).then((r) => r.trips);
};

export const bookBusSeats = (tripId: string, seatCount: number) =>
  apiFetch<{ success: boolean; booking: BusBooking }>('/api/v1/bus/bookings', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ tripId, seatCount }),
  }).then((r) => r.booking);

export const cancelBusBooking = (bookingId: string) =>
  apiFetch<{ success: boolean; booking: BusBooking }>(`/api/v1/bus/bookings/${bookingId}/cancel`, { method: 'POST' }).then((r) => r.booking);

export const fetchMyBusBookings = () =>
  apiFetch<{ success: boolean; bookings: BusBooking[] }>('/api/v1/bus/bookings/my-history?size=20').then((r) => r.bookings);
