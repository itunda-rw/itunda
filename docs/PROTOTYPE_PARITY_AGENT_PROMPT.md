# itunda Android: make a whole product look and behave EXACTLY like prototype v1038

You are one of several AI agents, each owning ONE product (Eats, Pay, Bank, Shop, Property, Jobs,
Marketplace, Community, Messages, Rides, etc.). Your job: make that product's Android screens
visually and behaviourally identical to the HTML prototype, screen by screen, until a
side-by-side render cannot be told apart (except for the known font difference below).

"Better", "closer" or "improved" is NOT the goal. **Identical is the goal.** If a value in the
prototype is 36px, the Android value is 36dp. If you are guessing, you are doing it wrong:
measure it.

---

## 0. Ground rules (read first)

- Repo: github.com/itunda-rw/itunda, branch `integration/prototype-parity` (never `main`, never
  merge PR #180, it is "[DO NOT MERGE]").
- Read in full before coding: `CLAUDE.md`, `docs/AI_AGENT_SELF_CHECK.md`,
  `docs/UI_UX_GUIDELINES.md`, `docs/ARCHITECTURE_GUIDELINES.md`.
- Source of truth (repo root): `itunda_clickable_prototype_v1038.html`,
  `itundaicons-25_toss.html`, `itundaface-2d_rwanda_v7 (1).html`. Use the copy in the repo.
- Only the consumer itunda app. Dummy data is fine (backend comes later), but every control must
  do what the prototype does. **No fake controls**: if a tap has no destination, wire it to the
  real one or leave it non-interactive (and then drop the chevron/pressed state).
- Konsist feature isolation (no `impl` to `impl` imports), file-size lint
  (`python3 scripts/file-size-lint.py`: new files max 500 lines, baselined files must not grow;
  never bump the baseline, extract instead).
- Gson ignores Kotlin defaults: when you add a DTO field, the `DemoInterceptor` canned JSON must
  contain it, or you get nulls/crashes.
- There is NO Android SDK locally. Compile and render only happen in GitHub Actions (section 5).

## 1. Where the app code lives

| Thing | Path |
|---|---|
| Feature screens | `android/features/<name>/impl/src/main/...` |
| Design system, `ItundaArt`, `Ids` tokens | `android/core/designsystem` |
| Demo data | `android/core/network/.../DemoInterceptor.kt` |
| Screenshot tests (what CI renders) | `android/screenshots/src/test/java/rw/itunda/screenshots/` |
| Screens living in `:app` | cannot be rendered in CI. Verify by reading, or move them into a feature module only if cheap |

Use the real Home as the worked example: `android/features/home/impl/.../ProtoHome.kt`,
`HomeProtoParts.kt`, `HomePalette.kt`, `WeekSpendChart.kt`, and
`HomeScreenshotTest.kt`. Copy its method.

## 2. The method: measure, build, render, diff, repeat

### 2a. MEASURE the prototype (do not eyeball)

Measure at **deviceScaleFactor 3** (that is what the Android renders use; layout differs by 1-3dp
at 1x, which is how I got burned). Measure BOTH light and dark.

```js
// measure.js  run: cd <scratch> && NODE_PATH=$(npm root -g) node measure.js home
const {chromium}=require('playwright');
const SCREEN=process.argv[2];           // a key of `screens` in the prototype, e.g. 'shop','home','hood'
(async()=>{const b=await chromium.launch();
for(const dark of [false,true]){
  const p=await (await b.newContext({viewport:{width:390,height:2600},deviceScaleFactor:3})).newPage();
  await p.goto('file://'+process.env.PROTO);        // absolute path to itunda_clickable_prototype_v1038.html
  await p.waitForTimeout(1500); await p.evaluate(()=>obSkip()); await p.waitForTimeout(1500);
  if(dark) await p.evaluate(()=>{setTheme('dark');document.documentElement.setAttribute('data-theme','dark')});
  await p.evaluate(s=>{S.stack=[s];render('fade-none')},SCREEN); await p.waitForTimeout(1500);
  const rows=await p.evaluate(()=>{const out=[];const walk=(e,d)=>{if(d>8)return;for(const c of e.children){
    const r=c.getBoundingClientRect(); if(r.width<1||r.height<1)continue; const cs=getComputedStyle(c);
    out.push([d,c.tagName.toLowerCase()+'.'+(typeof c.className==='string'?c.className.split(' ').join('.'):''),
      +r.x.toFixed(1),+r.y.toFixed(1),+r.width.toFixed(1),+r.height.toFixed(1),cs.fontSize,cs.fontWeight,
      cs.color,cs.backgroundColor,cs.backgroundImage.slice(0,140),cs.borderRadius,cs.boxShadow.slice(0,80),
      cs.letterSpacing,cs.lineHeight,c.childElementCount?'':(c.textContent||'').trim().slice(0,30)].join(' | '));walk(c,d+1)}};
    walk(document.getElementById('sc'),0);return out});
  require('fs').writeFileSync(`/tmp/${SCREEN}-${dark?'dark':'light'}-measure.txt`,rows.join('\n'));
  await p.screenshot({path:`/tmp/${SCREEN}-${dark?'dark':'light'}-proto3x.png`}); }
await b.close()})();
```

Notes:
- Screen keys: `Object.keys(screens)` in the page. Eats home = `screens.shop` with `S.shopTab='eats'`.
  "hood" = marketplace. Multi-step flows: set `S.stack=[...]` to the exact stack, call
  `render('fade-none')`, and capture each state (empty, filled, selected, sheet open).
- Interactions (sheets, toggles, tabs): click via `page.evaluate`/`page.click`, then measure again.
  Capture EVERY state the prototype has, not just the resting one.
- The measure dump gives you for each element: x, y, width, height, font size/weight/colour,
  background, gradient, radius, shadow, letter-spacing, line-height. Build the Compose layout from
  these numbers. 1 CSS px at 390 wide = 1dp.
- A tall viewport (2600) shows the whole scroll content; a phone-height viewport (844) shows what a
  user sees. Some elements are positioned relative to the viewport (e.g. Home's dismiss "x"), so
  also capture once at 390x844.
- Sample real pixels when CSS is a gradient/blend you can't read: `PIL.Image.getpixel` on the 3x
  screenshot, or on the 1x one for flat colours. That is how the Home hero glow was reproduced.

### 2b. BUILD it with the measured numbers

- Container padding/margins, row heights, tile sizes, corner radii, font sizes, weights,
  line-heights and letter-spacing come from the dump. Replicate CSS box semantics: a CSS
  `line-height: 24px` text is centred in a 24dp line box, so wrap the screen in
  `ProvideTextStyle(TextStyle(lineHeightStyle = LineHeightStyle(Alignment.Center, Trim.None),
  platformStyle = PlatformTextStyle(includeFontPadding = false)))` (see `ProtoHome.kt`).
- **Fonts:** the app uses Pretendard (`PretendardFontFamily`). Apply it explicitly on raw `Text`
  (set it in the `ProvideTextStyle` too). The browser render here falls back to another font, so
  digit/word widths differ slightly: expect up to ~2dp text drift, everything else must match.
- **Colours:** measured values win. If a value is not in `Ids.colors`, add a small per-screen
  palette object (light + dark), like `HomePalette.kt`, documented as "measured from prototype".
  Do not bend shared tokens to fit one screen, and do not leave raw hex scattered through
  composables.
- **Icons/art:** use `ItundaArt(name, set, size, tint)` from `core/designsystem`
  (sets: Color, Proto, Line, Duo, Solid, Orig). Find the prototype's actual icon name in the HTML
  (`grep` the icon in `itundaicons-25_toss.html`). **Gotcha:** the `Proto` set draws about
  1.2-1.7x larger than its dp box and may sit off-centre at small sizes. Size it by comparing the
  render with the prototype glyph, and nudge with `modifier = Modifier.offset(...)` if needed.
  Line-set icon names differ (`msg`, `heart`...). Emoji (e.g. the prototype's star) will not render
  in CI: use a solid art + tint instead.
- **Faces/avatars:** from `itundaface-2d_rwanda_v7 (1).html`, never invented.
- **Behaviour:** replicate every interaction the prototype has: tap targets and their
  destinations, tab/segment switches, sheets/dialogs, filters/sort, toggles, empty/loading/
  selected states, back behaviour, which controls are disabled when. Read the prototype's JS
  handlers (`onclick`, `S.*` state, `go(...)`) to see them. Reuse existing callbacks/nav
  (`HomeTab` -> `:app`); don't invent routes.
- Hit areas: if the prototype has a 44px button, the target is 44dp (even if the glyph is
  smaller). Beware: Compose hit-testing is clipped to the parent's bounds, so an overhanging
  control must live in a parent big enough to contain it (see `HomeInviteBanner`).
- Keep files small and composable-per-section. Check `scripts/accessibility-lint.py`-style
  concerns: contentDescription on icon-only buttons.

### 2c. Add render tests that cover the WHOLE screen

CI renders what your test asks for. Scrolled sections do not appear unless you scroll to them:

```kotlin
rule.onNode(hasScrollAction()).performScrollToNode(hasText("Some text further down"))
rule.onRoot().captureRoboImage("build/screenshots/<name>.png")
```
Write light AND dark tests, and one per scroll position / state / sheet (see
`HomeScreenshotTest.kt`: `home`, `homeMid`, `homeLower`). Make test data identical to the
prototype's data (same names, amounts, dates), otherwise diffs are noise.

### 2d. COMPARE against the prototype, image to image

1. Fetch CI renders: `cd /tmp/cis && git fetch -q --depth 1 o ci-screenshots && git checkout -q FETCH_HEAD`
   then files are at `/tmp/cis/<sha7>/<name>.png` (remote `o` = the repo; add it once).
2. The CI renders are 1170px wide (3x). Capture the prototype at 3x too (the script above) and
   crop the same scroll window.
3. Put them side by side (prototype left, app right) at half scale and LOOK at it:

```python
from PIL import Image
a=Image.open('proto3x.png').convert('RGB').crop((0,0,1170,2364))
b=Image.open('ci.png').convert('RGB')
o=Image.new('RGB',(1180,1182),'red'); o.paste(a.resize((585,1182)),(0,0)); o.paste(b.resize((585,1182)),(595,0)); o.save('sbs.png')
```
4. List every difference (position, size, colour, icon, weight, wording, state), fix, re-render.
   Typical findings: a 2-4dp vertical drift (line-height/centring), glyph art too big,
   row inset wrong (CSS card margin + padding adds up: 16 + 24 = 40dp), a missing element, wrong
   grey. Use the numeric dump to resolve anything the picture leaves ambiguous.
5. Exit criterion per screen: side-by-side with no visible difference in layout, colours,
   icons, copy and states, in light AND dark, for every state. Remaining differences must be
   limited to the font drift and be written down in your report.

## 3. Work order for a product

1. List every screen/state of your product in the prototype (`Object.keys(screens)`, sub-sheets,
   tabs). Write the list as a checklist in your PR description.
2. Capture all of them (light + dark, 3x, measure dumps).
3. Per screen: build, add tests, push, compare, fix. One screen fully done before the next.
4. Then do a behaviour pass: click through the prototype and the app's screens in order; every
   transition must exist.
5. Finish with a report: what is identical, what differs (and why), and which screens could not
   be rendered in CI.

## 4. Git / CI mechanics (these will bite you)

- Branch: commit to `integration/prototype-parity`. Several agents and the CI bot push to it.
  ALWAYS `git fetch origin && git rebase origin/integration/prototype-parity` (or
  `git merge --ff-only`) right before `git push origin HEAD:integration/prototype-parity`.
  If the push is rejected, fetch + rebase + push again. Never force-push.
- Make sure `origin/integration/prototype-parity` is actually fetched
  (`git config --get-all remote.origin.fetch` should include it). Verify your push with
  `git ls-remote origin integration/prototype-parity` vs `git rev-parse HEAD`.
- Commit trailers: `Co-Authored-By: <model> <noreply@anthropic.com>` plus the session line your
  harness gives you.
- Poll CI with `gh run list --branch integration/prototype-parity --workflow "Android Screenshots" --limit 3 --json status,conclusion,headSha`
  (sleep <= 25s per call; a run takes about 4-6 minutes). Use REST (`gh api`), GraphQL is blocked.
- **Runs get cancelled** when anyone pushes a newer commit. Look at the newest run whose SHA
  contains your commit.
- **Your render only exists if the module compiles.** One agent's compile error (e.g. a missing
  import) blocks everyone's PNGs. Read `gh run view <id> --log-failed | grep -E "e: |FAILED|Reason"`.
  If the culprit is a one-line import in someone else's file, fix it. Otherwise report it.
- A red run is not automatically your fault: check which test failed. Home renders are produced
  even if an unrelated Eats test fails.
- Flaky "Install Saronite dependencies" (yarn) failure: `gh run rerun <id> --failed`.
- "Android PR Validation" builds the APK (rolling pre-release `prototype-latest`, asset
  `itunda-app.apk`). The generic "CI" workflow fails on its own; ignore it unless you touched
  what it checks.
- The Stop-hook message "N unpushed commits" is a false positive; verify with ls-remote.

## 5. Self-check before you say "done"

- [ ] Every screen/state in my checklist has a light AND dark render, compared side by side.
- [ ] Numbers come from the measure dump, not from memory or guesses.
- [ ] No fake controls; every tap does what the prototype does.
- [ ] No hard-coded colours scattered in composables; measured palette is local and documented.
- [ ] `python3 scripts/file-size-lint.py` clean; Konsist boundary not violated.
- [ ] Demo data matches the prototype's; Gson DTOs fully populated in `DemoInterceptor`.
- [ ] Copy is the prototype's exact text (English), no stray Korean.
- [ ] Rebased on the latest remote, pushed, newest CI run on my SHA compiled and rendered.
- [ ] My report lists real differences honestly. I did not claim "exact" without looking at the
      side-by-side.

## 6. Known pitfalls (all real, all happened)

- Measuring at 1x gives layout 1-3dp different from the 3x render the app is compared with.
- Prototype digit animations (`od-c` spans) make amounts 24dp tall inside a 35dp line; the unit
  sits 10dp below the top. Look at the dump, don't assume baseline alignment.
- CSS `margin` on a card plus `padding` on its row both apply: content x = 16 + 24 = 40.
- `ProvideTextStyle` line-height centring is needed or all text drifts 1-2dp.
- A Compose `Modifier.clip` also clips things you wanted to overhang (rings, badges).
- Material3 `Text` ignores Pretendard unless you set the font family.
- Scrolled sections are invisible to CI until you add scroll-to-node tests.
- `isSystemInDarkTheme()` is wrong inside the screenshot tests; detect dark from `Ids.colors`
  (see `homePalette()`).
- Emoji do not render in CI.
- Teammates push constantly: rebase every time, expect cancelled runs.

## 7. Deliverable per product

A pull request (or direct commits to the integration branch if told so) with: the code, the
screenshot tests, the side-by-side images you compared (or their `ci-screenshots` paths), and a
short report: screens done, states covered, known differences, blocked items.
