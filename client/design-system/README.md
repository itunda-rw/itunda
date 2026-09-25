# Itunda Design System

**IDS v2** is the shared product language for Itunda Web, Android and iOS.

## Benchmark

IDS uses the same level of systemization expected from a mature product design system: foundations, semantic tokens, component anatomy, states, interaction, accessibility, platform parity and product patterns.

Itunda does **not** copy another company's visual identity. The benchmark is system quality; the identity remains Itunda.

## Architecture

```
Primitive tokens
      ↓
Semantic tokens
      ↓
Component roles
      ↓
Product patterns
      ↓
Web · Android · iOS
```

## Completion contract

A component is not considered production-ready until it documents:

- anatomy
- variants
- sizes
- default / hover / pressed / focused / disabled states
- loading / empty / error / success states where applicable
- dark mode
- responsive behavior
- reduced motion
- keyboard behavior
- VoiceOver / TalkBack semantics
- localization and long-content behavior
- content guidance
- usage examples

## Product patterns

The next IDS layer should standardize real Itunda flows:

- authentication
- identity verification
- money movement
- payments
- marketplace
- chat
- business
- mini apps
- security
- empty / error / offline states

## Source of truth

- `tokens.css` — web-consumable foundation tokens
- `tokens.json` — portable token model
- `components.css` — web primitives and state behavior
- `../developers/design/` — human-readable documentation and sandbox

## Licensing

Itunda-authored design-system source is intended to be open-source under MIT. Third-party fonts/assets retain their own licenses.


## Existing Itunda implementation sources

The IDS v2 layer explicitly includes the design implementations already present in this repository. See [`RECONCILIATION.md`](./RECONCILIATION.md) for the audited Android, iOS, Web, SDUI and product-level sources and the migration rules.
