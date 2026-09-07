// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// USSD basic-banking access (own lib/ussd.ts data layer, exactly one external call
// site -- `{tab === 'USSD' && <UssdSettingsView />}`).

import { useState } from 'react';
import { IconEye, IconEyeOff } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { showToast } from './Toast';
import { setUssdPin } from './lib/ussd';


// Real USSD basic-banking access (item 231) -- the fourth feature in this codebase
// not sourced from Toss/당근/Coupang/Naver/Kakao. See lib/ussd.ts's own doc comment
// for the full sourced account (Rwanda's real ~34-35% smartphone penetration).
export function UssdSettingsView() {
  const { t } = useI18n();
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11/§12):
  // a local UI-only affordance, not a security control -- especially useful here since a
  // mismatched PIN only surfaces as an error after submitting both fields.
  const [pinVisible, setPinVisible] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!/^\d{4,6}$/.test(pin)) {
      setError(t('ussd.pinLengthError'));
      return;
    }
    if (pin !== confirmPin) {
      setError(t('ussd.pinMismatchError'));
      return;
    }
    setSubmitting(true);
    try {
      await setUssdPin(pin);
      setPin('');
      setConfirmPin('');
      showToast(t('ussd.saved'));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '4px' }}>{t('ussd.title')}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          {t('ussd.description')}
        </p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          {t('ussd.honestScope')}
        </p>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div style={{ position: 'relative' }}>
            <input
              type={pinVisible ? 'text' : 'password'} inputMode="numeric" value={pin} onChange={(e) => setPin(e.target.value)}
              placeholder={t('ussd.pinPlaceholder')} maxLength={6}
              style={{ width: '100%', boxSizing: 'border-box', padding: '12px 40px 12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
            />
            <button
              type="button"
              onClick={() => setPinVisible((v) => !v)}
              aria-label={pinVisible ? 'Hide PIN' : 'Show PIN'}
              style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--itunda-grey-500)' }}
            >
              {pinVisible ? <IconEyeOff size={18} /> : <IconEye size={18} />}
            </button>
          </div>
          <input
            type={pinVisible ? 'text' : 'password'} inputMode="numeric" value={confirmPin} onChange={(e) => setConfirmPin(e.target.value)}
            placeholder={t('ussd.confirmPinPlaceholder')} maxLength={6}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
          />
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
            {submitting ? t('ussd.saving') : t('ussd.submit')}
          </button>
        </form>
      </div>
    </div>
  );
}
