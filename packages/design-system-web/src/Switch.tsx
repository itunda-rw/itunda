import React from 'react';

export interface SwitchProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
  description?: React.ReactNode;
  error?: string;
  loading?: boolean;
}

export function Switch({
  label,
  description,
  error,
  loading = false,
  id,
  className = '',
  disabled,
  ...props
}: SwitchProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-switch-${reactId}`;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;
  const isDisabled = disabled || loading;

  return (
    <div className={`ids-switch-field ${className}`.trim()}>
      <label className="ids-switch" htmlFor={inputId}>
        <input
          {...props}
          id={inputId}
          type="checkbox"
          role="switch"
          disabled={isDisabled}
          aria-busy={loading || undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          aria-errormessage={error ? errorId : undefined}
        />
        <span className="ids-switch__track" aria-hidden="true"><span className="ids-switch__thumb" /></span>
        <span className="ids-switch__content">
          <span className="ids-switch__label">{label}</span>
          {description ? <span id={descriptionId} className="ids-switch__description">{description}</span> : null}
          {loading ? <span className="ids-switch__status" aria-live="polite">Updating…</span> : null}
        </span>
      </label>
      {error ? <p id={errorId} className="ids-switch__error" role="alert">{error}</p> : null}
    </div>
  );
}
