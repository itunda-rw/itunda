// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread, ARCHITECTURE_GUIDELINES.md §2's "code that changes together
// lives together" rule) -- this component is used across ~20 sites throughout
// BankDashboard.tsx's own money-moving flows (Transfer/Savings/Group Account/
// Stocks/etc), all needing the same real device-verification step-up retry
// prompt. Extracted first, ahead of StocksView.tsx, specifically so that and any
// other future extraction can import this shared prompt without creating a
// circular dependency back on BankDashboard.tsx itself.

import { useState } from 'react';
import { IconEye, IconEyeOff } from './icons/ItundaIcons';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { verifyDevice } from './lib/device';
import { Button, TextField, TextFieldButton } from '@itunda/design-system-web';

export function DeviceStepUpPrompt({ onVerified, onCancel }: { onVerified: () => void; onCancel: () => void }) {
  const { t } = useI18n();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11/§12),
  // matching the identical same-day fix on LoginPage.tsx: a local UI-only affordance, not
  // a security control.
  const [showPassword, setShowPassword] = useState(false);

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await verifyDevice(password);
      onVerified();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', display: 'flex', alignItems: 'center', gap: '6px' }}><LockGlyph size={16} /> {t('deviceStepUp.title')}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        {t('deviceStepUp.body')}
      </p>
      {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11, rule
          #4), matching the identical same-day fix on Android/iOS's device step-up dialog:
          this password field is the sole meaningful action on the entire prompt. */}
      <TextField
        label={t('deviceStepUp.passwordPlaceholder')}
        type={showPassword ? 'text' : 'password'}
        autoFocus
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        error={error ?? undefined}
        suffix={
          <TextFieldButton aria-label={showPassword ? t('deviceStepUp.hidePassword') : t('deviceStepUp.showPassword')} onClick={() => setShowPassword((v) => !v)}>
            {showPassword ? <IconEyeOff size={16} /> : <IconEye size={16} />}
          </TextFieldButton>
        }
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <Button type="button" variant="secondary" size="md" fullWidth onClick={onCancel} disabled={busy}>{t('deviceStepUp.cancel')}</Button>
        <Button type="submit" variant="primary" size="md" fullWidth disabled={busy} loading={busy} loadingLabel={t('deviceStepUp.verifying')}>{t('deviceStepUp.verify')}</Button>
      </div>
    </form>
  );
}
