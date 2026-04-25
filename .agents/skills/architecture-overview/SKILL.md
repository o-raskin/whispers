---
name: architecture-overview
description: Use when you need to understand the repository structure, entry points, package boundaries, request flow, startup wiring, migrations, and public contract before making changes.
---

# Architecture Overview

## Purpose
Build an accurate mental model of this backend before making changes.

## Use this skill when
- The task touches multiple packages or domains.
- The task asks for repo analysis or architecture explanation.
- The task is large enough that incorrect assumptions are risky.
- You are about to perform a structural refactor.
- You need to explain how a request flows through the system.

## Repository-specific goals
Understand:
- application entry points
- startup wiring in `ChatServer`
- controller/service/repository boundaries
- HTTP and WebSocket flow
- auth flow
- migration layout
- public contract location
- testing layout
- runtime dependencies and required env vars

## Inspect at minimum
- `src/main/java/com/oraskin/App.java`
- `src/main/java/com/oraskin/ChatServer.java`
- `src/main/java/com/oraskin/resource/**`
- `src/main/java/com/oraskin/auth/**`
- `src/main/java/com/oraskin/chat/**`
- `src/main/java/com/oraskin/user/**`
- `src/main/java/com/oraskin/websocket/**`
- `src/main/java/com/oraskin/common/**`
- `src/main/resources/db/changelog/**`
- `src/test/java/com/oraskin/**`
- `swagger.yaml`
- `README.md`
- `pom.xml`
- `Dockerfile`
- `docker-compose.yml`

## Required output
Provide:
1. short project summary
2. entry points
3. main package/domain map
4. request/response flow summary
5. auth flow summary
6. persistence and migration summary
7. public contract summary
8. test layout summary
9. major risks, gaps, or inconsistencies
10. what was verified vs inferred

## Rules
- Prefer code over README if they conflict.
- Do not invent architecture that is not present.
- Call out uncertainty explicitly.
- Distinguish framework behavior from custom infrastructure.
- Mention whether behavior is verified from code, tests, or docs.

## Delivery style
- Be factual and concise.
- Use bullet points only when they improve scanability.
- Separate verified facts from assumptions.