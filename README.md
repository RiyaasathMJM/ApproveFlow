# ApproveFlow

ApproveFlow is a small approval engine that I built for the Neirah Tech Solutions engineering assignment.

The idea is simple: companies keep needing "someone must approve this" flows (expense claims, leave requests, purchase requests). Instead of hard-coding each one, ApproveFlow stores the approval rules as data. An admin can change who approves what, and the code stays the same.

Two workflows come ready to use:

- **Expense Claim**: Manager approves. If the amount is above LKR 10,000, Finance approves after that.
- **Leave Request**: Manager approves.

## What it can do

- Log in with JWT. Passwords are hashed with bcrypt. Every permission is checked on the server.
- Three kinds of users: **Admin** (manages workflows, sees everything), **Requester** (creates and submits requests), **Approver** (Manager or Finance, approves, rejects or returns requests).
- Workflows are versioned. Editing one creates a new version, and requests already in progress keep the version they started with.
- A strict request lifecycle: Draft, Pending approval, Approved, Rejected, Returned.
- Returned requests can be edited and resubmitted. This starts a new approval cycle and the old history is kept.
- Every action is saved in an append-only audit timeline.
- Overdue steps can be escalated, and running the escalation twice does not create duplicates.
- Admin dashboard with pending, overdue, approved and rejected counts and average completion time.

## What you need first

- JDK 21 or newer
- Node.js 20.19 or newer (with npm)
- MySQL 8 or MariaDB 10, running on `localhost:3306`

## Setup and run

### 1. Create the databases

```sql
CREATE DATABASE approveflow      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE approveflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`approveflow` is the real app database. `approveflow_test` is only for automated tests and is wiped before every test.

### 2. Set environment variables (optional)

Everything has a default that works for a local MySQL with user `root` and no password. Set these only if yours is different.

| Variable | Default | What it is |
|---|---|---|
| `DB_USER` | `root` | Database user |
| `DB_PASSWORD` | empty | Database password |
| `JWT_SECRET` | dev-only value | Key used to sign tokens (32+ characters). Change it outside local development |
| `SEED_PASSWORD` | `Demo@12345` | Password for the demo users |

Windows PowerShell:

```powershell
$env:DB_USER="root"
$env:DB_PASSWORD="your_password"
```

macOS / Linux:

```bash
export DB_USER=root
export DB_PASSWORD=your_password
```

Real secrets are never committed. Local values live in environment variables.

### 3. Start the backend (this also migrates and seeds)

```bash
cd Backend/approveflow
./mvnw spring-boot:run
```

On Windows use `.\mvnw.cmd spring-boot:run`.

When it starts, it:

1. **Migrates**: Flyway creates all tables, keys, constraints and indexes from `src/main/resources/db/migration`.
2. **Seeds**: it creates the demo users and the two workflows (only if they do not already exist, so restarting is safe).

The API runs on http://localhost:8080 and the Swagger docs are at http://localhost:8080/swagger-ui.html.

### 4. Start the frontend

In a second terminal:

```bash
cd Frontend/approveflow-ui
npm install
npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` calls to the backend, so there is no CORS setup to do.

### 5. Run the tests

```bash
cd Backend/approveflow
./mvnw test
```

On Windows use `.\mvnw.cmd test`. This one command runs everything.

There are 12 tests, covering all the required scenarios from the assignment (T01 to T12). They start the app on a random port, call the real HTTP API, and then check the database itself, not just the HTTP status codes. They need the `approveflow_test` database from step 1.

## Demo accounts

Password for all of them: `Demo@12345` (or whatever you set in `SEED_PASSWORD`).

| Email | Role |
|---|---|
| admin@demo.local | Admin |
| requester@demo.local | Requester |
| manager@demo.local | Manager |
| manager2@demo.local | Manager (a second manager, handy for showing concurrent approvals) |
| finance@demo.local | Finance |

## Try it yourself (about 5 minutes)

1. Log in as **admin**. Open *Workflow Definitions* and look at the two workflows and their versions.
2. Log in as **requester**. Submit an expense of LKR 9,500, an expense of LKR 15,000, and a leave request.
3. Log in as **manager**. Approve the 9,500 expense (it is finished). Approve the 15,000 expense (it moves to Finance).
4. Log in as **finance**. Approve the 15,000 expense, then open it and look at the timeline.
5. As **requester**, submit another request. As **manager**, return it with a reason. Edit and resubmit it as requester, and notice the approval cycle becomes 2 while the earlier history is still there.
6. Try something forbidden: approve your own request, or approve a request twice. The server refuses and nothing changes.
7. As **admin**, open the dashboard and press *Run overdue escalation* twice. Only one escalation record is created.

## How it works (short version)

**Routing.** A workflow version is a list of steps. Each step has an approver role, a due time in hours, and an optional condition like "amount > 10000". The approval engine does not know about expenses or leave. It just picks the first step whose condition matches, and after each approval the next matching one.

**Versioning.** Editing a workflow never changes the old version. It adds a new version, and only new submissions use it. Each request remembers the version it was submitted with, forever.

**State, step and cycle.** A request stores its status, its current step, its workflow version and its cycle number. Status alone is not enough to know who should act next.

**Concurrency.** If two people approve the same request at the same moment, only one decision is saved:

1. The request row is locked while a decision is being processed, so decisions on one request happen one at a time. The second person sees the updated request and gets a 409 conflict.
2. A database unique key on `(request, step, cycle)` blocks duplicate decisions even if something slips past the code.
3. A version counter on the request is a last safety net.

The decision, the status change and the audit event are written in a single database transaction, so they succeed or fail together.

**Escalation.** A unique key on `(request, step, cycle)` makes the job idempotent. Escalation only records that a step is late. It does not change the route, and the approver can still act.

## API

The full, interactive list is in Swagger (http://localhost:8080/swagger-ui.html), and a Postman collection is in `docs/`.

| Endpoint | What it does | Who |
|---|---|---|
| `POST /api/auth/login` | Log in, get a token | Anyone |
| `GET /api/auth/me` | Who am I | Logged-in users |
| `POST /api/workflows` | Create a workflow or a new version | Admin |
| `GET /api/workflows` | List workflows | Logged-in users |
| `GET /api/workflows/:id/versions` | Version history | Admin |
| `PATCH /api/workflows/:id/activation` | Turn a workflow on or off for new requests | Admin |
| `POST /api/requests` | Create a draft | Requester |
| `PATCH /api/requests/:id` | Edit own draft or returned request | Owner |
| `DELETE /api/requests/:id` | Delete own draft | Owner |
| `POST /api/requests/:id/submit` | Submit or resubmit | Owner |
| `GET /api/requests` | List requests (filter by `status`, paginate with `page` and `size`) | Logged-in users |
| `GET /api/requests/:id` | Details and timeline | Owner, admin, or the assigned approver |
| `GET /api/approvals/pending` | Requests waiting for me | Manager, Finance |
| `POST /api/requests/:id/decisions` | `APPROVE`, `REJECT` or `RETURN_FOR_CHANGES` | Assigned approver |
| `GET /api/dashboard/summary` | Dashboard numbers | Admin |
| `POST /api/admin/escalations/run` | Mark overdue steps | Admin |

Pagination uses `page` (starting at 0) and `size` (default 20, maximum 100).

Errors always look like `{"error":{"code":"INVALID_TRANSITION","message":"..."}}`.

| Status | Meaning |
|---|---|
| 400 / 422 | Invalid input |
| 401 | Not logged in, or token expired |
| 403 | Logged in but not allowed |
| 404 | Not found, or you are not allowed to know it exists |
| 409 | Duplicate or stale action |

## Documentation

- `docs/er-diagram.md`: database diagram
- `docs/architecture.md`: one-page note on routing, versioning and concurrency
- `docs/ApproveFlow.postman_collection.json`: sample requests

## Known limitations

- **Approvers are assigned by role, not by person.** Anyone with the step's role can act, and the first decision wins.
- **One active workflow per request type.** If there are several, the first active one is used.
- **Escalation is manual.** It runs from the admin dashboard. There is no scheduled job.
- **Conditions only support the `amount` field**, which is the scope the assignment allows.
- **Average completion time** is measured from first submission to final approval, for approved requests only.
- **Local use only.** There is no production deployment setup.
- **Tested on MariaDB 10.** The SQL is written to work on MySQL 8 as well.

Not built (optional extensions from the assignment): CSV export, in-app notifications, Docker Compose, workflow diagram, configurable SLA durations and the outbound webhook stub.