// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// personal KYC identity submission (own lib/identity.ts data layer, exactly one
// external call site -- `{tab === 'IDENTITY' && <IdentityView />}`).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { fetchIdentityStatus, submitIdentity, type IdentityDocumentType, type KycSubmission } from './lib/identity';
import { fetchCreditScoreSuggestions } from './lib/creditScore';

// Real personal KYC identity submission (2026-07-22) -- found fully built on the
// backend (rw.itunda.identity) with zero client UI anywhere; merchant-mfe already has
// KYB submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client -- kyc-mfe, checked
// directly, is an unwired mock shell with no real API calls at all.
const IDENTITY_DOCUMENT_LABELS: Record<IdentityDocumentType, string> = { NATIONAL_ID: 'National ID', PASSPORT: 'Passport' };
const IDENTITY_STATUS_LABELS: Record<string, string> = { PENDING: 'Pending review', VERIFIED: 'Verified', REJECTED: 'Rejected' };

export function IdentityView() {
  const { t } = useI18n();
  const [submissions, setSubmissions] = useState<KycSubmission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [documentType, setDocumentType] = useState<IdentityDocumentType>('NATIONAL_ID');
  const [documentNumber, setDocumentNumber] = useState('');
  const [documentReference, setDocumentReference] = useState('');
  // Real "Explain Why" fix (2026-08-19) -- Toss's own real Product Principle
  // (toss.im/tossfeed/article/tossproductprinciples): "clarify the reasoning behind
  // required actions... never assume what's obvious to us is obvious to users." This
  // screen used to open straight into a document-upload form with zero explanation of
  // why. Grounded in a REAL, live number rather than an invented claim: the same
  // `fetchCreditScoreSuggestions` endpoint CreditScoreView already uses reports the exact
  // real point value `CreditScoreService.KYC_VERIFIED_POINTS` awards on approval, so this
  // reuses it rather than hardcoding a number that could drift from the backend truth.
  const [kycPointsGain, setKycPointsGain] = useState<number | null>(null);
  useEffect(() => {
    fetchCreditScoreSuggestions()
      .then((suggestions) => setKycPointsGain(suggestions.find((s) => s.action === 'Verify your identity')?.pointsGain ?? null))
      .catch(() => {});
  }, []);

  const refresh = () => {
    setError(null);
    fetchIdentityStatus()
      .then(setSubmissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const hasPending = submissions?.some((s) => s.status === 'PENDING') ?? false;
  const hasVerified = submissions?.some((s) => s.status === 'VERIFIED') ?? false;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await submitIdentity(documentType, documentNumber, documentReference);
      setDocumentNumber(''); setDocumentReference('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>Verify your identity</h2>
      {!hasPending && !hasVerified && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          A quick, one-time check that confirms it's really you -- it protects your account from takeover
          {kycPointsGain != null ? `, and raises your Credit Score by ${kycPointsGain} points once approved.` : '.'}
        </p>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {hasPending ? (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Submission pending review</h4>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>We'll update your status once it's reviewed.</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div style={{ display: 'flex', gap: '6px' }}>
            {(['NATIONAL_ID', 'PASSPORT'] as IdentityDocumentType[]).map((t) => (
              <button
                type="button" key={t}
                className={documentType === t ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                onClick={() => setDocumentType(t)}
              >
                {IDENTITY_DOCUMENT_LABELS[t]}
              </button>
            ))}
          </div>
          <input
            type="text" value={documentNumber} onChange={(e) => setDocumentNumber(e.target.value)} placeholder="Document number" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text" value={documentReference} onChange={(e) => setDocumentReference(e.target.value)} placeholder="Document reference (scan/photo reference)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Submitting…' : 'Submit for review'}</button>
        </form>
      )}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Your submissions</h3>
      {submissions === null ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> :
        submissions.length === 0 ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>You have no submissions yet.</p> :
        submissions.map((s) => (
          <div key={s.id} className="itunda-flat-section">
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{IDENTITY_DOCUMENT_LABELS[s.documentType as IdentityDocumentType] ?? s.documentType} · {s.documentNumber}</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Status: {IDENTITY_STATUS_LABELS[s.status] ?? s.status}</p>
            {s.decisionReason && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{s.decisionReason}</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Filed: {s.submittedAt}</p>
          </div>
        ))}
    </div>
  );
}
