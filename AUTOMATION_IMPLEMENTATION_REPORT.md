# Health Hand Automation Integration — Implementation Report

## Architecture

`Android admin → authenticated Health Hand backend → durable SQLite event queue → dedicated n8n production webhook`

The APK contains no n8n URL or credential. n8n delivery is server-side only.

## Backend

Implemented in `portal_api.py`:

- persistent `automation_events` queue;
- unique event IDs and idempotent enqueue;
- `pending`, `delivered`, `failed` states;
- attempt count, sanitized error code, next attempt time and exponential backoff;
- one-shot worker: `python3 portal_api.py automation-worker --once`;
- authenticated summary, recent-events, retry and production-test endpoints;
- event emission after successful service, employee, shift, booking and registration mutations;
- business mutations remain successful when n8n is unavailable.

### TDD evidence

Worker CLI regression:

- RED: worker command attempted to bind the HTTP server and failed with `OSError: [Errno 98] Address already in use`; suite result `1 failed, 19 passed`.
- GREEN: worker dispatches once, prints a parseable summary and exits; suite result `20 passed`.

Final command:

```bash
python3 -m pytest tests -q
```

Final hardened result on 2026-07-12:

```text
26 passed in 14.52s
```

`python3 -m py_compile portal_api.py` and `git diff --check` also passed.

## n8n

The active `Health Hand — Unified AI Automation` workflow now includes:

- `Backend Events Webhook` at `/webhook/health-hand-backend-events`;
- contract validation for `event_id`, `event`, and `data`;
- exact `X-Event-Id == body.event_id` validation;
- idempotency commit only after successful processing;
- duplicate and accepted response branches;
- Telegram operational notification before idempotency commit, backed by an encrypted live n8n credential;
- no decrypted credential values in repository JSON.

The backend-events webhook is not publicly reachable: nginx returns HTTP 404 for the exact path. The backend calls n8n only over `127.0.0.1:5678`.

Production verification:

- localhost webhook returned HTTP 200 with exact acknowledgement;
- repeating the same event ID over localhost returned `duplicate: true`;
- hardened backend production test event `f932ea4591e7f8a1261c4d5bf71b8992` was delivered on attempt 1;
- corresponding n8n execution `40` finished with `status: success`;
- real authenticated `service_updated` mutation created pending outbox event `3634f52bcb5433335ca7fd68cd2cc2c5`, then the worker delivered it on attempt 1 and n8n execution `41` finished with `status: success`.
- final Telegram-enabled production test event `b128df5332b69055d27dabdcee436536` was delivered on attempt 1 and n8n execution `43` finished with `status: success`.

Queue hardening includes an atomic lease token, stale-lease recovery, max-attempt enforcement, no SQLite write lock during HTTP, exact acknowledgement validation, payload-size rejection, transactional enqueue with each business mutation, PII minimization and 30-day delivered-event retention.

## Production backend

- backup of `portal_api.py` and `portal.db` created before deployment;
- backend service restarted and is active;
- `HH_N8N_WEBHOOK` configured server-side;
- `health-hand-automation-worker.timer` enabled and active every 60 seconds;
- public authenticated summary returned `configured: true`, `pending: 0`, `failed: 0`.

## Android

Implemented:

- Retrofit contracts and Moshi models for automation summary/events/retry/test;
- repository and ViewModel actions;
- Ukrainian `Авто` tab;
- configured state, queue counters, last success/failure, recent events, retry and production-test controls;
- no raw webhook URLs, secrets, event IDs or internal exception details in normal UI.

Preliminary local verification:

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon
```

Result:

```text
BUILD SUCCESSFUL
```

## Security scan

Added-lines scan results:

```text
hardcoded_secret=0
shell_injection=0
dangerous_eval=0
unsafe_pickle=0
sql_format=0
```

Workflow export contains credential references only and no credential `data` fields.

## Remaining release gate

- independent fail-closed reviewers;
- verified commit and push;
- GitHub Actions build;
- artifact download/hash verification;
- GitHub Release and browser HTTP verification.
