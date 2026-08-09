import { useState } from 'react';
import { Eye, EyeOff, Store } from 'lucide-react';
import { ApiError, login } from './lib/api';

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
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
        className="itunda-card"
        style={{ width: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <Store size={24} color="var(--itunda-blue)" />
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Itunda Business</h1>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginTop: '-8px' }}>
          Sign in to your Itunda account.
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Phone number</span>
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
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Password</span>
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
            aria-label={showPassword ? 'Hide password' : 'Show password'}
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
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
