export const hardcodedUserId = {
  slug: 'authenticated-but-not-authorized',
  title: "Authentication was real. Authorization wasn't.",
  date: '2026-06-24',
  author: 'Platform Security Team',
  tags: ['security', 'backend', 'incident-writeup'],
  excerpt:
    'Every route had working JWT verification. Every controller still hardcoded whose wallet to read. A second real account could authenticate with its own valid token and reach the first account\'s money.',
  content: `
This is the kind of bug that's uncomfortable to write up honestly, so we're writing it up honestly.

itunda's \`requireAuth\` middleware was wired onto nearly every route and worked correctly: it verified the JWT signature, checked expiry, and set \`req.userId\` to the authenticated caller's real ID. If you sent a garbage or missing token, you got a real \`401\`. Authentication was never the problem.

The problem was that almost nothing downstream *read* \`req.userId\`. Every controller — wallet, loans, stocks, savings, contacts, notifications, rewards, insurance, bills, support — had a hardcoded stand-in for "the current user": \`user_1\`, \`wallet_1\`, \`wallet_3\`, chosen because that's who the seed data represented during early development, and it never got wired up to the real identity once real auth landed.

The practical consequence: register a second account through the (already-working) \`POST /auth/register\` endpoint, authenticate with its own valid token, and every one of those endpoints would still hand you \`user_1\`'s data. \`GET /wallet\` returned \`user_1\`'s wallets, not yours. \`POST /wallet/transfer/quote\` with an arbitrary \`fromWalletId\` would price a transfer *out of \`user_1\`'s wallet* using your own valid session. Nothing about the request looked wrong to the server, because the server was never checking whose it actually was.

## Why this didn't show up in normal testing

It's an easy bug to miss because single-account testing never exercises it. If you only ever have one seeded test user, every hardcoded \`user_1\` reference is indistinguishable from a correctly-resolved \`req.userId\` — they return identical data. It only becomes visible the moment you have two real accounts and check whether account B can see or touch account A's things. Nobody had done that check before.

## The fix, and how we verified it

Every handler now resolves the caller's own resources by their real ID: \`findMainWallet(req.userId)\`, \`contacts.filter(c => c.userId === req.userId)\`, and so on — no controller trusts a client-supplied wallet or loan ID without checking it belongs to the caller first, returning \`403\` on a mismatch. Transfer quotes now carry the creating user's ID and get re-checked at confirm time too, closing a secondary path where you could hijack someone else's already-created quote even if you couldn't create one against their wallet directly.

We verified this the only way that actually proves it: registered a second real account, authenticated as it, and checked that every endpoint that previously leaked \`user_1\`'s data now correctly returns *this* account's own (empty, for a fresh signup) data instead — and that any attempt to touch \`user_1\`'s wallet, loan, or ticket comes back \`403\`, not \`200\`.

## What we changed about how we test

Single-account manual testing will structurally never catch this class of bug, because the bug is specifically about the boundary *between* accounts. We now treat "register a second account and try to touch the first one's resources" as a standing check on every new money-moving endpoint, not an afterthought.
`,
};
