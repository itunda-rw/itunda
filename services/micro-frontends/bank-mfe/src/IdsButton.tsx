import type { ReactNode } from 'react';
import { Button } from '@itunda/design-system-web';

export type IdsButtonVariant = 'filled' | 'tinted' | 'danger';
export type IdsButtonSize = 'large' | 'medium' | 'small';

export function IdsButton({
  children,
  onClick,
  variant = 'filled',
  size = 'large',
  disabled = false,
  icon,
  fullWidth,
  style,
  type = 'button',
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: IdsButtonVariant;
  size?: IdsButtonSize;
  disabled?: boolean;
  icon?: ReactNode;
  fullWidth?: boolean;
  style?: React.CSSProperties;
  type?: 'button' | 'submit';
}) {
  return (
    <Button
      type={type}
      onClick={onClick}
      disabled={disabled}
      variant={variant === 'filled' ? 'primary' : variant === 'tinted' ? 'secondary' : 'danger'}
      size={size === 'large' ? 'lg' : size === 'medium' ? 'md' : 'sm'}
      fullWidth={fullWidth ?? size === 'large'}
      className="itunda-ids-button"
      style={style}
    >
      {icon}
      {children}
    </Button>
  );
}
