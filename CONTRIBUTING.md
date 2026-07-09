# Contributing to Itunda

Thank you for your interest in contributing to Itunda! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

- Be respectful and inclusive
- Focus on the code, not the person
- Welcome newcomers and help them get started
- Keep discussions professional and constructive

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/your-username/itunda.git
   cd itunda
   ```
3. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. **Install dependencies**:
   ```bash
   npm install
   ```

## Development Workflow

### 1. Make Your Changes

- Create a new branch for each feature/fix
- Make small, focused commits
- Write clear commit messages
- Follow the existing code style

### 2. Test Your Changes

```bash
# Run linter
npm run lint

# Run unit tests
npm run test

# Run E2E tests (if applicable)
npm run test:e2e

# Build to check for compilation errors
npm run build
```

### 3. Commit Your Changes

Use clear, descriptive commit messages:

```bash
git commit -m "feat: add two-factor authentication

- Add TOTP support
- Add SMS verification
- Add backup codes generation

Closes #123"
```

**Commit message format**:
- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation
- `style:` for formatting/style changes
- `refactor:` for code restructuring
- `perf:` for performance improvements
- `test:` for adding/modifying tests
- `chore:` for maintenance tasks

### 4. Push and Create a Pull Request

```bash
git push origin feature/your-feature-name
```

Then create a pull request on GitHub.

## Pull Request Guidelines

### Before Submitting
- Ensure all tests pass
- Ensure linting passes
- Update documentation if needed
- Add tests for new functionality
- Keep PRs focused on a single feature/fix

### PR Description
Include:
- Clear description of changes
- Related issues (use `Closes #123`)
- How to test the changes
- Screenshots/videos for UI changes
- Breaking changes (if any)

### PR Title Format
```
[Service] Type: Brief description

Examples:
- [API Gateway] feat: Add rate limiting middleware
- [Frontend] fix: Fix transaction history pagination
- [Mobile] docs: Update installation instructions
```

## Code Standards

### Frontend (React/TypeScript)

```typescript
// Use functional components
export function MyComponent() {
  return <div>Content</div>;
}

// Use TypeScript types
interface Props {
  title: string;
  count: number;
}

// Name exports for easier refactoring
export { MyComponent };
```

### Backend (NestJS/TypeScript)

```typescript
// Use dependency injection
@Injectable()
export class MyService {
  constructor(private readonly db: DatabaseService) {}
}

// Clear error handling
throw new BadRequestException('Validation failed');

// Type safe
async findUser(id: string): Promise<User> {
  return this.userRepository.findById(id);
}
```

### General Rules

- Use meaningful variable names
- Keep functions small and focused
- Add comments for complex logic
- Remove console.log statements
- Use async/await instead of .then()
- Handle errors appropriately

## Branch Naming

Use descriptive branch names:
- `feature/user-authentication`
- `fix/transaction-calculation-bug`
- `docs/api-documentation`
- `refactor/simplify-payment-flow`

## Issue Reporting

When reporting issues:
1. Check if the issue already exists
2. Use clear, descriptive titles
3. Include steps to reproduce
4. Provide expected vs actual behavior
5. Include screenshots/logs if relevant
6. Specify your environment (OS, Node version, etc.)

## Feature Requests

When requesting features:
1. Clearly describe the feature
2. Explain the use case
3. Provide examples if possible
4. Discuss potential implementation approaches

## Documentation

- Update README.md for significant changes
- Add JSDoc comments to functions
- Keep API documentation up to date
- Document breaking changes in CHANGELOG.md

## Database Migrations

For database changes:
1. Create migration file
2. Update schema documentation
3. Provide rollback migration
4. Update TypeORM entities

## Performance

- Consider performance impact of changes
- Add performance tests for critical paths
- Use proper indexing for queries
- Optimize images and assets

## Security

- Never commit secrets or API keys
- Use environment variables
- Validate all inputs
- Follow OWASP guidelines
- Report security issues privately to maintainers

## Testing

### Unit Tests
```typescript
describe('MyService', () => {
  it('should do something', () => {
    const result = myFunction();
    expect(result).toBe(expected);
  });
});
```

### Integration Tests
```typescript
describe('UserController (e2e)', () => {
  it('should create a user', () => {
    return request(app.getHttpServer())
      .post('/users')
      .send({ email: 'test@example.com' })
      .expect(201);
  });
});
```

## Questions?

- Check existing issues and discussions
- Ask in pull request comments
- Open a discussion
- Contact maintainers

## Recognition

Contributors will be:
- Listed in CONTRIBUTORS.md
- Mentioned in release notes
- Given credit in commits

Thank you for contributing to Itunda! 🚀
