# Itunda Public Platform Surface Map

This map defines the public-facing Itunda platform as a set of coordinated products.

| Surface | Host | Primary audience | Contract |
|---|---|---|---|
| Itunda | itunda.im | everyone | brand/product entry |
| Consumer App | app.itunda.im | consumers | consumer app shell |
| Business | business.itunda.im | merchants | business product entry |
| Business App | business-app.itunda.im | merchants/staff | authenticated business app |
| Developers | developers.itunda.im | developers/partners | APIs, SDKs, sandbox, console |
| Tech Blog | tech-blog.itunda.im | engineers | engineering knowledge |
| API | api.itunda.im | machines/developers | versioned OpenAPI APIs |

## Platform layers

```text
                     itunda.im
                        |
          +-------------+-------------+
          |             |             |
        Consumer      Business     Developers
          |             |             |
       app.itunda   business-app   developers
          |             |             |
          +-------------+-------------+
                        |
                   API Platform
                        |
       +----------------+----------------+
       |                |                |
     Identity        Money           Platform
       |             Ledger        Mini-apps
       |             Payments       Sandbox
       |             Risk           SDKs
       |             Merchant       Releases
       |             Settlement     Analytics
       +----------------+----------------+
                        |
                 Infrastructure
              Kubernetes / DB / Kafka
              Observability / Security
```

## Non-negotiable separation

- Consumer UI never becomes the business UI.
- Business UI never becomes the developer console.
- Developer documentation never depends on private internal code paths.
- Public API contracts are versioned.
- Mini-apps depend on Saronite contracts, not internal service implementation.
- Internal operations tools use privileged APIs and are never exposed through consumer navigation.
- Each hostname has explicit routing and a product-specific error boundary.

## Source-backed benchmark

The benchmark is intentionally limited to public Toss evidence:

- Toss GitHub publishes Granite, Apps in Toss examples/harnesses, and reusable engineering libraries.
- Granite documents brownfield React Native, small bundles, ESBuild, infrastructure automation and independent mini-app deployment.
- Apps in Toss public examples demonstrate SDK domains, mock development and developer tooling.
- Toss Payments documents client SDK initiation, server-side confirmation, idempotency and webhooks.
- Toss technical architecture articles describe independently deployable services, Kubernetes, MySQL and distributed-system reliability patterns for relevant regulated entities.

These references establish patterns, not a claim that Itunda knows Toss's private source code.
