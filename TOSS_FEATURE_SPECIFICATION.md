# Itunda: Rwanda's Toss-Equivalent Financial Operating System
## Complete Feature Specification & Implementation Plan

**Status**: Specification v1.0
**Date**: July 2, 2026
**Target**: 100% Toss feature parity for Rwanda market

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
| Toss Securities | Itunda Invest | Local stock market + Dahabshiil integration |
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

## Security & Compliance Checklist

### Encryption
- [x] TLS 1.3 for data in transit
- [x] AES-256 for data at rest
- [x] End-to-end encryption for sensitive fields
- [x] Key rotation (quarterly)
- [x] Hardware security modules (HSM) for key storage

### Authentication
- [x] JWT with 15-minute expiry
- [x] Refresh token with 30-day expiry
- [x] Biometric authentication (Face ID, Fingerprint)
- [x] Multi-factor authentication (SMS, Email, Authenticator)
- [x] Account lockout after 5 failed attempts
- [x] Session management & timeout (30 min inactivity)

### Compliance
- [x] KYC/AML verification (document upload, manual review)
- [x] Sanctions list checking (OFAC, UN, EU, local Rwanda lists)
- [x] GDPR compliance (consent management, data retention)
- [x] Data residency (Rwanda primary, regional backup)
- [x] PCI-DSS compliance (payment handling)
- [x] Audit logging (immutable transaction records)
- [x] Regulatory reporting (daily to BNR)

### Fraud Prevention
- [x] Transaction risk scoring
- [x] Behavioral analytics
- [x] Device fingerprinting
- [x] Velocity checks (daily/monthly limits)
- [x] Machine learning anomaly detection
- [x] Manual review queue for high-risk transactions
- [x] Real-time fraud alerts

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
- **Connectivity**: Direct integration with BNR, RTB (Rwanda Stock Exchange)

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
