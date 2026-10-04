import React from 'react';

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string;
  helpText?: string;
  error?: string;
  success?: string;
  required?: boolean;
  clearable?: boolean;
  onClear?: () => void;
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
  showCharacterCount?: boolean;
  multiline?: boolean;
  rows?: number;
}

export interface TextFieldButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
}

export function TextFieldButton({ children, className = '', type = 'button', ...props }: TextFieldButtonProps) {
  return (
    <button {...props} type={type} className={`ids-field__button ${className}`.trim()}>
      {children}
    </button>
  );
}

export function TextField({
  id,
  label,
  helpText,
  error,
  success,
  required,
  clearable = false,
  onClear,
  prefix,
  suffix,
  showCharacterCount = false,
  multiline = false,
  rows = 4,
  className = '',
  value,
  defaultValue,
  maxLength,
  disabled,
  readOnly,
  ...props
}: TextFieldProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-field-${reactId}`;
  const helpId = helpText ? `${inputId}-help` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const successId = success ? `${inputId}-success` : undefined;
  const countId = showCharacterCount && maxLength ? `${inputId}-count` : undefined;
  const describedBy = [helpId, errorId, successId, countId].filter(Boolean).join(' ') || undefined;
  const currentLength = String(value ?? defaultValue ?? '').length;
  const canClear = clearable && !disabled && !readOnly && currentLength > 0 && onClear;

  const fieldProps = {
    ...props,
    id: inputId,
    className: 'ids-field__input',
    value,
    defaultValue,
    maxLength,
    disabled,
    readOnly,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
    'aria-required': required || undefined,
    'aria-errormessage': error ? errorId : undefined,
  };

  const control = multiline
    ? <textarea {...fieldProps} rows={rows} />
    : <input {...fieldProps} />;

  return (
    <div className={`ids-field ${className}`.trim()}>
      <label className="ids-field__label" htmlFor={inputId}>
        <span>{label}</span>
        {required ? <span className="ids-field__required" aria-hidden="true">*</span> : null}
      </label>

      <div className={`ids-field__control ${multiline ? 'ids-field__control--multiline' : ''}`.trim()}>
        {prefix ? <span className="ids-field__prefix">{prefix}</span> : null}
        {control}
        {canClear ? (
          <button
            type="button"
            className="ids-field__clear"
            aria-label={`Clear ${label}`}
            onClick={onClear}
          >
            <span aria-hidden="true">×</span>
          </button>
        ) : null}
        {suffix ? <span className="ids-field__suffix">{suffix}</span> : null}
      </div>

      <div className="ids-field__meta">
        <div>
          {error ? <p id={errorId} className="ids-field__message ids-field__message--error" role="alert">{error}</p> : null}
          {!error && success ? <p id={successId} className="ids-field__message ids-field__message--success">{success}</p> : null}
          {!error && !success && helpText ? <p id={helpId} className="ids-field__message">{helpText}</p> : null}
        </div>
        {showCharacterCount && maxLength ? (
          <span id={countId} className="ids-field__count" aria-live="polite">
            {currentLength}/{maxLength}
          </span>
        ) : null}
      </div>
    </div>
  );
}
