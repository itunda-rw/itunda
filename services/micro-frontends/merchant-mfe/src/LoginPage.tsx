import { useState } from 'react';
import { Eye, EyeOff, Store } from 'lucide-react';
import { ApiError, login } from './lib/api';
import { useI18n } from './i18n/I18nContext';
import { LOCALES } from './i18n/translations';

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const { t, locale, setLocale } = useI18n();
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11),
  // matching the identical same-day fix on bank-mfe's LoginPage.tsx: a local UI-only
  // affordance, not a security control.
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(phoneNumber, password);
      onLogin();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(t('login.connectionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <form
        onSubmit={handleSubmit}
        className="itunda-card"
        style={{ width: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', marginBottom: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Store size={24} color="var(--itunda-blue)" />
            <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Itunda Business</h1>
          </div>
          {/* Real first language switcher for merchant-mfe (2026-08-15) -- see
              src/i18n's own doc comment: itunda's merchant/agent network is arguably
              even more locally-Rwandan-first than bank-mfe's own customer base, making
              this at least as high-value a target, not an afterthought. Placed on
              login specifically, same reasoning bank-mfe's own switcher used. */}
          <select
            value={locale}
            onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
            aria-label="Language"
            style={{ fontSize: '12px', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-700)', background: '#fff' }}
          >
            {LOCALES.map((l) => (
              <option key={l.code} value={l.code}>{l.label}</option>
            ))}
          </select>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginTop: '-8px' }}>
          {t('login.tagline')}
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('login.phoneNumber')}</span>
          {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11,
              rule #4), matching the identical same-day fix on bank-mfe's LoginPage.tsx. */}
          <input
            type="tel"
            autoFocus
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            placeholder="+250788123456"
            required
            style={{
              padding: '12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--itunda-grey-200)',
              fontSize: '15px',
            }}
          />
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('login.password')}</span>
          <div style={{ position: 'relative' }}>
          <input
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={{
              width: '100%',
              boxSizing: 'border-box',
              padding: '12px 40px 12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--itunda-grey-200)',
              fontSize: '15px',
            }}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
            style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--itunda-grey-500)' }}
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
          </div>
        </label>

        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? t('login.signingIn') : t('login.signIn')}
        </button>
      </form>
    </div>
  );
}
