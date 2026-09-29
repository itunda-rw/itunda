// Real "verify with itunda" identity-verification-for-partners consent screen
// (Partners product-completeness pass, 2026-09-07) -- ported from Android's own
// real, already-live-verified IdentityVerificationConsentScreen (ItundaAppScreen.kt).
// Own file (own lib/partners.ts data layer, exactly one external call site --
// BankDashboard.tsx's `?verifyRequestId=` param handling), matching CertificateView's
// own "extracted, single external call site" shape.

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import {
  approveIdentityVerification, declineIdentityVerification, getIdentityVerificationRequest,
  type IdentityVerificationRequest,
} from './lib/partners';
import { useI18n } from './i18n/I18nContext';

export function IdentityVerificationConsentView({ requestId, onDone }: { requestId: string; onDone: () => void }) {
  const { t } = useI18n();
  const [request, setRequest] = useState<IdentityVerificationRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getIdentityVerificationRequest(requestId)
      .then(setRequest)
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : t('common.loadError'));
      })
      .finally(() => setLoading(false));
  }, [requestId]);

  const handleApprove = async () => {
    setBusy(true);
    setError(null);
    try {
      await approveIdentityVerification(requestId);
      setOutcome(`Shared with ${request?.partnerName ?? 'the partner'}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleDecline = async () => {
    setBusy(true);
    setError(null);
    try {
      await declineIdentityVerification(requestId);
      setOutcome('Declined. Nothing was shared.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'var(--itunda-white)', zIndex: 1000, overflowY: 'auto' }}>
      <div style={{ maxWidth: '480px', margin: '0 auto', padding: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
          <button onClick={onDone} aria-label="Close" style={{ fontSize: '20px', padding: '4px 8px' }}>×</button>
          <h2 style={{ flex: 1, textAlign: 'center', fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Verify with itunda</h2>
          <div style={{ width: '28px' }} />
        </div>

        {loading && <p style={{ color: 'var(--itunda-grey-500)' }}>Loading request…</p>}

        {!loading && outcome && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{outcome}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              You can return to the site or app that sent you here.
            </p>
            <button className="itunda-btn itunda-btn-primary" onClick={onDone}>Done</button>
          </div>
        )}

        {!loading && !outcome && error && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
            <button className="itunda-btn itunda-btn-secondary" onClick={onDone}>Close</button>
          </div>
        )}

        {!loading && !outcome && !error && request && request.status !== 'PENDING' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              This request has already been answered, or it expired. Requests are only valid for a few minutes.
            </p>
            <button className="itunda-btn itunda-btn-secondary" onClick={onDone}>Close</button>
          </div>
        )}

        {!loading && !outcome && !error && request && request.status === 'PENDING' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, marginBottom: '8px' }}>
                {request.partnerName} wants to verify your identity
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>
                If you approve, itunda will share only this with them:
              </p>
              {request.requestedFields.map((field) => (
                <p key={field} style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>• {field}</p>
              ))}
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>
                Nothing is shared unless you approve. itunda never shares your PIN, balance, or transaction history.
              </p>
              {/* Real defense-in-depth (deep-link/phishing-clone finding, 2026-09-08 --
                  see project_itunda_deeplink_scheme_hijacking memory's own named
                  partial mitigation, already shipped on Android/iOS commit 4d72df12).
                  A convincing fake clone of this page (a lookalike domain, not the
                  scheme-hijacking vector native apps face, but the same real risk on
                  web) could still try to social-engineer a credential out of the user
                  once they believe they're on the real page -- this closes that
                  specific escalation. Reuses the same red-tint warning box shape
                  already established for the scam-warning callout (BankDashboard.tsx). */}
              <div style={{ backgroundColor: 'var(--itunda-red-light)', border: '1px solid var(--itunda-red)', borderRadius: '8px', padding: '8px 10px', marginTop: '8px' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', margin: 0 }}>
                  This screen will never ask you to type your PIN, password, or a one-time code. If it ever does, close it — you're not on the real itunda site.
                </p>
              </div>
            </div>
            <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleApprove}>
              {busy ? '…' : 'Approve and share'}
            </button>
            <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleDecline}>
              Decline
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
