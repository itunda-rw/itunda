import { useEffect, useState } from 'react';
import { IconShieldCheck, IconItundaLogo } from './icons/ItundaIcons';
import { ApiError, getRememberedPhoneNumber, login, tryPasswordlessLogin } from './lib/api';
import { PinPad } from './PinPad';
import { useI18n } from './i18n/I18nContext';
import { LOCALES } from './i18n/translations';

export default function LoginPage({
  onLogin,
  onCreateAccount,
}: {
  onLogin: () => void;
  onCreateAccount: () => void;
}) {
  const { t, locale, setLocale } = useI18n();
  const [phoneNumber, setPhoneNumber] = useState(() => getRememberedPhoneNumber() ?? '');
  const [phoneConfirmed, setPhoneConfirmed] = useState(() => getRememberedPhoneNumber() !== null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [checkingDevice, setCheckingDevice] = useState(true);

  useEffect(() => {
    const remembered = getRememberedPhoneNumber();
    if (!remembered) {
      setCheckingDevice(false);
      return;
    }
    tryPasswordlessLogin(remembered)
      .then((user) => {
        if (user) onLogin();
        else setCheckingDevice(false);
      })
      .catch(() => setCheckingDevice(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePin = async (pin: string) => {
    setError(null);
    setSubmitting(true);
    try {
      await login(phoneNumber, pin);
      onLogin();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (checkingDevice) {
    return (
      <main className="itunda-auth-page" aria-busy="true">
        <div className="itunda-auth-loading">
          <span className="itunda-brand-mark" aria-hidden="true"><IconItundaLogo size={28} color="currentColor" /></span>
          <span>{t('login.checkingDevice')}</span>
        </div>
      </main>
    );
  }

  return (
    <main className="itunda-auth-page">
      <div className="itunda-auth-orb itunda-auth-orb--one" aria-hidden="true" />
      <div className="itunda-auth-orb itunda-auth-orb--two" aria-hidden="true" />

      <section className="itunda-auth-surface" aria-label="Itunda sign in">
        <header className="itunda-auth-header">
          <div className="itunda-brand">
            <span className="itunda-brand-mark" aria-hidden="true">
              <IconShieldCheck size={22} color="currentColor" />
            </span>
            <span className="itunda-brand-name">Itunda</span>
          </div>

          <label className="itunda-language">
            <span className="sr-only">Language</span>
            <select
              value={locale}
              onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
              aria-label="Language"
            >
              {LOCALES.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
            </select>
          </label>
        </header>

        <div className="itunda-auth-intro">
          <div className="itunda-auth-eyebrow">ITUNDA</div>
          <h1>{phoneConfirmed ? t('login.enterPin') : t('login.tagline')}</h1>
          <p>
            {phoneConfirmed
              ? 'Enter your 6-digit PIN to continue.'
              : 'Your money, payments and everyday services — in one place.'}
          </p>
        </div>

        {phoneConfirmed ? (
          <div className="itunda-auth-flow">
            <PinPad label="" onComplete={handlePin} error={error} disabled={submitting} />
            <button
              type="button"
              className="itunda-auth-account"
              onClick={() => { setPhoneConfirmed(false); setError(null); }}
            >
              <span>{phoneNumber}</span>
              <span aria-hidden="true">Change</span>
            </button>
          </div>
        ) : (
          <form
            className="itunda-auth-form"
            onSubmit={(e) => { e.preventDefault(); setPhoneConfirmed(true); }}
          >
            <label className="itunda-field">
              <span className="itunda-field__label">{t('login.phoneNumber')}</span>
              <input
                className="itunda-field__control itunda-phone-control"
                type="tel"
                autoFocus
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="+250 788 123 456"
                required
                autoComplete="tel"
                inputMode="tel"
              />
            </label>

            <button type="submit" className="itunda-btn itunda-btn-primary itunda-auth-primary">
              {t('login.next')}
            </button>

            <button type="button" className="itunda-auth-secondary" onClick={onCreateAccount}>
              {t('login.createAccount')}
            </button>
          </form>
        )}

        <footer className="itunda-auth-footer">
          <IconShieldCheck size={16} color="var(--itunda-brand)" />
          <span>Secure sign-in with Itunda</span>
        </footer>
      </section>
    </main>
  );
}
