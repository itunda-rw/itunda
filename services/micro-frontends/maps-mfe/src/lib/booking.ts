import { apiFetch } from './api';

// Real Naver Smart Place/Kakao Hair Shop/Karrot Business-Profile-style local business
// appointment booking (see backend MerchantBookingService's own doc comment) -- a
// bookable service is a MerchantProduct with durationMinutes set, no second catalog.
//
// Ported here from bank-mfe's own lib/booking.ts (2026-08-25, direct user feedback:
// "booking... that's features that supposed to be in itunda place not in itunda
// shopping") -- these are the same real backend endpoints, just called from itunda
// Place (this micro-frontend) instead of Shop now. See MapsBooking.tsx's own doc
// comment for the full account.
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

export const fetchMerchantReviews = (merchantId: string) =>
  apiFetch<{ success: boolean; reviews: MerchantBookingReview[]; rating: { average: number | null; count: number } }>(
    `/api/v1/merchant/${merchantId}/reviews?size=20`,
  );

// Real "My reviews" parity gap, closing the last open item from
// project_itunda_uncalled_method_sweep_2026_09_04 -- Android's MyTab.kt already shows
// this inline; bank-mfe/iOS had neither a fetch nor a section (iOS's own copy landed in
// MyBookingReviewsSection.swift). Distinct from fetchMerchantReviews above (a specific
// merchant's public reviews) -- this is the caller's own review history across every
// merchant they've reviewed. Rendered via MyBookingReviewsCard in MapsBooking.tsx (not
// bank-mfe's MyView.tsx) since this whole booking-review domain already lives in itunda
// Place -- bank-mfe can't import this module's own lib/booking.ts directly per
// .dependency-cruiser.cjs's cross-MFE-src-import ban, and duplicating the fetch into a
// second copy would repeat the exact "defined but never rendered" mistake this sweep
// just found once already (bank-mfe's own lib/maps.ts location-share duplicate).
export const fetchMyBookingReviews = () =>
  apiFetch<{ success: boolean; reviews: MerchantBookingReview[] }>('/api/v1/merchant/reviews/my-reviews').then((r) => r.reviews);

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

// Real bookable-service listing for a merchant -- the itunda Place-side entry point
// (a product with a real durationMinutes set), reusing the same real merchant-products
// endpoint Shop's own catalog fetch already calls.
export interface BookableService {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  durationMinutes: number | null;
  requiresPrepay?: boolean;
}

export const fetchBookableServices = (merchantId: string) =>
  apiFetch<{ success: boolean; products: BookableService[] }>(`/api/v1/shopping/merchants/${merchantId}/products`).then((r) =>
    r.products.filter((p) => p.durationMinutes != null),
  );
