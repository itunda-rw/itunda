export const fakeSuccess = {
  slug: 'stop-catching-and-faking-success',
  title: 'The bug pattern that taught us to stop catching-and-faking success',
  date: '2026-07-05',
  author: 'Mobile Platform Team',
  tags: ['android', 'kotlin', 'incident-writeup'],
  excerpt:
    "One try/catch block quietly replaced every failed login with a fake logged-in state. It turned out the same shape of bug existed in five other places: transfers, loan applications, stock orders, and bill payments.",
  content: `
We found this one by accident, while checking whether login actually worked end to end. It didn't — and worse, there was no way to tell from using the app that it didn't.

## The first one: login

\`AuthViewModel.login()\` called the real login endpoint, and its \`catch\` block — meant to handle "the network is unreachable" — instead logged the user in anyway, as a hardcoded fake account, on *any* failure. Wrong password: logged in. Backend unreachable: logged in. Malformed request: logged in. The login screen had no code path that could show "that didn't work," because as far as the UI was concerned, it never didn't work.

That alone would have been bad enough, but it was compounding with two other bugs: the request/response models used field names (\`phone\`, \`pin\`, \`token\`) that don't exist in the real backend's contract (\`phoneNumber\`, \`password\`, \`accessToken\`), so every field silently deserialized to null — and the password field was capped at 6 numeric digits and labeled "PIN," so even a correctly-addressed request could never carry the real seeded account's actual password. Three independent bugs, each one fully masked by the other two. The login screen had, as far as we can tell, never once successfully authenticated against the real backend — and nothing about using it suggested that.

## Then we went looking for the same shape elsewhere

Once we knew what to look for — a \`catch\` block that produces a synthetic success value instead of surfacing the failure — we checked every other money-moving action in the app. It was in all of them:

- **Transfers**: sent a hardcoded, nonexistent wallet ID as the funding source, and a contact's local ID where the backend expected a phone number. Every real transfer 400'd. The catch block replaced that with "Sent 5,000 RWF to Jean."
- **Loan applications**: sent \`offerId\` where the backend reads \`loanId\`. Every application 400'd, replaced with "Application submitted."
- **Stock orders**: sent \`symbol\`/\`quantity\` where the backend reads \`stockId\`/\`shares\`. Every order 400'd, replaced with "Bought 5 shares."
- **Bill payments**: sent \`providerId\`/\`accountRef\` where the backend reads \`billId\`/\`accountNumber\`. Every payment 400'd, replaced with "Payment complete" — and the bill still got removed from the pending list, so the fake success even updated local state to match the fake story.

Every single one of these had the same structure: a field-name mismatch that guaranteed the real request would fail, made permanently invisible by a catch block that fabricated the success case instead of reporting the failure.

## Why this is worse than a normal bug

A crash is loud. A wrong field name usually is too, once you check a network log. This wasn't either — it was quiet in exactly the way that matters most in a financial app: the user experience of a failed transfer and a successful transfer were *identical*. There was no version of manually testing the app that would surface it, because manual testing checks "did I get a success message," and the success message was never conditional on anything real.

## What we changed

Every one of these now checks \`response.isSuccessful && body?.success == true\` explicitly, parses a real error message out of the backend's actual error body when available, and shows a real distinct error state on failure — network unreachable, validation failure, and success are three different, visibly different outcomes now, not one. We verified each one by making the exact real request the fixed client now sends and confirming it actually succeeds against the real backend, not by trusting the UI's word for it.

The broader change was procedural: we no longer consider "the success screen appeared" sufficient proof that an action worked. The verification step that matters is checking the actual backend state changed — the balance moved, the loan record exists, the order is in the portfolio — independent of what the client claimed happened.
`,
};
