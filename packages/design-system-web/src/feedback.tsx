import React from 'react';

export interface KeypadButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: 'default' | 'delete';
}

export function KeypadButton({ tone = 'default', className = '', type = 'button', ...props }: KeypadButtonProps) {
  return <button {...props} type={type} className={[
    'ids-keypad-button',
    tone === 'delete' ? 'ids-keypad-button--delete' : '',
    className,
  ].filter(Boolean).join(' ')} />;
}

export interface BadgeProps {
  text: React.ReactNode;
  tone?: 'brand' | 'neutral' | 'success' | 'danger' | 'warning';
  variant?: 'filled' | 'soft';
  className?: string;
}

export function Badge({ text, tone = 'brand', variant = 'filled', className = '' }: BadgeProps) {
  return <span className={['ids-badge', `ids-badge--${variant}`, `ids-badge--${tone}`, className].filter(Boolean).join(' ')}>{text}</span>;
}


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
