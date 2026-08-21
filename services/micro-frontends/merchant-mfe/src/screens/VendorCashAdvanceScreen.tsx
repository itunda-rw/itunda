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
import { useI18n } from '../i18n/I18nContext';

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see lib/vendorCashAdvance.ts's
// own doc comment for the full sourced account. First web client for this feature (v1
// is web-only, consistent with MerchantBusinessAccountService's own web-first scope --
// Android/iOS clients are a named follow-up, not built here).
export default function VendorCashAdvanceScreen({ merchant }: { merchant: Merchant }) {
  const { t } = useI18n();
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t('vendorAdvance.loadError')));
  };

  useEffect(load, [merchant.id]);

  const handleApply = async () => {
    setError(null);
    setBusy(true);
    try {
      await applyForVendorCashAdvance(merchant.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('vendorAdvance.applyError'));
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
      setError(err instanceof ApiError ? err.message : t('vendorAdvance.disburseError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRepayEarly = async () => {
    if (!advance) return;
    const value = Number(repayAmount);
    if (!value || value <= 0) {
      setError(t('vendorAdvance.amountValidationError'));
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await repayVendorCashAdvanceEarly(advance.id, value);
      setRepayAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('vendorAdvance.repayError'));
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
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('vendorAdvance.title')}</h2>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '0' }}>
          {t('vendorAdvance.pitchBody')}
        </p>
      </div>

      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">
          {error}
        </p>
      )}

      {!advance && offer && offer.eligible && (
        <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('vendorAdvance.eligibleFor')}</p>
          <h3 style={{ fontSize: '24px', fontWeight: 700 }}>{offer.offerAmount?.toLocaleString()} RWF</h3>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
            {t('vendorAdvance.offerBody', {
              feeAmount: offer.feeAmount?.toLocaleString() ?? '0',
              ratePercent: offer.collectionRatePercent ?? 0,
              totalRepay: ((offer.offerAmount ?? 0) + (offer.feeAmount ?? 0)).toLocaleString(),
              avgDaily: offer.averageDailySettlement?.toLocaleString() ?? '0',
              tradingDays: offer.tradingDays ?? 0,
            })}
          </p>
          <button className="itunda-btn itunda-btn-primary" onClick={handleApply} disabled={busy}>
            {busy ? t('vendorAdvance.applying') : t('vendorAdvance.applyButton')}
          </button>
        </div>
      )}

      {!advance && offer && !offer.eligible && (
        <div className="itunda-card">
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {t('vendorAdvance.notEligibleBody', { reason: offer.reason ?? '' })}
          </p>
        </div>
      )}

      {advance && advance.status === 'REQUESTED' && (
        <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {t('vendorAdvance.readyToDisburseBody', { principalAmount: advance.principalAmount.toLocaleString() })}
          </p>
          <button className="itunda-btn itunda-btn-primary" onClick={handleDisburse} disabled={busy}>
            {busy ? t('vendorAdvance.disbursing') : t('vendorAdvance.disburseButton')}
          </button>
        </div>
      )}

      {advance && advance.status === 'DISBURSED' && (
        <>
          <div className="itunda-card">
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('vendorAdvance.remainingOwedLabel')}</p>
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
                  backgroundColor: 'var(--itunda-indigo)',
                }}
              />
            </div>
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>
              {advance.lastCollectionAt
                ? t('vendorAdvance.progressBodyWithLastCollection', {
                    totalOwed: advance.totalOwed.toLocaleString(),
                    ratePercent: advance.collectionRatePercent,
                    lastCollectionDate: new Date(advance.lastCollectionAt).toLocaleDateString(),
                  })
                : t('vendorAdvance.progressBody', {
                    totalOwed: advance.totalOwed.toLocaleString(),
                    ratePercent: advance.collectionRatePercent,
                  })}
            </p>
          </div>

          <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: 700 }}>{t('vendorAdvance.repayEarlyTitle')}</h3>
            <input
              type="number"
              min="1"
              value={repayAmount}
              onChange={(e) => setRepayAmount(e.target.value)}
              placeholder={t('vendorAdvance.amountPlaceholder')}
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
            <button className="itunda-btn itunda-btn-secondary" onClick={handleRepayEarly} disabled={busy}>
              {busy ? t('vendorAdvance.repaying') : t('vendorAdvance.repayButton')}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
