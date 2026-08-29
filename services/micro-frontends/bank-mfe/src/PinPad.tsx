import { useEffect, useState } from 'react';
import { motion, useAnimation } from 'framer-motion';
import { Delete } from 'lucide-react';

// Real Toss-sourced 6-digit PIN pad (2026-08-24) -- see backend AuthService's own doc
// comment on the sourced Toss flow this replaces free-form password fields with.
// Real Toss UX: a real on-screen numeric keypad, never the OS text keyboard -- faster
// to tap, and never accidentally exposes the OS's own saved-password/autofill UI over
// what's now a real 6-digit credential, not a free-form password. Six filled/empty
// dots track progress; onComplete fires exactly once per 6 real digits entered.
export function PinPad({ label, onComplete, error, disabled }: { label: string; onComplete: (pin: string) => void; error?: string | null; disabled?: boolean }) {
  const [digits, setDigits] = useState('');
  const shakeControls = useAnimation();

  // Real reset-on-error (2026-08-24): a wrong PIN should let the user immediately
  // retry from a blank pad, not stare at 6 already-filled dots with no way back
  // except tapping delete 6 times. Real Toss-style shake added on top (2026-08-29,
  // 60fps.design's own real catalog of Toss's named interactions) -- matches the
  // shake Android/iOS's own PinPad ports already had; web never did until now.
  useEffect(() => {
    if (error) {
      setDigits('');
      shakeControls.start({ x: [0, -8, 8, -8, 8, 0], transition: { duration: 0.4 } });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error]);

  const press = (digit: string) => {
    if (disabled || digits.length >= 6) return;
    const next = digits + digit;
    setDigits(next);
    if (next.length === 6) {
      onComplete(next);
      // Real Toss-style brief "confirming" pulse (see the dots' own scale
      // animation below) before clearing -- previously cleared in the same
      // synchronous tick, so the 6th filled dot was never actually visible.
      setTimeout(() => setDigits(''), 180);
    }
  };

  const backspace = () => {
    if (disabled) return;
    setDigits((d) => d.slice(0, -1));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 600, color: 'var(--itunda-grey-700)', margin: 0 }}>{label}</p>
      <motion.div
        animate={digits.length === 6 ? { scale: [1, 1.15, 1] } : shakeControls}
        transition={digits.length === 6 ? { duration: 0.25 } : undefined}
        style={{ display: 'flex', gap: '14px' }}
        role="status" aria-label={`${digits.length} of 6 digits entered`}
      >
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            style={{
              width: '14px', height: '14px', borderRadius: '999px',
              backgroundColor: i < digits.length ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)',
              border: error ? '1px solid var(--itunda-red)' : 'none',
            }}
          />
        ))}
      </motion.div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 64px)', gap: '12px', marginTop: '8px' }}>
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((n) => (
          <button
            key={n}
            type="button"
            onClick={() => press(n)}
            disabled={disabled}
            style={{ width: '64px', height: '64px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 600, backgroundColor: 'var(--itunda-grey-100)', color: 'var(--itunda-grey-900)' }}
          >
            {n}
          </button>
        ))}
        <div />
        <button
          type="button"
          onClick={() => press('0')}
          disabled={disabled}
          style={{ width: '64px', height: '64px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 600, backgroundColor: 'var(--itunda-grey-100)', color: 'var(--itunda-grey-900)' }}
        >
          0
        </button>
        <button
          type="button"
          onClick={backspace}
          disabled={disabled}
          aria-label="Delete"
          style={{ width: '64px', height: '64px', borderRadius: '999px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--itunda-grey-500)' }}
        >
          <Delete size={22} />
        </button>
      </div>
    </div>
  );
}
