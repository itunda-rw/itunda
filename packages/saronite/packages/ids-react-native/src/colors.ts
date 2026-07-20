// Real fix (2026-07-13): textPrimary/textSecondary were both one semantic step off
// from every other platform's convention (Android IdsSemanticColors.kt,
// iOS IDS.swift, web packages/design-tokens/tokens.css all agree: textPrimary is
// grey900 #191F28, textSecondary is grey700 #4E5968) -- this file had textPrimary
// holding grey800's value and textSecondary holding grey500's, found via a
// repo-wide design-token audit.
export const colors = {
  primaryBlue: '#3182F6',
  background: '#F2F4F6',
  card: '#FFFFFF',
  textPrimary: '#191F28',
  textSecondary: '#4E5968',
  positiveBackground: '#E8F3FF',
  divider: '#E5E8EB',
};
