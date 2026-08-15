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

// Real, previously-uncalled-anywhere endpoint (found via a fresh uncalled-endpoint
// sweep, 2026-08-16) -- BookingWidget below already had to pick a date blind and only
// discover "no open times" after the fact; this is the merchant's real weekly
// open/closed windows so a closed day can be flagged before a customer wastes a pick.
export interface MerchantAvailabilityWindow {
  dayOfWeek: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
  startTime: string;
  endTime: string;
}

export const fetchMerchantAvailability = (merchantId: string) =>
  apiFetch<{ success: boolean; windows: MerchantAvailabilityWindow[] }>(`/api/v1/merchant/${merchantId}/booking-availability`).then(
    (r) => r.windows,
  );

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

// Real, previously-uncalled-anywhere endpoint (same sweep as fetchMerchantAvailability
// above) -- BookingWidget already tells a customer a deposit will be held
// (product.requiresPrepay), but once held there was no way to check its real status
// (still held / released back on completion / refunded on cancel / forfeited on a
// no-show) anywhere in any client. Backend throws a real 404 ("This booking has no
// deposit") for a booking whose service never required prepay -- that 404 is the
// correct, expected shape for most bookings, not an error to surface.
export type BookingDepositStatus = 'HELD' | 'RELEASED' | 'REFUNDED' | 'FORFEITED';

export interface BookingDeposit {
  id: string;
  bookingId: string;
  amount: number;
  fee: number;
  status: BookingDepositStatus;
  createdAt: string;
  updatedAt: string;
}

export const fetchBookingDeposit = (bookingId: string) =>
  apiFetch<{ success: boolean; deposit: BookingDeposit }>(`/api/v1/merchant/bookings/${bookingId}/deposit`).then((r) => r.deposit);

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

// Real pre-booking browsing (item 231, found via a defined-but-uncalled-endpoint
// sweep): getMerchantReviews/getCouponsForCustomer are both real, fully-authorized
// backend endpoints (the owner-side half of coupons has been on merchant-mfe since
// 2026-07-xx) but had zero client callers anywhere -- a buyer choosing whether to book
// with a merchant could never see that merchant's real review history/rating, or
// which of that merchant's real coupons they're eligible for, before requesting a slot.
export const fetchMerchantReviews = (merchantId: string) =>
  apiFetch<{ success: boolean; reviews: MerchantBookingReview[]; rating: { average: number | null; count: number } }>(
    `/api/v1/merchant/${merchantId}/reviews?size=20`,
  );

export interface MerchantCoupon {
  id: string;
  merchantId: string;
  title: string;
  description: string | null;
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  discountValue: number;
  regularsOnly: boolean;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
}

export const fetchCouponsForCustomer = (merchantId: string) =>
  apiFetch<{ success: boolean; coupons: MerchantCoupon[] }>(`/api/v1/merchant/${merchantId}/coupons`).then((r) => r.coupons);
