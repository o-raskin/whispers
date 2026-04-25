---
name: maintainability-review
description: Use to review and improve local code maintainability, readability, and structure without changing intended behavior or introducing unnecessary abstractions.
---

# Maintainability Review

## Purpose
Improve code clarity and maintainability with small, behavior-preserving changes.

## Use this skill when
- code is hard to read
- responsibilities are mixed
- methods or classes are too large
- naming is unclear
- duplication is growing
- the task asks for cleaner or more maintainable code

## Workflow
1. Read the affected flow end-to-end.
2. Identify the main maintainability problems.
3. Prefer the smallest changes with the highest readability gain.
4. Preserve behavior and public contract unless explicitly asked otherwise.
5. Prefer extraction, simplification, and clearer naming over rewriting.
6. Run focused tests first.
7. Run `mvn test` if shared code changed.

## Review checklist
- Is responsibility split clearly?
- Are controller, service, and repository roles respected?
- Are names clear and specific?
- Is nesting deeper than necessary?
- Is duplicated logic removable safely?
- Is there accidental complexity from premature abstraction?
- Would a small extraction improve clarity?
- Is custom HTTP or WebSocket flow still easy to follow?

## Rules
- Do not perform repo-wide cleanup unless explicitly asked.
- Do not introduce framework-style abstractions.
- Do not change public request/response or WebSocket shapes unless explicitly required.
- Do not rewrite large areas when a local extraction is enough.
- Match nearby code style and patterns.

## Required final summary
Report:
- maintainability issues found
- changes made
- behavior preserved
- tests run
- remaining debt not addressed