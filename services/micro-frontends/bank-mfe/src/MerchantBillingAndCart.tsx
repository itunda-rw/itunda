import { useState, useEffect, useRef } from 'react';
import { motion, useAnimation, useMotionValue } from 'framer-motion';
import { itundaSpring } from './lib/motion';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { IconBack, IconChevronRight, IconShieldCheck } from './icons/ItundaIcons';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { fetchMyMapBookmarks, type MapBookmark } from './lib/maps';
import { getStoredReferralCode } from './lib/affiliate';
import { placeOrder, type CommerceOrder } from './lib/commerce';
import {
  cancelBillingSubscription, subscribeToBillingPlan,
  type MerchantBillingPlan, type MerchantBillingSubscription,
} from './lib/shopping';
import { type CommerceCart } from './CommerceOrders';

export function BillingPlanRow({ plan, subscription, onChanged }: { plan: MerchantBillingPlan; subscription?: MerchantBillingSubscription; onChanged: () => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubscribe = async () => {
    setError(null);
    setBusy(true);
    try {
      await subscribeToBillingPlan(plan.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    if (!subscription) return;
    setError(null);
    setBusy(true);
    try {
      await cancelBillingSubscription(subscription.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{plan.name}</p>
          {plan.description && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{plan.description}</p>}
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', marginTop: '2px' }}>
            {plan.amount.toLocaleString()} RWF every {plan.intervalDays} day{plan.intervalDays === 1 ? '' : 's'}
          </p>
        </div>
        <button
          className={subscription ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
          style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}
          disabled={busy}
          onClick={subscription ? handleCancel : handleSubscribe}
        >
          {busy ? '…' : subscription ? 'Cancel' : 'Subscribe'}
        </button>
      </div>
      {subscription && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
          Next charge {new Date(subscription.nextChargeAt).toLocaleDateString()}
        </p>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
    </div>
  );
}

export interface CommerceCheckoutResult {
  merchantId: string;
  businessName: string;
  success: boolean;
  order?: CommerceOrder;
  error?: string;
}

// Real Toss "밀어서 결제하기" (swipe to pay) primitive (2026-08-25, direct user
// screenshot of Toss Shopping's real checkout sheet) -- Toss's own signature payment
// gesture, mirrors Android's SwipeToConfirmButton exactly (see that file's own doc
// comment for the full account: enabled gates dragging, busy freezes mid-swipe with a
// label swap, and a real failed attempt (busy clears without the caller navigating
// away) springs the handle back to the start so the buyer can retry).
export function SwipeToConfirmButton({ label, busyLabel, enabled, busy, onConfirm }: { label: string; busyLabel: string; enabled: boolean; busy: boolean; onConfirm: () => void }) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [trackWidth, setTrackWidth] = useState(0);
  const x = useMotionValue(0);
  const controls = useAnimation();
  const handleSize = 48;

  useEffect(() => {
    if (trackRef.current) setTrackWidth(trackRef.current.offsetWidth);
  }, []);

  const maxOffset = Math.max(0, trackWidth - handleSize - 8);

  useEffect(() => {
    if (!busy && enabled && x.get() > 0) {
      controls.start({ x: 0, transition: { type: 'spring', ...itundaSpring.bounce } });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busy, enabled]);

  return (
    <div
      ref={trackRef}
      style={{
        position: 'relative', width: '100%', height: '56px', borderRadius: '28px',
        background: enabled || busy ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)', overflow: 'hidden',
      }}
    >
      <p style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontWeight: 700, fontSize: 'var(--itunda-type-scale-15-size)', paddingLeft: `${handleSize}px`, margin: 0, pointerEvents: 'none' }}>
        {busy ? busyLabel : label}
      </p>
      <motion.div
        drag={enabled && !busy ? 'x' : false}
        dragConstraints={{ left: 0, right: maxOffset }}
        dragElastic={0}
        dragMomentum={false}
        animate={controls}
        style={{
          x, position: 'absolute', top: '4px', left: '4px', width: `${handleSize}px`, height: `${handleSize}px`,
          borderRadius: '50%', background: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', touchAction: 'none',
        }}
        onDragEnd={() => {
          if (maxOffset > 0 && x.get() >= maxOffset * 0.8) {
            controls.start({ x: maxOffset, transition: { type: 'spring', ...itundaSpring.quick } }).then(() => onConfirm());
          } else {
            controls.start({ x: 0, transition: { type: 'spring', ...itundaSpring.bounce } });
          }
        }}
      >
        <IconChevronRight size={20} color="var(--itunda-indigo)" />
      </motion.div>
    </div>
  );
}

export function MultiCartView({
  cart, onBack, onSetQty, onCheckedOut,
}: {
  cart: CommerceCart;
  onBack: () => void;
  onSetQty: (merchantId: string, productId: string, quantity: number) => void;
  onCheckedOut: (results: CommerceCheckoutResult[]) => void;
}) {
  const { t } = useI18n();
  const [address, setAddress] = useState('');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real device binding step-up (2026-07-21) -- Commerce checkout was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic per-order failure, same fix already applied to
  // Transfer/Savings. Every order in this batch shares the same device/session, so
  // hitting this once means every remaining order would fail identically -- the loop
  // below stops at the first one rather than collecting N duplicate failures.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10): this loop places one real order per merchant sequentially
  // and used to stop dead at the first DEVICE_NOT_VERIFIED, requiring the buyer to
  // resubmit the whole cart by hand -- which, naively retried, would have RE-PLACED
  // every order that already succeeded before the failing one (a real duplicate-order
  // bug, not just friction). These two refs let a retry resume from exactly the
  // merchant that failed, keeping every already-placed order's result instead of
  // restarting the loop from scratch.
  const checkoutResultsRef = useRef<CommerceCheckoutResult[]>([]);
  const checkoutResumeIndexRef = useRef(0);
  // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- same real gap
  // already closed for ride booking and Eats delivery address: itunda's own "map
  // bookmarks" feature was never surfaced in Commerce checkout's own delivery-address
  // field either. Unlike those two, this field has no coordinate-capture at all
  // (placeOrder's own real contract only ever takes a plain deliveryAddress string,
  // no lat/lng) -- a plain tap-to-fill chip row rather than a full search-autocomplete
  // dropdown, since there's no coordinate value a real autocomplete would add here.
  const [bookmarks, setBookmarks] = useState<MapBookmark[]>([]);
  useEffect(() => {
    fetchMyMapBookmarks().then(setBookmarks).catch(() => {});
  }, []);

  const groups = Object.entries(cart).filter(([, g]) => Object.values(g.lines).some((l) => l.quantity > 0));
  const grandTotal = groups.reduce(
    (sum, [, g]) => sum + Object.values(g.lines).reduce((s, l) => s + l.product.price * l.quantity, 0),
    0,
  );

  // Real per-seller order splitting -- each merchant group becomes its own real,
  // independent placeOrder() call (its own Idempotency-Key, its own account-to-account
  // ledger transaction). Sequential, not Promise.all: these are real money-moving
  // calls against the same buyer account, and a clear one-at-a-time result list is
  // more honest than a swallowed Promise.allSettled. A failure on one merchant's
  // order does not block or roll back any other -- exactly how a real multi-seller
  // checkout behaves (each seller is charged/fulfilled independently in real life).
  const handlePlaceOrders = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setPlacing(true);
    setError(null);
    setNeedsDeviceVerification(false);
    for (let i = checkoutResumeIndexRef.current; i < groups.length; i++) {
      const [merchantId, group] = groups[i];
      const items = Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => ({ productId, quantity: l.quantity }));
      try {
        const result = await placeOrder(merchantId, items, address.trim(), getStoredReferralCode());
        checkoutResultsRef.current.push({ merchantId, businessName: group.businessName, success: true, order: result.order });
      } catch (err) {
        if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
          checkoutResumeIndexRef.current = i;
          setNeedsDeviceVerification(true);
          setPlacing(false);
          return;
        }
        checkoutResultsRef.current.push({ merchantId, businessName: group.businessName, success: false, error: err instanceof ApiError ? err.message : t('common.actionError') });
      }
    }
    setPlacing(false);
    onCheckedOut(checkoutResultsRef.current);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to shop">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Your cart</h3>
      </div>
      {groups.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your cart is empty.</p>
      ) : (
        <form onSubmit={handlePlaceOrders} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {groups.map(([merchantId, group]) => (
            <div key={merchantId} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{group.businessName}</p>
              {Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => (
                <div key={productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span>{l.product.name} x{l.quantity}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span>{(l.product.price * l.quantity).toLocaleString()} RWF</span>
                    <button type="button" onClick={() => onSetQty(merchantId, productId, 0)} style={{ color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Remove</button>
                  </div>
                </div>
              ))}
            </div>
          ))}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>
              <span>Total ({groups.length} order{groups.length === 1 ? '' : 's'})</span>
              <span>{grandTotal.toLocaleString()} RWF</span>
            </div>
            <input
              type="text" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Delivery address" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            {bookmarks.length > 0 && (
              <div style={{ display: 'flex', gap: '8px', overflowX: 'auto' }}>
                {bookmarks.map((b) => (
                  <button
                    key={b.id} type="button" onClick={() => setAddress(b.displayName)}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0, padding: '8px 12px',
                      borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                    }}
                  >
                    <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: b.color, flexShrink: 0 }} />
                    {b.displayName}
                  </button>
                ))}
              </div>
            )}
            {needsDeviceVerification ? (
              // Real fix (2026-08-10) -- see checkoutResumeIndexRef's own doc comment.
              // Resumes the remaining orders from where the loop stopped instead of
              // re-placing every already-succeeded one.
              <DeviceStepUpPrompt onVerified={() => handlePlaceOrders()} onCancel={() => setNeedsDeviceVerification(false)} />
            ) : (
              <>
                {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
                {/* Real Toss "밀어서 결제하기" (swipe to pay) (2026-08-25, direct user
                    screenshot) -- replaces the plain submit button with Toss's own
                    signature deliberate-drag payment gesture. See
                    SwipeToConfirmButton's own doc comment. */}
                <SwipeToConfirmButton
                  label={`Swipe to place ${groups.length} order${groups.length === 1 ? '' : 's'}`}
                  busyLabel="Placing orders…"
                  enabled={!placing && !!address.trim()}
                  busy={placing}
                  onConfirm={() => handlePlaceOrders()}
                />
              </>
            )}
          </div>
        </form>
      )}
    </div>
  );
}

export function MultiCartResultsView({ results, onDone }: { results: CommerceCheckoutResult[]; onDone: () => void }) {
  const successCount = results.filter((r) => r.success).length;
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ textAlign: 'center', marginBottom: '20px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>
          {successCount} of {results.length} order{results.length === 1 ? '' : 's'} placed
        </h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        {results.map((r) => (
          <div key={r.merchantId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <span style={{ fontWeight: 600 }}>{r.businessName}</span>
            {r.success ? (
              <span style={{ color: 'var(--itunda-green)' }}>{r.order!.totalAmount.toLocaleString()} RWF — placed</span>
            ) : (
              <span style={{ color: 'var(--itunda-red)' }}>{r.error}</span>
            )}
          </div>
        ))}
      </div>
      <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={onDone}>
        {results.some((r) => !r.success) ? 'Back to cart' : 'Done'}
      </button>
    </div>
  );
}
