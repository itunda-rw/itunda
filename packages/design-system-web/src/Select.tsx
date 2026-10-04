import React from 'react';

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
