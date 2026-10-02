import { useEffect, useState } from 'react';
import { motion, useAnimation } from 'framer-motion';
import { Delete } from 'lucide-react';
import { KeypadButton } from '@itunda/design-system-web';

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
    <div className="itunda-pin-pad">
      <p className="itunda-pin-label">{label}</p>
      <motion.div
        animate={digits.length === 6 ? { scale: [1, 1.15, 1] } : shakeControls}
        transition={digits.length === 6 ? { duration: 0.25 } : undefined}
        className="itunda-pin-dots"
        role="status" aria-label={`${digits.length} of 6 digits entered`}
      >
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className={`itunda-pin-dot${i < digits.length ? ' is-filled' : ''}${error ? ' has-error' : ''}`}
          />
        ))}
      </motion.div>
      {error && <p className="itunda-pin-error" role="alert">{error}</p>}
      <div className="itunda-pin-grid">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((n) => (
          <KeypadButton key={n} onClick={() => press(n)} disabled={disabled} aria-label={`Enter ${n}`}>{n}</KeypadButton>
        ))}
        <div />
        <KeypadButton onClick={() => press('0')} disabled={disabled} aria-label="Enter 0">0</KeypadButton>
        <KeypadButton onClick={backspace} disabled={disabled} aria-label="Delete" tone="delete"><Delete size={22} aria-hidden="true" /></KeypadButton>
      </div>
    </div>
  );
}
