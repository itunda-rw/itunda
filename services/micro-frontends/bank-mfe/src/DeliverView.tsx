import { useState, useEffect } from 'react';
import { Bike } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import type { EatsOrder } from './lib/eats';
import {
  advanceRiderOrder, claimDelivery, fetchAvailableDeliveries, fetchMyRiderProfile, fetchRiderDeliveries, registerRider, setRiderAvailability,
  type Rider,
} from './lib/eatsRider';
import { EatsOrderCard, EATS_STATUS_LABEL, RIDER_STATUS_CHAIN } from './EatsOrderCard';
import { nextInChain } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function DeliverView() {
  const { t } = useI18n();
  const [rider, setRider] = useState<Rider | null | undefined>(undefined);
  const showRiderSkeleton = useDeferredLoading(rider === undefined);
  const [error, setError] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);
  const [available, setAvailable] = useState<EatsOrder[] | null>(null);
  const showAvailableSkeleton = useDeferredLoading(available === null);
  const [mine, setMine] = useState<EatsOrder[] | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  // Real pagination fix (2026-09-11): both lists are polled every 4s, so page
  // 0 must always stay a live, page-0-only fetch. olderAvailable/olderMine
  // are separate accumulators populated only by their own "Load more"
  // action, never touched by the poll -- same design as the Ride/Commerce/
  // Eats order-history fixes.
  const [olderAvailable, setOlderAvailable] = useState<EatsOrder[]>([]);
  const [availablePage, setAvailablePage] = useState(0);
  const [availableHasMore, setAvailableHasMore] = useState(false);
  const [loadingMoreAvailable, setLoadingMoreAvailable] = useState(false);
  const [olderMine, setOlderMine] = useState<EatsOrder[]>([]);
  const [minePage, setMinePage] = useState(0);
  const [mineHasMore, setMineHasMore] = useState(false);
  const [loadingMoreMine, setLoadingMoreMine] = useState(false);

  const loadRider = () => {
    setError(null);
    fetchMyRiderProfile()
      .then(setRider)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDER_NOT_REGISTERED') {
          setRider(null);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(loadRider, []);

  const loadDeliveries = () => {
    Promise.all([fetchAvailableDeliveries(0), fetchRiderDeliveries(0)])
      .then(([a, m]) => {
        setAvailable(a.orders);
        setAvailableHasMore(a.page + 1 < a.totalPages);
        setMine(m.orders);
        setMineHasMore(m.page + 1 < m.totalPages);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const loadMoreAvailable = () => {
    const nextPage = availablePage + 1;
    setLoadingMoreAvailable(true);
    fetchAvailableDeliveries(nextPage)
      .then((r) => {
        setOlderAvailable((prev) => [...prev, ...r.orders]);
        setAvailablePage(nextPage);
        setAvailableHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreAvailable(false));
  };

  const loadMoreMine = () => {
    const nextPage = minePage + 1;
    setLoadingMoreMine(true);
    fetchRiderDeliveries(nextPage)
      .then((r) => {
        setOlderMine((prev) => [...prev, ...r.orders]);
        setMinePage(nextPage);
        setMineHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreMine(false));
  };

  useEffect(() => {
    if (!rider) return;
    loadDeliveries();
    const interval = setInterval(loadDeliveries, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rider?.id]);

  const handleRegister = async () => {
    setRegistering(true);
    setError(null);
    try {
      setRider(await registerRider());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!rider) return;
    try {
      setRider(await setRiderAvailability(!rider.available));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleClaim = async (orderId: string) => {
    setBusyOrderId(orderId);
    setError(null);
    try {
      await claimDelivery(orderId);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RIDER_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRiderOrder(order.id, next);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (rider === undefined) return showRiderSkeleton ? <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  if (rider === null) {
    return (
      <div style={{ textAlign: 'center', padding: '28px 0' }}>
        <Bike size={32} color="var(--itunda-indigo)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '6px' }}>Deliver with Itunda</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          Earn a real delivery fee for every order you deliver, paid straight to your account.
        </p>
        <button className="itunda-btn itunda-btn-primary" onClick={handleRegister} disabled={registering}>
          {registering ? 'Registering…' : 'Become a rider'}
        </button>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const allAvailable = [...(available ?? []), ...olderAvailable];
  const allMine = [...(mine ?? []), ...olderMine];
  const activeDeliveries = allMine.filter((o) => o.status !== 'DELIVERED');
  const pastDeliveries = allMine.filter((o) => o.status === 'DELIVERED');

  return (
    <div>
      <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{rider.available ? "You're online" : "You're offline"}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{rider.available ? 'Visible for new deliveries' : 'Go online to see deliveries'}</p>
        </div>
        <button className={rider.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
          {rider.available ? 'Go offline' : 'Go online'}
        </button>
      </div>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}

      {activeDeliveries.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active deliveries</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {activeDeliveries.map((o) => {
              const next = nextInChain(RIDER_STATUS_CHAIN, o.status);
              return (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={next && (
                    <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                      {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                    </button>
                  )}
                />
              );
            })}
          </div>
        </div>
      )}

      {rider.available && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Available deliveries</h4>
          {available === null ? (
            showAvailableSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
          ) : allAvailable.length === 0 ? (
            <EmptyState message="No deliveries waiting right now — stay online and you'll be notified." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {allAvailable.map((o) => (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={
                    <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleClaim(o.id)}>
                      {busyOrderId === o.id ? 'Claiming…' : 'Claim delivery'}
                    </button>
                  }
                />
              ))}
              {availableHasMore && (
                <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreAvailable} onClick={loadMoreAvailable}>
                  {loadingMoreAvailable ? 'Loading…' : 'Load more'}
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {pastDeliveries.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastDeliveries.map((o) => <EatsOrderCard key={o.id} order={o} />)}
            {mineHasMore && (
              <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreMine} onClick={loadMoreMine}>
                {loadingMoreMine ? 'Loading…' : 'Load more'}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
