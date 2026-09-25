# Itunda Design System — Web primitives

This package is the Web implementation layer for IDS 3.0.

## Core primitives

- Button: primary, secondary, tertiary, danger; small/medium/large; disabled/loading; keyboard focus.
- TextField: label, help/error messaging, required state, accessible description wiring.
- Select: label, options, help/error messaging.
- Checkbox: native checkbox semantics with a custom visual treatment.
- Radio: native radio semantics with a custom visual treatment.
- Switch: native checkbox plus role=switch semantics.
- Tabs: tablist/tab semantics with a single active value.

## Contract

Components consume semantic/component tokens from @itunda/design-tokens. Product code should not reach into primitive color values when a semantic role exists.

Every interactive primitive must preserve:

1. keyboard operation;
2. visible focus;
3. minimum 44px interaction target;
4. long-label wrapping instead of clipping;
5. reduced-motion behavior;
6. light/dark semantic token behavior;
7. accessible names and descriptions;
8. stable APIs that prefer semantic intent over arbitrary styling props.

The Web layer is one implementation of the IDS contract; Android and iOS may use native controls while preserving the same semantic behavior.