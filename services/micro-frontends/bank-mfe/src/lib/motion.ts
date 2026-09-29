// Real Toss motion primitives (2026-08-24), fetched directly from their own
// published npm packages -- @toss/tds-spring-easing@0.0.1's real bundled source
// (`npm pack @toss/tds-spring-easing`, read dist/esm/index.js), not guessed or
// approximated. CSS transitions can't run real spring physics (only bezier curves
// -- see packages/design-tokens/tokens.css's --itunda-ease-* for those), so these
// raw stiffness/damping/mass triples are for Framer Motion's own
// `transition={{ type: 'spring', ...itundaSpring.medium }}` shape, which itunda's
// UI already uses throughout BankDashboard.tsx (`motion.div`/`whileTap`/etc.).
// Same 8 real named presets ported to Android (IdsMotion.kt's spring(dampingRatio,
// stiffness), damping ratio derived from these same raw values) and iOS (IDS.Motion's
// Animation.interpolatingSpring, which takes mass/stiffness/damping directly, no
// conversion needed) in the same pass -- all 3 platforms share identical spring
// feel, not just identical colors/type.
export interface ItundaSpringPreset {
  stiffness: number;
  damping: number;
  mass: number;
}

export const itundaSpring: Record<
  'basic' | 'small' | 'quick' | 'medium' | 'large' | 'slow' | 'rapid' | 'bounce',
  ItundaSpringPreset
> = {
  basic: { stiffness: 200, damping: 30, mass: 1 },
  small: { stiffness: 480, damping: 50, mass: 1 },
  quick: { stiffness: 800, damping: 55, mass: 1 },
  medium: { stiffness: 270, damping: 25, mass: 1 },
  large: { stiffness: 100, damping: 15, mass: 1 },
  slow: { stiffness: 70, damping: 20, mass: 1 },
  rapid: { stiffness: 1000, damping: 55, mass: 1 },
  bounce: { stiffness: 300, damping: 15, mass: 1 },
};
