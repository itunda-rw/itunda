import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
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
    >
      {loading ? <span className="ids-button__spinner" aria-hidden="true" /> : null}
      <span className="ids-button__label">{children}</span>
    </button>
  );
}

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string;
  helpText?: string;
  error?: string;
  required?: boolean;
}

export function TextField({
  id,
  label,
  helpText,
  error,
  required,
  className = '',
  ...props
}: TextFieldProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-field-${reactId}`;
  const helpId = helpText ? `${inputId}-help` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [helpId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`ids-field ${className}`.trim()}>
      <label className="ids-field__label" htmlFor={inputId}>
        <span>{label}</span>
        {required ? <span className="ids-field__required" aria-hidden="true">*</span> : null}
      </label>
      <input
        {...props}
        id={inputId}
        className="ids-field__input"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        aria-required={required || undefined}
      />
      {error ? <p id={errorId} className="ids-field__message ids-field__message--error" role="alert">{error}</p> : null}
      {!error && helpText ? <p id={helpId} className="ids-field__message">{helpText}</p> : null}
    </div>
  );
}
