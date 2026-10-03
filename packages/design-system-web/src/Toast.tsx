import React from 'react';

export interface ToastProps {
  message: React.ReactNode;
  actionLabel?: string;
  onAction?: () => void;
  role?: 'status' | 'alert';
  className?: string;
}

/** Canonical IDS transient feedback surface. Position/lifecycle is owned by the host; visual treatment is owned by IDS. */
export function Toast({ message, actionLabel, onAction, role = 'status', className = '' }: ToastProps) {
  return (
    <div className={['ids-toast', className].filter(Boolean).join(' ')} role={role} aria-live={role === 'alert' ? 'assertive' : 'polite'}>
      <span className="ids-toast__message">{message}</span>
      {actionLabel ? (
        <button type="button" className="ids-toast__action" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </div>
  );
}
