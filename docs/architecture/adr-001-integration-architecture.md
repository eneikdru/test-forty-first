# ADR 001: EIOS, Messengers, and Teachbase Integration Architecture

## Status
Approved

## Context
Our Educational Center Knowledge Base needs to integrate with three external systems:
1. **EIOS (Electronic Information and Educational Environment / ЭИОС)**: Used for single sign-on (SSO) authorization, user role synchronization (Admin, Content Manager, Teacher, Student/Ordinator/Aspirant), and exporting user analytics.
2. **Messengers**: Used for automatic notifications about document updates and important system events.
3. **Teachbase**: An external LMS containing educational materials that need to be indexed for the Knowledge Base's unified search.

We need a clear, secure, and resilient architectural specification detailing:
- EIOS Single Sign-On (SSO) authentication flows and role sync protocols.
- Messenger webhook structures, payload verification, retry, and rate-limiting strategies.
- An analytical comparison of polling vs. webhooks for indexing Teachbase materials, with a final decision and implementation details.

---

## 1. EIOS (Electronic Information and Educational Environment) Integration

### 1.1 Single Sign-On (SSO) Authentication Flow
To allow users to sign in using their corporate EIOS credentials, we will implement an OAuth 2.0 / OpenID Connect (OIDC) Authorization Code Flow.

```
+------------+             +------------------------+             +------------------+
| User Agent |             | Knowledge Base Backend |             | EIOS Auth Server |
+------------+             +------------------------+             +------------------+
      |                                |                                   |
      | 1. Click "Login with EIOS"     |                                   |
      |------------------------------->|                                   |
      |                                |                                   |
      | 2. Redirect to EIOS Login      |                                   |
      |<-------------------------------|                                   |
      |                                |                                   |
      | 3. Authenticate & Approve      |                                   |
      |------------------------------------------------------------------->|
      |                                                                    |
      | 4. Redirect with Auth Code                                         |
      |<-------------------------------------------------------------------|
      |                                                                    |
      | 5. GET /api/v1/auth/callback?code=CODE                             |
      |------------------------------->|                                   |
      |                                | 6. POST /oauth/token (code)       |
      |                                |------------------>|
      |                                |                                   |
      |                                | 7. Return Access & ID Tokens      |
      |                                |<------------------|
      |                                |                                   |
      |                                | 8. GET /userinfo                  |
      |                                |------------------>|
      |                                |                                   |
      |                                | 9. Return Profile & Roles         |
      |                                |<------------------|
      |                                |                                   |
      | 10. Establish Session / JWT    |                                   |
      |<-------------------------------|                                   |
```

- **Inbound Endpoints**:
  - `/api/v1/auth/login/eios`: Triggers redirect to EIOS Authorization Endpoint.
  - `/api/v1/auth/callback`: Handles the redirect back from EIOS containing the authorization `code` and `state` parameters.
- **Security Controls**:
  - **State Parameter**: A cryptographically secure, non-guessable random string stored in the user's session to prevent Cross-Site Request Forgery (CSRF).
  - **PKCE (Proof Key for Code Exchange)**: Mandatory code challenge and code verifier to prevent authorization code interception attacks.
  - **Secure Token Storage**: Received Access and ID tokens are stored securely in the backend, never exposed directly to the client browser.

### 1.2 User Role Synchronization Protocol
EIOS is the source of truth for user identities and roles.
- **Just-In-Time (JIT) Sync**: Upon successful SSO login, the backend reads user roles from the ID token / UserInfo response and updates the local user database.
- **Scheduled Sync (Incremental & Full)**:
  - To handle role updates for offline users or administrative adjustments, a background sync service runs daily at 02:00 AM (server local time).
  - Uses an EIOS SCIM (System for Cross-domain Identity Management) or a bulk query API `/api/v1/users/delta?since=TIMESTAMP`.
  - Maps EIOS roles to local Knowledge Base roles:
    - `eios_admin` -> `ROLE_ADMIN`
    - `eios_methodist` / `eios_editor` -> `ROLE_CONTENT_MANAGER`
    - `eios_teacher` / `eios_supervisor` -> `ROLE_TEACHER`
    - `eios_student` / `eios_ordinand` -> `ROLE_STUDENT`

---

## 2. Messenger Integration (Notifications & Webhooks)

When knowledge base materials are updated, notifications must be sent out. Also, we will design bidirectional communication via messenger webhooks where users can query the Knowledge Base.

### 2.1 Outbound Notification Delivery (API Integrations)
- Notifications are queued via a transactional outbox table in the database to prevent split-brain issues.
- Worker threads pick up notifications and call the external Messenger APIs.

### 2.2 Webhook Payload Structure
For inbound queries from messengers (e.g., automated assistant bot):
- **Endpoint**: `/api/v1/integration/messenger/webhook`
- **Method**: `POST`
- **Payload Schema**:
```json
{
  "update_id": 987654321,
  "message": {
    "message_id": 12345,
    "from": {
      "id": 555000123,
      "is_bot": false,
      "first_name": "Иван",
      "username": "ivan_doctor",
      "language_code": "ru"
    },
    "chat": {
      "id": -1001928374,
      "type": "group",
      "title": "Кафедра Эпидемиологии"
    },
    "date": 1785638400,
    "text": "/search ФГОС ординатура 2026"
  }
}
```

### 2.3 Verification & Security
- **HMAC Signatures**: Each incoming webhook request must contain an authorization signature in the header (e.g., `X-Messenger-Signature-256`).
  - Signature is computed as: `HMAC-SHA256(SecretKey, RequestBody)`.
  - The backend verifies this signature before processing the payload to prevent spoofing and tampering.
- **IP Whitelisting**: If supported by the messenger provider, incoming traffic is restricted to specific IP ranges.

### 2.4 Retry and Rate-Limiting Strategies
- **Inbound Webhook Rate Limiting**: Limit to 100 requests per minute per chat ID using a sliding-window token bucket algorithm to prevent Denial of Service (DoS).
- **Outbound API Rate Limiting**: Messenger APIs enforce strict limits (e.g., 30 messages/second). The outbound delivery queue will use a token bucket rate limiter matched to these boundaries.
- **Retry Strategy**:
  - Network timeouts or `5xx` errors from messengers trigger exponential backoff with jitter:
    - $Interval = Base \times 2^{attempt} + Jitter$
    - Max attempts: 5. After that, messages are moved to a Dead Letter Queue (DLQ) for operator review.

---

## 3. Teachbase Integration Strategy (Polling vs. Webhook)

Teachbase contains educational course content that must be indexable for unified search. We analyzed two distinct patterns to keep our index updated.

### 3.1 Comparison of Patterns

| Attribute | Polling Strategy | Webhook Strategy |
| :--- | :--- | :--- |
| **Real-Time Responsiveness** | Low. Synchronization depends on poll interval (e.g., hourly/daily). | High. Instantaneous updates when a document is modified in Teachbase. |
| **Server/API Overhead** | High API overhead if frequency is high; low if daily. Requires calling pagination/list APIs even if nothing changed. | Extremely low. Only triggered when actual change events occur. |
| **Complexity of Setup** | Low. Simple cron job or Spring Scheduled task querying `/endpoint?updated_since=...`. | Moderate to High. Requires exposing a public internet endpoint, registering webhook URLs in Teachbase, and handling verification. |
| **Reliability under Network Outages** | Very resilient. If a poll fails, the next poll covers everything since the last successful sync timestamp. | Vulnerable. If the Knowledge Base is down, missed webhooks require a complex replay or manual reconciliation fallback. |
| **Data Integrity / Reconciliation** | High. Naturally reconciles state by pulling all records or delta records. | Lower. Network drops, race conditions, or duplicate hook deliveries can lead to index drift unless paired with periodic polling. |

### 3.2 Decision and Rationale
We decide to implement a **Hybrid Strategy**, with **Scheduled Polling as the Primary Sync Mechanism** and **Webhooks as an Optional Optimization** (to be enabled once stable).

**Rationale**:
1. **Reliability and Data Consistency**: In educational systems, completeness and accuracy of search indexes (e.g., looking up current working programs, exams, or standard guidelines) are critical. Webhook-only architectures are prone to state desynchronization if requests are dropped, rate-limited, or if the server experiences downtime during a webhook event.
2. **Frequency Constraints**: Working programs, regulations, and curricula do not change second-by-second. A latency of a few hours is highly acceptable (Kano Performance, not Delighter). Therefore, the instantaneous benefit of webhooks is outweighed by the robust error-recovery of a timestamp-based poll.
3. **Simplicity and Reduced Waste**: Creating a complex webhook delivery mechanism with signature verification, retries, and duplicate detection for an external platform adds significant initial engineering waste (Muda) compared to a clean, daily background delta sync.

### 3.3 Implementation Details
- **Sync Schedule**: Background worker runs every hour for incremental changes, and a full-sync runs weekly at Saturday 11:00 PM.
- **Incremental Polling Endpoint**: `/api/v1/courses?updated_after={last_sync_timestamp}&page={page_num}`
- **State Persistence**: The timestamp of the last successful synchronization is persisted atomically in an integration state table:
  - `UPDATE integration_state SET last_successful_sync = ? WHERE system_name = 'TEACHBASE' AND last_successful_sync = ?` (using optimistic concurrency locking to prevent dual-worker races).
- **Failure Recovery**: If a poll fails, the background scheduler backs off and retries in 15 minutes, maintaining the original `{last_sync_timestamp}` until a successful run.

---

## 4. Consequences and Next Steps
- **Next Role**: The Database Specialist (BARCAN-TAG-08) is requested to create the necessary schema additions (`integration_state` tracking table, `audit_log` structure, and `messenger_outbox` table) in the database migrations.
- **Backend Developers**: Implement the OAuth2 client config, webhook verification filters, and background pollers according to these strict rules.
