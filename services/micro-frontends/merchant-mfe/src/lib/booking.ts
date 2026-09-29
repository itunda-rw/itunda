import { apiFetch } from './api';

// Real local-business appointment booking, owner side -- found via a fresh "defined
// but uncalled" endpoint sweep: MerchantBookingController's availability + queue
// endpoints were already wired on Android/iOS MerchantApp (2026-07-25) but had zero
// client on this web dashboard. Two real, independent jobs: declare a weekly
// availability schedule, and confirm/decline/complete incoming requests -- mirrors
// BookingScreen.kt's own account of the backend exactly.

export interface AvailabilityWindow {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

export type MerchantBookingStatus = 'REQUESTED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'COMPLETED';

export interface MerchantBooking {
  id: string;
  merchantId: string;
  customerId: string;
  serviceId: string;
  serviceName: string;
  bookingDate: string;
  startTime: string;
  endTime: string;
  status: MerchantBookingStatus;
  notes: string | null;
  createdAt: string;
}

export const getMyAvailability = () =>
  apiFetch<{ success: boolean; windows: AvailabilityWindow[] }>('/api/v1/merchant/booking/availability').then((r) => r.windows);

export const setAvailability = (windows: AvailabilityWindow[]) =>
  apiFetch<{ success: boolean; windows: AvailabilityWindow[] }>('/api/v1/merchant/booking/availability', {
    method: 'POST',
    body: JSON.stringify({ windows }),
  }).then((r) => r.windows);

export const getMerchantBookings = () =>
  apiFetch<{ success: boolean; bookings: MerchantBooking[] }>('/api/v1/merchant/bookings/merchant-bookings').then((r) => r.bookings);

export const respondToBooking = (bookingId: string, confirm: boolean) =>
  apiFetch<{ success: boolean; booking: MerchantBooking }>(`/api/v1/merchant/bookings/${bookingId}/respond`, {
    method: 'POST',
    body: JSON.stringify({ confirm }),
  }).then((r) => r.booking);

export const completeBooking = (bookingId: string) =>
  apiFetch<{ success: boolean; booking: MerchantBooking }>(`/api/v1/merchant/bookings/${bookingId}/complete`, { method: 'POST' }).then(
    (r) => r.booking,
  );
