import React from 'react';

export interface KeypadButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: 'default' | 'delete';
}

export function KeypadButton({ tone = 'default', className = '', type = 'button', ...props }: KeypadButtonProps) {
  return <button {...props} type={type} className={[
    'ids-keypad-button',
    tone === 'delete' ? 'ids-keypad-button--delete' : '',
    className,
  ].filter(Boolean).join(' ')} />;
}
