---
name: agents-md-structure-sync
description: Use when repository structure, package boundaries, entry points, workflow, or recurring agent guidance has changed and AGENTS.md must be updated to stay accurate and useful.
---

# AGENTS.md Structure Sync

## Purpose
Keep `AGENTS.md` accurate, concise, and aligned with the real repository after structural or workflow changes.

## Use this skill when
- package or domain structure changed
- important directories were added, removed, or repurposed
- entry points changed
- startup wiring changed materially
- build, test, or run workflow changed
- contract or migration workflow changed materially
- repeated Codex mistakes revealed missing repo guidance
- a task explicitly asks to update `AGENTS.md`

## Do not use this skill when
- the code change is too small to affect repository guidance
- only local implementation details changed without affecting repo-level instructions
- the desired change belongs in a narrower subdirectory-level `AGENTS.md` instead of the repo root file

## Mandatory workflow
1. Inspect the current repository structure and the current `AGENTS.md`.
2. Identify which parts of `AGENTS.md` are now inaccurate, missing, or misleading.
3. Prefer updating only the sections affected by the real repository change.
4. Keep `AGENTS.md` concise, practical, and durable.
5. Remove stale guidance when code and docs conflict.
6. If a repeated mistake is being fixed, codify the rule clearly enough that future runs will avoid it.

## Repository guidance priorities
Keep `AGENTS.md` accurate about:
- project scope
- backend-only or multi-part repo boundaries
- entry points
- important packages and their responsibilities
- controller/service/repository boundaries
- public contract location
- migration workflow
- build/test/run commands
- required validation steps
- important do/don’t rules for agents

## Rules
- Treat source code as the source of truth.
- Keep `AGENTS.md` short enough to stay useful.
- Prefer explicit repo-specific instructions over generic engineering advice.
- Do not restate obvious best practices unless they prevent a recurring mistake here.
- When structure changes, update ownership/boundary guidance accordingly.
- When workflow changes, update validation and command guidance accordingly.
- If guidance applies only to one subtree, consider whether it belongs in a more local `AGENTS.md`.

## Check for
- stale package descriptions
- outdated entry points
- outdated commands
- missing new domain boundaries
- missing repo-specific warnings
- contract workflow drift
- migration workflow drift
- instructions that are now too generic or too verbose
- repeated mistakes that should be codified

## Avoid
- rewriting all of `AGENTS.md` without need
- adding generic "clean code" advice
- duplicating README content unnecessarily
- keeping outdated guidance because it sounds useful
- growing the file with one-off task details that do not generalize

## Required final summary
Report:
- what changed in the repository
- what sections of `AGENTS.md` were updated
- what stale guidance was removed
- what new recurring guidance was added
- any remaining areas where more local `AGENTS.md` files may be useful