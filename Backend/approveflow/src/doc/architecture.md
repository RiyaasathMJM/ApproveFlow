# Architecture Note

## Overview

ApproveFlow is a Spring Boot REST API with a React front end. Approval rules live in the database as data. The approval engine is generic: it contains no expense-specific or leave-specific code.

```
React (Vite)  --/api-->  Controller  ->  Service  ->  Repository  ->  MySQL / MariaDB
                         (HTTP only)     (rules,       (JPA)           (constraints,
                                         transactions)                  Flyway migrations)
```

Code is organised by feature (`auth`, `workflow`, `request`, `approval`, `audit`, `escalation`, `dashboard`, `security`). Each feature has its own controller, service, repository and entities.

## Routing

A workflow version contains ordered steps. A step has an approver role, a due time (`due_hours`) and an optional condition stored as JSON, for example `{"field":"amount","op":">","value":10000}`.

- On submission, the engine picks the first step whose condition matches the request payload.
- On approval, it picks the next step (higher sequence) whose condition matches. If there is none, the request becomes APPROVED.
- Reject ends the request as REJECTED. Return sends it back to the requester as RETURNED.
- The first step of a workflow must be unconditional, so every request has a valid path.

Because routing is data, a new workflow (for example purchase requests) needs new rows, not new code.

## Versioning

- `workflow_definitions` holds the name, request type, active flag and a pointer to `current_version_id`.
- Editing a workflow inserts a new `workflow_versions` row with its own `workflow_steps`. Old versions are never changed.
- On first submission a request stores `workflow_version_id` and keeps it for life, including after a return and resubmit.
- Activating or deactivating a workflow only affects new submissions.

Result: a request in progress always follows the rules it was submitted under, even if an admin changes the workflow afterwards.

## State machine

| Current state | Action | Next state |
|---|---|---|
| DRAFT | Submit | PENDING_APPROVAL (cycle 1) |
| PENDING_APPROVAL | Approve, more steps | PENDING_APPROVAL (next step) |
| PENDING_APPROVAL | Approve, last step | APPROVED |
| PENDING_APPROVAL | Reject | REJECTED |
| PENDING_APPROVAL | Return for changes | RETURNED |
| RETURNED | Edit and resubmit | PENDING_APPROVAL (cycle + 1) |

APPROVED and REJECTED are terminal. Status alone does not identify the active step, so a request also stores `current_step_id`, `workflow_version_id` and `cycle`. Every transition is validated on the server, never trusted from the client.

## Authorization

- JWT in the `Authorization` header. The token carries only the user id; roles are read from the database on every request.
- Role checks use method security. Ownership, assigned-step and self-approval rules are checked in the service layer.
- 401 means not authenticated, 403 means not allowed, 404 means the record does not exist or is not visible to the caller.
- A decision is accepted only if the caller holds the current step's approver role and is not the request owner.

## Concurrency

Two approvers may click Approve on the same request at the same moment. Only one decision may win. Three layers:

1. **Row lock.** The decision transaction loads the request with a plain `SELECT ... FOR UPDATE` (native query, so it works on both MySQL and MariaDB). A second decision on the same request waits, then sees the committed state (for example APPROVED) and fails with 409.
2. **Unique constraint** on `approval_decisions (request_id, step_id, cycle)`. Even if the code path were bypassed, the database refuses a second decision for the same step and cycle.
3. **Optimistic lock** (`requests.version`, JPA `@Version`) as a final backstop against stale writes.

The decision row, the status change and the audit event are written in one transaction, so they all succeed or all roll back. Lock failures (timeout or deadlock) are mapped to a 409 response instead of a server error.

## Audit and escalation

- `audit_events` is append-only (columns are not updatable). It records request id, actor, action, previous status, next status, comment and timestamp for submit, approve, reject, return, resubmit and escalate. System events have no actor.
- Each active step has `current_step_due_at`. The escalation job finds overdue pending requests and inserts one `escalation_records` row per request, step and cycle, plus one audit event.
- The unique key makes the job idempotent: running it twice creates one record. Escalation never changes status or route, and an overdue step stays actionable.

## Testing

12 integration tests (T01 to T12) run against a real test database (`approveflow_test`). The app starts on a random port, tests call the HTTP API, and assertions check database state (statuses, cycles, decisions, audit events, escalation records). The concurrency test fires two simultaneous approvals and asserts exactly one committed decision. Run them with one command: `./mvnw test`.