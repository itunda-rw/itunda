export const theFeatureWeShelvedTwice = {
  slug: 'the-feature-we-shelved-twice',
  title: "We looked at this feature twice, wrote down exactly why it was hard, and walked away both times. The third time we actually built it.",
  date: '2026-08-09',
  author: 'Backend Team',
  tags: ['backend', 'messaging', 'feature-writeup'],
  excerpt:
    "Splitting a bill with someone in a group chat has worked for weeks. Splitting one with a single person you're just talking to, one on one, never did — and twice we went looking at why, wrote down a real design problem, and left it for later. The problem turned out to have a smaller fix than either investigation assumed.",
  content: `
There's a specific kind of unfinished work that's more honest than most: the kind where you actually did the thinking, wrote it down clearly, and still didn't build it. Not because you forgot, and not because it was low priority — because the real shape of the problem, once you looked closely, was bigger than it first appeared, and building it properly meant touching code you didn't want to touch carelessly. That happened to this feature twice.

## What already worked, and the one door it didn't have

Splitting a bill inside a real group chat has been solid for weeks: pick a total, pick who owes what, and each person pays their own share directly, no middleman holding the money. It's a real, complete feature. What it never had was a way in from a conversation between exactly two people. If you and one friend split a taxi, there was no group to attach that split to — you'd have needed to create an actual named group chat for a single one-time expense, which nobody does, and which would have left a permanent, oddly-named group cluttering both of your real conversation lists forever after.

## The first two times we looked

The first pass noticed the gap and moved on — a real, sourced idea, not yet investigated. The second pass actually designed it out. The obvious shortcut was right there: the system that creates a group chat doesn't actually require more than two people, so in principle you could quietly create a real two-person group behind the scenes and let the split-bill feature attach to it exactly like it attaches to any other group, with zero changes to the split logic itself.

That idea died the moment we thought through what a person would actually see. Creating a real group the ordinary way means it shows up in your real list of groups — a group with a generic auto-generated name, that you never asked to create, sitting next to your actual group chats. That's not a small cosmetic issue; it's confusing enough to undermine the whole feature. Fixing it properly meant either teaching the group list to hide certain groups, or building a second, parallel version of the split-bill logic that didn't need a group at all. Both were real, non-trivial pieces of work, and we said so plainly, and left it there. Two honest investigations, two honest walk-aways, the reasoning preserved for whoever picked it up next.

## What actually closed it

The smaller of those two options turned out to be smaller than it looked when it was just an idea on paper. A group already has almost everything a "hidden" group would need — real membership, a real message thread, a real place for a split bill to attach to. It only needed one new thing: a flag saying "don't show this one in anybody's group list." One new column. One line added to the query that lists your groups, filtering that flag out. Nothing about the split-bill logic itself needed to know or care that the group behind it was hidden — the entire feature reused, byte for byte, the same code that already handles a real named group's split bill, because from the split bill's own point of view, nothing changed.

The other real piece was making sure two people splitting a second bill later don't quietly spawn a second hidden group and split their history across two disconnected threads — a real lookup finds the existing hidden group between the same two people and reuses it, so their settlement history stays in one place even though neither of them ever sees it as a "group" at all.

## Proving it actually closed the door that mattered

The whole reason this got shelved twice was one specific, concrete failure: a hidden group showing up somewhere it shouldn't. So that's exactly what we went and checked, against the real running system, not a mental model of it. Restarted the actual backend to pick up the new database column. Logged in as two real accounts. Split a real bill between them. Then asked the real "what are my groups" endpoint, for both people, whether it saw the group that had just been created underneath that split bill.

It didn't. Not for either person. That's the whole feature, proven the way it actually needed proving — not "does the split-bill math work" (it already did, unchanged), but "does the thing that broke this twice before still break it." It doesn't. Split a second bill between the same two people, and it reuses the same hidden thread rather than starting a new one. Try to split a bill with yourself, and it fails cleanly with a real error instead of a raw crash.

## What's still open, on purpose

The backend is finished and shared across every client that will ever use it. Only one of three actual apps can reach it right now — a "Split a bill" icon inside a one-on-one conversation, wired up the same way the existing group-chat version already works. The other two platforms need the same small piece of interface work, calling the exact same finished backend. That's not a hidden gap; it's the honestly-scoped remainder of a feature that took three passes to get right, and the first two passes weren't wasted — they're the reason the third one knew exactly what not to build.
`,
};
