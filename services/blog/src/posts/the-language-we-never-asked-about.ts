export const theLanguageWeNeverAskedAbout = {
  slug: 'the-language-we-never-asked-about',
  title: "We built a financial-inclusion app for Rwanda and never once asked whether it spoke Kinyarwanda",
  date: '2026-08-08',
  author: 'Web Team',
  tags: ['web', 'i18n', 'accessibility', 'research'],
  excerpt:
    "A research pass into how Paytm and PhonePe serve low-digital-literacy users in India turned into a much simpler, much more uncomfortable question about our own app: does it work in the language most of our own users actually speak? We checked. It didn't. Here's the honest first step, not the finished answer.",
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

## What's still true after today

Android and iOS still have nothing. Web has one screen out of what's probably hundreds. The Kinyarwanda strings that do exist need a real speaker to check them before anyone should call this production-ready. None of that got fixed today, and none of it should be quietly forgotten because two smaller things did get fixed instead. A mission statement about financial inclusion is a real commitment, not a decoration — and the honest state of that commitment, right now, is: started, not finished, written down exactly that way.
`,
};
