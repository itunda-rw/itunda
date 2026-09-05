// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread, ARCHITECTURE_GUIDELINES.md §2's "code that changes together
// lives together" rule) -- the Rides sub-cluster of small, single-purpose cards
// used only by RidesView (trip card, driver rating, post-trip review/tip prompts,
// trusted contacts), which itself stays in BankDashboard.tsx pending a full Rides
// domain split. Moved together since RIDE_STATUS_LABEL/these components have no
// callers outside RidesView.

import { useEffect, useState } from 'react';
import { IconShieldCheck } from './icons/ItundaIcons';
import { ClockGlyph } from './icons/ItundaFaceMisc';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import {
  addTrustedContact, fetchDriverRating, fetchDriverReviews, fetchTrustedContacts, removeTrustedContact,
  submitRideReview, tipDriver,
  type RideDriverRating, type RideTrip, type RideTripReview, type RideTripStop, type RideTrustedContact,
} from './lib/rideshare';

const RIDE_STATUS_LABEL: Record<RideTrip['status'], string> = {
  REQUESTED: 'Finding a driver…',
  DRIVER_ASSIGNED: 'Driver assigned',
  IN_PROGRESS: 'Trip in progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

// Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10): dropped
// itunda-card. Deliberately NOT using .itunda-flat-section's per-row divider here --
// this shared component is reused across 5 different contexts in RidesView (a lone
// active trip, an active-trips list, an available-to-accept list, and 2 genuine
// past-trip history lists), and a divider baked into the component would be wrong
// for the non-list/non-history cases. Plain flat spacing instead, safe everywhere
// it's used.
export function RideTripCard({ trip, action, stops }: { trip: RideTrip; action?: React.ReactNode; stops?: RideTripStop[] | null }) {
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.pickupAddress}</p>
          {stops && stops.length > 0 && stops.map((s) => (
            <p key={s.id} style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: s.arrivedAt ? 'var(--itunda-grey-400)' : 'var(--itunda-grey-700)', margin: '1px 0' }}>
              {s.arrivedAt ? '✓' : '→'} {s.address}
            </p>
          ))}
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString('en-US')} RWF</p>
          {trip.scheduledFor && (
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-indigo)', fontWeight: 700, marginTop: '2px' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><ClockGlyph size={16} /> Scheduled for {new Date(trip.scheduledFor).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
            </p>
          )}
        </div>
        <span style={{
          fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? 'var(--itunda-red)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)',
          backgroundColor: trip.status === 'CANCELLED' ? 'var(--itunda-red-light)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-100)' : '#E8F0FE',
        }}>
          {trip.status === 'REQUESTED' && trip.scheduledFor ? 'Scheduled' : RIDE_STATUS_LABEL[trip.status]}
        </span>
      </div>
      {action}
    </div>
  );
}

// Real "meet your driver" rating + reviews during an active trip (item 233) -- see
// lib/rideshare.ts's own doc comment on fetchDriverReviews for the full sourced
// account. Honest v1: no driver name/vehicle shown, since RideDriver carries neither.
export function DriverRatingSection({ driverId }: { driverId: string }) {
  const [rating, setRating] = useState<RideDriverRating | null>(null);
  const [reviews, setReviews] = useState<RideTripReview[] | null>(null);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    fetchDriverRating(driverId).then(setRating).catch(() => {});
  }, [driverId]);

  if (!rating || rating.count === 0) return null;

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <button
        onClick={() => {
          setExpanded((e) => !e);
          if (!expanded && reviews === null) fetchDriverReviews(driverId).then(setReviews).catch(() => setReviews([]));
        }}
        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: '#FFC107' }}
      >
        ★ {rating.average?.toFixed(1)} <span style={{ color: 'var(--itunda-grey-500)', fontWeight: 400 }}>({rating.count} rating{rating.count === 1 ? '' : 's'}) {expanded ? '▲' : '▼'}</span>
      </button>
      {expanded && (
        reviews === null ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>Loading reviews…</p>
        ) : reviews.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>No written reviews yet.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
            {reviews.map((r) => (
              <div key={r.id} style={{ padding: '8px 10px', borderRadius: '8px', background: 'var(--itunda-grey-100)' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700 }}>{'⭐'.repeat(r.rating)}</p>
                {r.comment && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{r.comment}</p>}
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}

// Real Kakao T-style post-trip driver rating (item 213) -- one real review per real
// trip, rating the driver who completed it. See lib/rideshare.ts's own doc comment.
export function RideReviewPrompt({ tripId, onSubmitted }: { tripId: string; onSubmitted: () => void }) {
  const { t } = useI18n();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (rating === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitRideReview(tripId, rating, comment || undefined);
      onSubmitted();
    } catch (err) {
      // Real 409 (RIDE_TRIP_ALREADY_REVIEWED) means this trip was already rated in an
      // earlier session -- hide the prompt rather than surfacing a confusing error.
      if (err instanceof ApiError && err.code === 'RIDE_TRIP_ALREADY_REVIEWED') onSubmitted();
      else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Rate your driver</p>
      <div style={{ display: 'flex', gap: '4px', marginBottom: '6px' }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n} type="button" onClick={() => setRating(n)}
            style={{ fontSize: 'var(--itunda-type-scale-20-size)', color: n <= rating ? '#FFC107' : 'var(--itunda-grey-300)' }}
          >
            ★
          </button>
        ))}
      </div>
      {rating > 0 && (
        <>
          <input
            type="text" value={comment} placeholder="Leave a comment (optional)" onChange={(e) => setComment(e.target.value)}
            style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', marginBottom: '6px' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={submitting} onClick={handleSubmit} style={{ width: '100%', padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {submitting ? 'Submitting…' : 'Submit rating'}
          </button>
        </>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

const TIP_PRESETS = [500, 1000, 2000];

// Real Uber post-trip tipping -- see lib/rideshare.ts's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built on the backend with zero client
// anywhere. Same real device step-up pattern every other money-moving action in this
// file already needs (tip is a real account-to-account transfer, gated by
// DeviceVerificationFilter same as TransferFlow/DelayedTransfersCard).
export function TipDriverPrompt({ tripId, onTipped }: { tripId: string; onTipped: () => void }) {
  const { t } = useI18n();
  const [amount, setAmount] = useState<number | null>(null);
  const [customAmount, setCustomAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleSubmit = async (overrideAmount?: number) => {
    const finalAmount = overrideAmount ?? amount ?? Number(customAmount);
    if (!(finalAmount > 0)) return;
    setSubmitting(true);
    setError(null);
    try {
      await tipDriver(tripId, finalAmount);
      onTipped();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (needsDeviceVerification) {
    return (
      <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <DeviceStepUpPrompt onVerified={() => { setNeedsDeviceVerification(false); handleSubmit(); }} onCancel={() => setNeedsDeviceVerification(false)} />
      </div>
    );
  }

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Tip your driver</p>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '6px' }}>
        {TIP_PRESETS.map((preset) => (
          <button
            key={preset} type="button" disabled={submitting}
            onClick={() => { setAmount(preset); setCustomAmount(''); handleSubmit(preset); }}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              border: '1px solid var(--itunda-grey-200)', background: amount === preset ? 'var(--itunda-indigo)' : 'transparent',
              color: amount === preset ? 'white' : 'var(--itunda-grey-700)',
            }}
          >
            {preset.toLocaleString('en-US')}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: '6px' }}>
        <input
          type="number" placeholder="Custom amount (RWF)" value={customAmount}
          onChange={(e) => { setCustomAmount(e.target.value); setAmount(null); }}
          style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
        />
        <button
          className="itunda-btn itunda-btn-primary" disabled={submitting || !(Number(customAmount) > 0)}
          onClick={() => handleSubmit()} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 14px' }}
        >
          {submitting ? '…' : 'Send'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Uber Safety "Trusted Contacts" (help.uber.com) -- a persistent contact list set
// up once, distinct from the per-trip "Share trip status" pick above. Found while
// triaging the uncalled-endpoint sweep: RideController already shipped a complete,
// tested list/add/remove implementation (RideTrustedContactService) with zero client
// callers on any platform. This is bank-mfe's first UI for it.
export function TrustedContactsSection() {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<RideTrustedContact[] | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => { fetchTrustedContacts().then(setContacts).catch(() => setContacts([])); };
  useEffect(load, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !phone.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await addTrustedContact(phone.trim(), name.trim());
      setName('');
      setPhone('');
      setShowAdd(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRemove = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await removeTrustedContact(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ marginBottom: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
          <IconShieldCheck size={16} color="var(--itunda-green)" /> Trusted contacts
        </h3>
        {(contacts?.length ?? 0) < 5 && (
          <button style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }} onClick={() => setShowAdd((v) => !v)}>
            {showAdd ? 'Cancel' : '+ Add'}
          </button>
        )}
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Up to 5 people who can get your live ride status in one tap, every trip.
      </p>
      {showAdd && (
        <form onSubmit={handleAdd} style={{ marginBottom: '10px' }}>
          <input
            type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Name"
            style={{ width: '100%', padding: '8px', marginBottom: '6px', border: '1px solid var(--itunda-grey-200)', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone number"
            style={{ width: '100%', padding: '8px', marginBottom: '6px', border: '1px solid var(--itunda-grey-200)', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ width: '100%', padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {busy ? 'Adding…' : 'Add trusted contact'}
          </button>
        </form>
      )}
      {contacts === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : contacts.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>No trusted contacts yet.</p>
      ) : (
        contacts.map((c) => (
          <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{c.contactName}</span>
            <button
              onClick={() => handleRemove(c.id)} disabled={busyId === c.id}
              style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-red)' }}
            >
              {busyId === c.id ? '…' : 'Remove'}
            </button>
          </div>
        ))
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
    </div>
  );
}
