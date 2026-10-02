import React from 'react';

export interface KeypadButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  ariaLabel?: string;
}

export function KeypadButton({
  children,
  ariaLabel,
  className = '',
  type = 'button',
  ...props
}: KeypadButtonProps) {
  return (
    <button
      {...props}
      type={type}
      aria-label={ariaLabel}
      className={`ids-keypad-button ${className}`.trim()}
    >
      {children}
    </button>
  );
}
