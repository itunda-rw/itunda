export const whatWeFoundOutsideOurOwnReference = {
  slug: 'what-we-found-outside-our-own-reference',
  title: "We've been reading one company's homework for months. Here's what five others taught us in one afternoon.",
  date: '2026-08-08',
  author: 'Platform Team',
  tags: ['backend', 'android', 'ios', 'security', 'research', 'incident-writeup'],
  excerpt:
    "This whole project is modeled on Toss, and most of our research has been reading Toss's own conference archive. Told to look wider — domestic and international, not just our one reference point — we spent an afternoon on M-Pesa, Kakao, Nubank, WeChat, and Cash App instead. Two real bugs got fixed. One real, much bigger gap got found and, honestly, not fixed yet.",
  content: `
Every research pass this project has run for months has pointed at the same place: Toss's own conference archive, the company this whole app is explicitly modeled on. It's been a good source — a real accessibility scanner, a real design-system fix, a real silent-failure bug in account linking, all traced back to a Toss engineering talk. But it's one company's homework. Asked to look wider — "different ecosystems, domestic and international" — we spent an afternoon on five others instead: M-Pesa and MTN Mobile Money (the actual mobile-money rails we already integrate with), Kakao Pay and KakaoBank (Toss's own domestic rivals), Nubank and GCash and Paytm (the closest real analogues to what we're actually trying to build — mobile-first finance for underbanked markets), WeChat and Alipay's Mini Program platforms (the original version of the exact mini-app architecture we already run), and Revolut, Wise, N26, and Cash App.

Five parallel research passes, same rule as always: real sourced findings, no invented file paths — a rule that exists because an earlier pass in the Toss thread once cited a file that turned out not to exist, and we had to catch and correct it. This time, everything got checked before it got touched.

## The bug that showed up twice already, and once more

The M-Pesa/MTN research turned up something almost embarrassing in hindsight: real agent-network fraud in mobile money follows two well-documented patterns — deposit structuring (breaking a big cash-in into smaller ones to stay under reporting thresholds) and rapid account draining through a compromised account at an agent counter. We checked our own agent module against that. It had zero fraud-engine coverage. Not partial — zero. Cash-in and cash-out both built their ledger entries directly, with no \`FraudRuleEngine.evaluate()\` call anywhere in the file.

This is the third time this exact bug shape has turned up this project — Gift, GiftVoucher, and SplitBill all had the identical gap, found and fixed earlier. A real money-moving service gets built, correctly, with all its own domain logic — daily limits, receipt dedup, till reconciliation, an actual commission schedule modeled on MTN's real published rate card — and the one thing that isn't specific to that feature, the shared fraud check every other money-moving path already has, just doesn't get wired in, because wiring it in isn't part of building the feature itself. It's the kind of gap that only shows up when someone goes looking for it specifically, which is exactly what happened three times now.

Fixed the same way as the previous three: one line calling the existing engine, right after the ledger transaction posts, for both cash-in and cash-out.

## The bridge that trusted every URL

The WeChat/Alipay research pulled up something we hadn't compared ourselves against before: WeChat's Mini Programs are explicitly restricted from navigating anywhere outside a pre-declared list of domains. Not a suggestion — a hard platform rule. We have our own version of this architecture, Saronite, and its \`openURL\` bridge method — the one that lets a mini-app's JavaScript tell the native app "go open this link" — took any string, parsed it as a URL, and opened it. No domain check. No scheme check. On Android, it also skipped a permission gate every other bridge method in the same file already goes through — a gate whose own doc comment claimed "every method except one" was covered, which wasn't quite true.

Today, every mini-app we actually run is our own first-party code, so nobody's exploiting this right now. That's exactly the kind of true-but-not-reassuring fact worth acting on before it stops being true. Fixed on both platforms: \`https://\` is now checked against \`itunda.rw\` and its subdomains, \`tel:\` and \`mailto:\` stay allowed, everything else gets rejected.

## The one that turned out fine

Not every comparison found a gap. Cash App's parent company was fined $220 million combined by federal and state regulators for how it handled fraud disputes — the documented failure was telling users to go argue with their own bank instead of actually investigating, with no real bound on how long that could drag on. We went and checked our own dispute path expecting to find something similar, or at minimum something worth tightening. We found a real support-ticket system already built for exactly this: a dispute tied to a specific transaction, a real SLA deadline that's tighter for account-takeover reports than general complaints, an automatic wallet freeze the moment someone reports their account compromised, and a genuine refund-reversal path — already fixed once before for its own bug, a double-refund race a previous audit caught. It's reachable from all three real apps, not just the backend. This is the rare research finding that's just good news: whoever built this flow already solved the problem a much bigger company got fined nine figures for not solving.

## The one we're not going to pretend to have fixed

The most uncomfortable finding came from looking at Paytm and PhonePe's approach to serving users with lower digital literacy — heavy regional-language support, because in a comparable market more than half of new fintech users actively prefer their own language over English. That sent us checking something we'd genuinely never checked directly before: does this app, built explicitly for financial inclusion in Rwanda, support Kinyarwanda anywhere at all?

It doesn't. Not partially, not on one platform and not the others — we checked every locale convention we could think of, on web, Android, and iOS, and found nothing. Every string in the app is hardcoded English.

We're not fixing that today, and we're saying so plainly instead of either ignoring it or rushing something half-built. Real localization means wiring an actual translation framework into three separate client codebases and then translating what's probably thousands of individual strings — closer in size to a dedicated project than an afternoon's fix. Writing this down honestly, with a real starting point (locale infrastructure first, then the highest-traffic screens, not everything at once) felt like the more useful outcome than quietly shipping two small fixes and letting the bigger, harder thing go unrecorded because it didn't fit in the same pass.

## What didn't get finished

Three of the five research passes ran out of search budget partway through — Naver Pay and Samsung Pay never got looked at, and a couple of leads on Wise's and Revolut's own engineering blogs stayed unrecovered. Running five ecosystems fully in parallel split a shared research budget five ways; a narrower pass, one ecosystem at a time, would probably get further. That's a process note for next time, not a reason to have waited to start this time.
`,
};
