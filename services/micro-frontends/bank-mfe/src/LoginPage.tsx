import { useEffect, useState } from 'react';
import { IconShieldCheck } from './icons/ItundaIcons';
import { ApiError, getRememberedPhoneNumber, login, tryPasswordlessLogin } from './lib/api';
import { PinPad } from './PinPad';
import { useI18n } from './i18n/I18nContext';
import { LOCALES } from './i18n/translations';

// Real Toss-sourced passwordless-login rollout (2026-08-24, direct user follow-up
// "we need that simplification" after real sourced Toss research) -- see backend
// AuthService's own doc comment for the full account. Phone number + free-form
// password fields replaced with phone number + a real 6-digit PIN pad (matching
// Toss's own literal "6자리 비밀번호"), and this screen now attempts a real
// passwordless login FIRST (tryPasswordlessLogin, silent, against this device's
// already-registered key) before ever asking for the PIN at all -- the actual "no
// PIN needed on a recognized device" outcome, only falling through to the PIN pad
// below when there's no remembered phone number, no device key, or the attempt fails.
export default function LoginPage({ onLogin, onCreateAccount }: { onLogin: () => void; onCreateAccount: () => void }) {
  const { t, locale, setLocale } = useI18n();
  const [phoneNumber, setPhoneNumber] = useState(() => getRememberedPhoneNumber() ?? '');
  // Real two-step flow: a remembered phone number goes straight to the PIN pad
  // (tapping the phone number itself, shown below the pad, goes back to re-enter it).
  const [phoneConfirmed, setPhoneConfirmed] = useState(() => getRememberedPhoneNumber() !== null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real silent attempt on mount, before the user has touched anything -- a genuine
  // "checking this device" moment, not a dead loading spinner: if it succeeds, onLogin
  // fires and this whole screen is never actually seen.
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
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError(t('login.connectionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (checkingDevice) {
    return (
      <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('login.checkingDevice')}</p>
      </div>
    );
  }

  // Real Toss-sourced two-step flow: phone number, THEN the real 6-digit PIN pad --
  // never both on one form, matching the reference's own real screens.
  return (
    <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
      <div
        className="itunda-card"
        style={{ width: '100%', maxWidth: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', marginBottom: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <IconShieldCheck size={24} color="var(--itunda-indigo)" />
            <h1 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Itunda</h1>
          </div>
          {/* Real first language switcher (2026-08-08) -- see src/i18n's own doc comment
              for why this exists: itunda had zero localization anywhere before this,
              despite an explicit financial-inclusion mission in a market where English
              isn't most users' first language. Placed on login specifically since it's
              the one screen a Kinyarwanda-preferring user hits before anything else. */}
          <select
            value={locale}
            onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
            aria-label="Language"
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-700)', background: 'var(--itunda-white)' }}
          >
            {LOCALES.map((l) => (
              <option key={l.code} value={l.code}>{l.label}</option>
            ))}
          </select>
        </div>

        {phoneConfirmed ? (
          <>
            <PinPad label={t('login.enterPin')} onComplete={handlePin} error={error} disabled={submitting} />
            <button
              type="button"
              onClick={() => { setPhoneConfirmed(false); setError(null); }}
              style={{ background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', cursor: 'pointer', alignSelf: 'center' }}
            >
              {phoneNumber}
            </button>
          </>
        ) : (
          <form
            onSubmit={(e) => { e.preventDefault(); setPhoneConfirmed(true); }}
            style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', margin: 0 }}>
              {t('login.tagline')}
            </p>
            <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('login.phoneNumber')}</span>
              {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11,
                  rule #4 -- toss.tech/article/4-ways-for-minimum-input): the very first field on
                  itunda's single highest-traffic screen had no autoFocus, an extra tap before
                  every login. */}
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
                  fontSize: 'var(--itunda-type-scale-15-size)',
                }}
              />
            </label>
            <button type="submit" className="itunda-btn itunda-btn-primary">
              {t('login.next')}
            </button>
            <button
              type="button"
              onClick={onCreateAccount}
              style={{ background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', cursor: 'pointer' }}
            >
              {t('login.createAccount')}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
