---
name: security-validator
description: Use to review or harden auth, token handling, cookies, endpoint protection, validation, secrets handling, and browser-key-bound private chat behavior.
---

# Security Validator

## Purpose
Detect security-sensitive mistakes and reduce risk in auth, transport, validation, and secret handling.

## Use this skill when
- The task touches auth or OIDC.
- Cookie or token behavior changes.
- Endpoint protection changes.
- WebSocket auth changes.
- Input validation or serialization changes.
- `PRIVATE` chat logic changes.
- A security review is requested.

## Review areas
- authentication flow
- authorization checks
- token issuance and validation
- refresh token cookie behavior
- logout behavior
- OIDC configuration handling
- secret/env-var handling
- request validation
- unsafe logging
- WebSocket auth path
- browser-key-bound `PRIVATE` chat enforcement

## Repository-specific checks
Inspect:
- `auth/**`
- `resource/**`
- `websocket/**`
- `chat/**` for `PRIVATE` chat semantics
- `common/**` for transport/auth plumbing
- `ChatServer.java` for wiring and secret-dependent startup
- `swagger.yaml` for auth contract alignment

## Validation rules
- Verify protected endpoints remain protected.
- Verify refresh flow uses intended cookie semantics.
- Verify logout clears or invalidates relevant session/token state as designed.
- Verify no sensitive material is logged.
- Verify request parsing and validation are strict enough for the changed behavior.
- Verify `PRIVATE` chat remains browser-key-bound unless explicit requirements say otherwise.
- Verify new config/env vars are actually required and safely handled.

## Output style
If patching is not explicitly requested:
- review first
- list concrete findings
- rank by severity: high / medium / low
- recommend the smallest safe fixes

If patching is requested:
- make minimal fixes
- avoid unrelated hardening changes
- summarize exactly what risk was reduced

## Avoid
- generic security advice unrelated to the actual code
- speculative vulnerabilities without evidence
- broad redesigns unless explicitly requested

## Required final summary
Report:
- areas reviewed
- confirmed protections
- concrete findings
- fixes made if any
- remaining risks or unverified areas