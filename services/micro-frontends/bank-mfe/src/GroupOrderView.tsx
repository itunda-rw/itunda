import { useState, useEffect } from 'react';
import QRCode from 'qrcode';
import { useI18n } from './i18n/I18nContext';
import { getStoredUser, ApiError } from './lib/api';
import { LinkGlyph } from './icons/ItundaFaceMisc';
import { QrScanCamera, parseQrParam } from './QrScanCamera';
import type { ShoppingMerchant } from './lib/shopping';
import { fetchRestaurants, fetchMenu, type MenuItem, type EatsOrder } from './lib/eats';
import {
  cancelGroupEatsOrder, createGroupEatsOrder, fetchGroupEatsOrder, finalizeGroupEatsOrder, joinGroupEatsOrder, setMyGroupEatsOrderItems,
  type GroupEatsOrderDetail,
} from './lib/eatsGroupOrders';
import { buildJoinUrl, shareOrCopyLink, readAndClearUrlParam } from './BankDashboard';

// Real 배달의민족 함께주문 (Baemin "Together Order") -- see lib/eats.ts's own doc
// comment for the full account. A join-code-shared cart in front of the same real
// checkout/payment path OrderFoodView already uses (GroupEatsOrderService.finalizeOrder
// calls the exact same backend EatsOrderService.placeOrder underneath).
export function GroupOrderView() {
  const { t } = useI18n();
  const [groupOrderId, setGroupOrderId] = useState<string | null>(null);
  const [detail, setDetail] = useState<GroupEatsOrderDetail | null>(null);
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [restaurantId, setRestaurantId] = useState('');
  const [address, setAddress] = useState('');
  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [menu, setMenu] = useState<MenuItem[] | null>(null);
  const [menuItemId, setMenuItemId] = useState('');
  const [qty, setQty] = useState(1);
  // My own accumulated selections, since setMyGroupEatsOrderItems replaces the
  // caller's entire item list on every call rather than incrementally appending --
  // this client keeps the running list locally and resends the whole thing each time,
  // same "resend full current state" convention the backend's own doc comment expects.
  const [myItems, setMyItems] = useState<{ menuItemId: string; quantity: number }[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [placedOrder, setPlacedOrder] = useState<EatsOrder | null>(null);
  // Real fix (2026-08-19): same "asking user code, instead use qr code" anti-pattern as
  // OpenChatCard -- see QrScanCamera's own doc comment for the shared itunda://... QR
  // payload convention. Typed code stays as a real fallback for voice/text sharing.
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualJoinEntry, setManualJoinEntry] = useState(false);

  useEffect(() => {
    fetchRestaurants().then(setRestaurants).catch(() => setRestaurants([]));
  }, []);

  useEffect(() => {
    const joinCode = detail?.groupOrder.joinCode;
    if (joinCode) {
      QRCode.toDataURL(`itunda://join-eats?code=${joinCode}`, { width: 180, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
    } else {
      setQrDataUrl(null);
    }
  }, [detail?.groupOrder.joinCode]);

  const refresh = (id: string) => {
    fetchGroupEatsOrder(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  // Real bug found live (2026-08-19, while verifying the share-link fix above): `detail`
  // was never populated on create or join, only after finalize -- both host and joiner
  // silently rendered a blank join code (and now, a missing QR/share button) the entire
  // time they were building their cart. Pre-existing gap, not introduced by this pass.
  useEffect(() => {
    if (groupOrderId) refresh(groupOrderId);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- refresh is a stable per-render closure over setDetail/setError, re-running on identity change would refetch every render
  }, [groupOrderId]);

  const handleCreate = async () => {
    if (!restaurantId || !address.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const groupOrder = await createGroupEatsOrder(restaurantId, address.trim());
      setGroupOrderId(groupOrder.id);
      const menuResult = await fetchMenu(restaurantId);
      setMenu(menuResult.products);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const submitJoinCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setBusy(true);
    setError(null);
    try {
      const groupOrder = await joinGroupEatsOrder(trimmed);
      setGroupOrderId(groupOrder.id);
      const menuResult = await fetchMenu(groupOrder.restaurantId);
      setMenu(menuResult.products);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = () => void submitJoinCode(joinCodeInput);
  const handleScanJoin = (raw: string) => void submitJoinCode(parseQrParam(raw, 'code'));

  // Real remote-invite fix (2026-08-19) -- see buildJoinUrl's own doc comment. A
  // together-order invite is normally sent to friends who aren't in the room, so a
  // tapped link (via itunda talk/SMS/anywhere) should join immediately, same as
  // OpenChatCard's identical fix.
  useEffect(() => {
    const incoming = readAndClearUrlParam('joinEatsCode');
    if (incoming) void submitJoinCode(incoming);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount only
  }, []);

  const [shareStatus, setShareStatus] = useState<'idle' | 'shared' | 'copied' | 'failed'>('idle');
  const handleShare = async (code: string) => {
    const url = buildJoinUrl('EATS', 'joinEatsCode', code);
    const result = await shareOrCopyLink(url, 'Order together on itunda', 'Join my together order on itunda — tap to join instantly.');
    setShareStatus(result);
  };

  const handleAddItem = async () => {
    if (!groupOrderId || !menuItemId || qty < 1) return;
    setBusy(true);
    setError(null);
    try {
      const nextItems = [...myItems, { menuItemId, quantity: qty }];
      const nextDetail = await setMyGroupEatsOrderItems(groupOrderId, nextItems);
      setMyItems(nextItems);
      setDetail(nextDetail);
      setMenuItemId('');
      setQty(1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleFinalize = async () => {
    if (!groupOrderId) return;
    setBusy(true);
    setError(null);
    try {
      const result = await finalizeGroupEatsOrder(groupOrderId);
      setPlacedOrder(result.order);
      refresh(groupOrderId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    if (!groupOrderId) return;
    setBusy(true);
    setError(null);
    try {
      await cancelGroupEatsOrder(groupOrderId);
      setGroupOrderId(null);
      setDetail(null);
      setMyItems([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (placedOrder) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Real order #{placedOrder.id.slice(-8)} placed for {placedOrder.totalAmount.toLocaleString()} RWF. Every other participant with items in the cart has been sent a real Dutch-pay request via Split Bill.
        </p>
      </div>
    );
  }

  if (!groupOrderId) {
    // Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown together --
    // reused .itunda-flat-section for the section-boundary divider.
    return (
      <div>
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Start a together order</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Share one restaurant's cart with friends -- everyone adds their own items, you place one real order, and itunda asks each of them for their own share afterward.
          </p>
          <select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)} className="itunda-input" style={{ marginBottom: '8px', width: '100%' }}>
            <option value="">Select a restaurant…</option>
            {(restaurants ?? []).map((r) => (
              <option key={r.merchantId} value={r.merchantId}>{r.businessName}</option>
            ))}
          </select>
          <input
            className="itunda-input"
            placeholder="Delivery address"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            style={{ marginBottom: '8px', width: '100%' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !restaurantId || !address.trim()} onClick={handleCreate} style={{ width: '100%' }}>
            {busy ? '…' : 'Start together order'}
          </button>
        </div>
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Join a together order</h3>
          {!manualJoinEntry ? (
            <>
              {!scanUnavailable && !busy && <QrScanCamera onDetect={handleScanJoin} onUnavailable={() => setScanUnavailable(true)} />}
              {busy && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>Joining…</p>}
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setManualJoinEntry(true)} style={{ width: '100%' }}>
                {scanUnavailable ? 'Enter join code manually' : 'No camera? Enter join code'}
              </button>
            </>
          ) : (
            <>
              <input
                className="itunda-input"
                placeholder="e.g. K3F9XQ"
                value={joinCodeInput}
                onChange={(e) => setJoinCodeInput(e.target.value.toUpperCase())}
                autoFocus
                style={{ marginBottom: '8px', width: '100%' }}
              />
              <button className="itunda-btn itunda-btn-secondary" disabled={busy || !joinCodeInput.trim()} onClick={handleJoin} style={{ width: '100%' }}>
                {busy ? '…' : 'Join'}
              </button>
            </>
          )}
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const currentUser = getStoredUser();
  const isHost = !!detail && !!currentUser && detail.groupOrder.hostUserId === currentUser.id;

  // Real fix (2026-08-24, flat-design sweep): 3 distinct sections shown together --
  // reused .itunda-flat-section for section-boundary dividers, :last-child auto-drops
  // the trailing one before the finalize/cancel buttons.
  return (
    <div>
      <div className="itunda-flat-section">
        <div style={{ display: 'flex', gap: '14px', alignItems: 'center', marginBottom: '10px' }}>
          {qrDataUrl && <img src={qrDataUrl} alt={`QR code to join order ${detail?.groupOrder.joinCode}`} width={72} height={72} style={{ borderRadius: '8px', flexShrink: 0 }} />}
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Order together — {detail?.groupOrder.joinCode}</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Send friends a link to join instantly, or let someone nearby scan the code.</p>
          </div>
        </div>
        {detail?.groupOrder.joinCode && (
          <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={() => handleShare(detail.groupOrder.joinCode)}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><LinkGlyph size={16} /> Share invite link</span>
          </button>
        )}
        {shareStatus === 'copied' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginTop: '6px' }}>Link copied</p>}
        {shareStatus === 'failed' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }}>Could not copy the link — share the code above instead.</p>}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Add your own item</h3>
        <select value={menuItemId} onChange={(e) => setMenuItemId(e.target.value)} className="itunda-input" style={{ marginBottom: '8px', width: '100%' }}>
          <option value="">Select an item…</option>
          {(menu ?? []).map((m) => (
            <option key={m.id} value={m.id}>{m.name} -- {m.price.toLocaleString()} RWF</option>
          ))}
        </select>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input type="number" min={1} value={qty} onChange={(e) => setQty(Number(e.target.value))} className="itunda-input" style={{ width: '80px' }} />
          <button className="itunda-btn itunda-btn-secondary" disabled={busy || !menuItemId} onClick={handleAddItem} style={{ flex: 1 }}>
            {busy ? '…' : 'Add to my cart'}
          </button>
        </div>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Everyone's items -- {detail?.grandTotal.toLocaleString() ?? 0} RWF total</h3>
        {/* Real, sourced Baemin UX writing finding (2026-08-24,
            bcut.baemin.com/6287, Baemin's own official UX writing blog on this exact
            함께주문/group-ordering feature): "함께주문을 쓸 때 대표로 주문하는 사람
            입장에선 몇 명이 골랐는지보다 몇 명이 아직 안 골랐는지가 더 중요한 정보"
            (from the lead orderer's perspective, who HASN'T picked yet matters more
            than who has) -- Baemin rewrote their own completed-count text to a
            remaining-count for exactly this reason. Only shown to the host: this is
            the same "matters to the lead orderer specifically" framing the article's
            own finding names, not a generic status line every participant needs. */}
        {isHost && detail && detail.participants.some((p) => p.items.length === 0) && (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
            {detail.participants.filter((p) => p.items.length === 0).length} of {detail.participants.length} haven't added items yet.
          </p>
        )}
        {(detail?.participants ?? []).map((p) => (
          <div key={p.userId} style={{ marginBottom: '10px', paddingBottom: '10px', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
              {p.userId === detail?.groupOrder.hostUserId ? 'Host' : 'Participant'} -- {p.subtotal.toLocaleString()} RWF
            </p>
            {p.items.length === 0 ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>No items yet</p>
            ) : (
              p.items.map((i, idx) => (
                <p key={idx} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{i.quantity}x {i.productName} -- {i.lineTotal.toLocaleString()} RWF</p>
              ))
            )}
          </div>
        ))}
        <button className="itunda-btn" onClick={() => groupOrderId && refresh(groupOrderId)} style={{ width: '100%', fontSize: 'var(--itunda-type-scale-13-size)' }}>Refresh</button>
      </div>
      {isHost && (
        <>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !detail || detail.grandTotal <= 0} onClick={handleFinalize} style={{ width: '100%', marginBottom: '8px' }}>
            {busy ? '…' : 'Place the real order'}
          </button>
          <button className="itunda-btn" disabled={busy} onClick={handleCancel} style={{ width: '100%', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            Cancel group order
          </button>
        </>
      )}
    </div>
  );
}
