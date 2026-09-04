import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import {
  cancelBooking, createBooking, fetchAvailableSlots, fetchBookableServices, fetchBookingDeposit, fetchCouponsForCustomer,
  fetchMerchantAvailability, fetchMerchantReviews, fetchMyBookings, fetchMyBookingReviews, submitBookingReview,
  type BookableService, type BookingDeposit, type BookingDepositStatus, type BookingSlot, type MerchantAvailabilityWindow,
  type MerchantBooking, type MerchantBookingReview, type MerchantCoupon,
} from './lib/booking';

// Real Naver Smart Place/Kakao Hair Shop/Karrot Business-Profile-style local business
// appointment booking (customer side) -- see lib/booking.ts's own doc comment.
//
// Ported here from bank-mfe's own BankDashboard.tsx (2026-08-25, direct user feedback:
// "booking... that's features that supposed to be in itunda place not in itunda
// shopping") -- the original doc comment already named the real sourcing (Naver Smart
// Place/Karrot Business Profile, both real *local-place* products, never a nationwide
// online catalog), so a real-time appointment at a physical location belongs in itunda
// Place, not Shop's "completely online" catalog. The backend
// (MerchantBookingController/MerchantBookingService, real, already vertical-neutral)
// needed no change -- only this client-side UI ownership moved.
const JS_DAY_TO_AVAILABILITY_DAY: MerchantAvailabilityWindow['dayOfWeek'][] = [
  'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY',
];

function StarRatingInput({ value, onChange }: { value: number; onChange: (rating: number) => void }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n} type="button" onClick={() => onChange(n)}
          style={{ display: 'flex', padding: 0, fontSize: '22px', color: n <= value ? '#F5A623' : 'var(--itunda-grey-200)' }}
          aria-label={`${n} star${n === 1 ? '' : 's'}`}
        >
          {n <= value ? '★' : '☆'}
        </button>
      ))}
    </div>
  );
}

function BookingWidget({ merchantId, service, onBooked }: { merchantId: string; service: BookableService; onBooked: () => void }) {
  const [date, setDate] = useState('');
  const [slots, setSlots] = useState<BookingSlot[] | null>(null);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [requesting, setRequesting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [requested, setRequested] = useState(false);
  const [openDays, setOpenDays] = useState<Set<MerchantAvailabilityWindow['dayOfWeek']> | null>(null);

  useEffect(() => {
    fetchMerchantAvailability(merchantId)
      .then((windows) => setOpenDays(new Set(windows.map((w) => w.dayOfWeek))))
      .catch(() => setOpenDays(null));
  }, [merchantId]);

  const isClosedOn = (d: string) => {
    if (!openDays || openDays.size === 0) return false;
    const day = JS_DAY_TO_AVAILABILITY_DAY[new Date(`${d}T00:00:00`).getDay()];
    return !openDays.has(day);
  };

  const loadSlots = (d: string) => {
    setDate(d);
    setSlots(null);
    setSlotsError(null);
    if (!d) return;
    if (isClosedOn(d)) {
      setSlots([]);
      return;
    }
    fetchAvailableSlots(merchantId, service.id, d)
      .then(setSlots)
      .catch((err) => setSlotsError(err instanceof ApiError ? err.message : 'Could not load available times.'));
  };

  const book = async (slot: BookingSlot) => {
    setRequesting(slot.startTime);
    setError(null);
    try {
      await createBooking(merchantId, service.id, date, slot.startTime);
      setRequested(true);
      onBooked();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setRequesting(null);
    }
  };

  if (requested) {
    return (
      <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--itunda-grey-100)' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Booking requested</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>The business will confirm or decline your appointment. See it under your bookings below.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--itunda-grey-100)', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Book {service.name} ({service.durationMinutes} min)</p>
      {service.requiresPrepay && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
          Requesting this slot holds a {service.price.toLocaleString()} RWF deposit from your account.
        </p>
      )}
      <input
        type="date"
        value={date}
        min={new Date().toISOString().slice(0, 10)}
        onChange={(e) => loadSlots(e.target.value)}
        style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {slotsError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>{slotsError}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>{error}</p>}
      {date && slots !== null && (
        slots.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {isClosedOn(date) ? 'Closed on this day — try another date.' : 'No open times on this date — try another day.'}
          </p>
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {slots.map((slot) => (
              <button
                key={slot.startTime}
                className="itunda-btn itunda-btn-secondary"
                style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                onClick={() => book(slot)}
                disabled={requesting !== null}
              >
                {requesting === slot.startTime ? '…' : slot.startTime.slice(0, 5)}
              </button>
            ))}
          </div>
        )
      )}
    </div>
  );
}

// Real "Book an appointment" entry point for a selected place's real bookable services
// -- the itunda Place-side counterpart to what used to be Shop's per-product
// BookingWidget. Self-sufficient: fetches its own data off the merchant id.
export function MerchantBookableServicesSection({ merchantId }: { merchantId: string }) {
  const [services, setServices] = useState<BookableService[] | null>(null);
  const [activeService, setActiveService] = useState<BookableService | null>(null);

  useEffect(() => {
    fetchBookableServices(merchantId).then(setServices).catch(() => setServices([]));
  }, [merchantId]);

  if (!services || services.length === 0) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Book an appointment</p>
      <MerchantBookingInfoSection merchantId={merchantId} />
      {services.map((service) =>
        activeService?.id === service.id ? (
          <BookingWidget key={service.id} merchantId={merchantId} service={service} onBooked={() => {}} />
        ) : (
          <button
            key={service.id}
            className="itunda-btn itunda-btn-secondary"
            style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px' }}
            onClick={() => setActiveService(service)}
          >
            <span>{service.name} · {service.durationMinutes} min</span>
            <span>{service.price.toLocaleString()} RWF</span>
          </button>
        ),
      )}
    </div>
  );
}

// Real pre-booking browsing (item 231) -- see lib/booking.ts's own doc comment for the
// full sourced account.
function MerchantBookingInfoSection({ merchantId }: { merchantId: string }) {
  const [reviews, setReviews] = useState<MerchantBookingReview[] | null>(null);
  const [rating, setRating] = useState<{ average: number | null; count: number } | null>(null);
  const [coupons, setCoupons] = useState<MerchantCoupon[] | null>(null);

  useEffect(() => {
    fetchMerchantReviews(merchantId)
      .then((r) => {
        setReviews(r.reviews);
        setRating(r.rating);
      })
      .catch(() => {
        setReviews([]);
        setRating(null);
      });
    fetchCouponsForCustomer(merchantId).then(setCoupons).catch(() => setCoupons([]));
  }, [merchantId]);

  if ((reviews === null || reviews.length === 0) && (coupons === null || coupons.length === 0)) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {coupons !== null && coupons.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Coupons for you</p>
          {coupons.map((c) => (
            <div key={c.id} style={{ padding: '10px 12px', borderRadius: '10px', background: 'var(--itunda-indigo-50, #EAF2FF)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{c.title}</p>
                {c.description && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{c.description}</p>}
              </div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
                {c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString()} RWF off`}
              </p>
            </div>
          ))}
        </div>
      )}
      {reviews !== null && reviews.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
            Reviews{rating?.average != null && ` · ⭐ ${rating.average.toFixed(1)} (${rating.count})`}
          </p>
          {reviews.slice(0, 3).map((r) => (
            <div key={r.id} style={{ padding: '10px 12px', borderRadius: '10px', background: 'var(--itunda-grey-100)' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{'⭐'.repeat(r.rating)} · {r.serviceName}</p>
              {r.comment && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{r.comment}</p>}
              {r.ownerReply && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>↳ {r.ownerReply}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const DEPOSIT_STATUS_LABEL: Record<BookingDepositStatus, string> = {
  HELD: 'Deposit held', RELEASED: 'Deposit released', REFUNDED: 'Deposit refunded', FORFEITED: 'Deposit forfeited',
};
function BookingDepositBadge({ bookingId }: { bookingId: string }) {
  const [deposit, setDeposit] = useState<BookingDeposit | null>(null);
  useEffect(() => {
    fetchBookingDeposit(bookingId).then(setDeposit).catch(() => setDeposit(null));
  }, [bookingId]);
  if (!deposit) return null;
  return (
    <span style={{ fontSize: '10px', fontWeight: 700, padding: '3px 6px', borderRadius: '999px', background: 'var(--itunda-grey-100)', color: 'var(--itunda-grey-700)' }}>
      {DEPOSIT_STATUS_LABEL[deposit.status]} · {deposit.amount.toLocaleString()} RWF
    </span>
  );
}

// Real customer-side post-appointment review (item 143) -- see lib/booking.ts's own doc
// comment. A real 409 BOOKING_ALREADY_REVIEWED is treated as already-done, not an error.
function BookingReviewButton({ booking }: { booking: MerchantBooking }) {
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) {
      setError('Pick a star rating.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitBookingReview(booking.id, rating, comment);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'BOOKING_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Thanks for your review!</span>;
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => setOpen(true)}>
        Rate this visit
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
      <StarRatingInput value={rating} onChange={setRating} />
      <input
        type="text"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="How was it? (optional)"
        style={{ width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

// Real customer-side view of merchant bookings requested via BookingWidget above.
// Cancel is the only customer action here (confirm/decline/complete are owner-side,
// already real on merchant-mfe).
// Real "My reviews" parity gap, closing the last open item from
// project_itunda_uncalled_method_sweep_2026_09_04 -- Android's MyTab.kt already shows
// this inline; bank-mfe/iOS had neither a fetch nor a section (fixed on iOS/bank-mfe's
// scam-reports section separately, but booking reviews belong here instead: bank-mfe
// can't import this module's own lib/booking.ts directly, per
// .dependency-cruiser.cjs's cross-MFE-src-import ban, and this whole booking-review
// domain already lives in itunda Place per this file's own "moved here from
// bank-mfe... booking... supposed to be in itunda place not in itunda shopping"
// doc comment above). Distinct from fetchMerchantReviews above (a specific merchant's
// public reviews) -- this is the caller's own review history across every merchant
// they've reviewed.
export function MyBookingReviewsCard() {
  const [reviews, setReviews] = useState<MerchantBookingReview[] | null>(null);

  useEffect(() => {
    fetchMyBookingReviews().then(setReviews).catch(() => setReviews([]));
  }, []);

  if (!reviews || reviews.length === 0) return null;

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My reviews</h3>
      {reviews.map((r) => (
        <div key={r.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{'⭐'.repeat(r.rating)} · {r.serviceName}</p>
          {r.comment && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{r.comment}</p>}
          {r.ownerReply && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>↳ {r.ownerReply}</p>}
        </div>
      ))}
    </div>
  );
}

export function MyBookingsCard() {
  const [bookings, setBookings] = useState<MerchantBooking[] | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);

  const load = () => {
    fetchMyBookings().then(setBookings).catch(() => setBookings([]));
  };
  useEffect(load, []);

  if (!bookings || bookings.length === 0) return null;

  const cancel = async (id: string) => {
    setCancelling(id);
    try {
      await cancelBooking(id);
      load();
    } catch {
      // Real, non-critical -- a failed cancel just leaves the booking as-is; the user
      // can retry.
    } finally {
      setCancelling(null);
    }
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My bookings</h3>
      {bookings.slice(0, 5).map((b) => (
        <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', fontSize: 'var(--itunda-type-scale-13-size)', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontWeight: 600 }}>{b.serviceName}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{b.bookingDate} · {b.startTime.slice(0, 5)} · {b.status}</p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <BookingDepositBadge bookingId={b.id} />
            {(b.status === 'REQUESTED' || b.status === 'CONFIRMED') && (
              <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => cancel(b.id)} disabled={cancelling === b.id}>
                {cancelling === b.id ? '…' : 'Cancel'}
              </button>
            )}
            {b.status === 'COMPLETED' && <BookingReviewButton booking={b} />}
          </div>
        </div>
      ))}
    </div>
  );
}
