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

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string;
  helpText?: string;
  error?: string;
  success?: string;
  required?: boolean;
  maxLength?: number;
}

export function TextField({
  id,
  label,
  helpText,
  error,
  success,
  required,
  className = '',
  ...props
}: TextFieldProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-field-${reactId}`;
  const helpId = helpText ? `${inputId}-help` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const successId = success ? `${inputId}-success` : undefined;
  const describedBy = [helpId, errorId, successId].filter(Boolean).join(' ') || undefined;

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
        aria-errormessage={error ? errorId : undefined}
      />
      {error ? <p id={errorId} className="ids-field__message ids-field__message--error" role="alert">{error}</p> : null}
      {!error && success ? <p id={successId} className="ids-field__message ids-field__message--success">{success}</p> : null}
      {!error && !success && helpText ? <p id={helpId} className="ids-field__message">{helpText}</p> : null}
    </div>
  );
}


export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  options: Array<{ value: string; label: string }>;
  helpText?: string;
  error?: string;
}

export function Select({ id, label, options, helpText, error, className = '', ...props }: SelectProps) {
  const reactId = React.useId();
  const selectId = id ?? `ids-select-${reactId}`;
  const helpId = helpText ? `${selectId}-help` : undefined;
  const errorId = error ? `${selectId}-error` : undefined;
  const describedBy = [helpId, errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`ids-field ${className}`.trim()}>
      <label className="ids-field__label" htmlFor={selectId}>{label}</label>
      <select {...props} id={selectId} className="ids-field__input" aria-invalid={error ? true : undefined} aria-describedby={describedBy}>
        {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
      {error ? <p id={errorId} className="ids-field__message ids-field__message--error" role="alert">{error}</p> : null}
      {!error && helpText ? <p id={helpId} className="ids-field__message">{helpText}</p> : null}
    </div>
  );
}

export interface CheckControlProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: React.ReactNode;
}

export function Checkbox({ label, id, className = '', ...props }: CheckControlProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-checkbox-${reactId}`;
  return (
    <label className={`ids-check ${className}`.trim()} htmlFor={inputId}>
      <input {...props} id={inputId} type="checkbox" />
      <span className="ids-check__box" aria-hidden="true" />
      <span className="ids-check__label">{label}</span>
    </label>
  );
}

export function Radio({ label, id, className = '', ...props }: CheckControlProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-radio-${reactId}`;
  return (
    <label className={`ids-check ${className}`.trim()} htmlFor={inputId}>
      <input {...props} id={inputId} type="radio" />
      <span className="ids-check__radio" aria-hidden="true" />
      <span className="ids-check__label">{label}</span>
    </label>
  );
}

export interface SwitchProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
}

export function Switch({ label, id, className = '', ...props }: SwitchProps) {
  const reactId = React.useId();
  const inputId = id ?? `ids-switch-${reactId}`;
  return (
    <label className={`ids-switch ${className}`.trim()} htmlFor={inputId}>
      <input {...props} id={inputId} type="checkbox" role="switch" />
      <span className="ids-switch__track" aria-hidden="true"><span className="ids-switch__thumb" /></span>
      <span className="ids-switch__label">{label}</span>
    </label>
  );
}

export interface TabsProps {
  items: Array<{ id: string; label: string }>;
  value: string;
  onChange: (id: string) => void;
  ariaLabel?: string;
}

export function Tabs({ items, value, onChange, ariaLabel = 'Tabs' }: TabsProps) {
  const refs = React.useRef<Array<HTMLButtonElement | null>>([]);
  const move = (index: number) => {
    const next = (index + items.length) % items.length;
    onChange(items[next].id);
    requestAnimationFrame(() => refs.current[next]?.focus());
  };
  return (
    <div className="ids-tabs" role="tablist" aria-label={ariaLabel} onKeyDown={(event) => {
      if (!items.length) return;
      const index = items.findIndex(item => item.id === value);
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') { event.preventDefault(); move(index + 1); }
      if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') { event.preventDefault(); move(index - 1); }
      if (event.key === 'Home') { event.preventDefault(); move(0); }
      if (event.key === 'End') { event.preventDefault(); move(items.length - 1); }
    }}>
      {items.map((item, index) => (
        <button
          key={item.id}
          ref={node => { refs.current[index] = node; }}
          type="button"
          role="tab"
          aria-selected={value === item.id}
          tabIndex={value === item.id ? 0 : -1}
          className={`ids-tab ${value === item.id ? 'ids-tab--active' : ''}`}
          onClick={() => onChange(item.id)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}


export interface EmptyStateProps {
  title: string;
  message?: string;
  actionText?: string;
  onAction?: () => void;
}

export function EmptyState({ title, message, actionText, onAction }: EmptyStateProps) {
  return (
    <section className="ids-empty-state" aria-label={title}>
      <div className="ids-empty-state__icon" aria-hidden="true">○</div>
      <h2 className="ids-empty-state__title">{title}</h2>
      {message ? <p className="ids-empty-state__message">{message}</p> : null}
      {actionText && onAction ? (
        <Button onClick={onAction} variant="secondary" size="md">{actionText}</Button>
      ) : null}
    </section>
  );
}
