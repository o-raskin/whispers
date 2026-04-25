---
name: safe-refactor
description: Use for local refactors and behavior-preserving code cleanup. Keeps changes small, preserves contracts, respects existing architecture, and validates with tests.
---

# Safe Refactor

## Purpose
Perform the smallest safe refactor that improves structure without changing intended behavior.

## Use this skill when
- The task asks to refactor or clean up existing code.
- A file or class is getting hard to maintain.
- Logic should be extracted without changing public behavior.
- A small structural improvement is needed before a feature change.

## Do not use this skill when
- The task requires domain/package restructuring across major boundaries.
- The task intentionally changes public API behavior.
- The task is primarily about performance tuning.
- The task is primarily about schema or contract changes.

## Workflow
1. Read the affected flow end-to-end:
    - controller
    - service
    - repository/store
    - DTOs
    - tests
    - `swagger.yaml` if public shapes may be affected
2. State the refactoring target in one sentence.
3. Prefer extraction and simplification over rewrite.
4. Preserve current behavior unless the task explicitly allows behavior changes.
5. Keep changes local.
6. Run focused tests first.
7. Run `mvn test` if shared infrastructure or common code changed.
8. Summarize what changed and what was intentionally preserved.

## Refactoring rules
- Keep controllers thin.
- Keep business logic in services.
- Keep repository code focused on persistence and mapping.
- Preserve existing package boundaries unless explicitly asked otherwise.
- Prefer existing naming patterns and code style.
- Avoid introducing new abstractions unless they remove real complexity.
- Do not introduce frameworks or utility layers unnecessarily.
- Do not change request/response shapes unless explicitly requested.

## Allowed improvements
- extract helper methods
- extract small classes
- reduce duplication
- improve naming locally
- split oversized methods
- isolate parsing/validation from core logic
- improve testability without changing semantics

## Avoid
- broad rewrites
- repo-wide cleanup
- package reshuffling
- speculative abstractions
- public contract drift
- hidden behavior changes

## Validation
- Run the narrowest relevant tests first.
- If the change touches shared code, run `mvn test`.
- Call out any untested area explicitly.

## Required final summary
Report:
- refactoring goal
- files changed
- behavior intentionally preserved
- tests run
- remaining risks