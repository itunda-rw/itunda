import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { ApiError } from '../lib/api';
import { verifyDevice } from '../lib/device';
import { useI18n } from '../i18n/I18nContext';

// Real device step-up (2026-07-28 port) -- shown when a money-moving action
// (chargeCard, runPayroll) real-403s with DEVICE_NOT_VERIFIED. Re-proves password
// ownership on THIS device (resolved server-side from the caller's own JWT, never a
// client-supplied id) and marks it trusted, matching bank-mfe's identical component
// and the same real re-verification Toss requires before a new device can move money.
export function DeviceStepUpPrompt({ onVerified, onCancel }: { onVerified: () => void; onCancel: () => void }) {
  const { t } = useI18n();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11/§12),
  // matching the identical same-day fix on bank-mfe's DeviceStepUpPrompt: a local UI-only
  // affordance, not a security control.
  const [showPassword, setShowPassword] = useState(false);

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await verifyDevice(password);
      onVerified();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('deviceStepUp.verifyError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{t('deviceStepUp.title')}</p>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('deviceStepUp.body')}
      </p>
      {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11, rule
          #4), matching the identical same-day fix on bank-mfe's DeviceStepUpPrompt. */}
      <div style={{ position: 'relative' }}>
        <input
          type={showPassword ? 'text' : 'password'}
          autoFocus
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder={t('deviceStepUp.passwordPlaceholder')}
          required
          style={{ width: '100%', boxSizing: 'border-box', padding: '10px 40px 10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
        />
        <button
          type="button"
          onClick={() => setShowPassword((v) => !v)}
          aria-label={showPassword ? t('deviceStepUp.hidePassword') : t('deviceStepUp.showPassword')}
          style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--itunda-grey-500)' }}
        >
          {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={onCancel} disabled={busy}>{t('deviceStepUp.cancel')}</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy}>
          {busy ? t('deviceStepUp.verifying') : t('deviceStepUp.verifyButton')}
        </button>
      </div>
    </form>
  );
}
