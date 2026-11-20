# AI Agent and MCP Permissions Policy

This policy applies to AI coding agents, MCP servers, automation agents, and connected integrations operating on the Itunda project in the `itunda-rw` organization.

## Default access

Within resources owned by Itunda and explicitly connected/authorized for the current agent, grant the broadest access needed to complete development tasks without unnecessary per-file or per-operation permission prompts.

### Read access

Agents may read:
- All authorized Itunda repositories, source files, configuration, documentation, and tests.
- Repository branches, commits, issues, pull requests, reviews, and project metadata.
- CI workflows, run results, logs, and build artifacts.
- Connected project/workspace documentation and resources.
- Development and staging service configuration, logs, metrics, and data when the connected integration explicitly grants access.

### Write access

Agents may make and verify ordinary development changes:
- Edit, create, and delete repository files as needed for a task.
- Create and update feature/fix branches; commit changes.
- Open, update, and comment on pull requests; review code.
- Create and update issues, labels, and project documentation.
- Update tests, development configuration, and CI workflows.
- Use authorized MCP write tools for connected Itunda development services.
- Coordinate across connected tools (for example, GitHub, Vercel, Neon, Render, and Notion) when the tools are available and the target resource is Itunda-owned or explicitly authorized.

Do not require a separate approval for each routine file edit, branch, commit, issue, PR, or non-destructive development action.

## Scope and limitations

This policy is an instruction to agents. It does **not** grant OAuth scopes, repository roles, organization permissions, MCP capabilities, or account access that the user or service administrator has not actually configured. Use only tools and resources available to the current authenticated connection.

- Scope actions to Itunda-owned resources and accounts explicitly authorized for the task.
- Never access unrelated organizations, repositories, or personal resources.
- Never reveal, print, commit, or transmit secrets, credentials, access tokens, private keys, or sensitive environment values. Use secret references and redact logs.
- Do not silently change account-wide or organization-wide access settings, OAuth scopes, or permissions. Such changes require explicit authorization and the relevant admin controls.
- Do not perform destructive production operations, production database deletion/migrations that risk data loss, production resource deletion, billing-impacting changes, or irreversible customer-data changes without explicit user approval.
- Production deployments, production secrets/IAM changes, and security-sensitive configuration changes require explicit approval unless the user clearly authorized that exact action.
- Preserve auditability: use commits/PRs for code changes and summarize affected resources and verification.

## Execution expectations

- Inspect existing project instructions and the relevant code before changing files.
- Prefer a focused branch and pull request for policy or code changes.
- Run appropriate tests and checks; clearly report checks that could not run.
- Follow product-specific instructions and any stricter security policy. This policy does not override repository-specific constraints, deployment safeguards, or legal/compliance requirements.
