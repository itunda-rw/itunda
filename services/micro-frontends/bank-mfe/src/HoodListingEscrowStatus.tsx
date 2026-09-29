import { LockGlyph } from './icons/ItundaFaceSecurity';
import { PackageGlyph } from './icons/ItundaFaceMisc';
import type { MarketplaceEscrow } from './lib/marketplace';

// Extracted out of HoodListingCard.tsx (2026-08-31, first crossing of the 500-line
// file-size guideline) -- the real escrow status display (shown to BOTH buyer and
// seller of a SOLD listing, confirm-receipt/dispute-report stay buyer-only) is a
// coherent, self-contained theme distinct from the seller inline-edit forms extracted
// the same pass into HoodListingSellerForms.tsx. Purely presentational --
// HoodListingCard.tsx keeps owning all the state and handlers, passed down as props.

export function HoodListingEscrowStatus({
  escrow, isEscrowBuyer, showDispute, setShowDispute, disputeReason, setDisputeReason,
  resolvingEscrow, handleDisputeEscrow, handleConfirmReceipt,
}: {
  escrow: MarketplaceEscrow;
  isEscrowBuyer: boolean;
  showDispute: boolean;
  setShowDispute: (v: boolean) => void;
  disputeReason: string;
  setDisputeReason: (v: string) => void;
  resolvingEscrow: boolean;
  handleDisputeEscrow: () => void;
  handleConfirmReceipt: () => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {escrow.deliveryAddress && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', margin: 0, display: 'flex', alignItems: 'center', gap: '5px' }}><PackageGlyph size={13} /> Delivery address: {escrow.deliveryAddress}</p>
      )}
      {escrow.status === 'HELD' && (
        <>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: 0, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <LockGlyph size={14} /> {isEscrowBuyer ? 'Payment held by itunda until you confirm receipt' : 'Payment held by itunda until the buyer confirms receipt'}
          </p>
          {isEscrowBuyer && (
            showDispute ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <input
                  type="text"
                  value={disputeReason}
                  onChange={(e) => setDisputeReason(e.target.value)}
                  placeholder="What went wrong?"
                  style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                />
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={resolvingEscrow} onClick={() => setShowDispute(false)}>
                    Cancel
                  </button>
                  <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={resolvingEscrow || !disputeReason.trim()} onClick={handleDisputeEscrow}>
                    Submit
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={resolvingEscrow} onClick={handleConfirmReceipt}>
                  {resolvingEscrow ? 'Working…' : 'Confirm receipt'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={resolvingEscrow} onClick={() => setShowDispute(true)}>
                  Report a problem
                </button>
              </div>
            )
          )}
        </>
      )}
      {escrow.status === 'DISPUTED' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }}>⚠️ Reported -- itunda is reviewing this trade</p>}
      {escrow.status === 'RELEASED' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', margin: 0 }}>✅ Payment released to the seller</p>}
      {escrow.status === 'REFUNDED' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', margin: 0 }}>{isEscrowBuyer ? '↩️ Refunded to you' : '↩️ Refunded to the buyer'}</p>
      )}
    </div>
  );
}
