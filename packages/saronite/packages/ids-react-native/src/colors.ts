// Real fix (2026-07-13): textPrimary/textSecondary were both one semantic step off
// from every other platform's convention (Android IdsSemanticColors.kt,
// iOS IDS.swift, web packages/design-tokens/tokens.css all agree: textPrimary is
// grey900 #191F28, textSecondary is grey700 #4E5968) -- this file had textPrimary
// holding grey800's value and textSecondary holding grey500's, found via a
// repo-wide design-token audit.
// Brand rebranded blue -> indigo (2026-08-22, direct user identity work: petal-shape
// mark + indigo -- see project_itunda_brand_identity.md and web's
// packages/design-tokens/tokens.css --itunda-indigo for the full derivation note, and
// the identical rebrand applied the same day to Android's IdsSemanticColors.kt and
// iOS's IDS.swift). Not invented: same OKLCH hue-rotation Toss's own eng blog
// documents (toss.tech/article/tds-color-system-update), applied to the real anchors
// this file held before -- hold L/C of the old #3182F6/#F5FAFF, rotate hue to
// 280deg, clamp chroma to gamut.
export const colors = {
  primaryIndigo: '#7472F4',
  background: '#F2F4F6',
  card: '#FFFFFF',
  textPrimary: '#191F28',
  textSecondary: '#4E5968',
  positiveBackground: '#F8F9FF',
  divider: '#E5E8EB',
};
