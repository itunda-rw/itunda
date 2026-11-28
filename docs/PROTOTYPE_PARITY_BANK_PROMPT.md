# Bank (itunda Bank) — prototype v1038 parity addendum

Read `docs/PROTOTYPE_PARITY_AGENT_PROMPT.md` first (method, git/CI rules, pitfalls). This file only
adds what is specific to **Bank**. Pay, Shop and Eats are other products: call into them through
existing callbacks, never edit their modules.

## 1. Where Bank is today (do not redo it)

Code: `android/features/banking/impl/src/main/kotlin/rw/itunda/feature/banking/impl/`
(`BankHubScreen`, `BankHubHeader/Sections/ShellRow`, `BankAccountScreens`, `BankMoneyScreens`
(Send/Move/Deposit), `BankCardsScreen`, `BankLimitsScreen`, `BankManageScreen`, `BankServiceScreens`,
`BankSubParts`, `Grow31*`, `WeeklySavings*`, `GroupAccount*`). Credit/loans/invest live in
`features/credit` and `features/wealth`, not here.

Render tests (`android/screenshots/.../BankScreenshotTest.kt`): hub (savings / loans / services),
grow31, invest, loans, score, and sub-screens account, deposit, payees, scheduled, budget, cases,
docs, details, send, move, cards, statement, pockets, manage, limits, plus dark for account, send,
pockets, cards, manage, limits. Recent commits already matched Account, Limits, Move, Statement,
Pockets, Cards. Check `git log -- android/features/banking` and the latest "Android Screenshots"
PNGs before measuring anything: do not re-measure a screen that already matches.

## 2. Prototype screens to cover

Keys come from `Object.keys(screens)`. Capture light and dark at 3x.

| Key | Screen | State needed before measuring |
|---|---|---|
| `bank` | Bank hub (Savings / Loans / Services tabs) | tab state in `S`; capture each tab and scrolled positions |
| `bankd` | Account detail | |
| `bkmove`, `bkpay`, `sendset`, `bkreq` | Move money, Payees (`bkpay`), Send settings, Request | `bkpay` with 0 payees and with seeded `S.bp` |
| `bklimits`, `bkmanage`, `bkstmt`, `bktx`, `bksch`, `bkbud`, `bkcase`, `bkdocs`, `bkdep`, `dep` | Limits, Manage, Statement, Transaction, Scheduled, Budget, Cases, Documents, Deposit | `bktx` needs a selected transaction: click a row in `bankd`/`bkstmt` instead of setting state by hand |
| `card`, `pocket`, `pockets`, `loan`, `save31` | Cards, Pockets, Loans, 31-day savings | `loan` and `save31` have steps: capture every step |

Many keys are wrapped more than once (look for later `screens.X=function(){const h=_X...}`
wrappers): measure the final rendered DOM, not the first definition.
Plain `S.stack=[key]` works for most keys. For ones that need a payload, drive the real UI
(click the row) so `S`/`X` are set by the prototype itself (`X`, `FR`, `D` etc. are closure
variables, not `window` globals; reach them through exposed functions such as `window.pxLim()`).

## 3. Bank-specific measurements to take

- Money formatting: `fmt()` groups with commas, RWF is a suffix, negatives use the real minus
  (U+2212, shown "−3,500"), credits show "+".
- Account-card gradients and the green/lavender tile art: measure CSS 158deg gradients (use
  `cssGradient158` pattern from Pay: gradient line length is |w sin| + |h cos|).
- Hairline dividers (1px at `--chip`), inset 24 vs full width: check each list.
- Segmented controls and chips: measure selected AND unselected states (weight 700 vs 600,
  colours differ in dark).
- Keypads: reuse the exact key size from Move/Send, not a new one.
- Interest/rate figures: see section 4, do not "fix" them silently.

## 4. Open product decisions (ask the user, never decide)

- Deposit rate: prototype and backend disagree (2.8% vs 8.0%). Ship the prototype number only if
  the user confirms; otherwise keep the demo value and flag it in the report.
- 31-day savings bonus steps: undecided; render what the prototype shows and flag it.
- Anything touching real money (Send, Move, Deposit, Scheduled, Limits) keeps its real
  validation. Parity of layout must not remove a balance, limit or confirmation check, and must
  not add a fake success. Where the demo cannot move money, the CTA shows the honest state.

## 5. Behaviour rules specific to Bank

- Limits, payees and scheduled items edit a draft and hand the result to the caller; do not keep
  state in a composable that the backend should own.
- Payee rows: favourites sort first (`b.fav` before others), star glyph next to the name, initials
  avatar 44dp, subtitle "Name · Bank ••1234" (last 4 only; never show or log full account
  numbers, card numbers or CVC).
- Statement/export, Documents, Cases: if the prototype action needs a backend that does not exist,
  keep the control non-interactive and list it in the report instead of faking a toast.
- Prototype-only helpers ("fill a sample", "reset demo") are not product: omit them.
- Out of scope: Eats, Shop, Pay internals, new routes. Reuse existing navigation hooks in `:app`.

## 6. File and test rules

- Do not grow baselined files; new code goes in new files under 500 lines (`BankHubScreen.kt` is at
  442, extract before adding).
- Stateless-content pattern: `XContent(state, callbacks)` + a thin wrapper that loads data, so
  CI can render it without `NetworkClient`.
- One render test per screen/state, light and dark; scroll-to-node tests for content below the
  fold. Compare by pixel measurement (colour runs), not by eyeballing downscaled sheets.
- Per-feature palette (`BankPalette`), measured light + dark; no borrowing from another feature's
  `impl`.

## 7. Checklist before "done"

- [ ] Every prototype key in section 2 has a render (light + dark) compared to its measured dump.
- [ ] Money rows, signs and separators match; no full account/card numbers anywhere.
- [ ] No fake controls; unbackable actions listed in the report.
- [ ] `python3 scripts/file-size-lint.py` clean; Konsist boundary check clean.
- [ ] Rebased, pushed, newest "Android Screenshots" run on your SHA compiled and rendered.
- [ ] Report names real differences (font drift, CI viewport 390x788, static dates) and the open
      decisions from section 4.
