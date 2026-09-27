/** Canonical IDS spacing, shape and control anatomy.
 * Source: packages/design-tokens/tokens.json.
 * Components remain platform-native; these values define shared product semantics.
 */
export const layout = {
  space: { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32, huge: 48 },
  screenPaddingInline: 20,
  screenPaddingBlock: 16,
  sectionGap: 24,
  contentGap: 16,
  rowGap: 14,
  inlineGap: 12,
  tightGap: 8,
  controlHeight: { sm: 40, md: 48, lg: 56 },
  controlRadius: 12,
  buttonRadius: 16,
  cardRadius: 24,
  sectionRadius: 16,
  iconRadius: 12,
  minTouchTarget: 44,
  recommendedTouchTarget: 48,
} as const;

export type IdsLayout = typeof layout;
