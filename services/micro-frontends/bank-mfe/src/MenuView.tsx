import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { IconBack } from './icons/ItundaIcons';
import { SoldOutGlyph } from './icons/ItundaFaceMisc';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { EmptyState, ErrorCard } from './EmptyState';
import { EatsFrequentlyOrderedWith } from './EatsFrequentlyOrderedWith';
import { ShopBestSellerBadge } from './ShopSellerContactPicker';
import type { ShoppingMerchant } from './lib/shopping';
import { fetchMenu, placeEatsOrder, type MenuItem, type EatsOrder } from './lib/eats';
import { ProductPriceBlock } from './ProductDisplay';
import { RestaurantRatingBadge } from './EatsOrderCard';
import { type EatsCartLine, eatsCartKey, eatsOptionsSummary, eatsLineUnitPrice, AddressAutocomplete } from './BankDashboard';

export function MenuView({
  restaurant, onBack, onOrderPlaced, initialCart,
}: {
  restaurant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: EatsOrder) => void; initialCart?: Record<string, number>;
}) {
  const { t } = useI18n();
  const [menu, setMenu] = useState<{ businessName: string; products: MenuItem[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic error, same fix already applied to Transfer/Savings.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  const [cart, setCart] = useState<Record<string, EatsCartLine>>(
    () => Object.fromEntries(Object.entries(initialCart ?? {}).map(([productId, quantity]) => [productId, { productId, quantity, choiceIds: [] }])),
  );
  // Real menu-options selection UI (2026-07-21, v1: required single-select only) -- see
  // MenuOptionGroup.kt's own doc comment on the backend for the full account. Only one
  // item's option panel is expanded at a time, matching this file's own established
  // "inline-card-replaces-trigger" convention (no modal-overlay pattern exists anywhere
  // in this codebase).
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  // Real optional/multi-select menu option groups (itunda Eats redesign, 2026-08-28)
  // -- the backend has always supported 4 real group combinations (required x
  // multiSelect, see MenuOptionGroup.kt's own doc comment), but this UI only ever
  // rendered required-single-select radios. groupId -> the real selected choiceIds
  // for that group (0 or 1 for a single-select group, 0+ for multiSelect) -- the
  // underlying eatsCartKey/eatsLineUnitPrice/eatsOptionsSummary helpers already
  // operate on a plain choiceIds[] with no single-choice assumption, so this is a
  // real UI-layer fix, not a pricing/cart-model change. DineInMenuView has its own
  // separate, still-radio-only copy of this same pattern -- deliberately not
  // touched here, out of this pass's real scope (browse/menu/checkout, not
  // Dine-in's own structure).
  const [pendingChoices, setPendingChoices] = useState<Record<string, string[]>>({});
  const [address, setAddress] = useState('');
  const [addressCoords, setAddressCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [deliveryNotes, setDeliveryNotes] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);
  // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- the backend has
  // supported this since 2026-07-26, but no client anywhere let a buyer choose it.
  // DELIVERY is the default, matching every existing order's real behavior.
  const [fulfillmentType, setFulfillmentType] = useState<'DELIVERY' | 'PICKUP'>('DELIVERY');

  const load = () => {
    setError(null);
    fetchMenu(restaurant.merchantId)
      .then((r) => {
        setMenu({ businessName: r.merchant.businessName, products: r.products });
        // Real reorder-cart sanitization (2026-07-21) -- a reordered past order's cart is
        // rebuilt from plain product ids with no option selections (see OrderFoodView's
        // handleReorder, unchanged). If a product now genuinely requires an option
        // selection, that bare line can never check out -- drop it rather than let
        // checkout silently fail, same "discontinued item silently dropped" precedent
        // handleReorder itself already established for a menu item that's gone entirely.
        setCart((prev) => {
          const next = { ...prev };
          for (const [key, line] of Object.entries(prev)) {
            const product = r.products.find((p) => p.id === line.productId);
            if (product && (product.optionGroups?.length ?? 0) > 0 && line.choiceIds.length === 0) {
              delete next[key];
            }
          }
          return next;
        });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, line]) => line.quantity > 0);
  const cartCount = cartItems.reduce((sum, [, line]) => sum + line.quantity, 0);
  // Real cart-bar subtotal + savings breakdown (itunda Eats redesign, 2026-08-28) --
  // the fixed bottom bar previously only showed the item count, deferring every real
  // number to the checkout screen. Purely a client-side sum over cart data already
  // fetched -- no new backend call. cartOriginalSubtotal only differs from
  // cartSubtotal when a real originalPrice is set on at least one cart line.
  const cartSubtotal = cartItems.reduce((sum, [, line]) => {
    const item = menu?.products.find((p) => p.id === line.productId);
    return item ? sum + eatsLineUnitPrice(item, line.choiceIds) * line.quantity : sum;
  }, 0);
  const cartOriginalSubtotal = cartItems.reduce((sum, [, line]) => {
    const item = menu?.products.find((p) => p.id === line.productId);
    if (!item) return sum;
    return sum + (item.originalPrice ?? item.price) * line.quantity;
  }, 0);

  // For a no-option item only -- the original single-stepper interaction, completely
  // unchanged for the overwhelming majority of menu items that have no option groups.
  const setSimpleQty = (productId: string, qty: number) => {
    const key = eatsCartKey(productId, []);
    setCart((c) => ({ ...c, [key]: { productId, quantity: Math.max(0, qty), choiceIds: [] } }));
  };

  const setLineQty = (key: string, line: EatsCartLine, qty: number) => {
    setCart((c) => ({ ...c, [key]: { ...line, quantity: Math.max(0, qty) } }));
  };

  const toggleExpand = (productId: string) => {
    setPendingChoices({});
    setExpandedProductId((current) => (current === productId ? null : productId));
  };

  const addConfiguredToCart = (item: MenuItem) => {
    const groups = item.optionGroups ?? [];
    // Real optional/multi-select support (2026-08-28) -- a required group needs at
    // least one real selected choice; an optional group is valid with zero. A
    // multiSelect group may contribute more than one choiceId, a single-select
    // group at most one -- both flow through the same flatMap.
    if (groups.some((g) => g.required && (pendingChoices[g.id]?.length ?? 0) === 0)) return;
    const choiceIds = groups.flatMap((g) => pendingChoices[g.id] ?? []);
    const key = eatsCartKey(item.id, choiceIds);
    setCart((c) => ({ ...c, [key]: { productId: item.id, quantity: (c[key]?.quantity ?? 0) + 1, choiceIds } }));
    setPendingChoices({});
    setExpandedProductId(null);
  };

  const handlePlaceOrder = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!menu) return;
    setPlacing(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const items = cartItems.map(([, line]) => ({
        menuItemId: line.productId, quantity: line.quantity,
        selectedChoiceIds: line.choiceIds.length ? line.choiceIds : undefined,
      }));
      const result = await placeEatsOrder(
        restaurant.merchantId, items, fulfillmentType === 'PICKUP' ? '' : address.trim(),
        fulfillmentType === 'PICKUP' ? undefined : addressCoords?.latitude,
        fulfillmentType === 'PICKUP' ? undefined : addressCoords?.longitude,
        deliveryNotes.trim() || undefined, fulfillmentType,
      );
      onOrderPlaced(result.order);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setPlacing(false);
    }
  };

  if (needsDeviceVerification) {
    // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
    // account. handlePlaceOrder resets needsDeviceVerification itself.
    // Real fix (2026-08-24, flat-design sweep): dropped the itunda-card wrapper --
    // DeviceStepUpPrompt already renders its own inset grey background, matching
    // every other real call site in this file (none of them wrap it in a card).
    return <DeviceStepUpPrompt onVerified={() => handlePlaceOrder()} onCancel={() => setNeedsDeviceVerification(false)} />;
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (menu === null) {
    return <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />;
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
        {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this form
            IS the whole checkout screen's content (docs/UI_UX_GUIDELINES.md §10). */}
        <form onSubmit={handlePlaceOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-14-size)' }}>
                <span>{item.name}{eatsOptionsSummary(item, line.choiceIds)} x{line.quantity}</span>
                <span>{(unitPrice * line.quantity).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <div style={{ display: 'flex', gap: '4px', padding: '4px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
            {(['DELIVERY', 'PICKUP'] as const).map((ft) => (
              <button
                key={ft}
                type="button"
                onClick={() => setFulfillmentType(ft)}
                style={{
                  flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                  color: fulfillmentType === ft ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                  backgroundColor: fulfillmentType === ft ? 'var(--itunda-indigo)' : 'transparent',
                }}
              >
                {ft === 'DELIVERY' ? 'Delivery' : 'Pickup'}
              </button>
            ))}
          </div>
          {fulfillmentType === 'PICKUP' ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              No delivery fee -- collect your order at {menu.businessName} once it's ready.
            </p>
          ) : (
            <>
              <AddressAutocomplete
                value={address}
                onChangeText={(text) => { setAddress(text); setAddressCoords(null); }}
                onSelectSuggestion={(s) => { setAddress(s.displayName); setAddressCoords({ latitude: s.latitude, longitude: s.longitude }); }}
              />
              {addressCoords && (
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>Pinned -- real distance-based delivery fee applies</p>
              )}
            </>
          )}
          <textarea
            value={deliveryNotes}
            onChange={(e) => setDeliveryNotes(e.target.value.slice(0, 500))}
            placeholder={fulfillmentType === 'PICKUP' ? 'Pickup notes (optional)' : 'Delivery notes (optional) -- e.g. Leave at the gate, call on arrival'}
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={placing || (fulfillmentType === 'DELIVERY' && !address.trim())}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
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
      <div style={{ marginBottom: '12px' }}>
        <RestaurantRatingBadge restaurantId={restaurant.merchantId} />
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
            const allGroupsChosen = groups.every((g) => !g.required || (pendingChoices[g.id]?.length ?? 0) > 0);
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card, reusing
            // itunda-flat-section for this real Baemin-style flat menu-item list.
            return (
              <div key={item.id} className="itunda-flat-section">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{item.name}</p>
                    {/* Real per-dish discount badge (itunda Eats redesign, 2026-08-28) --
                        see lib/eats.ts's own MenuItem.discountPercent doc comment: the
                        shared catalog endpoint has always returned this, Eats' own menu
                        never rendered it. */}
                    <ProductPriceBlock price={item.price} originalPrice={item.originalPrice} discountPercent={item.discountPercent} />
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: item.soldOut ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                      {hasOptions ? 'Options required' : ''}
                      {item.soldOut ? <> · <SoldOutGlyph size={12} /> Sold out</> : ''}
                    </p>
                    {item.isBestSeller && <ShopBestSellerBadge />}
                  </div>
                  {/* Real Baemin CEO app/DoorDash-style "86" enforcement (2026-08-16) --
                      see MenuItem.soldOut's own doc comment. Shown, not hidden -- the
                      item stays fully visible on the menu, just can't be added right
                      now, same discipline Section 101's pause-orders badge established. */}
                  {item.soldOut ? null : hasOptions ? (
                    <button onClick={() => toggleExpand(item.id)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}>
                      {isExpanded ? 'Close' : 'Choose options'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {!item.soldOut && hasOptions && isExpanded && (
                  <div style={{ marginTop: '14px', paddingTop: '14px', borderTop: '1px solid var(--itunda-grey-200)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {groups.map((group) => {
                      const selected = pendingChoices[group.id] ?? [];
                      // Real optional/multi-select labels (2026-08-28) -- honest about
                      // what this group actually requires, matching its own real
                      // required/multiSelect flags rather than always claiming "choose 1".
                      const groupHint = group.multiSelect
                        ? (group.required ? 'choose at least 1' : 'choose any (optional)')
                        : (group.required ? 'choose 1' : 'choose 1 (optional)');
                      return (
                        <div key={group.id}>
                          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '6px' }}>
                            {group.name} <span style={{ color: 'var(--itunda-grey-400)', fontWeight: 400 }}>· {groupHint}</span>
                          </p>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {group.choices.map((choice) => (
                              <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', cursor: 'pointer' }}>
                                <input
                                  type={group.multiSelect ? 'checkbox' : 'radio'}
                                  name={`eats-option-group-${group.id}`}
                                  checked={selected.includes(choice.id)}
                                  onChange={() => setPendingChoices((p) => {
                                    if (group.multiSelect) {
                                      const next = selected.includes(choice.id) ? selected.filter((id) => id !== choice.id) : [...selected, choice.id];
                                      return { ...p, [group.id]: next };
                                    }
                                    // Single-select: re-clicking the current choice clears it
                                    // when the group is optional (a real "none of these"),
                                    // never for a required group.
                                    const next = selected[0] === choice.id && !group.required ? [] : [choice.id];
                                    return { ...p, [group.id]: next };
                                  })}
                                />
                                {choice.name}{choice.priceDelta > 0 ? ` (+${choice.priceDelta.toLocaleString()} RWF)` : ''}
                              </label>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                    <button
                      type="button"
                      className="itunda-btn itunda-btn-primary"
                      disabled={!allGroupsChosen}
                      onClick={() => addConfiguredToCart(item)}
                    >
                      Add to cart
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      {cartCount > 0 && (
        <div className="itunda-card" style={{ marginBottom: '80px', marginTop: '-2px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '10px' }}>Your cart</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {cartItems.map(([key, line]) => {
              const item = menu.products.find((p) => p.id === line.productId);
              if (!item) return null;
              return (
                <div key={key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{item.name}{eatsOptionsSummary(item, line.choiceIds)}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button onClick={() => setLineQty(key, line, line.quantity - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }}>−</button>
                    <span style={{ minWidth: '14px', textAlign: 'center', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>{line.quantity}</span>
                    <button onClick={() => setLineQty(key, line, line.quantity + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }}>+</button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
      {/* Real "frequently ordered together" cross-sell (itunda Eats redesign,
          2026-08-28) -- keyed off the most recently added cart line, matching the
          reference's own per-item placement as closely as this menu's flat (no
          per-item detail page) layout allows. See EatsFrequentlyOrderedWith.tsx's
          own doc comment. */}
      {cartItems.length > 0 && (
        <EatsFrequentlyOrderedWith
          productId={cartItems[cartItems.length - 1][1].productId}
          // Real, honest limitation: a "quick add" straight from this rail always
          // adds a no-option cart line -- if the real co-purchased dish actually has
          // real required option groups, the existing per-item "Choose options" flow
          // in the main list below is how a buyer configures it; this cross-sell
          // rail intentionally doesn't duplicate that UI for a secondary rail.
          onAdd={(item) => setSimpleQty(item.id, (cart[eatsCartKey(item.id, [])]?.quantity ?? 0) + 1)}
        />
      )}
      {cartCount > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 20px' }}
          onClick={() => setShowCheckout(true)}
        >
          <span>Checkout ({cartCount} item{cartCount === 1 ? '' : 's'})</span>
          {/* Real computed subtotal + savings (2026-08-28) -- see cartSubtotal's own
              doc comment just above. */}
          <span style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
            {cartOriginalSubtotal > cartSubtotal && (
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', textDecoration: 'line-through', opacity: 0.7 }}>{cartOriginalSubtotal.toLocaleString()} RWF</span>
            )}
            <span>{cartSubtotal.toLocaleString()} RWF</span>
          </span>
        </button>
      )}
    </div>
  );
}
