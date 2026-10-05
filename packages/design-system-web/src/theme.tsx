import React from 'react';

export interface IdsBrandPalette {
  brand: string;
  brandStrong: string;
  brandSurface: string;
  onBrand: string;
  focus: string;
  pressed: string;
}

export interface BrandThemeProps {
  light: IdsBrandPalette;
  dark: IdsBrandPalette;
  children: React.ReactNode;
  className?: string;
}

/**
 * Lets an app or partner keep its own brand expression while inheriting the
 * IDS component language. Only semantic brand roles are overridden; typography,
 * spacing, interaction anatomy, accessibility states, and component behavior
 * remain owned by IDS.
 */
export function BrandTheme({ light, dark, children, className = '' }: BrandThemeProps) {
  const style = {
    '--ids-brand-light': light.brand,
    '--ids-brand-strong-light': light.brandStrong,
    '--ids-brand-surface-light': light.brandSurface,
    '--ids-on-brand-light': light.onBrand,
    '--ids-focus-light': light.focus,
    '--ids-pressed-light': light.pressed,
    '--ids-brand-dark': dark.brand,
    '--ids-brand-strong-dark': dark.brandStrong,
    '--ids-brand-surface-dark': dark.brandSurface,
    '--ids-on-brand-dark': dark.onBrand,
    '--ids-focus-dark': dark.focus,
    '--ids-pressed-dark': dark.pressed,
  } as React.CSSProperties;

  return (
    <div
      data-ids-brand-theme=""
      className={className}
      style={style}
    >
      {children}
    </div>
  );
}
