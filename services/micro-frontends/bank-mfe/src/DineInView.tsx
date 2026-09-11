import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { IconBack, IconShieldCheck } from './icons/ItundaIcons';
import { EmptyState, ErrorCard } from './EmptyState';
import type { ShoppingMerchant } from './lib/shopping';
import { fetchMenu, fetchRestaurants, type MenuItem } from './lib/eats';
import {
  advanceDineInOrderStatus, cancelDineInOrder, fetchMyDineInOrders, fetchRestaurantDineInOrders, placeDineInOrder,
  type DineInOrder, type DineInOrderStatus,
} from './lib/dineIn';
import { nextInChain, eatsCartKey, eatsOptionsSummary, eatsLineUnitPrice, type EatsCartLine } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';
import { SwipeToConfirmButton } from './MerchantBillingAndCart';

const DINE_IN_STATUS_LABEL: Record<DineInOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted by restaurant',
  PREPARING: 'Preparing',
  SERVED: 'Served',
  CANCELLED: 'Cancelled — refunded',
};
const DINE_IN_STATUS_CHAIN: DineInOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'SERVED'];

function DineInOrderCard({ order, action }: { order: DineInOrder; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{DINE_IN_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Table {order.tableNumber}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString('en-US')} RWF</span>
      </div>
      {order.notes && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.notes}
        </p>
      )}
      {action}
    </div>
  );
}

export function DineInRestaurantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);
  const showOrdersSkeleton = useDeferredLoading(orders === null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  // Real pagination fix (2026-09-11): page 0 is polled every 4s for real-time
  // order-status accuracy, so it must always stay a live, page-0-only fetch.
  // olderOrders is a separate accumulator populated only by loadMoreOrders,
  // never touched by the poll -- same design as the Commerce/Eats order-
  // history fixes.
  const [olderOrders, setOlderOrders] = useState<DineInOrder[]>([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasMore, setOrdersHasMore] = useState(false);
  const [loadingMoreOrders, setLoadingMoreOrders] = useState(false);

  const load = () => {
    fetchRestaurantDineInOrders(0)
      .then((r) => { setOrders(r.orders); setOrdersHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  const loadMoreOrders = () => {
    const nextPage = ordersPage + 1;
    setLoadingMoreOrders(true);
    fetchRestaurantDineInOrders(nextPage)
      .then((r) => {
        setOlderOrders((prev) => [...prev, ...r.orders]);
        setOrdersPage(nextPage);
        setOrdersHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreOrders(false));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: DineInOrder) => {
    const next = nextInChain(DINE_IN_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceDineInOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleCancel = async (order: DineInOrder) => {
    setBusyOrderId(order.id);
    setError(null);
    try {
      await cancelDineInOrder(order.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return showOrdersSkeleton ? <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  const allOrders = [...orders, ...olderOrders];
  if (allOrders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Table orders for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {allOrders.map((o) => {
          const next = nextInChain(DINE_IN_STATUS_CHAIN, o.status);
          return (
            <DineInOrderCard
              key={o.id}
              order={o}
              action={
                (next || o.status === 'PLACED') && (
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {next && (
                      <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                        {busyOrderId === o.id ? 'Updating…' : `Mark ${DINE_IN_STATUS_LABEL[next].toLowerCase()}`}
                      </button>
                    )}
                    {o.status === 'PLACED' && (
                      <button className="itunda-btn itunda-btn-secondary" disabled={busyOrderId === o.id} onClick={() => handleCancel(o)}>
                        Cancel
                      </button>
                    )}
                  </div>
                )
              }
            />
          );
        })}
        {ordersHasMore && (
          <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreOrders} onClick={loadMoreOrders}>
            {loadingMoreOrders ? 'Loading…' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  );
}

function DineInMenuView({ restaurant, onBack, onOrderPlaced }: { restaurant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: DineInOrder) => void }) {
  const { t } = useI18n();
  const [menu, setMenu] = useState<{ businessName: string; products: MenuItem[] } | null>(null);
  const showMenuSkeleton = useDeferredLoading(menu === null);
  // Real fix (2026-09-04, same fix as MenuView.tsx's identical bug): this used to be
  // one shared `error` state for both the initial menu fetch AND checkout submission,
  // so a real order-placement failure replaced the entire checkout screen with a
  // full-screen ErrorCard whose "Retry" just reloads the menu -- discarding the
  // buyer's cart/table-number/notes and forcing them to rebuild checkout from
  // scratch, a real dead end (feedback_toss_error_handling memory's standing
  // instruction). `loadError` covers only the fetch failure (full-screen is correct
  // there); `error` covers only checkout submission and renders inline within the
  // still-visible checkout form.
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cart, setCart] = useState<Record<string, EatsCartLine>>({});
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  const [pendingChoices, setPendingChoices] = useState<Record<string, string>>({});
  const [tableNumber, setTableNumber] = useState('');
  const [notes, setNotes] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);

  useEffect(() => {
    fetchMenu(restaurant.merchantId)
      .then((r) => setMenu({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, line]) => line.quantity > 0);
  const cartCount = cartItems.reduce((sum, [, line]) => sum + line.quantity, 0);

  const setSimpleQty = (productId: string, qty: number) => {
    const key = eatsCartKey(productId, []);
    setCart((c) => ({ ...c, [key]: { productId, quantity: Math.max(0, qty), choiceIds: [] } }));
  };

  const toggleExpand = (productId: string) => {
    setPendingChoices({});
    setExpandedProductId((current) => (current === productId ? null : productId));
  };

  const addConfiguredToCart = (item: MenuItem) => {
    const groups = item.optionGroups ?? [];
    const choiceIds = groups.map((g) => pendingChoices[g.id]).filter((id): id is string => Boolean(id));
    if (choiceIds.length !== groups.length) return;
    const key = eatsCartKey(item.id, choiceIds);
    setCart((c) => ({ ...c, [key]: { productId: item.id, quantity: (c[key]?.quantity ?? 0) + 1, choiceIds } }));
    setPendingChoices({});
    setExpandedProductId(null);
  };

  const handlePlaceOrder = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setPlacing(true);
    setError(null);
    try {
      const items = cartItems.map(([, line]) => ({
        menuItemId: line.productId, quantity: line.quantity,
        selectedChoiceIds: line.choiceIds.length ? line.choiceIds : undefined,
      }));
      const result = await placeDineInOrder(restaurant.merchantId, tableNumber.trim(), items, notes.trim() || undefined);
      onOrderPlaced(result.order);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setPlacing(false);
    }
  };

  if (loadError) {
    return (
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{loadError}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }

  if (menu === null) {
    return showMenuSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to menu">
            <IconBack size={20} />
          </button>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-14-size)' }}>
                <span>{item.name}{eatsOptionsSummary(item, line.choiceIds)} x{line.quantity}</span>
                <span>{(unitPrice * line.quantity).toLocaleString('en-US')} RWF</span>
              </div>
            );
          })}
          <input
            type="text"
            value={tableNumber}
            onChange={(e) => setTableNumber(e.target.value)}
            placeholder="Table number (e.g. 12, Patio 3)"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value.slice(0, 500))}
            placeholder="Notes (optional) -- e.g. No onions"
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'none', fontFamily: 'inherit' }}
          />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          {/* Real Toss "밀어서 결제하기" (swipe to pay) -- see MenuView.tsx's identical
              fix for the full account; reused here for consistency across every real
              "place a paid order" flow. */}
          <SwipeToConfirmButton
            label="Swipe to place order"
            busyLabel="Placing order…"
            enabled={!placing && !!tableNumber.trim()}
            busy={placing}
            onConfirm={() => handlePlaceOrder()}
          />
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to restaurants">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      {menu.products.length === 0 ? (
        <EmptyState message="This restaurant hasn't added menu items yet — check back soon." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: cartCount > 0 ? '80px' : 0 }}>
          {menu.products.map((item) => {
            const groups = item.optionGroups ?? [];
            const hasOptions = groups.length > 0;
            const simpleKey = eatsCartKey(item.id, []);
            const simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0);
            const isExpanded = expandedProductId === item.id;
            const allGroupsChosen = groups.every((g) => Boolean(pendingChoices[g.id]));
            return (
              <div key={item.id} style={{ padding: '12px 0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{item.name}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{item.price.toLocaleString('en-US')} RWF</p>
                  </div>
                  {hasOptions ? (
                    <button className="itunda-btn itunda-btn-secondary" onClick={() => toggleExpand(item.id)}>
                      {isExpanded ? 'Close' : 'Add'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {isExpanded && (
                  <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {groups.map((group) => (
                      <div key={group.id}>
                        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-700)', marginBottom: '4px' }}>{group.name}</p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          {group.choices.map((choice) => (
                            <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                              <input
                                type="radio"
                                name={`group-${group.id}`}
                                checked={pendingChoices[group.id] === choice.id}
                                onChange={() => setPendingChoices((p) => ({ ...p, [group.id]: choice.id }))}
                              />
                              {choice.name}{choice.priceDelta ? ` (+${choice.priceDelta.toLocaleString('en-US')} RWF)` : ''}
                            </label>
                          ))}
                        </div>
                      </div>
                    ))}
                    <button
                      className="itunda-btn itunda-btn-primary"
                      disabled={!allGroupsChosen}
                      onClick={() => addConfiguredToCart(item)}
                    >
                      Add to order
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      {cartCount > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCheckout(true)}
        >
          Review order ({cartCount} item{cartCount === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

export function DineInCustomerView() {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<DineInOrder | null>(null);
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasMore, setOrdersHasMore] = useState(false);
  const [loadingMoreOrders, setLoadingMoreOrders] = useState(false);

  useEffect(() => {
    fetchRestaurants().then(setRestaurants).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  useEffect(() => {
    if (view === 'ORDERS') {
      fetchMyDineInOrders(0)
        .then((r) => { setOrders(r.orders); setOrdersPage(0); setOrdersHasMore(r.page + 1 < r.totalPages); })
        .catch(() => setOrders([]));
    }
  }, [view]);

  const loadMoreOrders = () => {
    const nextPage = ordersPage + 1;
    setLoadingMoreOrders(true);
    fetchMyDineInOrders(nextPage)
      .then((r) => {
        setOrders((prev) => [...(prev ?? []), ...r.orders]);
        setOrdersPage(nextPage);
        setOrdersHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreOrders(false));
  };

  if (confirmed) {
    // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this IS the
    // whole confirmation screen's content (docs/UI_UX_GUIDELINES.md §10).
    return (
      <div style={{ textAlign: 'center', padding: '28px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString('en-US')} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>Table {confirmed.tableNumber}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}>Done</button>
      </div>
    );
  }

  if (selected) {
    return <DineInMenuView restaurant={selected} onBack={() => setSelected(null)} onOrderPlaced={setConfirmed} />;
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : 'My orders'}
          </button>
        ))}
      </div>
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card throughout --
          a flat entity list (matching Android's VehicleValuationScreen/
          GroupAccountScreen precedent), no per-row divider (docs/UI_UX_GUIDELINES.md §10). */}
      {view === 'BROWSE' ? (
        error ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        ) : restaurants === null ? (
          <div className="itunda-flat-section skeleton" style={{ height: '160px' }} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {restaurants.map((r) => (
              <button key={r.merchantId} onClick={() => setSelected(r)} style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.businessName}</p>
                {r.category && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.category}</p>}
              </button>
            ))}
          </div>
        )
      ) : orders === null ? (
        <div className="itunda-flat-section skeleton" style={{ height: '160px' }} />
      ) : orders.length === 0 ? (
        <EmptyState message="No table orders yet — they'll show up here as diners order from their table." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {orders.map((o) => <DineInOrderCard key={o.id} order={o} />)}
          {ordersHasMore && (
            <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreOrders} onClick={loadMoreOrders}>
              {loadingMoreOrders ? 'Loading…' : 'Load more'}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
