# Shop (shopping) — prototype v1038 parity addendum

Read `docs/PROTOTYPE_PARITY_AGENT_PROMPT.md` first. It holds the method (measure at 3x, build
with exact dp/sp, render in CI, diff by pixel measurement), the git/CI rules and the pitfalls.
This file only adds what is specific to **Shop** (the non-Eats half of the prototype's Shop tab).
Eats is a different product with its own owner; do not touch `features/eats`.

## 1. What "Shop" is in the prototype

`screens.shop` renders both tabs. Eats is `S.shopTab==='eats'`; **Shop is `S.shopTab==='shop'`**
(`setShopTab('shop')`, `openShop('shop')`). `screens.shop` is wrapped several times near lines
2911, 2989, 4352, 4429 and 6261; the last wrapper adds the filter row (`fRow()`, above `#shsort`)
for the Shop tab. Measure the final, fully wrapped result, not the first definition.

Screens and states to capture (light + dark, 3x), found via `Object.keys(screens)` and the
`onclick` handlers on the Shop tab:

| Prototype key | What it is |
|---|---|
| `shop` (shopTab `shop`) | Home: header with cart button + badge, category chips, sort chips (`#shsort`), filter row, product grid, wishlist hearts |
| `spcat`, `minicat` | Category browse |
| `product` | Product detail (`PRODS[S.pi]`): 260dp hero r28, heart 44dp, price/discount/old price, cashback line, delivery and pay rows, low-stock line, "Add to cart" CTA |
| `scart` | Cart (opened by `openCart()`), quantities, totals, pay CTA |
| `shpay` | Shop pay / checkout |
| `payorder`, `ordered` | Order confirmation and done |
| `myorders`, `sorders` | Buyer order list and order detail/tracking |
| `myshop`, `seller`, `mxSeller` | Seller side. Only build what the prototype's consumer flow reaches |
| `cashback` | Cashback screen reached from product/cart |

Also capture every sheet and state: sort sheet, filter sheet, empty cart, out-of-stock,
`x.s<=5` low stock, favourited vs not, cart badge 0 vs n, insufficient balance.
Walk the real `onclick` handlers (`addCart`, `tgFav`, `openCart`, `payOrder`, `setShopTab`) to get
destinations; do not guess them.

## 2. Where the Android code lives

`android/features/shop/impl/src/main/java/rw/itunda/feature/shop/impl/` (note: `java/`, not
`kotlin/`). Existing files: `ShopProtoHome.kt`, `ShopScreen.kt`, `ShopBrowseComponents.kt`,
`ShopProductDetail.kt`, `ShopDetailDispatch.kt`, `ShopOrders.kt`, `ShopMerchantOrders.kt`,
`ShopMerchantDetail.kt`, `ShopReviews.kt`, `ShopReturns.kt`, `ShopPay*.kt`,
`ShopSellerContactPicker.kt`.

- `ShopScreen.kt` is baselined in `scripts/file-size-baseline.json` and must not grow. Put new
  work in new files (max 500 lines) and extract, never bump the baseline.
- Only `ShopProtoHome` has a render test today (`ShopScreenshotTest.kt`, light only, 390x844 with
  a 20dp padding box that the prototype does not have: remove it so the render is the real
  screen). Add tests for every screen and state above, light and dark, with scroll-to-node tests
  for the grid below the fold.
- Stateless-content pattern: split each screen into `XContent(state, callbacks)` and the
  network/state wrapper, so CI can render it (Pay's `PayHub`, `CouponBoxContent` are examples).
  Screens that need `NetworkClient` cannot render otherwise.
- Palette: add `ShopPalette.kt` (measured, light + dark) like `HomePalette`/`PayPalette`. Features
  cannot import each other's `impl` (Konsist), so do not borrow Pay's or Eats' palette.
- Product images: the prototype draws `gl(x.g, 132)` glyph art on a tinted tile, not photos. Find
  the glyph in `itundaicons-25_toss.html` and use `ItundaArt`. Remember the Proto set draws
  roughly 1.2-1.9x its dp box.
- Demo data: use the prototype's `PRODS` array verbatim (names, merchants, prices `p`, old prices
  `o`, stock `s`), and the same `fmt()` grouping (`7,000`). If a DTO gains a field, add it to the
  `DemoInterceptor` JSON too (Gson ignores Kotlin defaults).
- Cashback copy is computed: `round(price * 0.01)` RWF back (1%), discount % is `pct(p, o)`.

## 3. Shop-specific measurements to take (not just the dump)

- Grid: column count, gutter, card width/height, image tile ratio and radius, how a card with and
  without an old price differs, heart button position and hit size (44dp).
- Sticky/bottom-pinned elements (CTA bar, cart bar): capture at 390x844 as well as 2600 tall, and
  remember the CI viewport is 390x788dp, so bottom-pinned items differ by test viewport only.
- Cart badge: 18dp min width, r9, `#D92D20`, 11sp 800 at top 6 / right 4.
- Numbers: letter-spacing on prices (-0.6 on 28px, -0.4 on 22px), strikethrough colour for `.po`.
- Dark mode: error red and the link colour (`--lk`, used for the cashback line) change in dark;
  take them from the dump, not from light.

## 4. Behaviour rules specific to Shop

- Add to cart, favourite toggle, quantity steppers, cart badge, sort and filter must change state
  exactly as `addCart`/`tgFav`/`scCount` do, and survive navigating back.
- Pay must be disabled and show the prototype's error line when the total exceeds the itunda Pay
  balance (`S.pay`, 120,500 RWF in the demo).
- Seller screens (`myshop`, `seller`, `mxSeller`): do not expose them from the consumer app unless
  the prototype's consumer flow links to them. If something in the prototype has no real
  backing (for example a fake search or promo chip), keep it non-interactive and list it in the
  report instead of wiring a fake control.
- Out of scope: Eats screens, Pay internals (call into them via the existing callbacks), and any
  new route. Reuse `HomeTab`/`:app` navigation.

## 5. Checklist before you say "done"

- [ ] Screen/state checklist from section 1 written in the PR, each with light + dark renders.
- [ ] `ShopScreenshotTest` no longer wraps screens in padding the prototype lacks.
- [ ] Measured `ShopPalette`, no scattered hex; Pretendard on every raw `Text`.
- [ ] `python3 scripts/file-size-lint.py` clean (`ShopScreen.kt` did not grow).
- [ ] Every control does what the prototype's handler does; nothing fake.
- [ ] Rebased, pushed, newest "Android Screenshots" run on your SHA compiled and rendered.
- [ ] Report lists real differences (font drift, CI viewport, anything unrenderable).
