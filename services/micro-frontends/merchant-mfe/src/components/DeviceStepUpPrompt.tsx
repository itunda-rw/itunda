import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { ApiError } from '../lib/api';
import { verifyDevice } from '../lib/device';

// Real device step-up (2026-07-28 port) -- shown when a money-moving action
// (chargeCard, runPayroll) real-403s with DEVICE_NOT_VERIFIED. Re-proves password
// ownership on THIS device (resolved server-side from the caller's own JWT, never a
// client-supplied id) and marks it trusted, matching bank-mfe's identical component
// and the same real re-verification Toss requires before a new device can move money.
export function DeviceStepUpPrompt({ onVerified, onCancel }: { onVerified: () => void; onCancel: () => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not verify this device.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>🔒 Verify this device</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        This is a new device for your account. Re-enter your password to allow it to move money, then try again.
      </p>
      {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11, rule
          #4), matching the identical same-day fix on bank-mfe's DeviceStepUpPrompt. */}
      <div style={{ position: 'relative' }}>
        <input
          type={showPassword ? 'text' : 'password'}
          autoFocus
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Password"
          required
          style={{ width: '100%', boxSizing: 'border-box', padding: '10px 40px 10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
        <button
          type="button"
          onClick={() => setShowPassword((v) => !v)}
          aria-label={showPassword ? 'Hide password' : 'Show password'}
          style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--toss-grey-500)' }}
        >
          {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && <p style={{ fontSize: '12px', color: 'var(--toss-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>
          {busy ? 'Verifying…' : 'Verify device'}
        </button>
      </div>
    </form>
  );
}
