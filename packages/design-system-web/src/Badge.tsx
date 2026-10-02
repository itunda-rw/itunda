import React from 'react';

export interface BadgeProps {
  text: React.ReactNode;
  tone?: 'brand' | 'neutral' | 'success' | 'danger' | 'warning';
  variant?: 'filled' | 'soft';
  className?: string;
}

export function Badge({ text, tone = 'brand', variant = 'filled', className = '' }: BadgeProps) {
  return <span className={['ids-badge', `ids-badge--${variant}`, `ids-badge--${tone}`, className].filter(Boolean).join(' ')}>{text}</span>;
}
