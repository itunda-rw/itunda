// Extracted out of HoodListingCard.tsx (2026-08-31, first crossing of the 500-line
// file-size guideline) -- these three inline forms (price edit, "who bought this?"
// prompt, boost-tier picker) are a coherent "seller's own listing, ACTIVE status,
// inline edit" theme, distinct from the escrow-status display (extracted the same
// pass into HoodListingEscrowStatus.tsx) and from the card's own always-visible
// content. Purely presentational -- HoodListingCard.tsx keeps owning all the state
// and handlers, passed down as props, the same pattern HoodReportButton/HoodReviewForm
// already establish for this file's other extracted pieces.

export function HoodListingSellerForms({
  editingPrice, newPrice, setNewPrice, setEditingPrice, handleUpdatePrice, busy,
  markingSold, buyerPhone, setBuyerPhone, handleMarkSold,
  showBoostPicker, boostTiers, boosting, handleBoost, setShowBoostPicker,
}: {
  editingPrice: boolean;
  newPrice: string;
  setNewPrice: (v: string) => void;
  setEditingPrice: (v: boolean) => void;
  handleUpdatePrice: () => void;
  busy: boolean;
  markingSold: boolean;
  buyerPhone: string;
  setBuyerPhone: (v: string) => void;
  handleMarkSold: (buyerPhoneNumber?: string) => void;
  showBoostPicker: boolean;
  boostTiers: Record<string, number> | null;
  boosting: boolean;
  handleBoost: (days: number) => void;
  setShowBoostPicker: (v: boolean) => void;
}) {
  return (
    <>
      {editingPrice && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="number" min="1" value={newPrice} onChange={(e) => setNewPrice(e.target.value)}
            placeholder="New price (RWF)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => setEditingPrice(false)}>
              Cancel
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy || !(Number(newPrice) > 0)} onClick={handleUpdatePrice}>
              Save
            </button>
          </div>
        </div>
      )}
      {/* Real optional "who bought this?" prompt -- see backend
          MarketplaceService.markSold's own doc comment. */}
      {markingSold && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={buyerPhone}
            onChange={(e) => setBuyerPhone(e.target.value)}
            placeholder="Buyer's phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkSold()}>
              Skip
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkSold(buyerPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real seller-paid sponsored placement -- see lib/marketplace.ts's own doc
          comment on boostListing/BOOST_TIERS. Real gap found live 2026-08-31: this
          picker existed on Android/iOS with zero web equivalent. */}
      {showBoostPicker && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {boostTiers ? (
            <div style={{ display: 'flex', gap: '8px' }}>
              {Object.entries(boostTiers).sort(([a], [b]) => Number(a) - Number(b)).map(([days, price]) => (
                <button
                  key={days}
                  className="itunda-btn itunda-btn-primary"
                  style={{ flex: 1 }}
                  disabled={boosting}
                  onClick={() => handleBoost(Number(days))}
                >
                  {days}d · {price.toLocaleString('en-US')} RWF
                </button>
              ))}
            </div>
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading boost options…</p>
          )}
          <button className="itunda-btn itunda-btn-secondary" disabled={boosting} onClick={() => setShowBoostPicker(false)}>
            Cancel
          </button>
        </div>
      )}
    </>
  );
}
