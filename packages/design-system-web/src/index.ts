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
        {suffix ? <span className="ids-field__suffix">{suffix}</span> : null}
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

export interface TabsProps {
  items: Array<{ id: string; label: string; disabled?: boolean }>;
  value: string;
  onChange: (id: string) => void;
  ariaLabel?: string;
  tabPanelId?: string;
}

export function Tabs({ items, value, onChange, ariaLabel = 'Tabs', tabPanelId }: TabsProps) {
  const refs = React.useRef<Array<HTMLButtonElement | null>>([]);
  const enabledItems = items.filter(item => !item.disabled);
  const activeIndex = Math.max(0, enabledItems.findIndex(item => item.id === value));
  const move = (index: number) => {
    if (!enabledItems.length) return;
    const next = (index + enabledItems.length) % enabledItems.length;
    onChange(enabledItems[next].id);
    requestAnimationFrame(() => refs.current[items.indexOf(enabledItems[next])]?.focus());
  };

  return (
    <div
      className="ids-tabs"
      role="tablist"
      aria-label={ariaLabel}
      onKeyDown={(event) => {
        if (!enabledItems.length) return;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') { event.preventDefault(); move(activeIndex + 1); }
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') { event.preventDefault(); move(activeIndex - 1); }
        if (event.key === 'Home') { event.preventDefault(); move(0); }
        if (event.key === 'End') { event.preventDefault(); move(enabledItems.length - 1); }
      }}
    >
      {items.map((item, index) => {
        const selected = value === item.id;
        const panelId = tabPanelId ? `${tabPanelId}-panel` : undefined;
        const tabId = `${tabPanelId ? `${tabPanelId}-` : ''}tab-${item.id}`;
        return (
          <button
            key={item.id}
            id={tabId}
            ref={node => { refs.current[index] = node; }}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelId}
            aria-disabled={item.disabled || undefined}
            disabled={item.disabled}
            tabIndex={selected && !item.disabled ? 0 : -1}
            className={`ids-tab ${selected ? 'ids-tab--active' : ''}`}
            onClick={() => { if (!item.disabled) onChange(item.id); }}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}

export interface EmptyStateAction {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary';
}

export interface EmptyStateProps {
  title: React.ReactNode;
  message?: React.ReactNode;
  icon?: React.ReactNode;
  actions?: EmptyStateAction[];
  ariaLabel?: string;
}

export function EmptyState({
  title,
  message,
  icon,
  actions = [],
  ariaLabel,
}: EmptyStateProps) {
  return (
    <section className="ids-empty-state" aria-label={ariaLabel}>
      {icon ? <div className="ids-empty-state__icon" aria-hidden="true">{icon}</div> : null}
      <h2 className="ids-empty-state__title">{title}</h2>
      {message ? <p className="ids-empty-state__message">{message}</p> : null}
      {actions.length ? (
        <div className="ids-empty-state__actions">
          {actions.slice(0, 2).map((action) => (
            <Button
              key={action.label}
              onClick={action.onClick}
              variant={action.variant === 'primary' ? 'primary' : 'secondary'}
              size="md"
            >
              {action.label}
            </Button>
          ))}
        </div>
      ) : null}
    </section>
  );
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
