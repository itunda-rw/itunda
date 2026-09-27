# Itunda Surface Composition Rules

This is the product-level application layer for IDS 3.x.

## Core anatomy

Use this order whenever a screen asks the user to make a decision:

1. **Value** — what matters now.
2. **Context** — why it matters and what will happen.
3. **Decision** — the available choices.
4. **Action** — one obvious primary next step.
5. **Recovery** — loading, disabled, error, empty, and retry states.

## Composition

- Keep primary content within the canonical 680px measure when the surface is content-led.
- Use the 760px hero measure only when a hero/value block genuinely needs more room.
- Use a 24px decision gap around the primary decision/action group.
- Prefer whitespace and section rhythm over decorative cards.
- Use containers to communicate grouping, not as default wrappers.
- Preserve 44px minimum and 48px recommended touch targets.
- Keep Itunda Indigo (#7472F4) for identity and primary interaction; do not turn every element into a branded element.

## Financial surfaces

For balances, transfers, savings, and payments:

**amount → status/context → supporting detail → action**

Amounts should be visually dominant without making the surrounding interface noisy. Secondary information should remain readable but quiet.

## Product-wide rule

When an IDS primitive changes, propagate the same contract to Android, iOS, Saronite/React Native, and web products. Platform implementation may differ; hierarchy and semantics must not.

## Identity boundary

Toss is a reference for interaction quality, restraint, hierarchy, motion, and information architecture. Itunda keeps its own Indigo, typography, product language, Rwanda-specific services, icons, and component contracts.
