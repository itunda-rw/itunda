# Itunda Simplicity & Interaction Research Loop

**Status:** proposed product/engineering standard. This document defines how Itunda will validate simplification work; it does not claim that user research or experiments have already happened.

## Why this exists

Toss's public writing describes interaction as a way to communicate state, clarify the next action, and make a product easier to understand—not decoration. Toss has also written about reducing unnecessary input and validating navigation with users:

- [Interaction: does it really need to be there?](https://toss.tech/article/interaction)
- [Four ways to remove unnecessary input](https://toss.tech/article/4-ways-for-minimum-input)
- [Toss Navigation Score research process](https://toss.tech/article/Toss_Navigation_Score)
- [How Toss makes user research easier](https://toss.tech/article/ux-research-platform)
- [Toss's public GitHub organization](https://github.com/toss)

Itunda should learn from the underlying methods, not copy Toss branding, proprietary code, or assumptions about Korean users. Validate decisions with people who use Itunda in Rwanda, including Kinyarwanda-first users and people on lower-end phones or unreliable networks.

## 1. Start with a user job, not a screen

For each proposed change, write one sentence:

> When **[situation]**, the user wants to **[job]**, so they can **[outcome]**.

Then record:
- The current path and every required input/action.
- The point where users hesitate, fail, backtrack, or ask for help.
- The evidence: observation, support report, analytics, or a clearly marked hypothesis.
- The smallest change likely to remove the obstacle.
- What must remain true for security, consent, legal compliance, and financial safety.

Do not simplify by deleting necessary context, hiding fees, weakening consent, or making risky actions easier to trigger accidentally.

## 2. Measure before and after

For a meaningful flow, record a baseline before changing it. Use the same task, device class, language, and network conditions when comparing results.

Core measures:
- **Task success:** users who complete the intended task without assistance.
- **Time to completion:** median elapsed time among all attempted tasks; report failures separately so fast abandonment does not look like improvement.
- **Unnecessary effort:** taps, fields, repeated entries, backtracks, and avoidable permission requests.
- **Error and recovery:** validation failures, duplicate submissions, retries, and successful recovery.
- **Comprehension and confidence:** can users explain what will happen next, any fee, and whether the action succeeded?
- **Accessibility:** keyboard/screen-reader reachability, text scaling, contrast, touch-target usability, and reduced-motion behavior.
- **Reliability:** loading/error rates and p95 latency, segmented by device/network where feasible.

Never optimize click count alone. A confirmation step that protects a transfer or explains an irreversible action may be valuable friction. Publish sample size, task wording, dates, and known limitations alongside results. Do not call a result statistically significant unless the method supports that claim.

## 3. Test a small, repeatable task set

Begin with the flows that can cause the most user harm or frustration:

1. **Sign in / recover access:** can a user understand the PIN or verification step, recover from a wrong entry, and distinguish a network problem from an incorrect code?
2. **Find a place:** can a user search for a real Rwanda place, understand location permission, and recover from loading, empty, or offline states?
3. **Send money:** can a user identify the recipient, understand amount and fee, review the result, and avoid duplicate submission?
4. **Post in the marketplace:** can a user enter the minimum required details, understand visibility, and correct validation errors?
5. **Send a chat message:** can a user tell whether a message is sending, sent, failed, or safe to retry?

Use realistic tasks rather than asking, “Do you like this design?” Observe whether the participant succeeds without coaching. Ask what they expected before explaining the interface. Include Kinyarwanda and English, light and dark themes, and constrained-network/device conditions as appropriate.

## 4. Interaction acceptance criteria

An interaction is ready only when its purpose and all states are specified.

- **Purpose:** identifies the user uncertainty or action the interaction improves.
- **Idle:** the available action and its label are clear.
- **In progress:** repeated activation cannot create duplicate work; progress is visible when waiting is meaningful.
- **Success:** confirms the real outcome, not merely that a button was tapped.
- **Failure:** explains what happened and gives a safe, actionable next step.
- **Recovery:** preserves valid input where safe and supports retry without duplicate payment, post, or message.
- **Motion:** communicates cause, hierarchy, progress, or outcome. Prefer the simplest implementation that achieves the goal.
- **Accessibility:** no essential meaning depends only on motion, color, or a transient animation; respect reduced-motion preferences.
- **Consistency:** shared patterns behave consistently across web, Android, and iOS while respecting each platform's native conventions.

For financial or destructive actions, preserve explicit review and confirmation where needed. Never make an irreversible action easier solely to reduce taps.

## 5. Apply Itunda's identity and constraints

- Use Itunda's canonical design tokens and assets; do not replace them with Toss colors, logos, illustrations, or fonts.
- Use clear Kinyarwanda-first copy, with English support.
- Prefer one primary action per step, short forms, useful defaults, and progressive disclosure.
- Keep primary actions reachable on short and long screens; verify scroll behavior and keyboard/safe-area insets.
- State the actual reason for requesting permissions or sensitive information.
- Empty and error states must name what is missing, explain the real cause when known, and offer the next useful action.
- A map must distinguish permission denied, loading, no indexed results, offline/error, and successful results. Never disguise missing real place data with placeholder markers.
- Use the existing Itunda architecture and shared components rather than creating a one-off pattern for every feature.

## 6. Prioritization and release gate

Score each candidate from 1–5 on user impact, frequency, and confidence in the evidence; estimate engineering cost separately. Start with high-impact, frequent, evidence-backed problems—not the most visually noticeable animation.

Before release, attach:
- the user job and observed problem;
- baseline and post-change results, or a clear statement that measurement is pending;
- screenshots/video or reproducible test steps for key states;
- accessibility, localization, and low-connectivity checks;
- tests for failure/retry and duplicate-action safety;
- platform parity notes and any known gaps.

If the evidence is inconclusive, keep the result labeled as a hypothesis and run another test. Do not claim an improvement merely because the new screen looks simpler.

## 7. Lightweight research record

Use this template for each iteration:

- **Flow / platform / build:**
- **User job:**
- **Observed obstacle and evidence:**
- **Hypothesis:**
- **Change made:**
- **Participants / task / language / device / network:**
- **Baseline:**
- **Result:**
- **Failures and accessibility findings:**
- **Decision:** keep / revise / revert / gather more evidence
- **Owner and follow-up:**

The goal is a durable learning loop: observe → simplify → test → measure → ship safely → monitor real usage → revisit.
