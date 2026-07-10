# Itunda Testing Guide

## Overview

This guide covers all testing strategies for the Itunda platform.

## Test Types

### 1. Unit Tests
Test individual functions and components in isolation.

**Frontend Examples:**
```typescript
import { render, screen } from '@testing-library/react';
import { HomePage } from './HomePage';

describe('HomePage', () => {
  it('should display wallet balance', () => {
    render(<HomePage />);
    expect(screen.getByText(/balance/i)).toBeInTheDocument();
  });

  it('should display recent transactions', () => {
    render(<HomePage />);
    const transactions = screen.getAllByTestId('transaction-item');
    expect(transactions.length).toBeGreaterThan(0);
  });
});
```

**Backend Examples:**
```typescript
import { Test } from '@nestjs/testing';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [UserService],
    }).compile();
    service = module.get<UserService>(UserService);
  });

  it('should hash password', async () => {
    const password = 'SecurePassword123!';
    const hashed = await service.hashPassword(password);
    expect(hashed).not.toBe(password);
  });
});
```

### 2. Integration Tests
Test interaction between components and services.

**Example:**
```typescript
describe('User Registration (Integration)', () => {
  it('should register user and create wallet', async () => {
    const user = await userService.register({
      phoneNumber: '+250788111111',
      email: 'test@example.com',
      password: 'Test123456!',
    });
    
    const wallet = await walletService.getUserWallet(user.id);
    expect(wallet).toBeDefined();
    expect(wallet.balance).toBe(0);
  });
});
```

### 3. E2E (End-to-End) Tests
Test complete user workflows.

**Example:**
```typescript
describe('Send Money Flow (E2E)', () => {
  it('should complete money transfer', async () => {
    // Register users
    const sender = await registerUser('+250788111111');
    const recipient = await registerUser('+250788222222');
    
    // Login
    const token = await loginUser(sender.phoneNumber, 'password');
    
    // Fund wallet
    await fundWallet(sender.id, 1000000);
    
    // Send money
    const transfer = await request(app.getHttpServer())
      .post('/api/v1/transactions/send')
      .set('Authorization', `Bearer ${token}`)
      .send({
        recipientPhoneNumber: recipient.phoneNumber,
        amount: 50000,
      })
      .expect(201);
    
    expect(transfer.body.data.status).toBe('processing');
  });
});
```

### 4. Performance Tests
Test system performance and load capacity.

```typescript
describe('API Performance', () => {
  it('should handle 1000 concurrent requests', async () => {
    const requests = Array(1000).fill(null).map(() =>
      request(app.getHttpServer())
        .get('/api/v1/transactions')
        .set('Authorization', `Bearer ${token}`)
    );
    
    const start = Date.now();
    await Promise.all(requests);
    const duration = Date.now() - start;
    
    expect(duration).toBeLessThan(5000); // 5 seconds
  });
});
```

### 5. Security Tests
Test security vulnerabilities and compliance.

```typescript
describe('Security Tests', () => {
  it('should prevent SQL injection', async () => {
    const payload = "'; DROP TABLE users; --";
    
    const response = await request(app.getHttpServer())
      .post('/api/v1/users/search')
      .send({ query: payload })
      .expect(400);
    
    expect(response.body.errors).toBeDefined();
  });

  it('should enforce rate limiting', async () => {
    for (let i = 0; i < 101; i++) {
      await request(app.getHttpServer())
        .get('/api/v1/transactions')
        .set('Authorization', `Bearer ${token}`);
    }
    
    const response = await request(app.getHttpServer())
      .get('/api/v1/transactions')
      .set('Authorization', `Bearer ${token}`);
    
    expect(response.status).toBe(429); // Too Many Requests
  });
});
```

## Running Tests

### Frontend
```bash
# Run all tests
npm run test

# Run tests in watch mode
npm run test:watch

# Run specific test file
npm run test HomePage.test.tsx

# Run with coverage
npm run test:cov
```

### Backend
```bash
# Run all tests
npm run test

# Run specific module
npm run test UserService

# Run E2E tests
npm run test:e2e

# Run with coverage
npm run test:cov

# Run tests in watch mode
npm run test:watch
```

## Test Coverage Requirements

- **Statements**: 80%
- **Branches**: 75%
- **Functions**: 80%
- **Lines**: 80%

Critical areas (must have 100%):
- Authentication
- Authorization
- Payment processing
- Loan calculations

## Test Data & Mocking

### Mock Data
```typescript
const mockUser = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  phoneNumber: '+250788111111',
  email: 'test@example.com',
  status: 'active',
  createdAt: new Date(),
};

const mockTransaction = {
  id: '123e4567-e89b-12d3-a456-426614174001',
  amount: 50000,
  status: 'completed',
  createdAt: new Date(),
};
```

### Database Seeding
```bash
# Seed test database
npm run db:seed:test

# Reset test database
npm run db:reset:test

# Clear test data
npm run db:clean:test
```

## CI/CD Testing

Tests run automatically on:
- Push to any branch
- Pull requests
- Scheduled (daily at 2 AM UTC)

### Test Pipeline
1. Linting (2 min)
2. Type checking (3 min)
3. Unit tests (5 min)
4. Integration tests (8 min)
5. E2E tests (10 min)
6. Security scanning (5 min)
7. Performance tests (5 min)

Total: ~40 minutes

## Debugging Tests

### Debug Frontend Tests
```bash
# Run tests with debugging
node --inspect-brk ./node_modules/jest --runInBand

# Or use Chrome DevTools
npm run test:debug
```

### Debug Backend Tests
```bash
# Run with debugging
npm run test:debug

# Set breakpoints and use VSCode debugger
```

## Test Organization

```
src/
├── components/
│   ├── UserProfile.tsx
│   └── __tests__/
│       └── UserProfile.test.tsx
├── pages/
│   ├── HomePage.tsx
│   └── __tests__/
│       └── HomePage.test.tsx
├── services/
│   ├── api.ts
│   └── __tests__/
│       └── api.test.ts
└── store/
    ├── useStore.ts
    └── __tests__/
        └── useStore.test.ts
```

## Common Testing Patterns

### Testing API Calls
```typescript
jest.mock('../services/api');

it('should fetch user data', async () => {
  const mockUser = { id: '1', name: 'John' };
  api.getUser.mockResolvedValue(mockUser);
  
  const result = await getUser('1');
  expect(result).toEqual(mockUser);
});
```

### Testing State Management
```typescript
it('should update wallet balance', () => {
  const { result } = renderHook(() => useStore());
  
  act(() => {
    result.current.updateBalance(100000);
  });
  
  expect(result.current.balance).toBe(100000);
});
```

### Testing Async Operations
```typescript
it('should send money successfully', async () => {
  const { result } = renderHook(() => useSendMoney());
  
  act(() => {
    result.current.mutateAsync({
      amount: 50000,
      recipient: '+250788222222',
    });
  });
  
  await waitFor(() => {
    expect(result.current.isSuccess).toBe(true);
  });
});
```

## Performance Benchmarks

### Frontend
- Initial load: < 2 seconds
- Transaction list: < 1 second
- Send money: < 500ms

### Backend
- Login: < 100ms
- Get transactions: < 200ms (p95)
- Send money: < 2 seconds

## Accessibility Testing

```bash
npm run test:a11y
```

Tests WCAG compliance for:
- Color contrast
- Keyboard navigation
- Screen reader support
- Focus management

## Continuous Improvement

- Review test failures weekly
- Update tests when requirements change
- Add tests for bugs after fixes
- Maintain test code quality
- Regular refactoring of test utilities
