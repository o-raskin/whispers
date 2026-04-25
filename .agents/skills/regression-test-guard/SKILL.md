---
name: regression-test-guard
description: Use after refactors, bug fixes, performance changes, auth changes, WebSocket changes, or migration-sensitive edits to add or strengthen regression tests that protect existing behavior from accidental breakage.
---

# Regression Test Guard

## Purpose
Protect already-working behavior from accidental breakage by adding or improving regression-focused tests.

## Use this skill when
- a refactor may have changed behavior indirectly
- a bug fix should stay fixed
- auth or token flow changed
- WebSocket message handling changed
- controller binding/parsing changed
- repository behavior changed
- migration-related behavior may regress
- concurrency or performance tuning may affect correctness
- the task explicitly asks for regression coverage

## Difference from junit-test-writer
- `junit-test-writer` focuses on adding useful tests for changed or new behavior
- `regression-test-guard` focuses on protecting previously working behavior that is easy to break by accident

## Mandatory workflow
1. Inspect the changed flow end-to-end:
    - controller
    - service
    - repository/store
    - DTOs
    - tests
    - `swagger.yaml` if transport-visible behavior may be affected
2. Identify regression risks:
    - behavior that used to work and could now break
    - old bug that could reappear
    - edge-case semantics that refactoring could change
    - contract-visible behavior that must remain stable
3. Add focused regression tests for the riskiest behaviors.
4. Prefer high-signal tests over broad noisy coverage.
5. Reuse existing helpers and fakes where possible.
6. Run relevant tests.
7. Run `mvn test` when shared code or infrastructure changed.

## Repository-specific regression targets
Pay special attention to:
- auth login / refresh / logout behavior
- bearer-token and cookie semantics
- WebSocket auth and message handling
- `PRIVATE` chat browser-key-bound behavior
- DTO parsing and controller binding
- service-level business rules
- repository query behavior when persistence code changes
- migration-sensitive startup behavior
- startup wiring in `ChatServer`

## Test rules
- Prefer behavior-oriented regression tests.
- Keep tests small and scenario-specific.
- Cover edge cases only when they are realistic and fragile.
- Avoid asserting internal implementation details.
- Avoid duplicating existing coverage unless the old test is too weak to protect the behavior.
- When fixing a bug, add at least one regression test that would fail without the fix.

## Avoid
- writing generic tests with no clear regression target
- rewriting the whole test suite
- adding brittle tests tied to refactor-sensitive internals
- using integration-heavy setup when a focused service-level regression test is enough

## Required final summary
Report:
- regression risks identified
- tests added or strengthened
- behaviors protected
- tests run
- remaining unprotected risks