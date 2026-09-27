import { useEffect, useState } from 'react';
import { overlay } from 'overlay-kit';
import './KycDashboard.css';
import { getToken, ApiError } from './lib/api';
import { fetchIdentityStatus, submitIdentity, type IdentityDocumentType, type KycSubmission } from './lib/identity';
import { useI18n } from './i18n/I18nContext';
import { LOCALES } from './i18n/translations';

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
  const { t, locale, setLocale } = useI18n();
  const [submissions, setSubmissions] = useState<KycSubmission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loggedIn = getToken() !== null;

  const refresh = () => {
    if (!loggedIn) return;
    setError(null);
    fetchIdentityStatus()
      .then(setSubmissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('kyc.loadError')));
  };

  useEffect(refresh, [loggedIn]);

  if (!loggedIn) {
    return (
      <div className="kyc-card">
        <h2 className="kyc-card__title">{t('kyc.title')}</h2>
        <p className="kyc-card__subtitle">{t('kyc.signInFirst')}</p>
      </div>
    );
  }

  const hasPending = submissions?.some((s) => s.status === 'PENDING') ?? false;
  const latest = submissions?.[0] ?? null;

  return (
    <div className="kyc-card">
      {/* Real first language switcher for kyc-mfe (2026-08-15) -- shares the same
          'itunda.locale' localStorage key bank-mfe's own login writes, so a language
          already chosen elsewhere in itunda is honored here too. */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '4px' }}>
        <select
          value={locale}
          onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
          aria-label="Language"
          style={{ fontSize: '11px', padding: '3px 5px', borderRadius: '6px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-700)', background: '#fff' }}
        >
          {LOCALES.map((l) => (
            <option key={l.code} value={l.code}>{l.label}</option>
          ))}
        </select>
      </div>
      <h2 className="kyc-card__title">{t('kyc.title')}</h2>
      <p className="kyc-card__subtitle">{t('kyc.subtitle')}</p>

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
        {hasPending ? t('kyc.pendingReview') : t('kyc.verifyIdentity')}
      </button>
    </div>
  );
}

function KycSubmitModal({ isOpen, close, onSubmitted }: { isOpen: boolean; close: () => void; onSubmitted: () => void }) {
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('kyc.submitError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="kyc-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="kyc-modal-title">
      <div className="kyc-modal-sheet">
        {submitted ? (
          <div className="kyc-success">
            <div className="kyc-success__badge">
              <span className="kyc-success__check">✓</span>
            </div>
            <h2 id="kyc-modal-title" className="kyc-step__title">{t('kyc.submittedTitle')}</h2>
            <p className="kyc-success__body">
              {t('kyc.submittedBodyPrefix')} {submitted.documentType.replace('_', ' ').toLowerCase()} {t('kyc.submittedBodySuffix')}
            </p>
            <button onClick={close} className="kyc-success__done">{t('kyc.done')}</button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="kyc-step">
            <h2 id="kyc-modal-title" className="kyc-step__title">{t('kyc.verifyIdentity')}</h2>
            <p className="kyc-step__subtitle">{t('kyc.chooseDocType')}</p>

            <div className="kyc-doctype-row">
              {(['NATIONAL_ID', 'PASSPORT'] as IdentityDocumentType[]).map((docType) => (
                <button
                  type="button"
                  key={docType}
                  className={documentType === docType ? 'kyc-doctype-btn kyc-doctype-btn--active' : 'kyc-doctype-btn'}
                  onClick={() => setDocumentType(docType)}
                >
                  {docType === 'NATIONAL_ID' ? t('kyc.nationalId') : t('kyc.passport')}
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
              aria-label={documentType === 'NATIONAL_ID' ? t('kyc.nationalIdNumberLabel') : t('kyc.passportNumberLabel')}
              value={documentNumber}
              onChange={(e) => setDocumentNumber(e.target.value)}
              placeholder={documentType === 'NATIONAL_ID' ? t('kyc.nationalIdNumberLabel') : t('kyc.passportNumberLabel')}
              className="kyc-step__input"
              style={{ fontSize: '16px', marginBottom: '10px' }}
              required
            />
            <input
              aria-label={t('kyc.documentReferenceLabel')}
              value={documentReference}
              onChange={(e) => setDocumentReference(e.target.value)}
              placeholder={t('kyc.documentReferenceLabel')}
              className="kyc-step__input"
              style={{ fontSize: '16px', marginBottom: 0 }}
              required
            />

            <button
              type="submit"
              disabled={busy || documentNumber.trim().length === 0 || documentReference.trim().length === 0}
              className={busy || documentNumber.trim().length === 0 || documentReference.trim().length === 0 ? 'kyc-step__next kyc-step__next--disabled' : 'kyc-step__next kyc-step__next--enabled'}
            >
              {busy ? t('kyc.submitting') : t('kyc.submitForReview')}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
