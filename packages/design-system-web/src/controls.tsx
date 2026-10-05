import React from 'react';

export interface CheckControlProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
  description?: React.ReactNode;
  error?: string;
  indeterminate?: boolean;
}

export function Checkbox({
  label,
  description,
  error,
  indeterminate = false,
  id,
  className = '',
  checked,
  defaultChecked,
  disabled,
  ...props
}: CheckControlProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-checkbox-${reactId}`;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;
  const inputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = indeterminate;
    }
  }, [indeterminate]);

  return (
    <div className={`ids-check-field ${className}`.trim()}>
      <label className="ids-check" htmlFor={inputId}>
        <input
          {...props}
          ref={inputRef}
          id={inputId}
          type="checkbox"
          checked={checked}
          defaultChecked={defaultChecked}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          aria-checked={indeterminate ? 'mixed' : undefined}
          aria-errormessage={error ? errorId : undefined}
        />
        <span className="ids-check__box" aria-hidden="true" />
        <span className="ids-check__content">
          <span className="ids-check__label">{label}</span>
          {description ? <span id={descriptionId} className="ids-check__description">{description}</span> : null}
        </span>
      </label>
      {error ? <p id={errorId} className="ids-check__error" role="alert">{error}</p> : null}
    </div>
  );
}

export function Radio({
  label,
  description,
  error,
  id,
  className = '',
  disabled,
  required,
  ...props
}: CheckControlProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-radio-${reactId}`;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`ids-check-field ${className}`.trim()}>
      <label className="ids-check" htmlFor={inputId}>
        <input
          {...props}
          id={inputId}
          type="radio"
          disabled={disabled}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          aria-errormessage={error ? errorId : undefined}
        />
        <span className="ids-check__radio" aria-hidden="true" />
        <span className="ids-check__content">
          <span className="ids-check__label">{label}</span>
          {description ? <span id={descriptionId} className="ids-check__description">{description}</span> : null}
        </span>
      </label>
      {error ? <p id={errorId} className="ids-check__error" role="alert">{error}</p> : null}
    </div>
  );
}

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
