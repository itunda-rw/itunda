export const theScreenWeAllAssumedWasAlreadyDone = {
  slug: 'the-screen-we-all-assumed-was-already-done',
  title: "We localized the wrong screen. Then we checked and found we'd done it on all three platforms.",
  date: '2026-08-09',
  author: 'Mobile Team',
  tags: ['android', 'ios', 'web', 'i18n', 'incident-writeup'],
  excerpt:
    "A translation pass on the web app's homepage turned up something we didn't expect: the screen we'd called 'the second screen we localized' was never the screen a person actually lands on after logging in. Checking Android and iOS for the same mistake, on a hunch, found it there too — twice.",
  content: `
Somewhere in the middle of a long localization effort, we went looking for the highest-value screen left to translate on the web app. The obvious next target seemed like a screen we'd already called "the second screen" weeks earlier — the account overview, with savings and loans and investments laid out. Real screen, fully translated, genuinely important. Just not the one we thought it was.

A five-second check of the actual routing code said otherwise: the app doesn't open to that screen. It opens to a different one — a simpler wallet-balance view with recent transactions and a few quick actions — and that screen had only ever been half-translated, because all our attention had gone to the "second screen" that, it turned out, was never first.

## Why this is an easy mistake to make and a bad one to leave

Nobody set out to translate the wrong thing. The overview screen genuinely is the second screen in the sense that matters most while you're building it — the second real, substantial piece of UI to get real data wired up, the second thing worth a dedicated pass. But "the second screen we built" and "the screen a person sees right after logging in" are two different claims, and only one of them is the one that determines whether a Kinyarwanda-speaking user's actual daily experience is translated or not. We'd been treating them as the same claim without ever checking.

Finding this once was useful. What made it worth writing about is what happened when we asked the obvious follow-up question: did we make the identical mistake on the other two platforms?

## Checking on a hunch, and being right twice

Android's answer took about five minutes to find. The app's real default tab renders a composable we'd never touched — a big, separate screen with its own top bar, its own wallet card, its own savings summary — sitting right next to the screen we actually had localized, reachable from a completely different tab. Out of a file more than two thousand lines long, five lines called into the translation system, and all five were from a fix made earlier that same day for something unrelated. Everything else on Android's own real first screen was still English.

iOS made it three for three. The tab a person lands on by default doesn't render the screen our earlier pass had translated either — it renders a separate view, in its own self-contained module, with its own account summary card and its own quick-actions row, none of which had ever seen a translated string. Three unrelated codebases, three different histories, three different engineers' worth of decisions along the way, and the same shape of gap in every one of them.

That's not three coincidences. It's one mistake that's easy to make once you're deep enough into any single platform's own file structure to lose track of which screen the user's actual first tap lands on — and it's the kind of mistake that specifically hides itself, because every individual translation you shipped along the way was correct. The bug isn't in any string. It's in which screen got the strings.

## What actually changed

All three got the same fix, adapted to each platform's own constraints. Web's real landing screen got its remaining untranslated pieces filled in. Android's needed its own set of new string resources, plus a small Compose-specific correction: a couple of status labels and list-building patterns that had to be restructured slightly, because you can't call the translation function from inside certain kinds of loops the way the original code was shaped. iOS needed its own version of the same self-contained approach we'd already built once for a different screen that lives in its own separate module — and along the way we caught a real bug before it shipped: some of that screen's content was defined as data that only gets computed once, ever, for the lifetime of the running app. Baking a translated string into something that only evaluates once means it freezes in whichever language happened to be active the first time, and never updates again even if someone switches languages later. We caught it by thinking through what "once" actually meant before writing the fix, not by finding it broken after the fact.

## What we're not claiming

Web's fix is verified the way this whole effort tries to verify things: a real browser, a real login, real Kinyarwanda text confirmed on the actual rendered page. Android and iOS aren't there yet — both compile cleanly, iOS's specific module even built and linked and codesigned as a real framework, but neither has been watched running on an actual device or simulator this round. That's a real, named gap, and it's exactly the discipline this whole localization effort has tried to hold itself to from the start: a change that traces correctly through the code is a real step forward, and it is not the same claim as a screenshot of it working. We got fooled once already this thread by code that looked finished and wasn't. We're not going to blur that line again just because the fix feels obviously right this time too.
`,
};
