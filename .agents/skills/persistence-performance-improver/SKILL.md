---
name: persistence-performance-improver
description: Use to improve repository and database performance by analyzing SQL paths, round trips, indexing, mapping overhead, and schema support without moving business logic into persistence.
---

# Persistence Performance Improver

## Purpose
Improve persistence-layer performance with clear, measurable, low-risk changes.

## Use this skill when
- repository code is slow
- SQL queries are inefficient
- too many DB round trips occur
- indexes are missing or weak
- schema changes are needed to support query performance
- a performance review of persistence is requested

## Mandatory workflow
1. Identify the actual slow path or suspected path.
2. Inspect:
    - repository/store code
    - calling service code
    - relevant DTO mapping
    - existing SQL
    - current indexes and migrations
3. Explain the current bottleneck before changing code.
4. Prefer simple improvements with obvious value.
5. If schema/index changes are needed, use the Liquibase workflow.
6. Keep business rules in services, not SQL.
7. Summarize tradeoffs.

## Types of improvements to consider
- reducing redundant queries
- reducing round trips
- narrowing selected columns where appropriate
- adding targeted indexes
- simplifying joins where possible
- improving pagination/query shape
- avoiding wasteful mapping or repeated lookups
- caching only if justified and safe

## Rules
- Do not move business logic into repositories for speed.
- Do not add speculative indexes without tying them to query patterns.
- Keep SQL explicit and readable.
- Prefer measured or strongly evidenced improvements.
- Preserve correctness first.

## Also check
- whether a migration is required
- whether query changes affect contract-visible ordering/filtering behavior
- whether tests need updates
- whether startup migration impact changes

## Validation
- Run relevant tests.
- Run migration tests if schema/index changes are added.
- Run `mvn test` if shared code changed.

## Required final summary
Report:
- bottleneck identified
- improvement made
- SQL/schema changes made
- migrations added or not
- tests run
- expected tradeoffs or limitations