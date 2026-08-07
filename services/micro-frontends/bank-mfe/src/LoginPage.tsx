import { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { ApiError, login } from './lib/api';

export default function LoginPage({ onLogin, onCreateAccount }: { onLogin: () => void; onCreateAccount: () => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
        className="toss-card"
        style={{ width: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <ShieldCheck size={24} color="var(--toss-blue)" />
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>Itunda</h1>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginTop: '-8px' }}>
          Sign in to your Itunda account.
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Phone number</span>
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
              border: '1px solid var(--toss-grey-200)',
              fontSize: '15px',
            }}
          />
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={{
              padding: '12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--toss-grey-200)',
              fontSize: '15px',
            }}
          />
        </label>

        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <button
          type="button"
          onClick={onCreateAccount}
          style={{ background: 'none', border: 'none', fontSize: '13px', color: 'var(--toss-grey-500)', cursor: 'pointer' }}
        >
          New to itunda? Create an account
        </button>
      </form>
    </div>
  );
}
