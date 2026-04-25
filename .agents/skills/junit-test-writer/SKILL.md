---
name: junit-test-writer
description: Use to add or improve JUnit tests for services, migrations, DTO validation, and behavior-sensitive code while following existing repository testing patterns.
---

# JUnit Test Writer

## Purpose
Add useful tests that verify behavior, not implementation trivia.

## Use this skill when
- New logic was added.
- Existing behavior changed.
- A bug fix needs regression coverage.
- Schema/migration changes need test coverage.
- A task explicitly asks for tests.

## Repository-specific priorities
Prefer tests for:
- service logic
- migration behavior
- DTO validation when contract-sensitive
- auth logic when practical
- repository behavior only when worth the setup cost

## Inspect first
- existing tests in the same domain
- shared helpers such as `src/test/java/com/oraskin/chat/TestSupport.java`
- service and DTO code being exercised
- migration tests if schema changed

## Test-writing rules
- Prefer behavior-oriented tests.
- Match existing JUnit 5 style in the repo.
- Reuse existing fakes/helpers before creating new ones.
- Avoid brittle tests tied to internal implementation details.
- Keep tests readable and focused.
- One test should verify one behavior or scenario family.
- Add regression coverage for the bug being fixed.
- Avoid over-mocking when a simple fake is clearer.

## Preferred targets
1. service methods
2. migration/changelog logic
3. DTO/validation behavior
4. repository logic only when setup is justified
5. shared infrastructure only when behavior is stable and important

## Avoid
- asserting private implementation details
- snapshot-style assertions without value
- duplicating existing coverage
- forcing integration-style setup for a simple unit test

## Validation
- Run the new tests.
- Run relevant surrounding tests.
- Run `mvn test` if shared code changed or if confidence is otherwise low.

## Required final summary
Report:
- behaviors covered
- tests added or changed
- helpers/fakes used
- tests run
- remaining gaps