// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). The
// Itunda Certificate feature (own lib/certificate.ts data layer, exactly one
// external call site -- `{tab === 'CERTIFICATE' && <CertificateView />}`) is fully
// self-contained, matching the same safe staged extraction pattern the Stocks
// cluster used earlier the same day.

import { useEffect, useState } from 'react';
import { IconShieldCheck } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import {
  getCertificateStatus, getMyCertificate, issueCertificate, revokeCertificate, verifyCertificateSignature,
  type Certificate, type VerifyCertificateSignatureResult,
} from './lib/certificate';
import { useDeferredLoading } from './useDeferredLoading';

// Real itunda-branded "Verified ID" credential card (2026-08-30, market-readiness
// audit) -- the visual touchpoint real Toss 인증서/Apple Wallet/Google Wallet all give
// a digital certificate that this feature never had (CertificateView previously only
// ever showed a bare monospace serial number + text status, no card at all). This is
// itunda's own card design (indigo brand gradient, itunda's own wordmark), never a
// replica of any government-issued ID -- it shows only data the user already gave
// itunda (their name) plus certificate metadata itunda itself generated, the same
// safe pattern DemoNidaVerificationService/CertificateService already established of
// validating/representing real data honestly without fabricating a government
// document's actual visual design.
function ItundaCertificateCard({ certificate }: { certificate: Certificate }) {
  const user = getStoredUser();
  const isActive = certificate.status === 'ACTIVE';
  return (
    <div
      style={{
        borderRadius: '18px', padding: '22px', marginBottom: '20px', color: 'white',
        background: isActive
          ? 'linear-gradient(135deg, var(--itunda-indigo) 0%, var(--itunda-indigo-active) 100%)'
          : 'linear-gradient(135deg, #9A9AA5 0%, #6E6E78 100%)',
        boxShadow: '0 8px 24px rgba(116, 114, 244, 0.25)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '28px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 800, letterSpacing: '-0.3px' }}>itunda</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.85, marginTop: '2px' }}>Verified Certificate</p>
        </div>
        <IconShieldCheck size={22} color="white" />
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, marginBottom: '18px' }}>
        {user ? `${user.firstName} ${user.lastName}` : 'itunda user'}
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.75, marginBottom: '2px' }}>SERIAL</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontFamily: 'monospace', letterSpacing: '0.5px' }}>
            {certificate.serialNumber.slice(0, 4)} •••• {certificate.serialNumber.slice(-4)}
          </p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.75, marginBottom: '2px' }}>{isActive ? 'VALID THRU' : certificate.status}</p>
          {isActive && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{new Date(certificate.expiresAt).toLocaleDateString(undefined, { month: '2-digit', year: 'numeric' })}</p>}
        </div>
      </div>
    </div>
  );
}

export function CertificateView() {
  const { t } = useI18n();
  const [certificate, setCertificate] = useState<Certificate | null | undefined>(undefined);
  const showSkeleton = useDeferredLoading(certificate === undefined);
  const [issuedPrivateKey, setIssuedPrivateKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    setError(null);
    getMyCertificate()
      .then(setCertificate)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const handleIssue = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await issueCertificate();
      setCertificate(result.certificate);
      setIssuedPrivateKey(result.privateKey);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async () => {
    setBusy(true);
    setError(null);
    try {
      const revoked = await revokeCertificate();
      setCertificate(revoked);
      setIssuedPrivateKey(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (certificate === undefined) {
    return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
        <IconShieldCheck size={22} color={certificate?.status === 'ACTIVE' ? 'var(--itunda-green)' : 'var(--itunda-grey-500)'} />
        <h2 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>Itunda Certificate</h2>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '20px' }}>
        A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.
      </p>

      {certificate && certificate.status === 'ACTIVE' ? (
        <div>
          <ItundaCertificateCard certificate={certificate} />
          <button className="itunda-btn itunda-btn-secondary" onClick={handleRevoke} disabled={busy}>
            {busy ? 'Revoking…' : 'Revoke certificate'}
          </button>
        </div>
      ) : (
        <div>
          {certificate && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
              Your previous certificate was {certificate.status.toLowerCase()}.
            </p>
          )}
          <button className="itunda-btn itunda-btn-primary" onClick={handleIssue} disabled={busy}>
            {busy ? 'Issuing…' : 'Issue a certificate'}
          </button>
        </div>
      )}

      {issuedPrivateKey && (
        <div style={{ marginTop: '20px', padding: '16px', borderRadius: '12px', backgroundColor: '#FFF4E5' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: '#B25E09', marginBottom: '6px' }}>
            Save this private key now — you won't be able to see it again.
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontFamily: 'monospace', wordBreak: 'break-all', color: '#B25E09' }}>{issuedPrivateKey}</p>
        </div>
      )}

      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '16px' }} role="alert">{error}</p>
      )}

      <VerifyCertificateCard />
    </div>
  );
}

// Real public certificate status/verify (2026-08-04) -- see lib/certificate.ts's own
// doc comment on getCertificateStatus/verifyCertificateSignature: the two endpoints
// that answer "does this signed thing check out," found via a fresh backend-endpoint
// sweep with zero client anywhere. Deliberately separate from CertificateView above --
// that one manages the caller's own certificate; this one checks someone else's.
function VerifyCertificateCard() {
  const [serialNumber, setSerialNumber] = useState('');
  const [payload, setPayload] = useState('');
  const [signature, setSignature] = useState('');
  const [statusResult, setStatusResult] = useState<Certificate | null>(null);
  const [verifyResult, setVerifyResult] = useState<VerifyCertificateSignatureResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleCheckStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setVerifyResult(null);
    try {
      setStatusResult(await getCertificateStatus(serialNumber));
    } catch (err) {
      setStatusResult(null);
      setError('No certificate found with that serial number.');
    } finally {
      setBusy(false);
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setVerifyResult(await verifyCertificateSignature(serialNumber, payload, signature));
    } catch (err) {
      setVerifyResult(null);
      setError('Could not verify this signature.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ marginTop: '20px', paddingTop: '20px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Verify a certificate</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
        Check whether a certificate serial number is still active, or verify a document someone signed with theirs.
      </p>
      <form onSubmit={handleCheckStatus} style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
        <input
          value={serialNumber} onChange={(e) => { setSerialNumber(e.target.value); setStatusResult(null); setVerifyResult(null); }}
          placeholder="Serial number" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn" disabled={busy}>{busy ? 'Checking…' : 'Check status'}</button>
      </form>
      {statusResult && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '10px' }}>
          Status: <strong>{statusResult.status}</strong> · Expires {new Date(statusResult.expiresAt).toLocaleDateString()}
        </p>
      )}

      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginTop: '16px', marginBottom: '8px' }}>Verify a signature</h4>
      <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <input
          value={payload} onChange={(e) => { setPayload(e.target.value); setVerifyResult(null); }}
          placeholder="Payload (the exact text they signed)" required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          value={signature} onChange={(e) => { setSignature(e.target.value); setVerifyResult(null); }}
          placeholder="Signature (base64)" required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy || !serialNumber}>
          {busy ? 'Verifying…' : 'Verify signature'}
        </button>
      </form>
      {verifyResult && (
        <div style={{ marginTop: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: verifyResult.signatureValid ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
            {verifyResult.signatureValid ? '✓ Signature is valid' : '✗ Signature does not match'}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Certificate status: {verifyResult.certificateStatus}</p>
        </div>
      )}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}
