import { useEffect, useState } from 'react';
import { EmptyState } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  acceptInspection, cancelInspection, completeInspection, fetchAvailableMechanics, fetchMyInspectionBookings, fetchMyMechanicBookings,
  fetchMyMechanicProfile, registerAsMechanic, requestInspection, setMechanicAvailability,
  type VehicleInspectionBooking, type VehicleInspectionMechanic,
} from './lib/vehicleInspection';
import { useDeferredLoading } from './useDeferredLoading';

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// lib/vehicleInspection.ts's own doc comment for the full sourced account. A buyer
// books and 100%-prepays a real mechanic to inspect a real used-car listing before
// purchase; a mechanic can register, browse incoming bookings, and deliver findings.
export function VehicleInspectionsView() {
  const { t } = useI18n();
  const [tab, setTab] = useState<'BUYER' | 'MECHANIC'>('BUYER');

  // Buyer side
  const [mechanics, setMechanics] = useState<VehicleInspectionMechanic[] | null>(null);
  const [myBookings, setMyBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const showMyBookingsSkeleton = useDeferredLoading(myBookings === null);
  const [listingId, setListingId] = useState('');
  const [mechanicId, setMechanicId] = useState('');
  const [fee, setFee] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [buyerError, setBuyerError] = useState<string | null>(null);
  const [busyBookingId, setBusyBookingId] = useState<string | null>(null);

  const loadBuyerData = () => {
    Promise.all([fetchAvailableMechanics(), fetchMyInspectionBookings()])
      .then(([m, b]) => { setMechanics(m); setMyBookings(b); })
      .catch((err) => setBuyerError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (tab === 'BUYER') loadBuyerData();
  }, [tab]);

  const handleRequest = async () => {
    const numericFee = Number(fee);
    if (!listingId.trim() || !mechanicId || !numericFee || numericFee <= 0 || !scheduledAt) {
      setBuyerError('Fill in the listing id, a mechanic, a valid fee, and a scheduled time.');
      return;
    }
    setRequesting(true);
    setBuyerError(null);
    try {
      await requestInspection(listingId.trim(), mechanicId, numericFee, new Date(scheduledAt).toISOString());
      setListingId('');
      setMechanicId('');
      setFee('');
      setScheduledAt('');
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancel = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await cancelInspection(bookingId);
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  // Mechanic side
  const [mechanicProfile, setMechanicProfile] = useState<VehicleInspectionMechanic | null | undefined>(undefined);
  const showMechanicProfileSkeleton = useDeferredLoading(mechanicProfile === undefined);
  const [businessName, setBusinessName] = useState('');
  const [registering, setRegistering] = useState(false);
  const [mechanicBookings, setMechanicBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const showMechanicBookingsSkeleton = useDeferredLoading(mechanicBookings === null);
  const [mechanicError, setMechanicError] = useState<string | null>(null);
  const [findings, setFindings] = useState<Record<string, string>>({});

  const loadMechanicData = () => {
    fetchMyMechanicProfile()
      .then((m) => {
        setMechanicProfile(m);
        if (m) fetchMyMechanicBookings().then(setMechanicBookings).catch(() => {});
      })
      .catch((err) => setMechanicError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (tab === 'MECHANIC') loadMechanicData();
  }, [tab]);

  const handleRegister = async () => {
    if (!businessName.trim()) return;
    setRegistering(true);
    setMechanicError(null);
    try {
      setMechanicProfile(await registerAsMechanic(businessName.trim()));
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!mechanicProfile) return;
    try {
      setMechanicProfile(await setMechanicAvailability(!mechanicProfile.available));
    } catch {
      // Real, non-critical -- an availability toggle failure isn't worth a hard error.
    }
  };

  const handleAccept = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await acceptInspection(bookingId);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  const handleComplete = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await completeInspection(bookingId, findings[bookingId]);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyBookingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BUYER', 'MECHANIC'] as const).map((t) => (
          <button
            key={t} onClick={() => setTab(t)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: tab === t ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: tab === t ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {t === 'BUYER' ? 'Get a car inspected' : 'Mechanic'}
          </button>
        ))}
      </div>

      {tab === 'BUYER' ? (
        <div>
          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              form section on this tab. */}
          <div style={{ padding: '10px 0' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Book an inspection</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
              Pay a local mechanic to inspect a used car before you buy it -- held until they deliver their findings.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <input
                type="text" value={listingId} onChange={(e) => setListingId(e.target.value)} placeholder="Listing ID"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <select
                value={mechanicId} onChange={(e) => setMechanicId(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              >
                <option value="">Choose a mechanic</option>
                {(mechanics ?? []).map((m) => <option key={m.id} value={m.id}>{m.businessName}</option>)}
              </select>
              <input
                type="number" value={fee} onChange={(e) => setFee(e.target.value)} placeholder="Inspection fee (RWF)"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <input
                type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <button className="itunda-btn itunda-btn-primary" disabled={requesting} onClick={handleRequest}>
                {requesting ? 'Booking…' : 'Book & pay'}
              </button>
            </div>
            {buyerError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{buyerError}</p>}
          </div>

          {myBookings === null ? (
            showMyBookingsSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
          ) : myBookings.length === 0 ? (
            <EmptyState message="No inspections booked yet — book one to get a real used car checked before you buy." />
          ) : (
            // Real fix (2026-08-24, flat-design sweep): history log of booked
            // inspections, kept the per-row divider convention.
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {myBookings.map((b) => (
                <div key={b.id} className="itunda-flat-section">
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.findings && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '6px' }}>{b.findings}</p>}
                  {(b.status === 'REQUESTED' || b.status === 'ACCEPTED') && (
                    <button
                      className="itunda-btn itunda-btn-danger" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id}
                      onClick={() => handleCancel(b.id)}
                    >
                      {busyBookingId === b.id ? 'Cancelling…' : 'Cancel'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : mechanicProfile === undefined ? (
        showMechanicProfileSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : mechanicProfile === null ? (
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
        // form section shown in this state.
        <div style={{ padding: '10px 0' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Become an inspection mechanic</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
            Get booked and paid to inspect used cars for real buyers before they purchase.
          </p>
          <input
            type="text" value={businessName} onChange={(e) => setBusinessName(e.target.value)} placeholder="Business name"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={registering} onClick={handleRegister}>
            {registering ? 'Registering…' : 'Register'}
          </button>
          {mechanicError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{mechanicError}</p>}
        </div>
      ) : (
        // Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
        // together -- reused .itunda-flat-section for the section-boundary divider.
        <div>
          <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{mechanicProfile.businessName}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{mechanicProfile.available ? 'Visible for new bookings' : 'Not accepting bookings'}</p>
            </div>
            <button className={mechanicProfile.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
              {mechanicProfile.available ? 'Go unavailable' : 'Go available'}
            </button>
          </div>
          {mechanicError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{mechanicError}</p>}
          {mechanicBookings === null ? (
            showMechanicBookingsSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
          ) : mechanicBookings.length === 0 ? (
            <EmptyState message="No bookings yet — they'll show up here once a buyer books an inspection." />
          ) : (
            // Real fix (2026-08-24, flat-design sweep): history log of mechanic
            // bookings, kept the per-row divider convention.
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {mechanicBookings.map((b) => (
                <div key={b.id} className="itunda-flat-section">
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.status === 'REQUESTED' && (
                    <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id} onClick={() => handleAccept(b.id)}>
                      {busyBookingId === b.id ? 'Accepting…' : 'Accept'}
                    </button>
                  )}
                  {b.status === 'ACCEPTED' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                      <textarea
                        value={findings[b.id] ?? ''} onChange={(e) => setFindings((prev) => ({ ...prev, [b.id]: e.target.value }))}
                        placeholder="Inspection findings" rows={2}
                        style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', resize: 'vertical' }}
                      />
                      <button className="itunda-btn itunda-btn-primary" disabled={busyBookingId === b.id} onClick={() => handleComplete(b.id)}>
                        {busyBookingId === b.id ? 'Completing…' : 'Mark complete'}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
