import { apiFetch } from './api';

// Real Naver Smart Place/Kakao Hair Shop/Karrot Business-Profile-style local business
// appointment booking (see backend MerchantBookingService's own doc comment) -- a
// bookable service is a MerchantProduct with durationMinutes set, no second catalog.
// Real on the backend + merchant-mfe (owner-side availability/respond/complete) since
// 2026-07-25, but the CUSTOMER-facing half (browse slots, request a booking, my
// bookings, cancel) had zero client anywhere -- not bank-mfe, not Android, not iOS.
export type MerchantBookingStatus = 'REQUESTED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface BookingSlot {
  startTime: string;
  endTime: string;
}

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
  updatedAt: string;
}

export const fetchAvailableSlots = (merchantId: string, serviceId: string, date: string) =>
  apiFetch<{ success: boolean; slots: BookingSlot[] }>(
    `/api/v1/merchant/${merchantId}/booking-slots?serviceId=${encodeURIComponent(serviceId)}&date=${date}`,
  ).then((r) => r.slots);

export const createBooking = (merchantId: string, serviceId: string, date: string, startTime: string, notes?: string) =>
  apiFetch<{ success: boolean; booking: MerchantBooking }>('/api/v1/merchant/bookings', {
    method: 'POST',
    body: JSON.stringify({ merchantId, serviceId, date, startTime, notes: notes || undefined }),
  }).then((r) => r.booking);

export const fetchMyBookings = () =>
  apiFetch<{ success: boolean; bookings: MerchantBooking[] }>('/api/v1/merchant/bookings/my-bookings').then((r) => r.bookings);

export const cancelBooking = (bookingId: string) =>
  apiFetch<{ success: boolean; booking: MerchantBooking }>(`/api/v1/merchant/bookings/${bookingId}/cancel`, {
    method: 'POST',
  }).then((r) => r.booking);

// Real post-appointment reviews (item 143) -- see backend MerchantBookingReviewController's
// own doc comment. The owner-side list+reply half has been real on merchant-mfe since
// 2026-07-26; the CUSTOMER-facing submit-a-review half had zero client anywhere --
// merchant-mfe's own lib/merchant.ts doc comment named this explicitly as blocked on a
// real customer booking flow existing first, which bank-mfe's BookingWidget/
// MyBookingsCard now provide.
export interface MerchantBookingReview {
  id: string;
  bookingId: string;
  merchantId: string;
  customerId: string;
  serviceName: string;
  rating: number;
  comment: string | null;
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
}

export const submitBookingReview = (bookingId: string, rating: number, comment?: string) =>
  apiFetch<{ success: boolean; review: MerchantBookingReview }>(`/api/v1/merchant/bookings/${bookingId}/review`, {
    method: 'POST',
    body: JSON.stringify({ rating, comment: comment || undefined }),
  }).then((r) => r.review);

export const fetchMyBookingReviews = () =>
  apiFetch<{ success: boolean; reviews: MerchantBookingReview[] }>('/api/v1/merchant/reviews/my-reviews?size=50').then((r) => r.reviews);
