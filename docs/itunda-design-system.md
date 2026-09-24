# Itunda Design System

Itunda Design System (IDS) is the open-source visual and interaction foundation for Itunda web experiences.

Itunda targets the same **quality bar and product-first discipline** as leading fintech product sites while remaining unmistakably Itunda. Itunda Indigo, Itunda Data and Itunda Identity are signature brand layers.

## Goals

- Product before decoration.
- One idea per screen or section.
- Homepage = discovery and product film.
- Subpages = focused understanding.
- Motion communicates continuity, state or hierarchy.
- Real product UI is preferred over decorative mockups.
- Accessibility and reduced motion are built into the system.
- Components and tokens are reusable across web products.

## Source structure

`design-system/` contains framework-neutral CSS tokens and primitives:

- `tokens.css` — color, spacing, radius and layout tokens.
- `typography.css` — marketing and product typography ladder.
- `motion.css` — entrance, reveal, hover and accessibility motion primitives.
- `components.css` — container, buttons, surfaces and dividers.

Page-specific styling should consume these primitives instead of creating a competing token system.

## Color

| Token | Value |
|---|---|
| Itunda Indigo | #7472F4 |
| Indigo Dark | #625FE0 |
| Indigo Soft | #F0EFFF |
| Ink | #191F28 |
| Muted | #6B7684 |
| Soft | #F7F8FA |
| Line | #EEF0F2 |
| Dark | #080A10 |

## Typography

Marketing display type is intentionally large and expressive. Product UI uses a smaller, denser ladder.

- Display: 58–132px responsive
- Section title: 45–88px responsive
- Title: 28–40px
- Lead: 17–20px
- Body: 16px
- UI display: 30/40
- UI heading: 22/31
- UI body: 17/25.5
- Label/meta: 12px

Toss's public design-system documentation confirms the importance of tokenized typography, hierarchy and cross-platform consistency; IDS follows those principles without copying Toss's implementation or brand. 

## Motion

The homepage uses stronger narrative motion. Subpages deliberately use lighter motion.

Motion primitives:

- rise/reveal
- stagger
- sticky scroll narrative
- hover lift
- ambient float
- page/navigation transitions
- reduced-motion fallback

The current Itunda homepage's four-scene product film is the first implementation of this model.

## Interaction

- Primary actions have clear destinations.
- Hover movement is small.
- Keyboard focus is always visible.
- Mobile uses tap/scroll rather than hover-dependent interactions.
- Infinite motion is reserved for ambient visuals.
- No information is conveyed by color alone.

## Information architecture

Homepage:

**Money → Everyday → Identity → Data → Platform**

Focused pages:

- Services
- Identity
- Data
- Everyday
- Platform

The homepage tells one continuous story rather than behaving like a product catalog. This follows the product-storytelling approach documented by Toss's current homepage redesign while preserving Itunda's own content and architecture.

## Quality bar

Before shipping a page:

1. Does the first viewport communicate one idea immediately?
2. Can the user understand the product without reading every paragraph?
3. Does every animation have a purpose?
4. Does the mobile experience remain complete without hover?
5. Are typography, spacing and controls tokenized?
6. Are real Itunda capabilities represented accurately?
7. Does the page feel like a product rather than a brochure?
