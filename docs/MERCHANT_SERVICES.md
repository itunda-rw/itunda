# Merchant & B2B Services

> **Note:** This is a pre-existing planning document — an aspirational spec
> written without checking a real payment gateway's actual API (its
> "Online Payments"/"API Reference" sections invented an
> `Authorization: Bearer MERCHANT_API_KEY` scheme and webhook event names
> like `payment.received` that don't match how any real provider works).
> Most of this file is still not implemented — POS/card processing, B2B
> payroll/invoicing, webhooks, inventory, and analytics all remain exactly
> as aspirational as when this note was first written. For the real,
> working payment gateway — mirroring Toss Payments' actual REST API,
> researched from their public docs rather than imagined — see
> **[PAYMENTS.md](PAYMENTS.md)**.
>
> **Update (2026-07-11):** §§1–2's core idea — merchant registration and
> QR-based payment collection — is now real, at `rw.itunda.merchant` in
> `services/backend` (not the endpoint paths/fee numbers below, which are
> this file's own invented ones): `POST /api/v1/merchant/register`,
> `POST /api/v1/merchant/qr/generate`, `POST /api/v1/merchant/collect/{intentId}`.
> Ledger-backed, idempotent, and ownership-checked, same discipline as
> every other money-moving endpoint in that backend. See
> `docs/TOSS_PARITY_MATRIX.md`'s Merchant row for the current real/not-real
> split.

## Overview
Itunda Merchant Services enable businesses and merchants to accept payments, manage inventory, and process settlements.

## 1. Merchant Onboarding

### Registration Flow
```
1. Sign up with business info
2. KYC verification (document upload)
3. Bank account verification
4. Terms & conditions acceptance
5. API credentials generation
6. Merchant dashboard access
```

### Required Documents
- Business registration certificate
- Tax ID
- Bank account details
- Owner ID/passport
- Address proof

### Verification Timeline
- Documents uploaded → 24 hours for review
- Bank account verified → 1-2 business days
- Status: Pending → Verified → Active

## 2. Payment Collection

### QR Code Payments
```bash
# Generate QR code
POST /api/v1/merchants/qr/generate
{
  "amount": 50000,
  "currency": "RWF",
  "description": "Store A - Payment"
}

# Response
{
  "qrCode": "data:image/png;base64,...",
  "qrCodeUrl": "itunda.rw/pay/abc123",
  "expiresAt": "2026-07-03T12:56:00Z"
}
```

### POS Integration
```bash
# Link POS terminal
POST /api/v1/merchants/pos/link
{
  "terminalId": "POS-12345",
  "location": "Kigali Store"
}

# Process payment
POST /api/v1/merchants/pos/process
{
  "amount": 50000,
  "cardData": "...",
  "merchantId": "MERCHANT-123"
}
```

### Online Payments
```bash
# Payment link
POST /api/v1/merchants/links/create
{
  "amount": 50000,
  "description": "Invoice #12345",
  "redirectUrl": "https://store.com/success"
}

# Response
{
  "paymentLink": "https://pay.itunda.rw/link/xyz789",
  "expiresAt": "2026-07-03T12:56:00Z"
}
```

## 3. Merchant Dashboard

### Analytics
- Daily transactions
- Monthly revenue
- Top products/services
- Customer insights
- Refund tracking

### Inventory Management
- Product catalog
- Stock tracking
- Pricing management
- Bulk import/export

### Settlement Management
- Payout schedule
- Transaction fees
- Settlement history
- Bank account management

### Customer Management
- Transaction history
- Customer profiles
- Loyalty program
- Customer support tools

## 4. B2B Services

### Bulk Payments
```bash
POST /api/v1/b2b/bulk-pay
{
  "recipients": [
    { "phoneNumber": "+250788111111", "amount": 100000 },
    { "phoneNumber": "+250788222222", "amount": 150000 }
  ],
  "description": "Salary distribution"
}
```

### Payroll Integration
```bash
# Integrate with payroll system
POST /api/v1/b2b/payroll/integrate
{
  "payrollSystemId": "PAYROLL-123",
  "employeeData": "..."
}

# Automatic monthly payouts
Scheduled: Monthly on 25th
Status: Processing → Completed
```

### Invoicing & Billing
```bash
# Create invoice
POST /api/v1/b2b/invoices
{
  "client": { "id": "CLIENT-123" },
  "items": [...],
  "dueDate": "2026-08-02"
}

# Share invoice link
{
  "paymentLink": "https://pay.itunda.rw/invoice/abc123"
}
```

### B2B Reports
- Monthly reports
- Tax reports
- Compliance reports
- Custom reports

## 5. Fees & Pricing

### Transaction Fees
- P2P transfers: 1% (min 100 RWF, max 5,000 RWF)
- QR payments: 1.5%
- Online payments: 2%
- Payouts: Free (first 5), then 500 RWF

### Monthly Costs
- Basic Plan: Free
- Business Plan: 5,000 RWF/month (50+ transactions)
- Enterprise Plan: Custom pricing

### Volume Discounts
- 1,000-5,000 transactions: 0.25% discount
- 5,000-10,000 transactions: 0.5% discount
- 10,000+ transactions: 1% discount + dedicated support

## 6. API Reference

### Authentication
```bash
Authorization: Bearer MERCHANT_API_KEY
```

### Endpoints
```
GET  /api/v1/merchants/me           - Merchant profile
PUT  /api/v1/merchants/me           - Update profile
POST /api/v1/merchants/qr/generate  - Generate QR code
GET  /api/v1/merchants/transactions - Transaction history
GET  /api/v1/merchants/settlements  - Settlement history
POST /api/v1/merchants/payouts      - Request payout
GET  /api/v1/merchants/analytics    - Analytics data
POST /api/v1/merchants/webhooks     - Configure webhooks
```

### Webhook Events
```
payment.received
payment.failed
payment.refunded
settlement.completed
settlement.failed
payout.scheduled
payout.completed
```

## 7. Security

### Encryption
- TLS 1.3 for all APIs
- PCI-DSS Level 1 compliance
- Card data tokenization
- End-to-end encryption

### Fraud Prevention
- Real-time monitoring
- Velocity checks
- Anomaly detection
- 3D Secure for high-value transactions

### Audit
- All transactions logged
- Immutable transaction history
- Compliance reports
- Tax documentation

## 8. Support

### Merchant Support
- Email: merchant-support@itunda.rw
- Phone: +250 788 123 456
- Chat: In-app messaging
- Hours: 8 AM - 6 PM (Rwanda Time)

### Documentation
- API Documentation: https://docs.itunda.rw/merchants
- Integration Guide: https://docs.itunda.rw/integration
- FAQ: https://support.itunda.rw

## 9. Quick Start

```bash
# 1. Register as merchant
curl -X POST https://api.itunda.rw/api/v1/merchants \
  -d '{
    "businessName": "My Store",
    "email": "owner@mystore.rw",
    "phone": "+250788111111"
  }'

# 2. Upload documents
curl -X POST https://api.itunda.rw/api/v1/merchants/kyc \
  -F "document=@certificate.pdf"

# 3. Generate QR code
curl -X POST https://api.itunda.rw/api/v1/merchants/qr/generate \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "amount": 50000,
    "description": "Store payment"
  }'

# 4. Receive payment
# Customer scans QR → Payment processed → Notification sent

# 5. View transactions
curl https://api.itunda.rw/api/v1/merchants/transactions \
  -H "Authorization: Bearer YOUR_API_KEY"
```

---

**Last Updated**: 2026-07-02
**Version**: 1.0.0
