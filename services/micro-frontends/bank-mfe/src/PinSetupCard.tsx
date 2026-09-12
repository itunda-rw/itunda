import { useEffect, useState } from 'react';
import { ApiError, setPin } from './lib/api';
import { fetchProfile } from './lib/neighborhood';
import { PinPad } from './PinPad';
import { useI18n } from './i18n/I18nContext';

// Real Toss-sourced passwordless-login rollout (2026-08-24) -- see backend
// User.pinSet's own doc comment. A real, non-blocking upgrade prompt for a
// pre-PIN-era user -- their existing password keeps working exactly as before either
// way (AuthService.login is shape-agnostic); this is purely an offered convenience,
// never forced. `currentCredential` is a plain text field (their existing password
// could be any shape, not necessarily 6 digits, so PinPad doesn't apply there);
// `newPin`/confirm reuse the real PinPad component RegisterPage/LoginPage already
// established. Extracted to its own file (not inline in BankDashboard.tsx) matching
// this session's own established file-size-lint discipline for new, self-contained
// pieces (PayHomeExtras.tsx, AccountManageScreen.tsx).
export function PinSetupCard() {
  const { t } = useI18n();
  const [pinSet, setPinSetState] = useState<boolean | null>(null);
  // Explicit step state (not inferred from field values) -- keeps this a flat
  // if/else-if chain below rather than a nested ternary, matching this codebase's
  // own no-nested-ternary rule.
  const [step, setStep] = useState<'closed' | 'credential' | 'pin' | 'confirm' | 'success'>('closed');
  const [currentCredential, setCurrentCredential] = useState('');
  const [newPin, setNewPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchProfile().then((u) => setPinSetState(u.pinSet)).catch(() => {});
  }, []);

  if (pinSet !== false) return null;

  const submitCurrentCredential = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentCredential.trim()) return;
    setError(null);
    setStep('pin');
  };

  const handleFirstPin = (pin: string) => {
    setNewPin(pin);
    setStep('confirm');
  };

  const handleConfirmPin = async (pin: string) => {
    if (pin !== newPin) {
      setError("That didn't match. Try again.");
      setStep('pin');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await setPin(currentCredential, pin);
      setStep('success');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setStep('pin');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      {step === 'success' ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green)' }}>Your 6-digit PIN is set — use it to sign in next time.</p>
      ) : (
        <>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Set your 6-digit PIN</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            A quick 6-digit PIN replaces typing your password to sign in — the same real
            simplification Toss uses.
          </p>
        </>
      )}
      {step === 'closed' && <button className="itunda-btn itunda-btn-secondary" onClick={() => setStep('credential')}>Set up my PIN</button>}
      {step === 'credential' && (
        <form onSubmit={submitCurrentCredential} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="password"
            placeholder="Your current password"
            value={currentCredential}
            onChange={(e) => setCurrentCredential(e.target.value)}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary">Continue</button>
        </form>
      )}
      {step === 'pin' && <PinPad label="Create a 6-digit PIN" onComplete={handleFirstPin} error={error} disabled={busy} />}
      {step === 'confirm' && <PinPad label="Confirm your PIN" onComplete={handleConfirmPin} error={error} disabled={busy} />}
    </div>
  );
}
