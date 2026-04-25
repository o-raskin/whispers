---
name: readme-updater
description: Use when meaningful repo-level behavior changes require documentation updates to setup, run, test, architecture, environment variables, or supported capabilities.
---

# README Updater

## Purpose
Keep `README.md` useful, accurate, and aligned with the current repository.

## Use this skill when
- setup steps changed
- run/test commands changed
- required env vars changed
- architecture summary changed materially
- supported capabilities changed materially
- local development workflow changed
- a task explicitly asks for README updates

## Do not use this skill when
- the change is too small to matter for repository users
- only internal code structure changed without affecting usage or contributor understanding

## Workflow
1. Inspect the changed code and configs.
2. Check current `README.md`.
3. Update only the sections made inaccurate by the change.
4. Keep the README concise and practical.
5. Prefer verified commands and verified env vars over assumptions.

## Repository-specific priorities
README should stay accurate about:
- project purpose
- backend-only scope
- main technologies
- local PostgreSQL dependency
- required env vars
- how to run tests
- how to start locally
- contract location
- migration behavior if relevant

## Rules
- Prefer code over stale docs.
- Do not turn README into an internal design dump.
- Keep architecture summary high-level.
- Keep setup instructions actionable.
- Remove stale statements when they contradict the implementation.

## Avoid
- overly generic boilerplate
- documenting speculative future work
- copying full internal architecture detail from code when a short summary is enough
- leaving old env vars or commands in place

## Required final summary
Report:
- README sections updated
- why they changed
- what was verified from code/config
- any remaining doc drift not addressed