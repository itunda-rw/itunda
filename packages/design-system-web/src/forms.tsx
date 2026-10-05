import React from 'react';

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

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  options: SelectOption[];
  helpText?: string;
  error?: string;
  success?: string;
  required?: boolean;
  placeholder?: string;
}

export function Select({
  id,
  label,
  options,
  helpText,
  error,
  success,
  required,
  placeholder,
  className = '',
  ...props
}: SelectProps) {
  const reactId = React.useId();
  const selectId = id ?? `ids-select-${reactId}`;
  const helpId = helpText ? `${selectId}-help` : undefined;
  const errorId = error ? `${selectId}-error` : undefined;
  const successId = success ? `${selectId}-success` : undefined;
  const describedBy = [helpId, errorId, successId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`ids-field ${className}`.trim()}>
      <label className="ids-field__label" htmlFor={selectId}>
        <span>{label}</span>
        {required ? <span className="ids-field__required" aria-hidden="true">*</span> : null}
      </label>
      <div className="ids-select__control">
        <select
          {...props}
          id={selectId}
          className="ids-field__input ids-select__input"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          aria-required={required || undefined}
        >
          {placeholder ? (
            <option value="" disabled={Boolean(props.value || props.defaultValue)}>
              {placeholder}
            </option>
          ) : null}
          {options.map(option => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
        <span className="ids-select__chevron" aria-hidden="true">⌄</span>
      </div>
      {error ? <p id={errorId} className="ids-field__message ids-field__message--error" role="alert">{error}</p> : null}
      {!error && success ? <p id={successId} className="ids-field__message ids-field__message--success">{success}</p> : null}
      {!error && !success && helpText ? <p id={helpId} className="ids-field__message">{helpText}</p> : null}
    </div>
  );
}
