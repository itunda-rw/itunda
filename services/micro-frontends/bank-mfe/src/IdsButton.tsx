import type { ReactNode } from 'react';

// Real fix, grounded in Toss's own published account of this exact failure mode
// ("디자인 시스템 다시 생각해보기" / "Rethinking Design System",
// toss.tech/article/rethinking-design-system) -- already applied to Android's own
// IdsButton.kt (core/designsystem/components/IdsButton.kt, 2026-07-21) after the
// identical local-fork drift happened there (ItundaAppScreen.kt had three separate
// local button composables that never touched the shared file). Web never got the
// equivalent: found via a fresh Toss Simplicity research pass (docs/
// DESIGN_REFERENCES.md Section 14) that every real button across bank-mfe/merchant-mfe/
// ops-mfe/kyc-mfe is a raw <button className="itunda-btn ..."> -- 415+ occurrences in
// bank-mfe's own BankDashboard.tsx alone, not a real shared component, so real
// accessibility/disabled-state/type="button" behavior gets hand-duplicated (or
// silently skipped) at every one of those call sites independently.
//
// Matches Android's real solution shape exactly: a single "Flat," props-based API
// (variant/size, not a Compound/slot API) covering every real shape already found in
// this codebase's own button usage.
export type IdsButtonVariant = 'filled' | 'tinted' | 'danger';
export type IdsButtonSize = 'large' | 'medium' | 'small';

const HEIGHT: Record<IdsButtonSize, string> = { large: '56px', medium: '48px', small: '36px' };
const RADIUS: Record<IdsButtonSize, string> = { large: '14px', medium: '12px', small: '10px' };
const FONT_SIZE: Record<IdsButtonSize, string> = { large: '16px', medium: '15px', small: '13px' };
const PADDING: Record<IdsButtonSize, string> = { large: '16px 20px', medium: '12px 18px', small: '6px 14px' };

// Mirrors Android's real Filled/Tinted color mapping exactly (Ids.colors.brand /
// Ids.colors.pressed+textBrand), plus a real `danger` variant matching the existing
// .itunda-btn-danger class this component replaces -- not a new shape, the same one
// itunda's own destructive actions (device remove, cancel plan) already use.
const COLORS: Record<IdsButtonVariant, { background: string; color: string }> = {
  filled: { background: 'var(--itunda-blue)', color: 'var(--itunda-white)' },
  tinted: { background: 'var(--itunda-blue-light)', color: 'var(--itunda-blue)' },
  danger: { background: 'var(--itunda-red)', color: 'var(--itunda-white)' },
};

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
  // Defaults to Android's own real behavior: full width only at Large size, an
  // explicit prop rather than a size-inferred default for the (real, existing) inline
  // secondary/small action buttons that were never meant to span the container.
  fullWidth?: boolean;
  style?: React.CSSProperties;
  type?: 'button' | 'submit';
}) {
  const { background, color } = COLORS[variant];
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      // Real disabled-state colors (2026-08-12, direct user screenshot of Toss's real
      // bottom "Confirm" bar: a dim TINT of the same brand blue while its required
      // input is empty, turning fully solid the moment it's valid) -- a neutral grey
      // disabled state reads as "broken/unavailable" rather than "not ready yet, same
      // action, just waiting on you." Only `filled` gets the blue tint (matches
      // Android's identical fix in IdsButton.kt); `tinted`/`danger` keep the existing
      // neutral fallback, a secondary style not shown in the reference.
      // color-mix (not a hardcoded rgba literal) so this stays in sync with
      // --itunda-blue automatically if the token ever changes -- the exact hardcoded-
      // color drift bug this file's own header comment already describes once.
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '6px',
        width: fullWidth ?? size === 'large' ? '100%' : undefined,
        height: HEIGHT[size],
        padding: PADDING[size],
        borderRadius: RADIUS[size],
        fontWeight: 600,
        fontSize: FONT_SIZE[size],
        border: 'none',
        cursor: disabled ? 'not-allowed' : 'pointer',
        backgroundColor: disabled
          ? variant === 'filled'
            ? 'color-mix(in srgb, var(--itunda-blue) 35%, transparent)'
            : 'var(--itunda-grey-200)'
          : background,
        color: disabled ? (variant === 'filled' ? 'var(--itunda-white)' : 'var(--itunda-grey-400)') : color,
        transition: 'var(--itunda-transition)',
        ...style,
      }}
    >
      {icon}
      {children}
    </button>
  );
}
