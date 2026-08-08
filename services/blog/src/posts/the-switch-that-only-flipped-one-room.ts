export const theSwitchThatOnlyFlippedOneRoom = {
  slug: 'the-switch-that-only-flipped-one-room',
  title: "We shipped a language switcher across three screens before checking whether it actually reached the second one.",
  date: '2026-08-08',
  author: 'Mobile Team',
  tags: ['android', 'i18n', 'incident-writeup'],
  excerpt:
    "Adding a language toggle to a fourth screen turned into tracing exactly where the first one's state actually went. The answer: nowhere past the screen it was drawn on. Every translated string on every other screen this session had built was quietly reading the phone's own system language the entire time.",
  content: `
Four screens into a localization effort, we went to add a language switcher to a screen that didn't have one yet — Settings, the one place a person who's already logged in could reasonably expect to find it, since the switcher on the login screen stops being reachable the moment you're signed in. That should have been the easy part. We'd built this exact toggle once before, on the login screen itself, and it worked — we watched it work, on a real emulator, switching a real screen from English to Kinyarwanda and back with a tap. Wiring the same thing onto a second screen looked like copying a pattern that was already proven.

It wasn't a pattern. It was one screen's local answer to a question the rest of the app never actually asked.

## What "it worked" actually meant

The original switcher lived entirely inside the login screen's own code: a small piece of state, a function that saved the choice to disk, and a wrapper around that one screen's own content that told it which language to draw itself in. All of that is real, and all of it does exactly what it looks like it does — for exactly the one screen it's attached to. Nothing else in the app was ever told about it.

That distinction didn't matter yet when we built it, because at the time the login screen was the only thing translated. Every screen we localized after that — the account overview, the transfer flow, most of a settings screen — got its own real strings pulled out into real translation files, in both languages, each one individually correct. Each of those screens asks the same underlying question every time it draws a piece of text: what language am I supposed to be in? And every single one of them was answering that question by asking the phone's own operating system, not the app's own switcher, because nothing had ever told them the switcher existed.

If your phone happens to already be set to Kinyarwanda at the system level, you would never have noticed. Everything would have rendered correctly, for a reason that has nothing to do with any of the work in this thread. If your phone is set to English — which is the actual, overwhelmingly likely case for the audience this whole effort exists for — flipping the toggle on the login screen would visibly, satisfyingly switch that one screen to Kinyarwanda, and then every single screen after it would go straight back to English, silently, with no error and nothing that looked broken. The bug doesn't announce itself. It looks like success right up until the second screen.

## Finding it by trying to reuse it, not by looking for it

We didn't set out to audit this. We went looking for where the login screen's switcher lived so we could copy the same approach onto Settings, and the honest answer to "where does this state live" turned out to be "nowhere that anything else can see." The screen that hosts every other screen in the app — the one thing that sits above both the login flow and everything a signed-in user sees — never wrapped any of it in the piece of code that actually makes the language override take effect. Only the login screen ever built that wrapper, around itself, for itself.

That's a real, specific kind of bug, and it's the same shape as one we hit on day one of this whole effort: a switch that looks connected and isn't. The first time, it was an API call that silently did nothing because of an assumption about what kind of screen it was running inside. This time it's a UI element that only ever reached as far as its own four walls. Different mechanism, same lesson, and we'd already written that lesson down once — which made it worse to find a second instance of it, not better, because it's exactly the kind of thing a "did we actually check this works app-wide" pass should have caught before now.

## What actually needed to change

The fix isn't "add the same trick to a second screen." It's moving the state somewhere every screen can see it, and doing the actual language-override wrapping exactly once, at the true top of the app — above the login screen and above everything a signed-in user sees, not inside either one separately. We had a working example of that shape already sitting in the codebase: a theme preference — light mode, dark mode, follow the system — that lives as a single shared, observable value, read once at app startup and updated from wherever a person changes it. We gave the language choice the same treatment. One shared value. One place that watches it and rebuilds the app's text-rendering context when it changes, sitting above every screen instead of inside one of them. The login screen's own switcher and the new one on Settings both just read and write that same shared value now, instead of each owning a private, disconnected copy of the idea.

The part worth sitting with is what this means about everything localized before today. None of that work was wrong. Every string is genuinely extracted, genuinely translated, genuinely wired up correctly to ask "what language am I in" at render time. The question itself just wasn't reaching the only place that had a real answer, until today. A translation effort can be executed correctly at every step and still not work, if the one connective piece that's supposed to carry the user's actual choice across screen boundaries was only ever built to work in the one room it was born in.

## What we're not claiming

We haven't watched this fixed version run. The device that would prove it — flip the switch on Settings, walk into the overview screen, see Kinyarwanda where English used to be regardless of what the phone's own system language is set to — isn't available to us in this session the way it was for the very first version of this switcher. What we have is a change that traces correctly through the code: the shared value exists, the one wrapper that matters now sits above both the logged-in and logged-out states instead of inside just one of them, and every screen's existing translation calls don't need to change at all, because they were already asking the right question — they just needed the app to finally be listening for the real answer. That's a real fix, reasoned through carefully. It is not the same claim as a screenshot of it working, and after finding a bug that looked exactly like success for four screens running, we're not going to blur that difference again.
`,
};
