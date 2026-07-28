# AI Instructions — Gestion RH (HB Développement)

> **Purpose.** Canonical, tool-agnostic rules for any AI coding assistant
> (terminal agent, IDE assistant, anything). Read this file at the start of
> every session, together with `docs/avancement-projet.md` (living status:
> what is done, in progress, and next). This file holds the **stable rules**;
> never write status updates here.
> This file lives in `rh-backend/docs/` and is the single source of truth
> for the rules — if you are asked to update rules, update THIS file
> only. No native pointer files (`.github/copilot-instructions.md`,
> `.cursorrules`) are maintained in either repo; open this file directly.

## Repository layout — TWO repos, cloned side by side

```
workspace/            <- open your editor/agent HERE to see both repos
├── rh-backend/       <- pivot repo: Spring Boot + docker-compose +
│                        n8n/workflows/ + docs/ + .env.example
└── rh-frontend/      <- React/Vite app only
```

Two-repo split is an HB Développement convention (non-negotiable). The
backend repo is the pivot: local dev (`docker compose up` from
`rh-backend/`, compose references `../rh-frontend`), all project docs, and
the n8n workflows live there. Each repo has its **own** CI/CD pipeline and
publishes its **own** Docker image to Docker Hub (`sha-<short>` immutable
tags).

**A feature is still one vertical slice, but now two PRs:** backend PR
first (endpoint + tests + regenerated OpenAPI spec), frontend PR the same
day (regenerated types + screens). Never let a backend API change merge
without its frontend counterpart planned.

## Project in one paragraph

Internal HR management web app for HB Développement (Morocco). Monolithic
backend. 10 business modules: auth (RBAC + approval delegation), employee
records, QR attendance (incl. hybrid telework schedules), recruitment
(email CV ingestion + AI analysis), administrative requests (leave ledger),
HR documents (certificates + contract-end surveillance), dashboard,
exports, configuration, in-app notifications. Requirements live in
`rh-backend/docs/01-requirements.md` (EF-* / NFR-* codes), UML rationale
in `docs/02-diagrams-README.md`, database rationale in
`docs/03-schema-README.md`. When implementing, trace work to EF codes.

## Stack (fixed — do not substitute)

- **Backend:** Spring Boot (Java 17+), Maven wrapper. PostgreSQL 14+ only
  (schema uses Postgres ENUMs and `pg_trgm`; never target H2).
- **Frontend:** React + Vite + TypeScript. Node 22+ (see `.nvmrc`/
  `engines` in `rh-frontend/package.json`; also what `ci.yml` and the
  `dev`-stage Dockerfile pin).
- **Workflows:** n8n. **AI:** OpenRouter (free-tier text model, default
  `openai/gpt-oss-20b:free`) behind an interface (see below). Decision
  (2026-07-28): replaces Gemini — Gemini's free tier is hard-gated at a
  quota of 0 on every account/project tried, and enabling billing (Google's
  documented fix) isn't possible without a payment method. CVs are
  extracted to plain text server-side (PDFBox/POI, already dependencies)
  before being sent — OpenRouter's free models are text-only, not native
  multimodal file readers like Gemini was.
- **Local dev:** `docker compose up` from `rh-backend/` (backend hot
  reload via DevTools, frontend via Vite HMR, Mailpit catches all
  outgoing mail).
- **CI/CD:** GitHub Actions **per repo** (`ci.yml`, `release.yml` in each),
  Trivy (dependency/image scanning). No CodeQL/SAST — GitHub Advanced
  Security requires a paid per-committer license on private repos, not
  budgeted for this project; revisit at T6.4 (hardening) if that changes.
  `release.yml` currently builds the image and runs `trivy image` locally
  only (no push). Target registry is **GHCR**, tagged `sha-<short>`
  (immutable; `latest` is human convenience only) — push step
  (`docker/login-action` + push) not yet implemented, tracked as
  follow-up work on T1.A2.

## API contract discipline (critical in a two-repo setup)

1. The backend exposes its OpenAPI spec via springdoc-openapi and exports
   it to `rh-backend/contracts/openapi.json` (make target). Regenerate it
   in every PR that touches an endpoint.
2. The frontend generates `src/types/api.ts` from
   `../rh-backend/contracts/openapi.json` via openapi-typescript (make/npm
   script). **Never hand-edit `src/types/api.ts`.**
3. If the generated types break the frontend build, that is the system
   working: fix the mismatch, don't bypass the generation.

## Architecture rules

1. **Feature-based, vertical slices.** Backend: one package per module under
   `ma/hbdev/rh/` (`auth/`, `employee/`, `attendance/`, `recruitment/`,
   `adminrequest/`, `document/`, `dashboard/`, `export/`, `config/`,
   `notification/`, plus `shared/`). Frontend: mirrored under
   `src/features/`. Never organize by technical layer.
2. **Stay inside the feature you were asked to work on.** If a change is
   needed in `shared/` or another feature, stop and say so explicitly —
   do not silently modify code outside the current feature.
3. **Keep features flat** (controller, service, repository, entities, DTOs
   directly in the package). Sub-packages only past ~15 files.
4. **Package-private by default.** Only controllers and classes explicitly
   consumed by other modules are `public`. Never call another feature's
   repository directly.
5. **Modules communicate via Spring application events**, not direct calls.
   Examples: recruitment publishes `CandidatEmbauche` → employee module
   creates a pre-filled record (EF-REC-13); business events → notification
   listeners (in-app always created, Mattermost attempt independent —
   EF-NOTIF-01/06); sensitive actions → audit listener (NFR-SEC-03).
6. **AI vendor is quarantined** behind `shared/ai`'s `CvAnalysisProvider`
   interface. Feature code never mentions the vendor by name (OpenRouter).
   The app must start and degrade gracefully without an API key
   (EF-REC-05).
7. **n8n is transport only.** IMAP polling, cron pings, SMTP sends. All
   business logic (normalization, dedup, J-3/J-15 rules) lives in the
   backend. n8n workflows are versioned as JSON in
   `rh-backend/n8n/workflows/` (run `make n8n-export` after editing in the
   UI); credentials are never exported or committed.

## Database rules

1. **Flyway, SQL files only**, in
   `rh-backend/src/main/resources/db/migration/`. A merged migration is
   immutable — always add `V<next>__*.sql`.
2. Migrations carry **schema + reference data only** (default schedule,
   fixed civil holidays, system parameters, seed admin). **Real HR data
   never enters via SQL** — it goes through the validated import feature
   (EF-EMP-07: server-side validation, dry-run, dedup by matricule,
   idempotent).
3. **Integration tests use Testcontainers** with real PostgreSQL 14,
   replaying all migrations from scratch. Never H2.
4. Business entities use **logical deletion** via `statut` columns
   (NFR-DATA-01); leave balance is **always computed from the
   `mouvements_conges` ledger, never stored**.
5. `journal_audit` is append-only (SQL RULES block UPDATE/DELETE) — never
   write code that edits or deletes audit rows.

## Domain rules that are easy to get wrong

- **A planned telework day is remote *worked time* — never an absence,
  never leave.** The anomaly engine must check `plannings_teletravail`
  (+ `plannings_teletravail_jours`) first and skip all anomaly detection
  for active remote days (EF-ATT-04/08/09). No schedule = on-site every
  working day. Schedules are entered manually by the admin, never inferred,
  and never retro-recalculate past attendance.
- Server timestamp is authoritative for scans; lunch break (1h) is deducted
  automatically; anomalies are evaluated against the reference schedule
  **in effect at scan time** (historized `horaires_reference`).
- All schedule/lateness comparisons happen explicitly in the
  `Africa/Casablanca` timezone (Morocco suspends DST during Ramadan —
  never rely on the JVM default timezone).
- Mattermost failure must never block the in-app notification, and vice
  versa (two independent listeners).

## Frontend rules

- **Server state: TanStack Query only. Never fetch in `useEffect`.**
- Routing: React Router (data router). Forms: react-hook-form + zod
  (client validation is UX; real validation is server-side, NFR-SEC-05).
- **Components: Tailwind CSS, styled to the HB Développement Figma design,
  built on Radix UI primitives (`Dialog`, `Select`, `DropdownMenu`,
  `Popover`) + `@tanstack/react-table` for data grids.** Decision (2026-07-15):
  replaces the earlier Ant Design mandate. Never hand-roll a table's
  sort/filter/pagination or a dialog's focus-trap/keyboard handling from
  scratch — that reintroduces exactly the maintenance cost the AntD rule
  existed to avoid for a two-person team. Use the shared wrappers in
  `src/components/ui/` (`Dialog`, `Select`, `DatePicker`, `DropdownMenu`,
  `FormField`, `Input`, `Alert`, `Button`, `toast`, `confirm`) instead of
  reimplementing per screen.
- **Design tokens: one source.** Brand colors/fonts live as CSS custom
  properties in `src/styles/theme.css`; `src/components/ui/tokens.ts` is a
  thin TS-side re-export for non-className contexts (chart colors, inline
  SVG) — never a second hand-maintained copy of the palette.
- Client state: session context (role) only. No Redux.
- API types come from the generated `src/types/api.ts` (see contract
  discipline above) — never hand-written.
- A component moves to `src/components/` only when a **second** feature
  uses it.
- UI language is **French**; dates in French format (`date-fns` + its `fr`
  locale; `react-day-picker` for date inputs).

### Mock-first workflow (decision 2026-07-15)

UI may be scaffolded ahead of its backend — wired into the router and
sidebar, shown live — driven by `src/lib/featureFlags.ts`. Two rules keep
this from eroding the Definition of Done:

1. Every screen whose flag is still `false` must render the shared
   `MockBanner` (`src/components/ui/MockBanner.tsx`) — a mock screen is
   never shown without an explicit, visible indicator that its data isn't
   real. Never silently ship a mock screen indistinguishable from a
   finished one.
2. "No mocks left" still gates a module as done (Quality bar, below): a
   task isn't complete until its flag flips to `true` and the banner is
   gone, regardless of how finished the screen looks.

## Quality bar (Definition of Done for any feature work)

Endpoints under real RBAC (admin/manager per the EF) · unit tests +
Testcontainers integration tests when DB is touched · audit events for
sensitive actions · frontend wired to the real API (no mocks left) ·
OpenAPI spec + TS types regenerated if any endpoint changed ·
`CvAnalysisProvider` mocked in all tests (never a real key in CI) ·
lint/format clean (Spotless/Checkstyle, ESLint/Prettier — follow the
committed configs) · **both PRs** (backend and frontend, when the feature
touches both repos) reviewed by the other dev ·
`docs/avancement-projet.md` updated.

## Secrets

All configuration via environment variables only (`.env` in `rh-backend/`,
gitignored; `.env.example` is the documented contract). Never hardcode a
secret, never write one to a committed file, never log one. If you need a
new config value, add it to `.env.example` with a comment and a functional
dummy value.
