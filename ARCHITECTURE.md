# Itunda Fintech Platform - Complete System Architecture

For the canonical Toss/Rwanda product, architecture, and UI/UX alignment target, see [docs/TOSS_RWANDA_ALIGNMENT.md](docs/TOSS_RWANDA_ALIGNMENT.md).

This document describes the intended architecture. Some services listed below are target bounded contexts, while the current repository implements a React web app, Express demo API, SwiftUI iOS shell, Android scaffold, Spring backend scaffold, and a loan-service prototype. Do not treat every service name here as a production-running service.

## Overview
Itunda is a comprehensive fintech platform for Rwanda, built to match Toss's capabilities. It's a full-stack system with frontend, backend microservices, mobile apps, and admin console.

## System Components

### 1. Frontend (Web Application)
- **Framework**: React 19 + TypeScript + Vite
- **Location**: `/src`
- **Pages**:
  - Home - Dashboard with wallet balance, recent transactions
  - Pay - Send money to contacts
  - Benefits - Promotions and rewards
  - Stock - Stock trading
  - Crypto - Cryptocurrency trading
  - Insurance - Insurance products
  - Savings - Savings accounts and fixed deposits
  - Loans - Loan applications and management
  - Bills - Utility bill payments
  - Entire - App settings and features
  - Profile - User settings and KYC
  - Analytics - Spending insights and reports

### 2. Backend Microservices Architecture

#### Core Services
1. **API Gateway** (Port 3000)
   - Request routing and load balancing
   - Authentication & authorization
   - Rate limiting
   - API versioning

2. **User Service** (Port 3001)
   - User registration and management
   - KYC/AML verification
   - Profile updates
   - User settings

3. **Account Service** (Port 3002)
   - Account creation and management
   - Account types (checking, savings, mobile_money)
   - Multi-currency support

4. **Transaction Service** (Port 3003)
   - Money transfers (P2P)
   - Transaction history
   - Real-time updates via WebSocket/Kafka

5. **Ledger Service** (Port 3004)
   - Double-entry ledger maintenance
   - Transaction recording
   - Balance calculations
   - Audit trails

6. **Loan Service** (Port 3005)
   - Loan applications
   - AI-powered credit scoring
   - Loan approvals
   - Payment tracking

7. **Investment Service** (Port 3006)
   - Stock trading
   - Cryptocurrency trading
   - Mutual funds and ETFs
   - Portfolio management

8. **Insurance Service** (Port 3007)
   - Insurance product management
   - Policy issuance
   - Claims processing

9. **Notification Service** (Port 3008)
   - Email notifications
   - SMS notifications
   - Push notifications
   - WebSocket real-time updates

10. **Analytics Service** (Port 3009)
    - Spending analytics
    - AI recommendations
    - Trend analysis
    - Reports generation

11. **Savings Service** (Port 3010)
    - Savings accounts
    - Fixed deposits
    - Goal tracking

12. **Admin Service** (Port 3011)
    - User management
    - Transaction monitoring
    - Compliance checks
    - System health monitoring

### 3. Mobile Applications

#### iOS App (Swift/SwiftUI)
- Location: `/ios`
- Architecture: Microfeatures Architecture managed by Tuist (modular independent features)
- Features: All web features + biometric auth, offline mode, push notifications

#### Android App (Kotlin/Jetpack Compose)
- Location: `/android`
- Architecture: Multi-module "Apps-in-Toss" Super-App framework
- Features: All web features + biometric auth, offline mode, push notifications

### 4. Infrastructure

#### Databases
- **MySQL 8 (Enterprise equivalent)**: Primary relational database (users, accounts, transactions, loans, ledger)
- **MongoDB**: Document storage (user profiles, settings, preferences)
- **Redis**: Caching and session management

#### Message Queue
- **Kafka**: Event streaming for service-to-service communication

#### Logging & Monitoring
- **Elasticsearch**: Log aggregation
- **Kibana**: Log visualization
- **Prometheus**: Metrics collection
- **Grafana**: Metrics visualization

#### Cloud Storage
- **AWS S3**: Document storage, images, backups

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     CLIENT LAYER                                │
├─────────────────────────────────────────────────────────────────┤
│  Web App (React)  │  iOS App (Swift)  │  Android App (Kotlin)  │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Spring Cloud Gateway)           │
│            (Authentication, Routing, Rate Limiting)            │
└─────────────────────────────────────────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ User Service │  │Account Svc   │  │Transaction   │
    │              │  │              │  │Service       │
    └──────────────┘  └──────────────┘  └──────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │Loan Service  │  │Investment    │  │Notification │
    │              │  │Service       │  │Service       │
    └──────────────┘  └──────────────┘  └──────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │Insurance Svc │  │Analytics Svc │  │Admin Service │
    │              │  │              │  │              │
    └──────────────┘  └──────────────┘  └──────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │   MySQL      │  │  MongoDB     │  │   Redis      │
    │  (Ledger)    │  │(User Data)   │  │ (Cache)      │
    └──────────────┘  └──────────────┘  └──────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │   Kafka      │  │Elasticsearch │  │   AWS S3     │
    │(Event Stream)│  │  (Logs)      │  │ (Storage)    │
    └──────────────┘  └──────────────┘  └──────────────┘
```

## Technology Stack

### Backend
- **Framework**: Spring Boot (Cloud Native)
- **Language**: Kotlin / Java
- **Architecture**: Microservices Architecture (MSA) with CQRS
- **API**: REST + gRPC
- **Database**: MySQL Enterprise Edition, MongoDB
- **Search & Indexing**: Elasticsearch
- **Cache**: Redis
- **Message Queue**: Apache Kafka
- **Authentication**: JWT + OAuth2
- **Testing**: JUnit, MockK

### Frontend
- **Framework**: React 19
- **Language**: TypeScript
- **Styling**: CSS Modules/Tailwind CSS
- **State Management**: Zustand
- **Animation**: Framer Motion
- **Icons**: Lucide React
- **HTTP Client**: React Query (TanStack Query)

### Mobile
- **iOS Architecture**: Microfeatures Architecture managed by Tuist
- **iOS**: Swift + SwiftUI
- **Android Architecture**: Multi-module "Apps-in-Toss" Super-App framework
- **Android**: Kotlin + Jetpack Compose
- **Design System**: Itunda Design System (aligned with Toss Design System)
- **State Management**: Redux/MVI
- **Local Storage**: SQLite + Encrypted Preferences

### Infrastructure
- **Containerization**: Docker
- **Orchestration**: Kubernetes + Istio (Service Mesh)
- **Monitoring**: Prometheus + Grafana
- **Logging**: ELK Stack
- **CI/CD**: GitHub Actions

## Security

### Authentication
- JWT tokens with refresh mechanism
- OAuth2 integration (Google, Apple)
- Biometric authentication (Face ID, Fingerprint)
- Multi-factor authentication (MFA)

### Data Protection
- End-to-end encryption for sensitive data
- AES-256 encryption at rest
- SSL/TLS for data in transit
- PCI-DSS compliance

### Compliance
- AML/KYC verification
- Sanctions list checking
- GDPR compliant
- Data privacy regulations

## Deployment

### Development
```bash
docker-compose up
npm run dev
```

### Production
- Kubernetes deployment
- Auto-scaling with load balancing
- Database replication
- Disaster recovery setup

## API Documentation

Full API documentation available at `http://localhost:3000/api/docs` (Swagger UI)

## Testing

### Unit Tests
```bash
npm run test
```

### E2E Tests
```bash
npm run test:e2e
```

### Load Testing
```bash
npm run test:load
```

## Performance Metrics

- API Response Time: < 200ms (p95)
- Transaction Processing: < 2 seconds
- Database Query: < 100ms
- Cache Hit Ratio: > 80%
- System Uptime: 99.99%

## Support & Contact

- **Documentation**: `/docs`
- **API Reference**: `http://localhost:3000/api/docs`
- **Issue Tracker**: GitHub Issues
- **Email**: support@itunda.rw
