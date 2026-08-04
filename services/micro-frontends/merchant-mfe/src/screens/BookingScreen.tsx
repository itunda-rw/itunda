import { useEffect, useState } from 'react';
import { CalendarClock } from 'lucide-react';
import { ApiError } from '../lib/api';
import { EmptyState, ErrorCard } from '../components/EmptyState';
import {
  completeBooking,
  getMerchantBookings,
  getMyAvailability,
  respondToBooking,
  setAvailability,
  type AvailabilityWindow,
  type MerchantBooking,
} from '../lib/booking';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const DAY_LABEL: Record<string, string> = {
  MONDAY: 'Mon', TUESDAY: 'Tue', WEDNESDAY: 'Wed', THURSDAY: 'Thu', FRIDAY: 'Fri', SATURDAY: 'Sat', SUNDAY: 'Sun',
};

// Real local-business appointment booking, owner side -- see lib/booking.ts's own
// doc comment. Two independent sections: declare a weekly availability schedule
// (top), and confirm/decline/complete incoming requests (below) -- mirrors
// BookingScreen.kt's own account of the backend exactly.
export default function BookingScreen() {
  return (
    <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <AvailabilityEditor />
      <BookingQueue />
    </div>
  );
}

function AvailabilityEditor() {
  const [windows, setWindows] = useState<AvailabilityWindow[] | null>(null);
  const [day, setDay] = useState(DAYS[0]);
  const [start, setStart] = useState('09:00');
  const [end, setEnd] = useState('17:00');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = () => {
    getMyAvailability()
      .then(setWindows)
      .catch(() => setWindows([]));
  };
  useEffect(load, []);

  const save = async (next: AvailabilityWindow[]) => {
    setSaving(true);
    setError(null);
    try {
      setWindows(await setAvailability(next));
    } catch {
      setError("Couldn't save your availability. Try again.");
    } finally {
      setSaving(false);
    }
  };

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    const startMinutes = parseHm(start);
    const endMinutes = parseHm(end);
    if (startMinutes === null || endMinutes === null || startMinutes >= endMinutes) {
      setError('Enter a real start time before the end time (HH:mm).');
      return;
    }
    save([...(windows ?? []), { dayOfWeek: day, startTime: `${start}:00`, endTime: `${end}:00` }]);
  };

  return (
    <div className="toss-card" style={{ padding: '20px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Weekly availability</h3>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Customers can only request an appointment inside these windows.
      </p>

      {windows === null ? (
        <div className="toss-card skeleton" style={{ height: '80px' }} />
      ) : windows.length === 0 ? (
        <EmptyState message="No availability set yet — add a window below." icon={CalendarClock} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '12px' }}>
          {windows.map((w, i) => (
            <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '13px' }}>{DAY_LABEL[w.dayOfWeek] ?? w.dayOfWeek} {w.startTime.slice(0, 5)}-{w.endTime.slice(0, 5)}</span>
              <button
                onClick={() => save(windows.filter((_, j) => j !== i))}
                disabled={saving}
                style={{ fontSize: '13px', fontWeight: 600, color: '#E53935' }}
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleAdd}>
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '10px' }}>
          {DAYS.map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => setDay(d)}
              style={{
                padding: '6px 10px', borderRadius: '8px', fontSize: '12px', fontWeight: 700, whiteSpace: 'nowrap',
                color: day === d ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: day === d ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
              }}
            >
              {DAY_LABEL[d]}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
          <input
            type="time" value={start} onChange={(e) => setStart(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <input
            type="time" value={end} onChange={(e) => setEnd(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
        </div>
        {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '10px' }} role="alert">{error}</p>}
        <button type="submit" className="toss-btn toss-btn-primary" disabled={saving} style={{ width: '100%' }}>
          {saving ? 'Saving…' : 'Add window'}
        </button>
      </form>
    </div>
  );
}

function parseHm(v: string): number | null {
  const parts = v.split(':');
  if (parts.length !== 2) return null;
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  if (Number.isNaN(h) || Number.isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) return null;
  return h * 60 + m;
}

function BookingQueue() {
  const [bookings, setBookings] = useState<MerchantBooking[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = () => {
    getMerchantBookings()
      .then((b) => { setBookings(b); setError(null); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your bookings.'));
  };

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleRespond = async (bookingId: string, confirm: boolean) => {
    setBusyId(bookingId);
    try {
      await respondToBooking(bookingId, confirm);
      refresh();
    } catch {
      setError("Couldn't update this booking.");
    } finally {
      setBusyId(null);
    }
  };

  const handleComplete = async (bookingId: string) => {
    setBusyId(bookingId);
    try {
      await completeBooking(bookingId);
      refresh();
    } catch {
      setError("Couldn't update this booking.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '10px' }}>Bookings</h3>
      {error && <ErrorCard message={error} onRetry={refresh} />}
      {bookings === null ? (
        <div className="toss-card skeleton" style={{ height: '120px' }} />
      ) : (
        (() => {
          const active = bookings.filter((b) => b.status === 'REQUESTED' || b.status === 'CONFIRMED');
          if (active.length === 0) {
            return <EmptyState message="No open bookings right now." icon={CalendarClock} />;
          }
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {active.map((booking) => (
                <div key={booking.id} className="toss-card" style={{ padding: '16px 18px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                    <BookingStatusBadge status={booking.status} />
                    <span style={{ fontWeight: 700, fontSize: '13px' }}>{booking.bookingDate} {booking.startTime.slice(0, 5)}</span>
                  </div>
                  <p style={{ fontSize: '13px' }}>{booking.serviceName}</p>
                  {booking.notes && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Note: {booking.notes}</p>}
                  {booking.status === 'REQUESTED' ? (
                    <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                      <button
                        className="toss-btn toss-btn-primary"
                        onClick={() => handleRespond(booking.id, true)}
                        disabled={busyId === booking.id}
                        style={{ flex: 1 }}
                      >
                        Confirm
                      </button>
                      <button
                        className="toss-btn"
                        onClick={() => handleRespond(booking.id, false)}
                        disabled={busyId === booking.id}
                      >
                        Decline
                      </button>
                    </div>
                  ) : booking.status === 'CONFIRMED' ? (
                    <button
                      className="toss-btn toss-btn-primary"
                      onClick={() => handleComplete(booking.id)}
                      disabled={busyId === booking.id}
                      style={{ width: '100%', marginTop: '10px' }}
                    >
                      {busyId === booking.id ? 'Updating…' : 'Mark completed'}
                    </button>
                  ) : null}
                </div>
              ))}
            </div>
          );
        })()
      )}
    </div>
  );
}

function BookingStatusBadge({ status }: { status: string }) {
  const label = status === 'REQUESTED' ? 'Requested'
    : status === 'CONFIRMED' ? 'Confirmed'
    : status === 'DECLINED' ? 'Declined'
    : status === 'CANCELLED' ? 'Cancelled'
    : status === 'COMPLETED' ? 'Completed'
    : status;
  return (
    <span style={{ fontSize: '11px', fontWeight: 700, padding: '3px 8px', borderRadius: '8px', backgroundColor: 'var(--toss-grey-100)', color: 'var(--toss-grey-700)' }}>
      {label}
    </span>
  );
}
