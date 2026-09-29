export const theFixThatForgotItsOwnLesson = {
  slug: 'the-fix-that-forgot-its-own-lesson',
  title: "We fixed a real privacy bug in this endpoint once already. The fix itself still had the bug we keep finding everywhere else.",
  date: '2026-08-08',
  author: 'Backend Team',
  tags: ['backend', 'security', 'incident-writeup'],
  excerpt:
    "A third pass through our own IDOR audit closed out the last unchecked corner of the backend. The one real finding sat inside a method whose own doc comment already describes fixing a worse version of the same underlying problem — and the second fix was incomplete in exactly the way our own established convention says to check for.",
  content: `
Two passes into auditing this backend for IDOR — insecure direct object reference, the class of bug where a resource ID in a URL leaks more than it should — we'd covered roughly 85 controllers and found two real, if minor, instances of the same specific shape: an endpoint correctly checks that a resource belongs to the caller, but returns the wrong status code when it doesn't. A 403 instead of a 404. That sounds pedantic until you think about what a 403 actually confirms to whoever's asking: this thing exists, and it's real, you're just not allowed to see it. A 404 confirms nothing. For anything private, that distinction is the whole point of hiding it in the first place.

We closed out the remaining, previously-unaudited corner of the backend today — designated-driver bookings, dine-in orders, half a dozen merchant sub-features, certificates, incident reports, vehicle inspections, the rest of a list that had been sitting untouched since the second pass. One real finding came out of it, and it's a more interesting one than the first two, because of where it was hiding.

## The method that already knew better

\`CommunityService.getSessionAttendance\` has a doc comment on it. It's not a generic one — it names a real, specific bug this exact method had before: with zero authentication or membership check of any kind, anyone who could guess or find a real meetup session ID could pull back the full list of real user IDs who'd checked in. That's a genuine privacy leak, worse than the one we're talking about today, and it got a real fix: a membership check, the same one the check-in flow itself uses. Only a real, joined member of that meetup's own group chat can see who else showed up.

That fix was correct. It's also, on its own, proof that whoever wrote it understood exactly how sensitive this data is — private group membership, real user identities, real attendance records. It's the kind of code that should have every reason to also get the 403-versus-404 distinction right, because someone clearly already knew this data needed protecting.

It didn't. The membership-violation exception this fix throws was mapped to a 403. Which means: a real session ID from a group you're not in returns "you're not allowed to see this" — an admission the session and its group are both real. A fake session ID returns 404. The membership gate the earlier fix built was completely correct. It just told a stranger's automated probe of session IDs exactly which ones were real, one status code at a time.

## Why this one is worth writing down separately

The first two times we found this bug shape, it read like an oversight — a controller nobody had specifically thought hard about, following an old default instead of the newer convention. This time it sits inside code that had *already* been the subject of a real privacy fix, on the exact same axis of "how much does this response reveal to someone who shouldn't be looking." Getting the big, obvious half of the problem right (don't leak the attendee list) and missing the smaller, quieter half of the same problem (don't leak whether the list exists at all) is a genuinely easy thing to do, because they don't look like the same bug when you're staring at the first one. They are.

The fix here was one line — the same fix as the two before it, changing a \`FORBIDDEN\` to a \`NOT_FOUND\`. The part worth remembering isn't the line. It's that "we already fixed the real bug here" turned out to mean exactly what it said, and nothing more — and that's true of a lot of code that looks finished until you go back and check the version of the question you weren't asking the first time.

## Where the audit stands now

Three passes, roughly 105 controllers, the practical entirety of this backend's API surface. Three real findings, every one of them the same shape — an ownership check that works, mapped to the wrong status code. Zero instances of the more severe version: a check that's missing outright, letting a stranger actually read or change something that isn't theirs. That's a genuinely good result for a system that moves real money, and it's also not something we're going to declare permanently true. The next new controller gets the same default review as the first hundred and five did.
`,
};
