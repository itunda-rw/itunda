# IDS Reduced Motion

Motion should communicate interaction, not become a dependency for understanding the product.

## Motion contract

- `instant`: 0ms
- `fast`: 140ms
- `normal`: 220ms
- `slow`: 420ms
- `reduced`: 0ms

When reduced motion is enabled, remove or minimize non-essential movement while preserving the final visual state and essential feedback.

## Implementation

Web: honor `prefers-reduced-motion`.
Android: follow platform animation/reduced-motion preferences.
iOS: honor Reduce Motion.

Loading indicators may remain animated when animation communicates ongoing progress, but avoid decorative motion.