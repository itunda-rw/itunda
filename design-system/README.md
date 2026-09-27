# Itunda Design System (IDS) 3.0

IDS is the shared product-quality contract for Itunda Web, Android, and iOS. It is intentionally built as an independent design-system product: foundations, semantic tokens, component contracts, accessibility, motion, platform parity, and rendered evidence evolve together.

## Source of truth

- Tokens: `packages/design-tokens/tokens.json`
- Generated token outputs: Android, iOS, Web
- Component contract: `design-system/components/contract-manifest.json`
- Platform contract: `design-system/platform-contract.md`
- Core primitive contracts: `design-system/components/core-primitives.md`
- Platform implementation map: `design-system/components/PLATFORM_IMPLEMENTATIONS.md`
- Accessibility: `design-system/accessibility/`
- Automated audit: `scripts/audit-ids.mjs`
- Interactive showcase: `business/developers/design/index.html`

## Quality model

A component is not complete because it looks correct in one screenshot. IDS treats quality as a contract across five dimensions:

1. **States** — default, interaction, disabled, loading, validation, and selection states where applicable.
2. **Content** — long labels, localization, validation copy, and large-text stress cases.
3. **Accessibility** — native semantics, accessible names/descriptions, keyboard or platform focus behavior, screen readers, and minimum touch targets.
4. **Motion** — intentional interaction feedback with explicit reduced-motion behavior.
5. **Platforms** — the same product contract mapped deliberately to Web, Android, and iOS.

See [QUALITY_MODEL.md](./QUALITY_MODEL.md) for release gates and evidence levels.

## Showcase

The interactive IDS showcase is deployed independently from the Itunda Developers documentation so the design system can be reviewed as a product rather than as a page inside another site.

## Principles

- **Reference the discipline, not the identity.** IDS can learn from mature Korean fintech systems such as Toss—clear hierarchy, restrained surfaces, compact type scales, predictable spacing, and direct actions—without reproducing their brand assets or visual identity.
- **Itunda Indigo is the canonical Itunda brand role:** `#7472F4`, with `#625FE6` as the stronger interaction role.
- **Typography is structural.** Use the Pretendard stack and the shared semantic scale before introducing one-off font sizes.
- **Spacing creates hierarchy.** The 4px base rhythm and named spacing steps should determine layout before decorative containers do.
- **Quiet surfaces, strong content.** Prefer whitespace, typography, alignment, and semantic color over unnecessary cards, borders, gradients, or shadows.
- **Native behavior over imitation.** Web, Android, and iOS share the same product contract while respecting platform conventions.
- **Worst-case content is part of component design,** including localization, long labels, large text, validation, and financial values.
- **Accessibility and reduced motion belong in the component contract.**
- **Mapped, contract-checked, visual-verified, and build-verified are distinct claims.**

## Open source boundary

IDS documents and reusable contracts can be shared independently from private product/business logic. Do not publish credentials, private service configuration, customer data, or internal production infrastructure as part of the design-system package.
