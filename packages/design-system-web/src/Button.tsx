import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  loadingLabel?: string;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  loadingLabel = 'Loading',
  disabled,
  children,
  className = '',
  type = 'button',
  ...props
}: ButtonProps) {
  const classes = [
    'ids-button',
    `ids-button--${variant}`,
    `ids-button--${size}`,
    fullWidth ? 'ids-button--full' : '',
    loading ? 'ids-button--loading' : '',
    className,
  ].filter(Boolean).join(' ');

  return (
    <button
      {...props}
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-disabled={loading || disabled || undefined}
    >
      {loading ? <span className="ids-button__spinner" aria-hidden="true" /> : null}
      <span className="ids-button__label">{loading ? loadingLabel : children}</span>
    </button>
  );
}
