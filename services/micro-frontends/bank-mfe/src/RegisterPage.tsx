import { useState } from 'react';
import { Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { ApiError, register } from './lib/api';

// Real sign-up page (2026-08-04) -- closes docs/DESIGN_REFERENCES.md Section 8
// recommendation #7: bank-mfe had no registration page at all, unlike Android/iOS's
// real 3-step phone -> name -> password flow (LoginScreen.kt/.swift). Mirrors that same
// field set and step order on one scrolling form rather than a multi-step wizard --
// LoginPage.tsx's own single-form pattern already fits this content amount, and Toss's
// own real-name signup redesign (cited in this doc's "One thing, one page" section)
// explicitly favors a single reverse-stacking scroll over multiple "next"-tap screens
// for a form this size.
export default function RegisterPage({ onRegistered, onBackToLogin }: { onRegistered: () => void; onBackToLogin: () => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [password, setPassword] = useState('');
  const [referralCode, setReferralCode] = useState('');
  const [showReferralField, setShowReferralField] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11),
  // matching the identical same-day fix on LoginPage.tsx: a local UI-only affordance,
  // not a security control. Especially valuable here -- a mistyped password at
  // registration silently locks the account behind a typo neither the user nor
  // itunda can recover.
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(phoneNumber, password, firstName, lastName, referralCode);
      onRegistered();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Can't connect right now. Please try again in a moment.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <form
        onSubmit={handleSubmit}
        className="toss-card"
        style={{ width: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <ShieldCheck size={24} color="var(--toss-blue)" />
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>Itunda</h1>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginTop: '-8px' }}>
          Create your Itunda account.
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Phone number</span>
          {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11,
              rule #4), matching the identical same-day fix on LoginPage.tsx. */}
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
              border: '1px solid var(--toss-grey-200)',
              fontSize: '15px',
            }}
          />
        </label>

        <div style={{ display: 'flex', gap: '10px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>First name</span>
            <input
              type="text"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--toss-grey-200)',
                fontSize: '15px',
              }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Last name</span>
            <input
              type="text"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--toss-grey-200)',
                fontSize: '15px',
              }}
            />
          </label>
        </div>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Password</span>
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
              border: '1px solid var(--toss-grey-200)',
              fontSize: '15px',
            }}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--toss-grey-500)' }}
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
          </div>
        </label>

        {showReferralField ? (
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Referral code (optional)</span>
            <input
              type="text"
              value={referralCode}
              onChange={(e) => setReferralCode(e.target.value)}
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--toss-grey-200)',
                fontSize: '15px',
              }}
            />
          </label>
        ) : (
          <button
            type="button"
            onClick={() => setShowReferralField(true)}
            style={{ background: 'none', border: 'none', padding: 0, textAlign: 'left', fontSize: '13px', color: 'var(--toss-blue)', cursor: 'pointer' }}
          >
            Have a referral code?
          </button>
        )}

        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>

        <button
          type="button"
          onClick={onBackToLogin}
          style={{ background: 'none', border: 'none', fontSize: '13px', color: 'var(--toss-grey-500)', cursor: 'pointer' }}
        >
          Already have an account? Log in
        </button>
      </form>
    </div>
  );
}
