---
name: swagger-updater
description: Use whenever public HTTP or WebSocket behavior changes. Updates swagger.yaml to match implementation and verifies request, response, auth, and command shapes.
---

# Swagger Updater

## Purpose
Keep `swagger.yaml` aligned with the actual public contract.

## Use this skill when
- An endpoint is added, removed, or changed.
- A request or response DTO changes.
- A public field changes.
- Auth behavior changes.
- A WebSocket command or event shape changes.
- A task explicitly asks to update API docs.

## Mandatory workflow
1. Inspect implementation first:
    - controllers
    - DTOs
    - relevant services
    - auth handling
    - WebSocket command/event logic
2. Identify all public contract changes.
3. Update `swagger.yaml` in the same change.
4. Verify required fields, optional fields, names, enums, and auth descriptions match code.
5. Mention any contract ambiguity explicitly.

## Repository-specific rules
- Treat `swagger.yaml` as required for public contract changes.
- Do not leave implementation and contract out of sync.
- For WebSocket-related behavior, document command/event shapes as represented in repo conventions.
- If auth behavior changes, update both endpoint docs and any related security description.

## Check for
- path changes
- HTTP methods
- request body shape
- response shape
- status codes
- enum values
- auth requirements
- cookie-related behavior
- WebSocket message types
- field renames or removals

## Avoid
- documenting behavior that is not implemented
- keeping stale examples
- silently changing contract names
- assuming DTO changes are internal if they cross a transport boundary

## Validation
- Compare docs directly against code.
- If practical, confirm existing naming patterns in `swagger.yaml`.

## Required final summary
Report:
- contract changes detected
- `swagger.yaml` sections updated
- implementation areas checked
- any ambiguity or unverified behavior