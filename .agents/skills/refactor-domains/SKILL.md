---
name: refactor-domains
description: Use for larger structural refactors across domain/package boundaries. Re-establishes clean ownership, preserves contracts, and avoids mixing transport, business, and persistence concerns.
---

# Refactor Domains

## Purpose
Reshape domain boundaries safely when logic has drifted across packages or responsibilities.

## Use this skill when
- responsibilities are mixed across domains
- business logic leaked into controllers or repositories
- package boundaries no longer reflect actual ownership
- a larger structural refactor is explicitly requested

## Do not use this skill when
- a local refactor is enough
- the task only needs naming cleanup
- the task is mainly about performance tuning

## Mandatory workflow
1. Build an architecture map first.
2. Identify current ownership problems:
    - transport mixed with business logic
    - business logic mixed with persistence
    - auth concerns mixed with chat logic
    - shared code living in the wrong place
3. Define the target ownership model before editing.
4. Preserve public behavior unless explicitly asked otherwise.
5. Move code in small safe steps.
6. Update tests as boundaries move.
7. Update `swagger.yaml` only if the public contract actually changes.

## Repository-specific boundaries to preserve
- controllers in `resource`
- business logic in services inside domain packages
- persistence in repositories/stores
- reusable HTTP/WebSocket/runtime infrastructure in `common`
- startup wiring in `ChatServer`

## Rules
- Prefer moving existing code over rewriting it.
- Keep public DTOs stable unless explicitly changing the contract.
- Avoid creating fake shared abstractions just to “clean up” package layout.
- Do not merge unrelated concerns into `common`.
- Keep the composition root understandable.

## Validation
- Run focused tests as you move logic.
- Run `mvn test` for substantial boundary changes.
- Report any package-level risks or incomplete moves.

## Required final summary
Report:
- ownership problems found
- target boundary model
- files/packages moved or restructured
- public contract impact
- tests run
- remaining architectural debt