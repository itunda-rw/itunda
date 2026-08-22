import { useEffect, useState } from 'react';
import { overlay } from 'overlay-kit';

// Real Toss TDS Toast component (Simplicity research, docs/DESIGN_REFERENCES.md
// Section 14): brief, auto-dismissing feedback -- 3000ms default, 5000ms when it
// carries an action button -- announced via aria-live rather than a persistent
// banner the user has to dismiss themselves. itunda's own real pattern before this
// was silence: a successful deposit/transfer/goal-creation/claim anywhere in this
// file showed NO confirmation at all, and the one existing exception
// (UssdSettingsView's own local `success` boolean) never auto-dismisses either --
// both real, cross-cutting divergences from Toss's own spec, not specific to one
// screen. Built on `overlay-kit` (already installed, already wired at App.tsx's
// root via <OverlayProvider>, but never actually called anywhere until this) rather
// than a new bespoke portal/context.
//
// Real Toss placement is bottom-of-screen, a single toast at a time (a new one
// replaces whatever's still showing, matching the real product's own behavior --
// stacking transient confirmations reads as noisy, not informative).

let activeToastId: string | null = null;

function ToastPill({
  message,
  actionLabel,
  onAction,
  isOpen,
  close,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  isOpen: boolean;
  close: () => void;
}) {
  // overlay-kit's own isOpen is already true on first render (no mount->open
  // transition of its own), so a CSS transition keyed only on isOpen would only
  // ever animate the EXIT (close() flips it false), popping in instantly on
  // entry. This local flag, flipped one frame after mount, gives the entrance
  // slide-up/fade-in the same real transition the exit already gets.
  const [entered, setEntered] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setEntered(true));
    const timeout = setTimeout(close, actionLabel ? 5000 : 3000);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(timeout);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const shown = isOpen && entered;

  return (
    <div
      style={{
        position: 'fixed',
        left: '50%',
        bottom: 'max(24px, env(safe-area-inset-bottom))',
        transform: `translateX(-50%) translateY(${shown ? '0' : '12px'})`,
        opacity: shown ? 1 : 0,
        transition: 'transform 0.22s cubic-bezier(0.25, 0.1, 0.25, 1), opacity 0.22s ease',
        zIndex: 2147483000,
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        maxWidth: 'min(420px, calc(100vw - 32px))',
        padding: '14px 18px',
        borderRadius: '14px',
        // Deliberately a fixed literal, not var(--itunda-grey-900) -- that token is
        // semantic text-primary (dark-on-light in light mode, but WHITE in dark mode,
        // since it's "the highest-contrast text color," not "a dark neutral"). A
        // toast needs the OPPOSITE: one fixed dark surface regardless of the app's
        // own theme, for consistent max-contrast ephemeral visibility against
        // whatever's behind it (matches Toss's own real toast, which doesn't flip
        // color with the page). Caught live: this first shipped using the token and
        // rendered a white-on-white pill in dark mode.
        background: '#191f28',
        boxShadow: '0 8px 24px rgba(0,0,0,0.24)',
        pointerEvents: shown ? 'auto' : 'none',
      }}
      role="status"
      aria-live="polite"
    >
      <span style={{ color: '#ffffff', fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, lineHeight: 1.4 }}>
        {message}
      </span>
      {actionLabel && (
        <button
          type="button"
          onClick={() => {
            onAction?.();
            close();
          }}
          style={{
            flexShrink: 0,
            background: 'none',
            border: 'none',
            padding: 0,
            color: 'var(--itunda-indigo-active)',
            fontSize: 'var(--itunda-type-scale-14-size)',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export function showToast(message: string, options?: { actionLabel?: string; onAction?: () => void }) {
  if (activeToastId) {
    overlay.unmount(activeToastId);
  }
  activeToastId = overlay.open(({ isOpen, close, unmount }) => (
    <ToastPill
      message={message}
      actionLabel={options?.actionLabel}
      onAction={options?.onAction}
      isOpen={isOpen}
      close={() => {
        close();
        // overlay-kit keeps the controller mounted through its own close
        // animation window before calling back -- give the fade-out transition
        // above time to run rather than yanking the node out mid-transition.
        setTimeout(unmount, 220);
      }}
    />
  ));
}
