---
name: liquibase-safe-migration
description: Use for schema changes. Adds append-only Liquibase migrations, updates master changelog, keeps SQL explicit, and checks compatibility and test coverage.
---

# Liquibase Safe Migration

## Purpose
Make schema changes safely and consistently using the repository's append-only Liquibase workflow.

## Use this skill when
- A table, column, index, constraint, or data migration is needed.
- Repository changes require schema support.
- Query performance requires a schema/index update.

## Mandatory workflow
1. Inspect:
    - affected repositories/stores
    - relevant services
    - existing changelog files
    - `db.changelog-master.xml`
    - migration-related tests
2. Create a new changelog file under:
    - `src/main/resources/db/changelog/ddl`
3. Include it from:
    - `src/main/resources/db/changelog/db.changelog-master.xml`
4. Keep SQL explicit and readable.
5. Preserve append-only migration history.
6. Add or update tests when practical.
7. Summarize compatibility assumptions and risk.

## Migration rules
- Never rewrite historical migrations unless the task is specifically migration repair.
- Prefer additive changes when possible.
- Be careful with destructive operations.
- If a change can break startup or old code paths, call that out clearly.
- Name the migration file consistently with existing repo patterns.
- If indexes are added, explain what query/path they support.
- If constraints are added, confirm application code behavior still matches.

## Check for
- startup-time migration impact
- nullability changes
- default value behavior
- backfill needs
- index usefulness
- lock risk on large tables
- contract implications
- test data implications

## Also inspect
- whether repository SQL needs to change
- whether DTO/service validation should change
- whether README or docs need updates

## Validation
- Run migration-related tests.
- Run relevant service/repository tests.
- Run `mvn test` when the change has broader impact.

## Required final summary
Report:
- schema change made
- migration file added
- master changelog updated or not
- code changes required by migration
- tests run
- compatibility or rollout risks