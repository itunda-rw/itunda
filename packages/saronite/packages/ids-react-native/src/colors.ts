// IDS semantic color contract for native mini-app surfaces.
// Keep these roles aligned with packages/design-tokens/tokens.json.
export const colors = {
  primaryIndigo: '#8F89FF',
  primaryIndigoPressed: '#6F67E8',
  background: '#F2F4F6',
  surface: '#FFFFFF',
  surfaceBrand: '#F1EFFF',
  textPrimary: '#191F28',
  textSecondary: '#4E5968',
  textTertiary: '#6B7684',
  textDisabled: '#B0B8C1',
  divider: '#E5E8EB',
  borderStrong: '#D1D6DB',
  positive: '#05804A',
  positiveBackground: '#F8F9FF',
  error: '#F04452',
  errorBackground: '#FFECEB',
  white: '#FFFFFF',
} as const;

export type IdsColors = typeof colors;
