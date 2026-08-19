import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import {
  bookBusSeats, cancelBusBooking, fetchBusTripBookings, fetchMyBusBookings, fetchMyBusTrips, postBusTrip, searchBusTrips,
  type BusBooking, type BusTrip,
} from './lib/bus';

// Extracted from BankDashboard.tsx (2026-08-10) into its own lazy-loaded chunk --
// see InsuranceView.tsx's own doc comment for the full account of why. Self-contained:
// no shared state or helper components with any other screen.
export default function BusView() {
  const [subTab, setSubTab] = useState<'RIDE' | 'OPERATE'>('RIDE');

  // Rider side
  const [searchOrigin, setSearchOrigin] = useState('');
  const [searchDestination, setSearchDestination] = useState('');
  const [trips, setTrips] = useState<BusTrip[] | null>(null);
  const [myBookings, setMyBookings] = useState<BusBooking[] | null>(null);
  const [seatCounts, setSeatCounts] = useState<Record<string, string>>({});
  const [riderError, setRiderError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);
  const [busyBookingId, setBusyBookingId] = useState<string | null>(null);

  const loadTrips = () => {
    searchBusTrips(searchOrigin.trim() || undefined, searchDestination.trim() || undefined)
      .then(setTrips)
      .catch((err) => setRiderError(err instanceof ApiError ? err.message : 'Could not search trips.'));
  };

  const loadMyBookings = () => {
    fetchMyBusBookings().then(setMyBookings).catch(() => {});
  };

  useEffect(() => {
    if (subTab !== 'RIDE') return;
    loadTrips();
    loadMyBookings();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  const handleBookSeats = (tripId: string) => {
    const seatCount = Number(seatCounts[tripId] || '1');
    if (!Number.isFinite(seatCount) || seatCount < 1) return;
    setBusyTripId(tripId);
    setRiderError(null);
    bookBusSeats(tripId, seatCount)
      .then(() => { loadTrips(); loadMyBookings(); setBusyTripId(null); })
      .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not book these seats.'); setBusyTripId(null); });
  };

  const handleCancelBooking = (bookingId: string) => {
    setBusyBookingId(bookingId);
    setRiderError(null);
    cancelBusBooking(bookingId)
      .then(() => { loadMyBookings(); setBusyBookingId(null); })
      .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not cancel this booking.'); setBusyBookingId(null); });
  };

  // Operator side
  const [myTrips, setMyTrips] = useState<BusTrip[] | null>(null);
  const [tripOrigin, setTripOrigin] = useState('');
  const [tripDestination, setTripDestination] = useState('');
  const [tripDeparture, setTripDeparture] = useState('');
  const [tripSeats, setTripSeats] = useState('');
  const [tripFare, setTripFare] = useState('');
  const [posting, setPosting] = useState(false);
  const [operatorError, setOperatorError] = useState<string | null>(null);

  const loadMyTrips = () => {
    fetchMyBusTrips().then(setMyTrips).catch((err) => setOperatorError(err instanceof ApiError ? err.message : 'Could not load your trips.'));
  };

  // Real trip manifest -- see fetchBusTripBookings's own doc comment. Previously a
  // real, tested backend endpoint (GET /bus/trips/{id}/bookings) with zero client
  // anywhere on any platform: an operator could post a route and see the seat
  // countdown, but never who actually booked. Lazily loaded per trip, not prefetched
  // for every route at once.
  const [expandedTripId, setExpandedTripId] = useState<string | null>(null);
  const [tripBookings, setTripBookings] = useState<BusBooking[] | null>(null);
  const [manifestError, setManifestError] = useState<string | null>(null);

  const toggleManifest = (tripId: string) => {
    if (expandedTripId === tripId) {
      setExpandedTripId(null);
      return;
    }
    setExpandedTripId(tripId);
    setTripBookings(null);
    setManifestError(null);
    fetchBusTripBookings(tripId)
      .then(setTripBookings)
      .catch((err) => setManifestError(err instanceof ApiError ? err.message : 'Could not load bookings for this route.'));
  };

  useEffect(() => {
    if (subTab === 'OPERATE') loadMyTrips();
  }, [subTab]);

  const handlePostTrip = () => {
    const totalSeats = Number(tripSeats);
    const farePerSeat = Number(tripFare);
    if (!tripOrigin.trim() || !tripDestination.trim() || !tripDeparture || !Number.isFinite(totalSeats) || totalSeats <= 0 || !Number.isFinite(farePerSeat) || farePerSeat <= 0) {
      return;
    }
    setPosting(true);
    setOperatorError(null);
    postBusTrip(tripOrigin.trim(), tripDestination.trim(), new Date(tripDeparture).toISOString(), totalSeats, farePerSeat)
      .then(() => {
        setTripOrigin(''); setTripDestination(''); setTripDeparture(''); setTripSeats(''); setTripFare('');
        loadMyTrips();
        setPosting(false);
      })
      .catch((err) => { setOperatorError(err instanceof ApiError ? err.message : 'Could not post this trip.'); setPosting(false); });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RIDE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('RIDE')} style={{ flex: 1 }}
        >
          Find a bus
        </button>
        <button
          className={subTab === 'OPERATE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('OPERATE')} style={{ flex: 1 }}
        >
          My routes
        </button>
      </div>

      {subTab === 'RIDE' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {riderError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{riderError}</p>}
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Search routes</p>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
              <input
                type="text" value={searchOrigin} placeholder="From (e.g. Kigali)" onChange={(e) => setSearchOrigin(e.target.value)}
                style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <input
                type="text" value={searchDestination} placeholder="To (e.g. Musanze)" onChange={(e) => setSearchDestination(e.target.value)}
                style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
            </div>
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={loadTrips}>Search</button>
          </div>
          {trips === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
          ) : trips.length === 0 ? (
            <EmptyState message="No upcoming trips — request a ride and it'll show up here." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {trips.map((trip) => (
                <div key={trip.id} className="itunda-card">
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.origin} → {trip.destination}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                    {new Date(trip.departureTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                    {' · '}{trip.farePerSeat.toLocaleString()} RWF/seat · {trip.availableSeats} seat(s) left
                  </p>
                  <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                    <input
                      type="number" min={1} max={trip.availableSeats} value={seatCounts[trip.id] ?? '1'}
                      onChange={(e) => setSeatCounts((prev) => ({ ...prev, [trip.id]: e.target.value }))}
                      style={{ width: '60px', padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                    />
                    <button
                      className="itunda-btn itunda-btn-primary" disabled={busyTripId === trip.id} style={{ flex: 1 }}
                      onClick={() => handleBookSeats(trip.id)}
                    >
                      {busyTripId === trip.id ? '…' : 'Book seats'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
          {myBookings && myBookings.filter((b) => b.status === 'BOOKED').length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your bookings</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myBookings.filter((b) => b.status === 'BOOKED').map((b) => (
                  <div key={b.id} className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{b.seatCount} seat(s)</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{b.totalFare.toLocaleString()} RWF</p>
                    </div>
                    <button className="itunda-btn itunda-btn-secondary" disabled={busyBookingId === b.id} onClick={() => handleCancelBooking(b.id)}>
                      {busyBookingId === b.id ? '…' : 'Cancel'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OPERATE' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {operatorError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{operatorError}</p>}
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Post a route</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
              Any itunda user can post a scheduled trip -- no transport-licensing check.
            </p>
            <input
              type="text" value={tripOrigin} placeholder="Origin" onChange={(e) => setTripOrigin(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
            />
            <input
              type="text" value={tripDestination} placeholder="Destination" onChange={(e) => setTripDestination(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
            />
            <input
              type="datetime-local" value={tripDeparture} onChange={(e) => setTripDeparture(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
            />
            <input
              type="number" value={tripSeats} placeholder="Total seats" onChange={(e) => setTripSeats(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
            />
            <input
              type="number" value={tripFare} placeholder="Fare per seat (RWF)" onChange={(e) => setTripFare(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
            />
            <button
              className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
              disabled={posting || !tripOrigin.trim() || !tripDestination.trim() || !tripDeparture || !tripSeats || !tripFare}
              onClick={handlePostTrip}
            >
              {posting ? 'Posting…' : 'Post route'}
            </button>
          </div>
          {myTrips && myTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your routes</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myTrips.map((trip) => (
                  <div key={trip.id} className="itunda-card">
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.origin} → {trip.destination}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                      {new Date(trip.departureTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      {' · '}{trip.availableSeats}/{trip.totalSeats} seats left · {trip.farePerSeat.toLocaleString()} RWF/seat
                    </p>
                    <button
                      className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}
                      onClick={() => toggleManifest(trip.id)}
                    >
                      {expandedTripId === trip.id ? 'Hide bookings' : 'View bookings'}
                    </button>
                    {expandedTripId === trip.id && (
                      <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
                        {manifestError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{manifestError}</p>}
                        {!manifestError && tripBookings === null && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>}
                        {tripBookings !== null && tripBookings.length === 0 && (
                          <EmptyState message="No one's booked a seat yet — share your route to fill it up." />
                        )}
                        {tripBookings !== null && tripBookings.length > 0 && (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {tripBookings.map((b) => (
                              <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)' }}>
                                <span style={{ color: 'var(--itunda-grey-700)' }}>
                                  Rider #{b.riderUserId.slice(-6)} · {b.seatCount} seat{b.seatCount > 1 ? 's' : ''}
                                </span>
                                <span style={{ fontWeight: 700, color: b.status === 'CANCELLED' ? 'var(--itunda-grey-400)' : 'var(--itunda-grey-900)' }}>
                                  {b.status === 'CANCELLED' ? 'Cancelled' : `${b.totalFare.toLocaleString()} RWF`}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
