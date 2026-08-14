import { useEffect, useState } from 'react';
import { overlay } from 'overlay-kit';
import './KycDashboard.css';
import { getToken, ApiError } from './lib/api';
import { fetchIdentityStatus, submitIdentity, type IdentityDocumentType, type KycSubmission } from './lib/identity';

// Real personal KYC identity submission (2026-07-26) -- this whole module used to be a
// fully mocked, unwired shell: typing any 6 digits into a fake "OTP" field flipped
// straight to a fabricated "Verification Complete ✓" screen with zero backend calls,
// confirmed by direct inspection (no fetch/axios anywhere in the old file). A real user
// tapping host-app's own "Identity" tab would have believed they were NIDA-verified
// when nothing had happened at all -- itunda already has a real, working identity
// submission flow (bank-mfe's own IdentityView, built 2026-07-22), just not reachable
// from this separately-deployed remote. This rewires kyc-mfe to the same real backend
// (rw.itunda.identity): a real NATIONAL_ID/PASSPORT submission goes to a real PENDING
// row for human review -- there is no instant "verified" step, honestly, because that
// is not how real KYC review actually works.
export default function KycDashboard() {
  const [submissions, setSubmissions] = useState<KycSubmission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loggedIn = getToken() !== null;

  const refresh = () => {
    if (!loggedIn) return;
    setError(null);
    fetchIdentityStatus()
      .then(setSubmissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your identity status.'));
  };

  useEffect(refresh, [loggedIn]);

  if (!loggedIn) {
    return (
      <div className="kyc-card">
        <h2 className="kyc-card__title">KYC Verification</h2>
        <p className="kyc-card__subtitle">Sign in from the Home tab first, then come back here to verify your identity.</p>
      </div>
    );
  }

  const hasPending = submissions?.some((s) => s.status === 'PENDING') ?? false;
  const latest = submissions?.[0] ?? null;

  return (
    <div className="kyc-card">
      <h2 className="kyc-card__title">KYC Verification</h2>
      <p className="kyc-card__subtitle">Rwanda National ID (NIDA) or passport verification.</p>

      {error && <p className="kyc-error" role="alert">{error}</p>}

      {latest && (
        <div className="kyc-status-row">
          <span className={`kyc-status-badge kyc-status-badge--${latest.status.toLowerCase()}`}>{latest.status}</span>
          <span className="kyc-status-detail">{latest.documentType} · {latest.documentNumber}</span>
        </div>
      )}
      {/* Real fix (2026-08-15): decisionReason is a genuine, human-written reason a
          real reviewer enters at decision time (IdentityService.decide's own real
          `reason` param, not an internal code) -- bank-mfe's own IdentityView already
          renders this (confirmed by reading it directly), but this separately-deployed
          remote never did, so a rejected user here saw a bare red "REJECTED" badge
          with zero explanation and no way to know what to fix before resubmitting. */}
      {latest?.decisionReason && (
        <p className="kyc-status-reason">{latest.decisionReason}</p>
      )}

      <button
        className="kyc-card__cta"
        disabled={hasPending}
        onClick={() => {
          overlay.open(({ isOpen, close }) => (
            <KycSubmitModal isOpen={isOpen} close={close} onSubmitted={refresh} />
          ));
        }}
      >
        {hasPending ? 'Submission pending review' : 'Verify your identity'}
      </button>
    </div>
  );
}

function KycSubmitModal({ isOpen, close, onSubmitted }: { isOpen: boolean; close: () => void; onSubmitted: () => void }) {
  const [documentType, setDocumentType] = useState<IdentityDocumentType>('NATIONAL_ID');
  const [documentNumber, setDocumentNumber] = useState('');
  const [documentReference, setDocumentReference] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState<KycSubmission | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const submission = await submitIdentity(documentType, documentNumber, documentReference);
      setSubmitted(submission);
      onSubmitted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That submission could not be completed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="kyc-modal-backdrop">
      <div className="kyc-modal-sheet">
        {submitted ? (
          <div className="kyc-success">
            <div className="kyc-success__badge">
              <span className="kyc-success__check">✓</span>
            </div>
            <h2 className="kyc-step__title">Submitted for review</h2>
            <p className="kyc-success__body">
              A real reviewer will check your {submitted.documentType.replace('_', ' ').toLowerCase()} and update your status --
              this isn't instant, real KYC review never is.
            </p>
            <button onClick={close} className="kyc-success__done">Done</button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="kyc-step">
            <h2 className="kyc-step__title">Verify your identity</h2>
            <p className="kyc-step__subtitle">Choose a document type and enter its number.</p>

            <div className="kyc-doctype-row">
              {(['NATIONAL_ID', 'PASSPORT'] as IdentityDocumentType[]).map((t) => (
                <button
                  type="button"
                  key={t}
                  className={documentType === t ? 'kyc-doctype-btn kyc-doctype-btn--active' : 'kyc-doctype-btn'}
                  onClick={() => setDocumentType(t)}
                >
                  {t === 'NATIONAL_ID' ? 'National ID' : 'Passport'}
                </button>
              ))}
            </div>

            {error && <p className="kyc-error" role="alert">{error}</p>}

            {/* Real a11y fix (item 244, web accessibility sweep): these two fields
                relied entirely on placeholder text for their identity -- not a
                substitute for a real label (WCAG 3.3.2), and placeholder disappears
                the moment someone starts typing, for every user, not just screen
                reader users. This is itunda's real KYC identity-document submission
                flow, not a low-stakes screen. aria-label added (matching this
                minimalist single-column wizard's existing visual design, which has
                no room shown for a persistent visible caption above each field) so
                the field's name survives even after typing starts. */}
            <input
              autoFocus
              aria-label={documentType === 'NATIONAL_ID' ? '16-digit Rwandan ID number' : 'Passport number'}
              value={documentNumber}
              onChange={(e) => setDocumentNumber(e.target.value)}
              placeholder={documentType === 'NATIONAL_ID' ? '16-digit Rwandan ID number' : 'Passport number'}
              className="kyc-step__input"
              style={{ fontSize: '16px', marginBottom: '10px' }}
              required
            />
            <input
              aria-label="Document reference (scan/photo reference)"
              value={documentReference}
              onChange={(e) => setDocumentReference(e.target.value)}
              placeholder="Document reference (scan/photo reference)"
              className="kyc-step__input"
              style={{ fontSize: '16px', marginBottom: 0 }}
              required
            />

            <button
              type="submit"
              disabled={busy || documentNumber.trim().length === 0 || documentReference.trim().length === 0}
              className={busy || documentNumber.trim().length === 0 || documentReference.trim().length === 0 ? 'kyc-step__next kyc-step__next--disabled' : 'kyc-step__next kyc-step__next--enabled'}
            >
              {busy ? 'Submitting…' : 'Submit for review'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
