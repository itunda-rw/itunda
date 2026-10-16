# Android parity audit — clickable prototype v66

**Scope:** Android app only. Source of truth: `itunda__clickable_prototype_v66.html` (prototype title: “itunda, clickable prototype v66”).  
**Brand:** preserve canonical Itunda Brand v3.1 assets and current accent `#8F89FF`.  
**Rule:** a route existing in Kotlin is not proof of visual parity. Mark a row visually verified only after comparing the running Android screen and its interactions against v66 on a device/emulator.

## Screen and interaction inventory

Status key: **Route exists** = Android entry point/module was found during source audit; **Visual check pending** = no verified device screenshot comparison yet; **Audit pending** = full behavior has not been traced end-to-end.

| Prototype area / flow | Prototype interaction anchors | Android destination found in source | Current status |
|---|---|---|---|
| Home and quick actions | Home render, quick actions, balance, recent activity | `HomeEntryPoint`, `HomeTab.kt` | In progress; device parity check pending |
| Banking and account | `openBank`, `open31`, account/detail flows | `BankHubEntryPoint`, account detail/manage screens | Route exists; visual/interaction audit pending |
| Transfers and payments | `flow`, `amount`, `confirm_`, `finish`, `openTransferHub`-related flows | `TransferHubEntryPoint`, Pay tab, transfer flow modules | Route exists; each step and error state pending |
| Add money and deposits | `openAct`, `drawAdd`, `addMoney`, `depAsk`, `depDo` | `AddMoneyScreen.kt`, app money-action routes | Route exists; backend result and parity pending |
| Bills and airtime | `openBill`, `billSet`, `billAmt`, `billNum`, `billChk`, `billPay`, `billDone` | Existing Pay Bills mini-app activity; Home bill shortcut wired to it | Route exists; bill steps and airtime destination audit pending |
| Savings and goals | `openGoal`, `goalMake`, `goalAdd`, `goalDel`, `tgFreeze` | Savings goal dialog, Grow31 and weekly savings screens | Route exists; flow-by-flow parity pending |
| Activity and spending | `openSpend`, `openAct`, transaction filters/search | Account ledger, transaction history, spending screen | Route exists; filter/search visual checks pending |
| Shopping | `openShop`, `openProd`, `addCart`, `openCart`, `checkout`, `shopPay`, `shopDone` | `CommerceShopEntryPoint`, shop feature module | Route exists; cart/checkout parity pending |
| Restaurants and food orders | `openRest`, `eCart`, `ePay`, `eDone`, tracking flow | `EatsEntryPoint`, Eats feature module | Route exists; order/tracking parity pending |
| Neighborhood marketplace | `openHood`, `hdOpen`, `hdCat`, `hdApply`, `hdThread` | Marketplace/community/property/jobs feature entry points | Routes exist; prototype mapping and visual audit pending |
| Community posts and chat | `openCommunity`, `postSheet`, `postGo`, `postLike`, `openChat`, `chatSend` | `CommunityEntryPoint`, `TalkEntryPoint` | Routes exist; post and messaging interactions pending |
| Split/request money | `reqSheet`, `reqCode`, `reqCopy`, `splitSheet`, `splitMake`, `paySplit` | Transfer hub and Talk split-bill components | Route exists; end-to-end interaction audit pending |
| Notifications | `notifSheet` and notification actions | Notifications feed and app notification components | Route exists; visual/interaction audit pending |
| Transit and mobility | `openTransit`, `transitTop`, `transitTap` | Transit, ride and maps destinations | Routes exist; match each prototype path before marking done |
| Investing and stocks | `openInvest`, `stkSheet`, `stkBuy`, `stkSell` | `InvestEntryPoint`, wealth feature module | Route exists; purchase/sell guardrails and visual audit pending |
| Settings, theme and identity | `openSettings`, `tgSet`, `setTheme`, `logOut`, identity/security flows | Settings, My/profile, account/device management screens | Routes exist; prototype-to-screen map pending |
| Onboarding and PIN | `obGo`, `obBack`, `obSkip`, `obPhoneNext`, `obKey`, `obCreate`, `obStart` | Android app onboarding/authentication flows | Audit pending; verify every step and validation state |
| Support and reporting | `supportChat`, `reportSheet`, `reportGo` | Existing support/report surfaces to verify in app router | Audit pending |
| Search, language and accessibility | `allSearch`, `langSheet`, `setLang`, theme, empty/error states | App search and localization resources | Audit pending across all destinations |

## Acceptance criteria

- [ ] Every top-level prototype screen has a named Android destination and a tested back path.
- [ ] Every prototype interaction has a real Android action; no decorative/inert controls.
- [ ] Forms cover loading, validation, empty, failure, success, and retry states where applicable.
- [ ] Payment and order success screens are driven by real operation results, never optimistic/fake success.
- [ ] Kinyarwanda and English labels use Android resources; no newly introduced hardcoded user-facing copy.
- [ ] Compare screenshots at the same viewport, system bar state, theme, locale, and data state.
- [ ] Record screenshot evidence and the tested device/emulator for each completed screen.
- [ ] Run Android build, lint, and relevant UI tests after each implementation batch.
- [ ] No web or iOS source changes as part of this effort.

## Immediate next implementation batch

Continue from the existing Home milestone by tracing the prototype’s transfer/account/activity paths into the real Android routes, then fix the highest-impact mismatch with tests. Do not report whole-app parity until the checklist has device evidence for every row.
