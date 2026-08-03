# Repository Execution Boundary and Runtime Contract

Generated deterministically at project bootstrap - not by a Jules session. This documents
the same structure every Eneik-generated project starts from; it does not depend on the
client wishlist.

## Identity

- Project: test-forty-first
- Repository: https://github.com/eneikdru/test-forty-first

## Setup, run, test

Detected by manifest file presence (matches .github/workflows/ci.yml):
- `pom.xml` present -> Java/Maven backend: `mvn test` to verify, `mvn spring-boot:run` (or the
  project's documented entrypoint) to run.
- `package.json` present -> Node/frontend: `npm ci`, `npm test --if-present`, `npm run build --if-present`.
- `requirements.txt` present -> Python service: `pip install -r requirements.txt`, `pytest`.

Until a role's first PR introduces one of these manifests, that boundary is not yet
established - implementers should create the manifest as part of their first real change,
not assume one already exists.

## Backend / frontend / handoff boundaries

- Backend code belongs under a top-level backend source root (e.g. `src/main/java` for Java).
- Frontend code belongs under `frontend/` (e.g. `frontend/src` for a Svelte/Node frontend).
- Cross-cutting docs belong under `docs/`.
- New code must be placed under the boundary matching its role; do not mix backend and
  frontend concerns in the same source root.
