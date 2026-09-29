# Woodpecker CI Setup

Last updated: 2026-09-04

Real, open-source (Apache 2.0) CI running on infrastructure itunda owns, built as an
alternative execution path for when GitHub Actions' hosted runners are blocked (see
`project_itunda_ci_billing_blocked` -- the GitHub billing/spending-limit block that
motivated this). `.github/workflows/ci-cd.yml` is left in place; this is a parallel,
independent pipeline definition, not a replacement.

## Why Woodpecker, not a GitHub Actions self-hosted runner

A self-hosted GitHub Actions runner was the first option considered -- it still
depends on GitHub Actions' own control plane/scheduling/UI even when the compute is
local. Woodpecker only uses GitHub for source hosting and webhook delivery; the CI
system itself (scheduling, UI, pipeline definitions, execution) is fully
self-hosted and open source. Forked from Drone CI in 2021 after Drone's licensing
change restricted self-hosted use -- actively maintained, real GitHub integration,
server+agent under 50MB RAM combined.

## Architecture

- **Woodpecker server** (`infra/ci/docker-compose.yml`) -- lightweight control plane.
  Receives GitHub webhooks, serves the UI/API, schedules work to agents. Does not
  build anything itself. Needs a **publicly reachable address** so GitHub's webhook
  delivery and OAuth redirect can reach it.
- **Docker-based agent** (same compose file) -- runs every job except `ios-build` as
  a container per step (`.woodpecker/lint-and-typecheck.yml`, `backend.yml`,
  `android.yml`).
- **macOS "local"-backend agent** (`scripts/woodpecker-exec-agent-install.sh`) --
  runs `ios-build` directly on a Mac's own already-installed Xcode/tuist/pod (Xcode
  has no Linux container image, so this job structurally cannot run on the Docker
  agent). No container isolation on this path -- see the security note in
  `infra/ci/woodpecker-exec-agent.env.example`.

**Deliberately not deployed to `itunda-dc-a`** (the shared production k8s node) --
that node is already documented as overcommitted with real past memory-pressure
incidents (`project_itunda_private_cloud`). Adding CI build load there risks
repeating those incidents. Deploy the server to separate infrastructure.

## Manual steps only you can do

Woodpecker cannot create these itself -- no GitHub API exists for OAuth App
creation, and exposing a new public endpoint is a real infrastructure decision.

1. **Decide where the server runs and how GitHub reaches it.** It needs a stable
   public HTTPS address. Options, roughly in order of how much new infrastructure
   they need:
   - The existing private-cloud public path (`itunda.duckdns.org` is already
     real, DNS-resolved, and HTTPS-reachable per `project_itunda_private_cloud`) --
     but route the server to a SEPARATE VM/node from `itunda-dc-a`, not onto it, for
     the resource-contention reason above.
   - A new small VM (cloud or otherwise) with its own public IP or DDNS name.
   - A tunnel (Cloudflare Tunnel, Tailscale Funnel) fronting a machine with no
     public IP -- simplest to stand up, but reintroduces a third-party dependency
     this whole effort is partly meant to reduce; only pick this if the other two
     aren't practical.
2. **Create a GitHub OAuth App** for that address, under the `itunda-rw`
   organization's own settings (not a personal account) at
   `https://github.com/organizations/itunda-rw/settings/applications/new`:
   - Homepage URL: your chosen `WOODPECKER_HOST`
   - Authorization callback URL: `<WOODPECKER_HOST>/authorize`
   - Copy the generated Client ID and Client Secret into `infra/ci/.env`.
3. **Generate a real agent secret**: `openssl rand -hex 32`, put it in both
   `infra/ci/.env` (`WOODPECKER_AGENT_SECRET`) and
   `infra/ci/woodpecker-exec-agent.env` (same value, both agents talk to one server).
4. **Confirm the second contributor's real GitHub login** before adding them to
   `WOODPECKER_ADMIN` -- `CONTRIBUTORS.md`'s "에릭" entry has no confirmed GitHub
   username in git history (a local-machine commit email, not a GitHub-linked one).
   Don't guess.
5. **Enable "trusted" mode for this repo** in the Woodpecker UI's project settings,
   once the repo is registered. Off by default, and required for
   `.woodpecker/containers.yml`'s `application-container-build` steps to work at
   all -- Woodpecker gates any host volume mount (here, mounting the agent host's
   own `/var/run/docker.sock` so a step can run real `docker build` commands)
   behind this setting for security reasons. Without it, those 4 steps won't be
   able to mount the socket -- confirm the actual failure mode once Woodpecker is
   live rather than assuming it's an obvious error pointing at this setting.

## Bringing it up

```bash
# 1. Server + Docker-based agent (on whichever host you picked in step 1 above)
cp infra/ci/.env.example infra/ci/.env   # fill in real values first
cd infra/ci && docker compose up -d

# 2. macOS "local"-backend agent, for ios-build only (on a Mac with Xcode/tuist/pod
#    already installed -- confirmed present on this dev machine as of 2026-09-04)
cp infra/ci/woodpecker-exec-agent.env.example infra/ci/woodpecker-exec-agent.env
# fill in real values, matching the server's WOODPECKER_AGENT_SECRET
scripts/woodpecker-exec-agent-install.sh install
```

Then log into the Woodpecker UI at `WOODPECKER_HOST` with the GitHub account listed
in `WOODPECKER_ADMIN`, and activate the `itunda-rw/itunda` repo -- Woodpecker
registers the GitHub webhook automatically at that point (needs your OAuth token to
have repo admin scope, which it will if you're logged in as the account that
already has admin on the repo).

## Known caveats, not yet resolved

- **`cimg/android:2026.08` has no `linux/arm64` manifest** (confirmed via `docker
  pull` -- fails with "no matching manifest for linux/arm64/v8"; only `linux/amd64`
  exists). `infra/ci/docker-compose.yml`'s Docker-based agent doesn't pin a
  `platform:`, so it inherits whatever architecture the HOST it runs on is. If you
  deploy that agent to an arm64 host (an Apple Silicon Mac, an arm64 cloud
  instance), `.woodpecker/android.yml` will fail outright, not just run slow under
  emulation. Run the Docker-based agent on an amd64 host, or add
  `platform: linux/amd64` to that one step in `.woodpecker/android.yml` (forces
  QEMU emulation on an arm64 host -- works, but noticeably slower for a real
  Android build).
- `.woodpecker/android.yml` uses `cimg/android:2026.08` -- its exact bundled JDK
  version isn't confirmed against this project's `jvmTarget=17` pin. The file's own
  first step (`check-jdk-version`) surfaces this on the first real run; if it isn't
  17, the image needs `sdk use java 17.x` added before the Gradle steps (CircleCI's
  own images bundle multiple JDKs, selectable this way).
- Nothing here has been run against a live Woodpecker instance yet -- every command
  in `.woodpecker/*.yml` is a direct, verified port of `.github/workflows/ci-cd.yml`'s
  own already-proven commands, but the Woodpecker-specific plumbing (image choices,
  YAML syntax, label routing) is unverified until the first real webhook-triggered
  run. Confirm each file goes green before trusting it the way `ci-cd.yml` was
  trusted after its own real fixes.
