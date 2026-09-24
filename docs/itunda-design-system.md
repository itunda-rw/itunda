# Itunda Design System

The Itunda web system is a product storytelling system for a Rwanda-first super app. It takes inspiration from the clarity, hierarchy, motion and product-led storytelling of modern fintech sites while keeping Itunda's own identity, Indigo color and product architecture.

## 1. Design principles
1. One idea at a time — each screen/section should communicate one primary action or message.
2. Product before decoration — UI surfaces, real product states and believable data are preferred over abstract graphics.
3. Narrative on the homepage — the homepage behaves like a product film; service pages become focused explainers.
4. Calm subpages — subpages use fewer simultaneous elements, stronger reading rhythm and clearer feature grouping.
5. Data + Identity are signature layers — they are first-class Itunda concepts, not generic fintech sections.
6. Rwanda first, globally extensible — copy and examples should feel locally grounded without making the system visually provincial.
7. Motion has a job — animation explains hierarchy, continuity or state; it should never compete with content.
8. Accessibility is part of the system — reduced motion, readable contrast, keyboard access and responsive layouts are required.

## 2. Color tokens
| Token | Value | Role |
|---|---|---|
| Itunda Indigo | #7472F4 | Primary brand/action |
| Indigo Dark | #625FE0 | Hover, headings, active states |
| Indigo Soft | #F0EFFF | Soft backgrounds and selected surfaces |
| Ink | #191F28 | Primary text |
| Muted | #6B7684 | Secondary text |
| Soft | #F7F8FA | Neutral surface |
| Line | #EEF0F2 | Borders/dividers |
| Dark | #080A10 | Platform/developer sections |

## 3. Marketing typography
Marketing typography is deliberately larger than product UI typography.

| Role | Desktop | Mobile | Line height | Tracking |
|---|---:|---:|---:|---:|
| Hero display | 96–132px | 58–72px | 0.88–0.94 | -4 to -7px |
| Section display | 72–88px | 45–56px | 0.94–1.0 | -3 to -5px |
| Card title | 28–40px | 25–32px | 1.0–1.08 | -1 to -2px |
| Lead | 19–20px | 17–18px | 1.55–1.7 | slight negative |
| Body | 16–19px | 15–17px | 1.6–1.7 | normal |
| Label | 11–14px | 11–13px | 1.2 | +0.02–0.12em |

The web stack uses system fonts with SF Pro where available. If Itunda later ships a dedicated product typeface, replace the stack at the token level rather than changing individual components.

## 4. Product/UI typography
Marketing type and product UI type are intentionally different scales. Product surfaces should stay compact and readable.

Recommended UI ladder:
- UI Display: 30/40
- UI Large: 26/35
- UI Heading: 22/31
- UI Small Heading: 20/29
- UI Body Large: 18/27
- UI Body: 17/25.5
- UI Caption: 14/20
- UI Meta: 12/17

These values follow the same tokenized philosophy documented in Toss's public design-system typography guidance, but the Itunda implementation remains its own system.

## 5. Spacing
Use an 8px base rhythm: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96, 120, 160.
Marketing sections normally use 120–170px vertical padding on desktop and 80–110px on mobile.

## 6. Layout
- Max content width: 1180–1280px.
- Desktop gutter: 28–32px.
- Mobile gutter: 18–24px.
- Hero content is normally 2-column on desktop and 1-column on mobile.
- Large visual stages use 40–52px radius.
- Cards use 24–38px radius.
- Pills use 999px radius.
- Avoid dense dashboard grids on the homepage.

## 7. Navigation
Homepage primary categories: Services, Identity, Data, Everyday, Platform.
Homepage navigation links to focused subpages instead of forcing every explanation into one scroll.
Desktop: 72px header, translucent white background, blur/saturation, 14px navigation labels and Indigo active indicator.
Mobile: compact menu control, floating panel, same information architecture as desktop.
Subpages keep the same category order and provide an explicit path back to the homepage.

## 8. Homepage motion
The homepage has two motion layers.
Layer A — entrance: hero tiles stagger in; content uses opacity + translateY + subtle scale; large product visuals rise into position.
Layer B — narrative scroll: the home film is approximately 390vh with a sticky viewport.
Scenes: 1) Start with money. 2) Then life gets closer. 3) Trust follows you. 4) The platform opens.
Scroll controls scene state, progress bar, progress dots and subtle product movement.
The scroll narrative should feel like a continuous product demonstration, not a slideshow.

## 9. Subpage motion
Subpages intentionally use less motion than the homepage: card lift on hover, small product-stage movement, floating identity card, rotating data rings and platform code cursor.
No full-page cinematic controller is used on subpages. Homepage = discovery; subpage = understanding.

## 10. Interaction rules
- Every primary CTA has a clear destination.
- Hover states should use small translation, normally 4–8px, and a modest shadow change.
- Never use large scaling for ordinary controls.
- Interactive surfaces should have visible focus states.
- Motion should use a soft ease-out curve such as cubic-bezier(.22,1,.36,1).
- Avoid infinite animation except for subtle ambient visuals.
- Respect prefers-reduced-motion.

## 11. Product storytelling
The homepage communicates: Money → Everyday → Identity → Data → Platform.
Focused pages explain: Services = money movement and financial life; Identity = trust and verification; Data = context and user control; Everyday = marketplace, messaging and merchant; Platform = Saronite and mini apps.

## 12. Footer
The homepage footer is a real navigation surface, not only a copyright line.
Recommended groups: Product, Platform, Company, Support.
It includes the Itunda brand statement, product links, open-source/GitHub link, company/community links, privacy, terms, accessibility and copyright.

## 13. Content rules
Prefer short sentences, concrete verbs, one promise per heading, product language over corporate jargon and real implementation names when they exist.
Avoid unsupported features, generic super-app claims, giant lists of capabilities, fake metrics and decorative UI that implies functionality that does not exist.

## 14. Accessibility
Required: semantic headings, descriptive link labels, keyboard focus, sufficient color contrast, reduced-motion support, mobile layouts without horizontal overflow and no information conveyed by color alone.

## 15. Open-source evolution
The design system should evolve through versioned tokens and components rather than page-by-page styling.
Future extraction targets: tokens.css, typography.css, components.css, motion.css, reusable navigation/footer, product UI primitives and visual regression examples.