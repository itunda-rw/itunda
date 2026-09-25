# IDS 3.0 Core Primitives

Core primitives are semantic contracts shared by Web, Android, and iOS. Product code consumes semantic tokens; platform implementations may differ in rendering and input behavior.

## API policy

- Flat APIs for simple, high-frequency primitives.
- Compound APIs for structural components with meaningful composition.
- Variants represent reusable semantic differences, never arbitrary styling.
- State is separate from variant.
- Accessibility is part of the public contract.

## Button

**Flat API:** `Button({ variant, size, loading, disabled, children, onPress })`

Variants: primary, secondary, tertiary, destructive.
States: default, pressed, focus, disabled, loading.
Rules: labels may wrap; loading preserves layout; minimum target 44px/pt/dp.

## TextField

**Flat API:** `TextField({ label, value, onChange, helpText, error, disabled, ... })`

Modes: default, search, password.
States: empty, filled, focus, disabled, error, success.
Rules: label and validation messaging remain programmatically associated; IME/input behavior follows the platform.

## Select

**Flat API:** `Select({ value, options, onChange, disabled, error })`

Modes: single, multi.
States: closed, open, focus, disabled, error.
Rules: options must remain reachable at large text; Web uses combobox/listbox semantics; mobile uses native selection presentation where appropriate.

## Checkbox

**Flat API:** `Checkbox({ checked, indeterminate, onChange, disabled, label })`

States: unchecked, checked, indeterminate, focus, disabled, error.
Rules: the label remains part of the same interactive relationship and never relies on color alone.

## Radio

**Flat API:** `RadioGroup({ value, options, onChange, disabled })`

States: unselected, selected, focus, disabled, error.
Rules: the group has an accessible name and exactly one selected value when required.

## Switch

**Flat API:** `Switch({ checked, onChange, disabled, loading, label })`

States: on, off, focus, disabled, loading.
Rules: use for immediate binary settings, not form submission choices.

## Tabs

**Compound API:** `Tabs`, `Tabs.List`, `Tabs.Trigger`, `Tabs.Panel`.
Modes: equal-width, scrollable.
States: default, active, focus, disabled.
Rules: preserve active-tab visibility when scrolling; keyboard and screen-reader relationships must remain intact.

## Cross-platform contract

| Concern | Web | Android | iOS |
|---|---|---|---|
| Input target | 44px+ | 44dp+ | 44pt+ |
| Focus | visible keyboard focus | native focus/semantics | native focus/semantics |
| Motion | prefers-reduced-motion | system animator scale | Reduce Motion |
| Theme | semantic CSS tokens | semantic Compose tokens | semantic SwiftUI tokens |
| Content | localization-safe | localization-safe | localization-safe |

## Promotion gate

A primitive enters the public IDS catalog only when its anatomy, variants, states, accessibility, content behavior, dark mode, and platform mapping are documented and implemented.


## Empty state

**Flat API:** `EmptyState({ title, message?, actionText?, onAction? })`

Variants: informational, actionable.

States: default, with message, with action.

Rules: the title is always meaningful; supporting copy wraps without truncation; actions use the shared Button contract; the component remains useful when the message is omitted. Empty states must not imply that an empty result is an error.

### Platform implementations

| Platform | Primitive |
|---|---|
| Web | `EmptyState` |
| Android | `IdsEmptyState` |
| iOS | `IdsEmptyState` |

