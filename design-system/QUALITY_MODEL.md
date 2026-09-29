# IDS Quality Model

## Purpose

IDS uses a release-gate model so visual polish does not hide missing behavior. The target is consistent product quality across Web, Android, and iOS while allowing each platform to use native interaction patterns.

## Five mandatory dimensions

| Dimension | Minimum evidence | Failure examples |
|---|---|---|
| States | Every manifest state renders and is semantically represented | Loading looks different but remains actionable; disabled is only visually faded |
| Content | Long/localized/large-text cases remain usable | Label wraps into a clipped control; translated action becomes unreadable |
| Accessibility | Name, role, state, focus/keyboard, and target checks pass | Color is the only error signal; icon has no accessible name |
| Motion | Normal interaction has intentional feedback and reduced motion removes non-essential motion | Spinner/transition continues despite reduced-motion preference |
| Platforms | Web/Android/iOS implementations map to the same contract | One platform silently omits a state or changes meaning |

## Evidence levels

- **Mapped** — the component has a concrete implementation symbol on each target platform.
- **Contract-checked** — the implementation is checked against the shared manifest and API contract.
- **Web a11y-checked** — automated browser checks cover the Web accessibility contract.
- **Visual-verified** — rendered screenshots have been reviewed or compared against an accepted baseline.
- **Build-verified** — the relevant platform build/test actually succeeds.

A lower evidence level must never be described as a higher one.

## Release gate

A primitive can enter the shared IDS catalog only when:

- its manifest entry defines states, content, accessibility, motion, and platform coverage;
- its guide defines purpose, worst-case content, semantic tokens, and a practical checklist;
- Web, Android, and iOS implementations are mapped;
- the IDS audit passes;
- rendered evidence exists for normal and stress conditions;
- known platform build limitations are recorded rather than implied away.

## Review order

1. **Meaning** — is the role obvious without relying on color or decoration?
2. **Hierarchy** — do typography, spacing, and emphasis communicate priority?
3. **States** — can users understand what happened and what they can do next?
4. **Worst case** — does it survive localization, long content, validation copy, and large text?
5. **Accessibility** — can keyboard, screen-reader, and touch users complete the same task?
6. **Motion** — does feedback help without becoming mandatory for comprehension?
7. **Platform parity** — does each native implementation preserve meaning while following platform conventions?

## Toss-inspired quality boundary

IDS does not copy proprietary Toss implementation or assets. The target is quality parity in publicly documented principles: edge-case-aware component guides, accessibility, large text, dark mode, interaction behavior, and a design system that remains usable as the product grows.
