# Itunda: Rwanda's Toss-Equivalent Financial Operating System
## Complete Feature Specification & Implementation Plan

**Status**: Specification v1.0
**Date**: July 2, 2026
**Target**: 100% Toss feature parity for Rwanda market

> **Read this before the checklist below.** This is the original planning/target
> specification, written before most of the real backend existed. It describes the intended
> shape of the product, not what's actually built. **It is not a status report** — for real
> implementation status per feature, see `docs/TOSS_PARITY_MATRIX.md`; for what's actually
> verified real vs. demo vs. stub in the codebase, see `docs/ARCHITECTURE.md`; for the actual
> security posture, see `SECURITY.md`. In particular, the "Security & Compliance Checklist"
> below was originally written with every item checked `[x]` as if shipped — none of MFA,
> sanctions-list screening, GDPR tooling, PCI-DSS compliance, or BNR regulatory reporting
> exist in code as of 2026-07-12 (verified by grep across `services/backend`). Checkboxes
> below have been corrected to reflect that; treat this section as a requirements list, not a
> completion record. Also: Itunda never depends on Toss's own live systems (Toss operates
> under South Korean licenses) — every Rwanda-adapted service below routes through Rwanda's
> own rails and regulators (MTN Mobile Money, Airtel Money, RNP, BNR, RSE, NIDA, Irembo),
> never through Toss's infrastructure. Adopting Toss's *open-source* tooling (`granite`,
> `es-toolkit`, etc.) is a separate, fine thing — see `docs/TOSS_ARCHITECTURE_FACTS.md` §4.

---

## Executive Summary

Itunda is designed to replicate Toss's financial operating system for Rwanda, providing all 40+ services in a unified app. Rather than just a mobile payment app, Itunda will be Rwanda's complete financial control panel—managing banking, payments, lending, investments, insurance, and merchant services.

### Key Differentiators for Rwanda
- **Toss 100% Technical Parity**: Spring Boot/Kotlin Microservices (CQRS), Microfeatures iOS architecture (Tuist), and Apps-in-Toss Android super-app framework.
- **Open Banking Integration**: RNP (Rwanda National Digital Payment System) 2.0, MTN Money, Airtel Money
- **Merchant-First Approach**: Face Pay biometric integration, QR payments, POS systems
- **AI-Powered Underwriting**: Credit scoring for unbanked/underbanked populations
- **Multi-Currency Support**: RWF, USD, and regional currencies
- **Offline Capability**: Critical for rural Rwanda connectivity gaps

---

## Core Architecture Pillars

### 1. **Pillar 1: Money Movement** (Entry Point)
- P2P Transfers (instant, free between all banks via RNP)
- Mobile Money Integration (MTN, Airtel)
- Bill Payments (utilities, subscriptions)
- Merchant Payments (online & offline)
- International Transfers
- Currency Exchange

### 2. **Pillar 2: Account Aggregation & Financial Control**
- Multi-account Dashboard (view all banks, wallets, investments)
- Account Linking (RNP open banking, manual card linking)
- Transaction History & Search
- Spending Analytics & Categorization
- Budget Management
- Upcoming Bills & Subscriptions

### 3. **Pillar 3: Digital Banking** (Itunda Bank equivalent)
- Fully Digital Bank Account (RBDB licensed in Rwanda)
- Savings Products
- Fixed Deposits
- Advanced Interest Products ("Get Interest Now")
- Overdraft Protection
- Debit Card Management

### 4. **Pillar 4: Lending Marketplace**
- Personal Loans
- Business Loans
- Salary-Backed Loans
- AI Credit Scoring
- Loan Marketplace (multiple providers)
- Loan Comparison & Application
- Repayment Management

### 5. **Pillar 5: Investments**
- Fractional Stock Trading (local & international)
- Stock Savings Plans (automatic investing)
- Mutual Funds & ETFs
- Bond Investments
- Portfolio Management & Tracking
- Real-time Market Data
- AI-powered recommendations

### 6. **Pillar 6: Insurance & Protection**
- Insurance Product Marketplace
- Motor Insurance
- Health Insurance
- Home Insurance
- Life Insurance
- Travel Insurance
- Policy Management & Claims

### 7. **Pillar 7: Merchant Ecosystem**
- Merchant Dashboard
- Sales Analytics
- Toss Place POS System
- Face Pay Integration
- QR Code Payments
- Batch Settlement
- Payout Management

### 8. **Pillar 8: Loyalty & Engagement**
- Rewards Program
- Cashback
- Point System
- Daily Challenges (Toss Walk equivalent)
- Referral Bonuses
- Exclusive Offers
- VIP Tiers

---

## Complete Feature List (40+ Services)

### Money Management (8 services)
1. **P2P Transfers** - Free, instant bank-to-bank transfers via phone number
2. **Mobile Money Transfers** - MTN Money & Airtel Money integration
3. **Bill Payments** - Utility bills, subscriptions, taxes
4. **Merchant Payments** - Online and offline payments
5. **International Transfers** - Money to other countries
6. **Currency Exchange** - Real-time FX with competitive rates
7. **Contact Management** - Save favorite recipients
8. **Transaction Verification** - Fraud detection and scam alerts

### Banking (6 services)
9. **Digital Savings Account** - Interest-bearing account
10. **Fixed Deposits** - Time deposits with guaranteed returns
11. **Advance Interest Deposits** - Receive interest upfront
12. **Get Interest Now** - Earn interest on demand
13. **Account Aggregation** - Link all financial accounts
14. **Debit Card Management** - Virtual & physical card management

### Lending (5 services)
15. **Personal Loans** - Unsecured personal loans
16. **Business Loans** - For entrepreneurs and SMEs
17. **Salary-Backed Loans** - Quick loans against salary
18. **Loan Marketplace** - Compare multiple providers
19. **Credit Score Tracking** - Monitor credit health

### Investments (7 services)
20. **Stock Trading** - Buy/sell individual stocks
21. **Fractional Trading** - Invest in stock slices
22. **Stock Savings** - Automatic investment plans
23. **Mutual Funds** - Curated fund portfolios
24. **ETFs** - Exchange-traded funds
25. **Bonds** - Fixed-income securities
26. **Portfolio Analytics** - Performance tracking

### Insurance (4 services)
27. **Insurance Marketplace** - Browse all insurance products
28. **Motor Insurance** - Car and vehicle coverage
29. **Health Insurance** - Medical coverage plans
30. **General Insurance** - Home, travel, life insurance

### Payments & Commerce (5 services)
31. **Merchant Dashboard** - Sales and business analytics
32. **Face Pay** - Biometric facial recognition payments
33. **QR Code Payments** - Scan-to-pay functionality
34. **POS System** - In-store payment terminal
35. **Batch Settlements** - Group transaction payouts

### Analytics & Insights (3 services)
36. **Spending Analytics** - Category-based spend tracking
37. **AI Recommendations** - Personalized financial advice
38. **Financial Health Score** - Overall financial wellbeing metric

### Rewards & Engagement (2+ services)
39. **Loyalty Points** - Earn points on every transaction
40. **Cashback Program** - Get money back on purchases
41. **Daily Challenges** - Gamified earning opportunities
42. **Referral Rewards** - Bonuses for inviting friends

---

## Toss vs Itunda: Service Mapping

| Toss Korea Service | Itunda Rwanda Equivalent | Rwanda-Specific Features |
|---|---|---|
| P2P Money Transfer | RNP Direct Transfer | MTN/Airtel fallback |
| Toss Pay | Itunda Pay | QR + Face Pay |
| Toss Bank | Itunda Bank | RBDB partnership |
| Toss Securities | Itunda Invest | RSE (Rwanda Stock Exchange) equities/bonds first; global assets only where permitted |
| Toss Insurance | Itunda Protect | Local insurers (AAR, Intra Africa) |
| Toss Loan | Itunda Loans | AI scoring for unbanked users |
| Toss Card | Itunda Card | Debit card + mobile money |
| Open Banking | RNP Integration | 11+ bank connections |
| MyData | Financial Passport | Consent-based data sharing |
| Face Pay | Biometric Payments | Offline-capable recognition |
| Toss Rewards | Itunda Rewards | Point-based system |
| Stock Savings | Stock Savings | Rupiah/Dollar/ETF options |
| Toss Green | Eco-Initiatives | Carbon tracking (future) |
| Toss eCommerce | Itunda Marketplace | Local merchants |
| Toss Ride | Taxi Integration | Uber RW integration (phase 2) |
| Toss Mobile | Mobile Integration | Airtel/MTN data plans (phase 2) |

---

## Rwanda Financial Context Integration

### Open Banking & Interoperability
**RNP (Rwanda National Digital Payment System) 2.0**
- Real-time national payment system
- Connects all Rwandan banks
- Supports P2P, B2B, B2C payments
- Instant settlement
- APIs available for integration

**Participating Banks** (11+)
- BPR (Banque Populaire du Rwanda)
- Bank of Kigali (BoK)
- Ecobank Rwanda
- Kenya Commercial Bank Rwanda
- Equity Bank Rwanda
- Cogebank
- GT Bank Rwanda
- Access Bank Rwanda
- Finbank Rwanda
- Centenary Bank Rwanda
- I&M Bank Rwanda

### Mobile Money Operators
**MTN Money**
- 5.2M+ active users
- Highest penetration in Rwanda
- Agent network coverage
- Easy USSD access

**Airtel Money**
- 2.1M+ active users
- Strong in specific regions
- Integration with airtime top-up
- Competitive fee structure

### Financial Inclusion
- **96% adult financial inclusion** (as of 2024)
- **16.3M mobile money accounts**
- **60% unbanked population** has mobile money access
- **Cash dependency** still high in rural areas
- **Offline transactions** critical need

### Regulatory Framework
- **BNR (National Bank of Rwanda)** oversight
- **Digital Financial Services Guidelines** 2024
- **KYC/AML Requirements**
- **Consumer Protection Rules**
- **Data Privacy Laws**

---

## Phase-Based Implementation Plan

### Phase 1: MVP (Months 1-3)
**Core Money Movement & Banking**
- P2P transfers (RNP integration)
- Basic wallet management
- Itunda Bank account (digital savings)
- Mobile app (basic UI)
- KYC verification
- **Target Users**: 100K

### Phase 2: Ecosystem Expansion (Months 4-6)
**Add Lending & Investments**
- Loan marketplace integration
- Stock trading (basic)
- Bill payments
- Merchant integration
- Advanced analytics
- **Target Users**: 500K

### Phase 3: Premium Features (Months 7-9)
**Insurance, Advanced Investing, Rewards**
- Insurance marketplace
- Fractional trading
- AI recommendations
- Loyalty program
- Face Pay (iOS/Android)
- **Target Users**: 2M

### Phase 4: Enterprise & Scale (Months 10-12+)
**Merchant Platform & B2B**
- POS system deployment
- Merchant dashboard
- Business lending
- API marketplace
- International expansion
- **Target Users**: 5M+

---

## Technical Requirements by Service

### Database Schema Requirements

```
Core Tables:
- users (authentication, KYC data)
- accounts (bank accounts, wallets)
- transactions (all movement)
- ledger (double-entry accounting)
- loans (loan applications and repayments)
- investments (stock holdings, portfolios)
- insurance_policies (policy records)
- merchants (merchant accounts)
- cards (virtual/physical card management)
- notifications (audit trail)
- kyc_documents (identity verification)
- compliance_flags (AML/sanctions)
```

### API Endpoints Required

**Authentication (20 endpoints)**
- Register, Login, Logout, Refresh Token, MFA, Biometric Auth, KYC Upload, Document Verification

**Transfers (25 endpoints)**
- Send Money, Request Money, Split Bill, Schedule Transfer, Currency Exchange, International Transfer, Transfer History, Favorites, Transaction Details, Cancel Transfer

**Banking (18 endpoints)**
- Create Account, Link Account, Savings Products, Fixed Deposits, Account Statement, Debit Card Management, Interest Calculation, Account Aggregation

**Lending (22 endpoints)**
- Loan Application, AI Credit Score, Loan Comparison, Loan Approval, Repayment Schedule, Payment, Loan History, Refinance, Guarantor Management

**Investments (28 endpoints)**
- Stock Search, Buy Stock, Sell Stock, Portfolio, Watchlist, Market Data, Stock Savings, Dividend Management, Tax Reporting, Performance Analytics

**Insurance (15 endpoints)**
- Insurance Products, Quote, Purchase, Policy Management, Claims, Beneficiary Management, Coverage Details

**Merchant (20 endpoints)**
- Merchant Registration, Dashboard, Sales Analytics, POS Management, Settlement, Payout, Transaction Details, Batch Operations, Refund Management

**Notifications (8 endpoints)**
- Subscribe, Push Notifications, Email Preferences, SMS Preferences, Notification History, Mark Read, Notification Settings

**Analytics (12 endpoints)**
- Spending Analytics, Category Breakdown, Trends, Comparison, Budget Tracking, Financial Score, Recommendations, Insights, Export Reports, Recurring Transactions

**Admin (30+ endpoints)**
- User Management, Compliance Monitoring, Fraud Detection, System Health, Audit Logs, Regulatory Reports, KYC Verification, Transaction Monitoring

---

## API Response Patterns

### Standard Success Response
```json
{
  "success": true,
  "data": { /* service-specific data */ },
  "timestamp": "2026-07-02T10:30:00Z",
  "requestId": "req_123456"
}
```

### Standard Error Response
```json
{
  "success": false,
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "Transfer amount must be between 100 and 50000000 RWF",
    "statusCode": 400
  },
  "timestamp": "2026-07-02T10:30:00Z",
  "requestId": "req_123456"
}
```

---

## Security & Compliance Requirements (target — see SECURITY.md for real status)

These were originally checked off `[x]` as if already shipped. They are requirements for a
production money-moving system, not a record of what exists. As of 2026-07-12, verified real
in `services/backend`: JWT auth with signed/expiring tokens, bcrypt password hashing, per-user
ownership checks on every money-moving endpoint, rate limiting on auth, real refresh-token
rotation with blacklisting (see `SECURITY.md` and `docs/ARCHITECTURE.md` §1 for the exact,
dated fixes). Everything else below is genuinely not built yet — most of it (sanctions
screening, AML case management, BNR reporting) is regulatory/vendor-integration work, not a
pure engineering task; see `docs/TOSS_PARITY_MATRIX.md`'s "Non-Negotiable Gates Before Real
Money" for the honest breakdown of what's code-gated vs. license/vendor-gated.

### Encryption
- [ ] TLS 1.3 for data in transit (infra-level, not yet confirmed configured on any deployed endpoint)
- [ ] AES-256 for data at rest
- [ ] End-to-end encryption for sensitive fields
- [ ] Key rotation policy
- [ ] Hardware security modules (HSM) for key storage

### Authentication
- [x] JWT with signed, expiring tokens (real, `JwtService.kt`)
- [x] Refresh token rotation with blacklisting on reuse (real, `AuthServiceTest.kt`)
- [ ] Biometric authentication tied to a real server-side session (Android/iOS have local-only
      biometric gates — see `docs/ARCHITECTURE.md` §3 — not yet a server-verified factor)
- [ ] Multi-factor authentication (SMS, Email, Authenticator)
- [x] Rate limiting on login attempts (real, `AuthServiceTest.kt` covers ordering)
- [ ] Session timeout on inactivity

### Compliance
- [x] KYC submission workflow real (`POST /identity/submit` moves a credential to `REVIEW` and
      opens a real compliance-queue item — `docs/TOSS_PARITY_MATRIX.md`)
- [ ] An actual KYC decision-maker (real NIDA/vendor API or reviewer UI to resolve `REVIEW` →
      `VERIFIED`/`EXPIRED`) — blocked on regulatory/vendor access, not a code gap
- [ ] Sanctions list checking (OFAC, UN, EU, local Rwanda lists)
- [ ] GDPR-equivalent consent management and data retention tooling
- [ ] Formal data residency policy
- [ ] PCI-DSS certification (an audited status, not something a repo can self-declare — see
      `docs/TOSS_ARCHITECTURE_FACTS.md` §5)
- [x] Audit-relevant transaction records (real double-entry ledger, immutable append pattern)
- [ ] Regulatory reporting to BNR

### Fraud Prevention
- [ ] Transaction risk scoring
- [ ] Behavioral analytics
- [ ] Device fingerprinting
- [ ] Velocity checks (daily/monthly limits)
- [ ] Machine learning anomaly detection
- [ ] Manual review queue for high-risk transactions
- [ ] Real-time fraud alerts

---

## Performance Requirements

| Metric | Target | Method |
|--------|--------|--------|
| API Response Time (p95) | < 200ms | Optimized queries, caching |
| Transfer Processing | < 2 sec | RNP integration, async processing |
| Login Time | < 1 sec | JWT, local caching |
| App Load Time | < 2 sec | Code splitting, lazy loading |
| Database Query | < 100ms | Indexing, read replicas |
| Cache Hit Ratio | > 80% | Redis, intelligent invalidation |
| System Uptime | 99.99% | Multi-region deployment |
| Concurrent Users | 100K+ | Horizontal scaling |
| Daily Transactions | 10M+ | Message queue processing |

---

## Deployment Infrastructure

> **This section is an original aspirational target, not current infrastructure.** No AWS
> account, Terraform, DocumentDB/MongoDB, or Istio exists anywhere in this repo. For what
> actually exists and is deployable today (real Dockerfiles, real `infra/k8s/` manifests,
> real MySQL via Flyway), see `docs/DEPLOYMENT.md`.

### Cloud Provider: AWS
- **Region**: af-south-1 (Africa - Cape Town) + Regional DR
- **Compute**: EKS (Kubernetes) for Spring Boot Microservices + Istio Service Mesh
- **Database**: RDS MySQL (Enterprise equivalent), DocumentDB for MongoDB
- **Cache**: ElastiCache Redis
- **Message Queue**: Kafka (MSK)
- **Storage**: S3 for documents, CloudFront for CDN
- **Monitoring**: CloudWatch, Prometheus, Grafana
- **Logging**: CloudWatch Logs, Elasticsearch
- **CI/CD**: CodePipeline, CodeBuild
- **Security**: Secrets Manager, KMS, VPC

### On-Premise: Rwanda Hosting Center
- **Primary**: Data center in Kigali
- **Backup**: Regional replication to East Africa
- **Network**: Fiber connection to RNP
- **Connectivity**: Direct integration with BNR, RSE (Rwanda Stock Exchange — not RTB, the
  Rwanda TVET Board, which this line previously and incorrectly named)

---

## Success Metrics

### User Metrics
- **Month 1-3**: 100K users
- **Month 6**: 500K users
- **Month 9**: 2M users
- **Month 12**: 5M+ users
- **Daily Active Users**: 1M by Month 12
- **Monthly Active Users**: 3M by Month 12

### Financial Metrics
- **Transaction Volume**: $1M daily by Month 3, $100M daily by Month 12
- **GMV**: $500M annual by Year 2
- **User Retention**: 50%+ monthly retention
- **Unit Economics**: Profitable by Month 18

### Product Metrics
- **Feature Adoption**: 40+ services > 70% adoption
- **App Rating**: > 4.8 stars on both app stores
- **Customer Satisfaction**: NPS > 60
- **System Availability**: 99.99%+ uptime

---

## Next Steps

1. ✅ **Complete**: Feature specification document (this)
2. **Infrastructure Setup** (Task #2)
   - Configure Docker Compose for local development
   - Set up AWS infrastructure
   - Deploy databases and message queues
   - Implement API Gateway

3. **Core Implementation** (Tasks #3-#8)
   - Build microservices sequentially
   - Implement RNP integration
   - Develop mobile apps

4. **Quality & Deployment** (Tasks #14-#15)
   - Comprehensive testing
   - Security audit
   - Production deployment

---

## Document Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-07-02 | Initial specification, all 40+ services mapped |

---

## References

- [Toss Financial Architecture](https://meritech.substack.com/p/toss-fintechs-final-form-the-everything)
- [Rwanda Fintech Strategy 2024-2029](https://andersen.com)
- [RNP System Documentation](https://generisonline.com)
- [Toss 10-Year Data Report](https://blog.toss.im)
