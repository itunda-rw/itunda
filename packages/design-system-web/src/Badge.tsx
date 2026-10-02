import React from 'react';

export type BadgeVariant = 'brand' | 'neutral' | 'success' | 'danger' | 'warning';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  text?: string;
  variant?: BadgeVariant;
  filled?: boolean;
  children?: React.ReactNode;
}

export function Badge({
  text,
  variant = 'brand',
  filled = true,
  children,
  className = '',
  ...props
}: BadgeProps) {
  return (
    <span
      {...props}
      className={[`ids-badge ids-badge--${variant}`, filled ? 'ids-badge--filled' : 'ids-badge--soft', className].filter(Boolean).join(' ')}
    >
      {children ?? text}
    </span>
  );
}
