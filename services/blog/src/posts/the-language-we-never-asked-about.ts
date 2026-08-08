export const theLanguageWeNeverAskedAbout = {
  slug: 'the-language-we-never-asked-about',
  title: "We built a financial-inclusion app for Rwanda and never once asked whether it spoke Kinyarwanda",
  date: '2026-08-08',
  author: 'Web, Android & iOS Teams',
  tags: ['web', 'android', 'ios', 'i18n', 'accessibility', 'research', 'incident-writeup'],
  excerpt:
    "A research pass into how Paytm and PhonePe serve low-digital-literacy users in India turned into a much simpler, much more uncomfortable question about our own app: does it work in the language most of our own users actually speak? We checked. It didn't. Here's the honest first step, not the finished answer — including a real bug the compiler never would have caught.",
  content: `
This app exists because of a real mission statement, repeated enough times in our own commit history and design docs that it stopped feeling like something we needed to double-check: financial inclusion, for people the traditional banking system in Rwanda has historically underserved. We've built real infrastructure around that idea — alternative credit scoring that doesn't need a credit bureau, a USSD channel for feature phones, an agent network for cash-in/cash-out where there's no bank branch. What we never did, in any of that work, was ask a much more basic question: what language is all of this actually written in?

The answer, once we finally checked, was uncomfortable in its simplicity. English. All of it. Every screen, every button, every error message, on every platform.

## How the question even came up

We'd been researching Paytm and PhonePe — two real fintech products serving hundreds of millions of users in a market with a lot in common with ours: large populations with limited traditional banking access, a wide range of digital literacy, and a real, measured preference among new fintech users for their own language over English — over half, in the specific market we were looking at. That's not a soft cultural note. Language is UX. A financial app that reads like a foreign document to someone is an app that's harder to trust with money, no matter how good the underlying product is.

That sent us checking something about our own app we'd genuinely never checked before: does it support Kinyarwanda anywhere at all?

## What we actually found

Nothing. Not partially, not on one platform with gaps elsewhere — we checked every convention we could think of, across web, Android, and iOS: locale-specific resource folders, an i18n directory, a localization config, a stray translation key anywhere in the codebase. All empty. Every one of the three real client platforms we ship hardcodes English directly into the UI. Not a placeholder, not a TODO — just English, everywhere, because nobody had ever built anything else.

## What we shipped, and what we very deliberately didn't

Full localization of three separate client codebases is not a today problem. It's probably not a this-week problem. Realistically it's a project of its own — locale infrastructure on three different platforms, and then the actual work of translating what's likely thousands of individual strings, correctly, in a language none of us on this particular pass are fluent native speakers of. Pretending otherwise and rushing something across all three platforms in one sitting would have produced exactly the kind of half-finished, silently-declared-done work we've tried hard not to ship this whole project.

So we did the smaller, honest version instead. Real locale infrastructure went into the web app — a small dictionary-based system, deliberately not a heavyweight i18n framework, because right now we have two languages and one translated screen, not the dozens of locales and plural-rule tables a bigger framework is built for. A working language switcher landed on the one screen that matters most before anything else does: login. It remembers your choice. It defaults to Kinyarwanda automatically if that's what your phone is already set to.

And then we said, directly in the code, the thing that matters most about what we just built: the Kinyarwanda text is a careful, good-faith translation, not verified by a native speaker. That's not a hedge to cover ourselves — it's the same honesty this codebase already insists on everywhere else it can't fully verify something itself. A demo balance gets labeled as a demo balance. A simulated ID check gets labeled as simulated. This gets the same treatment: real infrastructure, real working feature, translation quality flagged honestly rather than asserted with confidence we don't actually have.

## The switcher that said "EN" twice in a row

We didn't stop at web. Android's own login screen is the same kind of highest-traffic, first-impression screen, and it had the identical problem: not one string resource anywhere in the whole app module, everything hardcoded straight into Kotlin. We pulled every literal string out into real \`values/strings.xml\` and \`values-rw/strings.xml\` resources, wired a language switcher into the same top bar, wrote it against Android's standard per-app language API, watched it compile clean, and moved on to verify it — which is where it stopped being clean.

We built the debug APK, installed it on a real emulator, and tapped the switcher. It still said "EN." We checked whether we'd tapped the wrong spot — pulled the exact on-screen coordinates straight from the UI hierarchy, tapped dead center, twice. Still "EN." That's the moment a change stops being "probably fine, it compiled" and starts being a real bug, and it's exactly the kind of thing that only shows up when you actually run the thing instead of trusting the build log.

The real cause: the API we used, \`AppCompatDelegate.setApplicationLocales\`, only works if the screen belongs to an \`AppCompatActivity\`. Ours doesn't — it's a plain \`FragmentActivity\`, which means there's no \`AppCompatDelegate\` attached to it to even notice the language had changed, let alone act on it. The call wasn't throwing an error. It was just talking to nobody.

Switching the app's whole Activity base class to fix one language switcher would have been a much bigger, riskier change than the feature itself. Instead we built the switch ourselves: wrap the localized screen in its own configuration override, applied directly through Compose, independent of whatever kind of Activity happens to be hosting it. Rebuilt, reinstalled, tapped the switcher again. This time the screen actually changed — English to Kinyarwanda, live, no reload. We killed the app entirely and relaunched it to check the choice actually stuck. It did.

## The third platform, and the honest gap in how we checked it

We closed out the third platform the same day. iOS's login screen got the identical treatment — a small dictionary of strings, a toggle switcher, a choice that survives closing and reopening the app. We deliberately kept it inside the one file that already existed rather than adding a new file to the project: this particular Xcode project doesn't use the newer auto-syncing folder feature, which means every new file has to be registered by hand in a large, easy-to-corrupt project file. That felt like a bad trade for a single screen's worth of strings, so we didn't make it.

Here's the part worth being honest about instead of quietly smoothing over: we couldn't check this one the way we checked Android's. The Android bug only surfaced because we installed a real build on a real emulator and pressed the actual button. We don't have that same setup ready for iOS in this environment — the full app can't even finish linking yet, for reasons that have nothing to do with this change, and there's no simulator-screenshot habit built up the way there is on the Android side. What we do have is a clean build of the specific file with zero errors, a clean syntax check, and a clean accessibility pass. That's real signal, but it's not the same thing as watching the screen actually change languages in front of us, and after what just happened on Android, we're not willing to pretend those are equivalent. Said plainly, in the code and here: this one still needs a real device check before anyone treats it as verified the same way the other two are.

## What's still true after today

Every platform now has one localized screen and a language switcher that actually works — two of them proven by watching it happen, one of them proven only by the build succeeding. The Kinyarwanda strings on all three still need a real speaker to check them before any of this is production-ready. None of that got fixed today, and none of it should be quietly forgotten because a handful of smaller things did get fixed instead. A mission statement about financial inclusion is a real commitment, not a decoration — and the honest state of that commitment, right now, is: started on three platforms, finished on none of them, verified to different degrees on each, written down exactly that way. The Android bug is still the sharpest lesson of the three: a language switcher that silently does nothing is worse than no switcher at all, and the only way we found that out was by actually pressing the button.
`,
};
