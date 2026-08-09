import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import type { Merchant } from '../lib/merchant';
import {
  applyForVendorCashAdvance,
  disburseVendorCashAdvance,
  getMyVendorCashAdvance,
  getVendorCashAdvanceOffer,
  repayVendorCashAdvanceEarly,
  type VendorCashAdvance,
  type VendorCashAdvanceOffer,
} from '../lib/vendorCashAdvance';

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see lib/vendorCashAdvance.ts's
// own doc comment for the full sourced account. First web client for this feature (v1
// is web-only, consistent with MerchantBusinessAccountService's own web-first scope --
// Android/iOS clients are a named follow-up, not built here).
export default function VendorCashAdvanceScreen({ merchant }: { merchant: Merchant }) {
  const [advance, setAdvance] = useState<VendorCashAdvance | null | undefined>(undefined);
  const [offer, setOffer] = useState<VendorCashAdvanceOffer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [repayAmount, setRepayAmount] = useState('');

  const load = () => {
    setError(null);
    getMyVendorCashAdvance(merchant.id)
      .then((a) => {
        setAdvance(a);
        if (!a) {
          getVendorCashAdvanceOffer(merchant.id)
            .then(setOffer)
            .catch(() => setOffer(null));
        }
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your vendor cash advance.'));
  };

  useEffect(load, [merchant.id]);

  const handleApply = async () => {
    setError(null);
    setBusy(true);
    try {
      await applyForVendorCashAdvance(merchant.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't apply for a vendor cash advance.");
    } finally {
      setBusy(false);
    }
  };

  const handleDisburse = async () => {
    if (!advance) return;
    setError(null);
    setBusy(true);
    try {
      await disburseVendorCashAdvance(advance.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't disburse this advance.");
    } finally {
      setBusy(false);
    }
  };

  const handleRepayEarly = async () => {
    if (!advance) return;
    const value = Number(repayAmount);
    if (!value || value <= 0) {
      setError('Enter a real amount.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await repayVendorCashAdvanceEarly(advance.id, value);
      setRepayAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't repay this advance. Check your balance.");
    } finally {
      setBusy(false);
    }
  };

  if (advance === undefined) {
    return <div className="itunda-card skeleton" style={{ height: '160px' }} />;
  }

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Isoko Vendor Cash Advance</h2>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '0' }}>
          A cash advance against your own real itunda sales history. There's no fixed repayment schedule --
          itunda automatically collects a share of your real QR/card sales here each day until it's paid off. This
          can only see and collect sales that actually go through itunda; cash you collect off-platform isn't part
          of this at all.
        </p>
      </div>

      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">
          {error}
        </p>
      )}

      {!advance && offer && offer.eligible && (
        <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>You're eligible for</p>
          <h3 style={{ fontSize: '24px', fontWeight: 700 }}>{offer.offerAmount?.toLocaleString()} RWF</h3>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
            One-time fee: {offer.feeAmount?.toLocaleString()} RWF -- itunda then collects {offer.collectionRatePercent}% of your
            real daily itunda-collected sales here until{' '}
            {((offer.offerAmount ?? 0) + (offer.feeAmount ?? 0)).toLocaleString()} RWF is repaid. Based on your real average of{' '}
            {offer.averageDailySettlement?.toLocaleString()} RWF/day over your last {offer.tradingDays} real trading days.
          </p>
          <button className="itunda-btn itunda-btn-primary" onClick={handleApply} disabled={busy}>
            {busy ? 'Applying…' : 'Apply for this advance'}
          </button>
        </div>
      )}

      {!advance && offer && !offer.eligible && (
        <div className="itunda-card">
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            Not eligible yet -- {offer.reason}. Keep collecting real QR/card sales through itunda and check back.
          </p>
        </div>
      )}

      {advance && advance.status === 'REQUESTED' && (
        <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            Your {advance.principalAmount.toLocaleString()} RWF advance was approved and is ready to disburse to your wallet.
          </p>
          <button className="itunda-btn itunda-btn-primary" onClick={handleDisburse} disabled={busy}>
            {busy ? 'Disbursing…' : 'Disburse to my wallet'}
          </button>
        </div>
      )}

      {advance && advance.status === 'DISBURSED' && (
        <>
          <div className="itunda-card">
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>Remaining owed</p>
            <h3 style={{ fontSize: '24px', fontWeight: 700 }}>{advance.remainingOwed.toLocaleString()} RWF</h3>
            <div
              style={{
                marginTop: '8px', height: '8px', borderRadius: '4px', backgroundColor: 'var(--itunda-grey-200)', overflow: 'hidden',
              }}
            >
              <div
                style={{
                  height: '100%',
                  width: `${Math.min(100, Math.round(((advance.totalOwed - advance.remainingOwed) / advance.totalOwed) * 100))}%`,
                  backgroundColor: 'var(--itunda-blue)',
                }}
              />
            </div>
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>
              of {advance.totalOwed.toLocaleString()} RWF total owed -- {advance.collectionRatePercent}% of your real daily
              itunda sales is collected automatically
              {advance.lastCollectionAt ? `, last collected ${new Date(advance.lastCollectionAt).toLocaleDateString()}` : ''}.
            </p>
          </div>

          <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Repay early</h3>
            <input
              type="number"
              min="1"
              value={repayAmount}
              onChange={(e) => setRepayAmount(e.target.value)}
              placeholder="Amount (RWF)"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
            <button className="itunda-btn itunda-btn-secondary" onClick={handleRepayEarly} disabled={busy}>
              {busy ? 'Repaying…' : 'Repay now'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
