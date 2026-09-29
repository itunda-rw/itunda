# Contributing to Itunda

Thank you for your interest in contributing to Itunda! This document provides guidelines and instructions for contributing to the project.

**Before writing any UI/UX or architecture code, read
[docs/UI_UX_GUIDELINES.md](docs/UI_UX_GUIDELINES.md) and
[docs/ARCHITECTURE_GUIDELINES.md](docs/ARCHITECTURE_GUIDELINES.md)** — prescriptive,
sourced rules synthesized from Toss/Kakao/Spotify/Netflix/Uber/Apple's real published
engineering practices, kept synchronized with what's actually shipped in this repo.

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
4. **Install dependencies** (this repo uses Yarn workspaces + PnP, not npm):
   ```bash
   yarn install
   ```

## Development Workflow

### 1. Make Your Changes

- Create a new branch for each feature/fix
- Make small, focused commits
- Write clear commit messages
- Follow the existing code style

### 2. Test Your Changes

For a JS/TS micro-frontend (e.g. `services/micro-frontends/bank-mfe`):

```bash
yarn workspace <package-name> run build   # tsc -b && vite build
yarn workspace <package-name> run lint    # oxlint
python3 scripts/accessibility-lint.py <changed-file>
```

For the real backend (`services/backend`, Kotlin + Spring Boot + MySQL):

```bash
./gradlew :app:compileKotlin
./gradlew test
```

For Android (`android/`) or iOS (`ios/`), see [README.md](README.md#android)'s own setup
section — there is no single repo-wide `npm run build`/`test`/`lint`; this is a
multi-stack monorepo, not a single Node.js project.

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

See [docs/UI_UX_GUIDELINES.md](docs/UI_UX_GUIDELINES.md) and
[docs/ARCHITECTURE_GUIDELINES.md](docs/ARCHITECTURE_GUIDELINES.md) for the real,
sourced rules. Quick orientation on itunda's actual stacks (this is a multi-stack
monorepo, not a single framework):

### Frontend (React/TypeScript, `services/micro-frontends/*`)

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
```

### Backend (`services/backend`, real Kotlin + Spring Boot + MySQL)

```kotlin
// Use constructor injection
@Service
class MyService(private val repository: MyRepository)

// Clear error handling via a real domain exception, mapped to an HTTP status
// in the controller layer, not a generic 500
class MyResourceNotFoundException(message: String) : RuntimeException(message)
```

### General Rules

- Use meaningful variable names; name complex conditions and magic numbers rather than
  leaving them inline (see ARCHITECTURE_GUIDELINES.md §1)
- Keep functions small and focused; no nested ternaries (enforced by `oxlint` in JS/TS)
- Comment only the non-obvious WHY (a hidden constraint, a workaround), not the WHAT —
  well-named code doesn't need a comment restating it
- Remove `console.log`/debug prints before committing
- Use async/await instead of raw `.then()` chains
- Handle errors at the real boundary they occur at, not with a blanket catch-and-ignore

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

`services/backend` uses real Flyway migrations (`app/src/main/resources/db/migration/`,
`ddl-auto: validate` — Hibernate never infers the schema) plus JPA `@Entity` classes in
`core/.../domain/`. For database changes:
1. Add a new `V<N>__description.sql` Flyway migration (never edit an already-applied one)
2. Update the matching `@Entity` class(es) to stay column-for-column in sync
3. Update schema documentation if the change is user-facing

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

### Backend (`services/backend`, real Kotest + MockK)
```kotlin
class MyServiceTest : BehaviorSpec({
    val repository = mockk<MyRepository>()
    val service = MyService(repository)

    given("a valid request") {
        `when`("calling doSomething") {
            then("it returns the expected result") {
                // ...
            }
        }
    }
})
```

### Frontend (JS/TS)
No test runner is currently wired into the JS/TS workspaces — verification today is
`tsc -b` (real typecheck) + `oxlint` + `accessibility-lint.py` + a real browser
click-through against the live deployed backend, not a mocked unit-test suite. If you add
one, document it here in the same pass (see ARCHITECTURE_GUIDELINES.md §5).

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
