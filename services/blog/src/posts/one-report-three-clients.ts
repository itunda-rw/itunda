import type { Post } from './index';

export const oneReportThreeClients: Post = {
  slug: 'one-report-three-clients',
  title: 'One report, three clients: API contracts are product infrastructure',
  date: '2026-07-30',
  author: 'Merchant Platform Team',
  tags: ['merchant', 'mobile', 'api-design', 'reliability'],
  excerpt:
    'A merchant report is only useful when its date range, totals, and channel breakdown mean the same thing on web, Android, and iOS. Here is the small contract discipline that made that true.',
  content: `
A collection report looks like a presentation problem: fetch a list, add some numbers, draw a table. It is actually a contract problem. The same merchant may use a browser at their desk, an Android register at the counter, and an iPhone away from the shop. If those clients quietly ask different questions of the same data, the report becomes a source of operational confusion.

That was the gap we closed in itunda's merchant products. The server already grouped settled collections by day and payment channel. The web console hard-coded its default period; native clients did the same; and nothing made the supported range an explicit part of the product contract.

## Make the query boundary explicit

The API now accepts an inclusive \`from\` and \`to\` calendar-date range and rejects two cases before it touches transaction data:

- a start date after the end date;
- a range wider than 31 days.

The limit is deliberately part of the API, not just a date picker setting. A client can be outdated, scripted, or buggy. Putting the invariant next to the query prevents an accidental unbounded report from becoming a slow database operation and makes every client receive the same useful \`400 INVALID_REPORT_RANGE\` response.

## Treat dates as calendar dates

Reports are about a merchant's day, not an instant in UTC. A subtle browser mistake is formatting a local \`Date\` with \`toISOString()\`; before UTC midnight catches up, that can submit yesterday's date. The web client now formats the local year, month, and day explicitly for its \`<input type="date">\` controls. Android uses \`LocalDate\`, and iOS uses a POSIX \`yyyy-MM-dd\` formatter.

This is not a formatting preference. It prevents the most damaging kind of reporting bug: a plausible-looking total for the wrong day.

## Keep the display model aligned

All three clients now use the same two bounded views—last 7 days and last 30 days—and show the same channel mix beside gross, fees, and net settlement. They do not calculate a discount rate or invent a channel label on the client. The server remains the source of truth for money and derives values such as discounts from their actual input prices.

There was one useful mobile detail here: the Android HTTP model reads channel counts as numeric values that may arrive as floating-point numbers, while the iOS decoder models them as integer counts. Both are normalized at the presentation boundary and rendered as collection counts. The important part is not the language-specific type; it is making the semantic contract—"this is a count"—visible and tested.

## Verify where merchants actually work

We verified the backend's range behavior with the merchant test suite, built the merchant web bundle, compiled the Android merchant app, and built the iOS merchant scheme for the simulator. A shared API does not imply a shared implementation, so each product surface needs its own compile-time check.

The wider lesson is simple: cross-client consistency is not polish added after an API is finished. The API range, date semantics, error codes, and summary definitions are the product. The UI is where that contract becomes legible to a merchant deciding what to do next.
`,
};
