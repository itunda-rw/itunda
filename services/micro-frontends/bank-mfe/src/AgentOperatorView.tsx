import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { EmptyState } from './EmptyState';
import { ApiError } from './lib/api';
import { QrScanCamera } from './QrScanCamera';
import {
  fetchAgentTill, fetchAgentActivity, agentCashIn, agentCashOut, submitAgentTillCount, setAgentLocation,
  isNotAgentOperatorError, type AgentTillSnapshot, type AgentActivityItem,
} from './lib/agentOperator';
import {
  postFloatListing, fetchNearbyFloatListings, fetchMyFloatListings, cancelFloatListing,
  requestFloat, fetchMyFloatRequests, fetchIncomingFloatRequests, acceptFloatRequest, declineFloatRequest,
  type NearbyFloatListing, type FloatListing, type FloatTransferRequest,
} from './lib/floatMarketplace';
import { useDeferredLoading } from './useDeferredLoading';

// Real fix (2026-08-26): split out of BankDashboard.tsx once that file grew past
// its file-size-lint baseline. The real Itunda cash-agent operator console + its
// own real float marketplace section are fully self-contained -- own data
// fetching, only ever rendered from the AGENT tab -- matching this codebase's own
// established pattern of splitting standalone views into their own file.

// Real Itunda cash-agent operator console -- see lib/agentOperator.ts's own doc
// comment. A regular account only sees this view usefully once admin-assigned as an
// operator (AgentAdminController.assignOperator); an unassigned account gets a clean
// "you are not an agent operator" state instead of a generic error.
export function AgentOperatorView() {
  const { t } = useI18n();
  const [section, setSection] = useState<'till' | 'float'>('till');
  const [till, setTill] = useState<AgentTillSnapshot | null>(null);
  const showSkeleton = useDeferredLoading(!till);
  const [activity, setActivity] = useState<AgentActivityItem[]>([]);
  const [notOperator, setNotOperator] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [cashInAccount, setCashInAccount] = useState('');
  const [cashInAmount, setCashInAmount] = useState('');
  const [cashInReceipt, setCashInReceipt] = useState('');
  const [cashOutAccount, setCashOutAccount] = useState('');
  const [cashOutAmount, setCashOutAmount] = useState('');
  const [cashOutReceipt, setCashOutReceipt] = useState('');
  const [cashOutCode, setCashOutCode] = useState('');
  const [cashOutScanUnavailable, setCashOutScanUnavailable] = useState(false);
  const [countedCash, setCountedCash] = useState('');

  const load = () => {
    setError(null);
    fetchAgentTill()
      .then((t) => { setTill(t); setNotOperator(false); })
      .catch((err) => {
        if (isNotAgentOperatorError(err)) { setNotOperator(true); return; }
        setError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
    fetchAgentActivity().then(setActivity).catch(() => setActivity([]));
  };
  useEffect(load, []);

  // Real gap closed 2026-09-07 (Agents product-completeness pass): Android's
  // AgentOperatorScreen.kt has had "Report my location" since the 2026-08-16
  // uncalled-endpoint sweep found setLocationForOperator with zero real caller;
  // bank-mfe never did, so a web-only agent's till never shows up on the
  // customer-facing "nearby agents" map.
  const [reportingLocation, setReportingLocation] = useState(false);
  const handleReportLocation = () => {
    if (!navigator.geolocation) {
      setError('Location is not available in this browser.');
      return;
    }
    setMessage(null);
    setError(null);
    setReportingLocation(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setAgentLocation(position.coords.latitude, position.coords.longitude)
          .then(() => setMessage('Your location has been updated.'))
          .catch((err) => setError(err instanceof ApiError ? err.message : t('common.actionError')))
          .finally(() => setReportingLocation(false));
      },
      () => {
        setError('Could not get your current location.');
        setReportingLocation(false);
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleCashIn = async () => {
    const amount = Number(cashInAmount);
    if (!cashInAccount.trim() || !cashInReceipt.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real account number, receipt number, and a positive amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await agentCashIn(cashInAccount.trim(), amount, cashInReceipt.trim());
      setMessage(`Cash in accepted — new customer balance ${result.newBalance.toLocaleString('en-US')} RWF`);
      setCashInAccount(''); setCashInAmount(''); setCashInReceipt('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCashOut = async () => {
    const amount = Number(cashOutAmount);
    if (!cashOutAccount.trim() || !cashOutReceipt.trim() || !cashOutCode.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real account number, receipt number, withdrawal code, and a positive amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await agentCashOut(cashOutAccount.trim(), amount, cashOutReceipt.trim(), cashOutCode.trim());
      setMessage(`Cash out paid — new customer balance ${result.newBalance.toLocaleString('en-US')} RWF`);
      setCashOutAccount(''); setCashOutAmount(''); setCashOutReceipt(''); setCashOutCode('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitTillCount = async () => {
    const counted = Number(countedCash);
    if (!Number.isFinite(counted) || counted < 0) {
      setError('Enter a real counted-cash amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const reconciliation = await submitAgentTillCount(counted);
      setMessage(`Till count submitted — variance ${reconciliation.variance.toLocaleString('en-US')} RWF (${reconciliation.status})`);
      setCountedCash('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (notOperator) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          You are not assigned as an Itunda agent till operator. Ask an Itunda staff admin to assign your account to a store.
        </p>
      </div>
    );
  }

  if (!till) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : (showSkeleton ? <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button className={section === 'till' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'} onClick={() => setSection('till')} style={{ flex: 1 }}>Till</button>
        <button className={section === 'float' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'} onClick={() => setSection('float')} style={{ flex: 1 }}>Float marketplace</button>
      </div>
      {section === 'float' ? <FloatMarketplaceSection /> : (
      <>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}
      {/* Real fix (2026-08-24, flat-design sweep): 5 distinct non-exclusive
          sections shown together -- reused .itunda-flat-section for its
          section-boundary divider, :last-child auto-drops the trailing one
          (docs/UI_UX_GUIDELINES.md §10, docs/DESIGN_REFERENCES.md §274). */}
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{till.agentName}</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{till.expectedCash.toLocaleString('en-US')} RWF expected in till</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Today: {till.todayCashIn.toLocaleString('en-US')} RWF in · {till.todayCashOut.toLocaleString('en-US')} RWF out
        </p>
        {till.reconciliation && (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            Last count: {till.reconciliation.countedCash.toLocaleString('en-US')} RWF ({till.reconciliation.status}, variance {till.reconciliation.variance.toLocaleString('en-US')})
          </p>
        )}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Store location</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
          Real customers use "nearby agents" to find your store — keep your location current.
        </p>
        <button className="itunda-btn itunda-btn-primary" disabled={reportingLocation} onClick={handleReportLocation}>
          {reportingLocation ? 'Getting your location…' : 'Report my location'}
        </button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Accept cash-in</h3>
        <input type="text" placeholder="Customer account number" value={cashInAccount} onChange={(e) => setCashInAccount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <input type="number" placeholder="Amount (RWF)" value={cashInAmount} onChange={(e) => setCashInAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <input type="text" placeholder="Receipt number" value={cashInReceipt} onChange={(e) => setCashInReceipt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleCashIn}>{busy ? 'Working…' : 'Accept cash-in'}</button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Pay cash-out</h3>
        <input type="text" placeholder="Customer account number" value={cashOutAccount} onChange={(e) => setCashOutAccount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <input type="number" placeholder="Amount (RWF)" value={cashOutAmount} onChange={(e) => setCashOutAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <input type="text" placeholder="Receipt number" value={cashOutReceipt} onChange={(e) => setCashOutReceipt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        {/* Real fix (2026-08-30, no-manual-code-UX sweep) -- was 100% typed entry
            with no scan option, unlike TransitCollectScreen.tsx's own identical
            "agent scans a customer's code" flow. Scan-first, manual fallback only
            when the camera is unavailable -- same convention, never a default
            "type this code" box. */}
        {!cashOutScanUnavailable && !cashOutCode ? (
          <div style={{ marginBottom: '8px' }}>
            <QrScanCamera onDetect={setCashOutCode} onUnavailable={() => setCashOutScanUnavailable(true)} />
          </div>
        ) : (
          <input type="text" placeholder="Customer's withdrawal code" value={cashOutCode} onChange={(e) => setCashOutCode(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        )}
        <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleCashOut}>{busy ? 'Working…' : 'Pay cash-out'}</button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Submit today's till count</h3>
        <input type="number" placeholder="Counted cash (RWF)" value={countedCash} onChange={(e) => setCountedCash(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSubmitTillCount}>{busy ? 'Working…' : 'Submit count'}</button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Recent activity</h3>
        {activity.length === 0 && <EmptyState message="No cash movements yet today — your cash-in/cash-out activity will show up here." />}
        {activity.map((a) => (
          <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <span>{a.type === 'CASH_IN' ? '↓ Cash in' : '↑ Cash out'} · {a.receiptNumber}</span>
            <span style={{ fontWeight: 600 }}>{a.amount.toLocaleString('en-US')} RWF</span>
          </div>
        ))}
      </div>
      </>
      )}
    </div>
  );
}

// Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- the fifth
// feature in this codebase not sourced from Toss/당근/Coupang/Naver/Kakao. See
// lib/floatMarketplace.ts's own doc comment for the full sourced account.
function FloatMarketplaceSection() {
  const { t } = useI18n();
  const [nearby, setNearby] = useState<NearbyFloatListing[]>([]);
  const [myListings, setMyListings] = useState<FloatListing[]>([]);
  const [myRequests, setMyRequests] = useState<FloatTransferRequest[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<FloatTransferRequest[]>([]);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [listAmount, setListAmount] = useState('');
  const [requestAmounts, setRequestAmounts] = useState<Record<string, string>>({});

  const loadMine = () => {
    fetchMyFloatListings().then(setMyListings).catch(() => setMyListings([]));
    fetchMyFloatRequests().then(setMyRequests).catch(() => setMyRequests([]));
    fetchIncomingFloatRequests().then(setIncomingRequests).catch(() => setIncomingRequests([]));
  };
  useEffect(loadMine, []);

  const handleFindNearby = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        fetchNearbyFloatListings(position.coords.latitude, position.coords.longitude)
          .then(setNearby)
          .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')))
          .finally(() => setLocating(false));
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handlePostListing = async () => {
    const amount = Number(listAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real positive amount of float to offer.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await postFloatListing(amount);
      setMessage('Listing posted -- other nearby agents can now request this float.');
      setListAmount('');
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancelListing = async (listingId: string) => {
    setBusy(true);
    setError(null);
    try {
      await cancelFloatListing(listingId);
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRequestFloat = async (listingId: string, remainingAmount: number) => {
    const raw = requestAmounts[listingId];
    const amount = Number(raw);
    if (!Number.isFinite(amount) || amount <= 0 || amount > remainingAmount) {
      setError(`Enter a real amount up to the ${remainingAmount.toLocaleString('en-US')} RWF still available on this listing.`);
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await requestFloat(listingId, amount);
      setMessage('Request sent -- the listing owner will accept or decline it.');
      setRequestAmounts((prev) => ({ ...prev, [listingId]: '' }));
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleAcceptRequest = async (requestId: string) => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await acceptFloatRequest(requestId);
      setMessage('Float transferred to the requesting agent.');
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleDeclineRequest = async (requestId: string) => {
    setBusy(true);
    setError(null);
    try {
      await declineFloatRequest(requestId);
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10): 5
  // distinct non-exclusive sections shown together -- reused .itunda-flat-section
  // for section-boundary dividers between them.
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Offer surplus float</h3>
        <input type="number" placeholder="Amount to offer (RWF)" value={listAmount} onChange={(e) => setListAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', marginBottom: '8px' }} />
        <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handlePostListing}>{busy ? 'Working…' : 'Post listing'}</button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Nearby agents with float to spare</h3>
        <button className="itunda-btn itunda-btn-secondary" disabled={locating} onClick={handleFindNearby} style={{ marginBottom: '8px' }}>
          {locating ? 'Finding…' : 'Find nearby listings'}
        </button>
        {/* Real copy-voice fix (item 244, round 6 of the empty-state pass): points
            back to the real "Find nearby listings" button right above. */}
        {nearby.length === 0 && <EmptyState message='No nearby listings loaded yet — tap "Find nearby listings" above to search.' />}
        {nearby.map((n) => (
          <div key={n.listing.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <span>{n.agentDisplayName} · {n.distanceKm.toFixed(1)} km</span>
              <span style={{ fontWeight: 600 }}>{n.remainingAmount.toLocaleString('en-US')} RWF available</span>
            </div>
            <div style={{ display: 'flex', gap: '8px', marginTop: '6px' }}>
              <input type="number" placeholder="Amount to request" value={requestAmounts[n.listing.id] || ''}
                onChange={(e) => setRequestAmounts((prev) => ({ ...prev, [n.listing.id]: e.target.value }))}
                style={{ padding: '8px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', flex: 1 }} />
              <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => handleRequestFloat(n.listing.id, n.remainingAmount)}>Request</button>
            </div>
          </div>
        ))}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My listings</h3>
        {myListings.length === 0 && <EmptyState message="No float listings yet — post one to let nearby agents claim your spare cash." />}
        {myListings.map((l) => (
          <div key={l.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <span>{l.amount.toLocaleString('en-US')} RWF offered · {l.claimedAmount.toLocaleString('en-US')} claimed · {l.status}</span>
            {l.status === 'OPEN' && <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => handleCancelListing(l.id)}>Cancel</button>}
          </div>
        ))}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Requests against my listings</h3>
        {incomingRequests.length === 0 && <EmptyState message="No requests yet — they'll show up here once another agent claims from your listing." />}
        {incomingRequests.map((r) => (
          <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <span>{r.amount.toLocaleString('en-US')} RWF · {r.status}</span>
            {r.status === 'REQUESTED' && (
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={() => handleAcceptRequest(r.id)}>Accept</button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => handleDeclineRequest(r.id)}>Decline</button>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My requests</h3>
        {myRequests.length === 0 && <EmptyState message="No requests yet — claim from a nearby listing above and it'll show up here." />}
        {myRequests.map((r) => (
          <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <span>{r.amount.toLocaleString('en-US')} RWF</span>
            <span style={{ fontWeight: 600 }}>{r.status}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
