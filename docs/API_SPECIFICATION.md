# Itunda API Specification

## Base URL
- Development: `http://localhost:3000/api/v1`
- Production: `https://api.itunda.rw/api/v1`

## Authentication
All endpoints (except login/register) require Bearer token in Authorization header:
```
Authorization: Bearer <jwt_token>
```

## HTTP Status Codes
- `200`: Success
- `201`: Created
- `204`: No Content
- `400`: Bad Request
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Not Found
- `409`: Conflict
- `422`: Unprocessable Entity
- `429`: Too Many Requests
- `500`: Internal Server Error

## Rate Limiting
- Global: 100 requests per 15 minutes
- Per endpoint: Varies (see endpoint docs)

## Response Format
```json
{
  "status": "success",
  "code": 200,
  "message": "Operation successful",
  "data": {},
  "timestamp": "2024-07-02T12:44:05Z"
}
```

## Error Response Format
```json
{
  "status": "error",
  "code": 400,
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    }
  ],
  "timestamp": "2024-07-02T12:44:05Z"
}
```

---

## Authentication Endpoints

### POST /auth/register
Register a new user

**Request Body**
```json
{
  "phoneNumber": "+250788111111",
  "email": "user@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1990-01-01"
}
```

**Response** (201)
```json
{
  "status": "success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

### POST /auth/login
Authenticate user

**Request Body**
```json
{
  "phoneNumber": "+250788111111",
  "password": "SecurePassword123!"
}
```

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

### POST /auth/refresh-token
Refresh JWT token

**Request Body**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

### POST /auth/logout
Logout user (invalidate token)

**Response** (204)
No content

---

## User Endpoints

### GET /users/profile
Get current user profile

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "phoneNumber": "+250788111111",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "status": "active",
    "kycStatus": "verified",
    "createdAt": "2024-01-01T10:00:00Z"
  }
}
```

### PUT /users/profile
Update user profile

**Request Body**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane@example.com"
}
```

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane@example.com",
    "updatedAt": "2024-07-02T12:44:05Z"
  }
}
```

### POST /users/kyc/verify
Submit KYC verification

**Request Body** (multipart/form-data)
```
nationalIdFront: <file>
nationalIdBack: <file>
selfie: <file>
address: "123 Main St"
city: "Kigali"
country: "Rwanda"
postalCode: "12345"
```

**Response** (201)
```json
{
  "status": "success",
  "message": "KYC verification submitted. Pending review.",
  "data": {
    "kycStatus": "pending"
  }
}
```

---

## Account Endpoints

### GET /accounts
List all user accounts

**Query Parameters**
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 10)
- `type`: Account type filter (savings, checking, mobile_money)
- `currency`: Currency filter (RWF, USD, EUR)

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "accountNumber": "ACC-001",
        "accountType": "savings",
        "balance": 1000000,
        "currency": "RWF",
        "status": "active",
        "createdAt": "2024-01-01T10:00:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 10,
      "total": 1
    }
  }
}
```

### POST /accounts
Create a new account

**Request Body**
```json
{
  "accountType": "savings",
  "currency": "RWF"
}
```

**Response** (201)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "accountNumber": "ACC-002",
    "accountType": "savings",
    "balance": 0,
    "currency": "RWF",
    "status": "active"
  }
}
```

---

## Transaction Endpoints

### GET /transactions
List transactions

**Query Parameters**
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 20)
- `status`: Status filter (pending, completed, failed)
- `type`: Transaction type filter
- `fromDate`: Start date (ISO 8601)
- `toDate`: End date (ISO 8601)

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "fromUserId": "550e8400-e29b-41d4-a716-446655440000",
        "toUserId": "660e8400-e29b-41d4-a716-446655440001",
        "amount": 50000,
        "currency": "RWF",
        "transactionType": "transfer",
        "status": "completed",
        "description": "Payment for groceries",
        "createdAt": "2024-07-02T12:44:05Z",
        "completedAt": "2024-07-02T12:44:15Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 150
    }
  }
}
```

### POST /transactions/send
Send money

**Request Body**
```json
{
  "recipientPhoneNumber": "+250788222222",
  "amount": 50000,
  "description": "Payment",
  "fromAccountId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** (201)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "transactionId": "TXN-001",
    "status": "processing",
    "amount": 50000,
    "fee": 1000,
    "totalAmount": 51000,
    "message": "Transaction initiated. It will be completed in 2-5 minutes."
  }
}
```

### GET /transactions/:id
Get transaction details

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "transactionId": "TXN-001",
    "fromUser": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "phoneNumber": "+250788111111"
    },
    "toUser": {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "phoneNumber": "+250788222222"
    },
    "amount": 50000,
    "fee": 1000,
    "totalAmount": 51000,
    "status": "completed",
    "createdAt": "2024-07-02T12:44:05Z",
    "completedAt": "2024-07-02T12:44:15Z"
  }
}
```

---

## Loan Endpoints

### POST /loans/apply
Apply for a loan

**Request Body**
```json
{
  "loanAmount": 500000,
  "durationMonths": 12,
  "purpose": "Personal use"
}
```

**Response** (201)
```json
{
  "status": "success",
  "data": {
    "loanId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "pending",
    "message": "Loan application submitted. You'll receive a response within 24 hours."
  }
}
```

### GET /loans
List loans

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "loanAmount": 500000,
        "interestRate": 12.5,
        "monthlyPayment": 45000,
        "status": "active",
        "remainingBalance": 450000,
        "nextPaymentDate": "2024-08-02",
        "createdAt": "2024-06-02T10:00:00Z"
      }
    ]
  }
}
```

---

## Notification Endpoints

### GET /notifications
Get notifications

**Query Parameters**
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 20)
- `isRead`: Filter by read status (true/false)

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Transfer Completed",
        "message": "Your transfer of 50,000 RWF to +250788222222 was successful.",
        "type": "transaction",
        "isRead": false,
        "createdAt": "2024-07-02T12:44:05Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "unreadCount": 5
    }
  }
}
```

### PUT /notifications/:id/read
Mark notification as read

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "isRead": true
  }
}
```

---

## Analytics Endpoints

### GET /analytics/spending
Get spending analytics

**Query Parameters**
- `period`: Analysis period (week, month, year) - default: month
- `category`: Category filter (optional)

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "period": "month",
    "totalSpent": 500000,
    "avgDailySpending": 16667,
    "byCategory": {
      "food": 100000,
      "transport": 50000,
      "utilities": 75000,
      "entertainment": 50000,
      "other": 225000
    },
    "topTransactions": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "amount": 50000,
        "category": "food",
        "description": "Supermarket"
      }
    ]
  }
}
```

### GET /analytics/insights
Get AI-powered insights

**Response** (200)
```json
{
  "status": "success",
  "data": {
    "spendingTrend": "increasing",
    "recommendations": [
      "Your spending is 20% higher than last month. Consider reviewing discretionary expenses.",
      "You can save 50,000 RWF monthly if you switch to a cheaper internet plan."
    ],
    "savingsPotential": 50000,
    "budgetSuggestion": {
      "recommended": 600000,
      "current": 500000
    }
  }
}
```

---

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| AUTH_001 | Invalid credentials | Wrong username or password |
| AUTH_002 | Token expired | JWT token has expired |
| AUTH_003 | Unauthorized | User not authenticated |
| AUTH_004 | Forbidden | Insufficient permissions |
| USER_001 | User not found | User does not exist |
| USER_002 | Email already exists | Email is already registered |
| USER_003 | Phone number already exists | Phone number is already registered |
| TRANS_001 | Insufficient balance | Account balance is insufficient |
| TRANS_002 | Transaction failed | Transaction processing failed |
| LOAN_001 | Loan application rejected | Loan application was rejected |
| LOAN_002 | Loan already exists | User already has an active loan |
| RATE_001 | Rate limit exceeded | Too many requests |
| SERVER_001 | Internal server error | Unexpected server error |

## Webhook Events

### transaction.completed
Sent when a transaction is completed

```json
{
  "event": "transaction.completed",
  "timestamp": "2024-07-02T12:44:05Z",
  "data": {
    "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    "amount": 50000,
    "status": "completed"
  }
}
```

### loan.approved
Sent when a loan is approved

```json
{
  "event": "loan.approved",
  "timestamp": "2024-07-02T12:44:05Z",
  "data": {
    "loanId": "550e8400-e29b-41d4-a716-446655440000",
    "amount": 500000,
    "interestRate": 12.5
  }
}
```

---

## Pagination

All list endpoints support pagination with:
- `page`: Current page number (1-based)
- `limit`: Items per page (1-100, default: 10)

Response includes:
```json
{
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 100,
    "totalPages": 10,
    "hasNext": true,
    "hasPrev": false
  }
}
```

## Filtering

Use query parameters for filtering:
```
GET /transactions?status=completed&type=transfer&fromDate=2024-01-01&toDate=2024-07-02
```

## Sorting

Use `sort` query parameter:
```
GET /transactions?sort=-createdAt
# ascending: fieldName
# descending: -fieldName
```
