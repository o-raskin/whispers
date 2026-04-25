---
name: dedicated-unit-test-per-class
description: Use when the task requires strict one-class-one-test-file unit coverage. Prevent grouped test suites and require a dedicated <ClassName>Test.java for each listed production class.
---

# Dedicated Unit Test Per Class

## Purpose
Create strict per-class unit test coverage where each target production class gets its own dedicated test file.

## Use this skill when
- the user asks for tests for a list of classes
- the user wants strict one-class-one-test-file coverage
- previous attempts created grouped or umbrella test files
- the user explicitly says each class must have its own `<ClassName>Test.java`

## This skill is specifically for
- direct class ownership of tests
- dedicated test files
- behavior-focused unit tests
- explicit completion tracking per class

## This skill is NOT for
- grouped suite files such as `CommonHttpTest` or `InfrastructureUtilityTest`
- indirect coverage used as a substitute for dedicated test files
- broad package-level “reasonable coverage”
- silent skipping of inconvenient classes

## Non-negotiable rules
1. For each listed production class, create or update exactly one dedicated test file:
   `<ClassName>Test.java`
2. Grouped test files do not count toward completion for this task.
3. “Covered indirectly” does not count toward completion unless the user explicitly allows it.
4. Do not skip any listed class silently.
5. If a class is hard to test, make the smallest safe refactor needed to enable testing, then create the dedicated test file.
6. If a class is truly trivial or passive, still create a dedicated test file unless there is a strong reason not to. If not, explain explicitly why.
7. Test files should focus primarily on the named class they own.

## Mandatory workflow
1. Inspect the listed production classes and the current test suite.
2. Build a checklist mapping every target class to its required `<ClassName>Test.java`.
3. Mark which dedicated files already exist and which are missing.
4. If grouped tests already contain useful assertions, move or split that coverage into the dedicated per-class test files.
5. Add meaningful unit tests for each missing class.
6. Prefer behavior-focused tests over shallow smoke tests.
7. Run relevant tests.
8. If shared code changed, run `mvn test`.
9. Finish with a per-class completion report.

## Test quality rules
- Prefer JUnit 5 and existing repo conventions.
- Reuse shared fakes/helpers where useful.
- Keep test ownership one class per file.
- Cover success paths, validation failures, and fragile behavior where relevant.
- Avoid brittle tests tied to private implementation details.
- Do not create fake value tests only to satisfy file count.
- For wiring/startup/helper classes, write lightweight but meaningful tests if full integration setup is not practical.

## Completion criteria
The task is complete only when every listed production class is accounted for with:
- a dedicated `<ClassName>Test.java`, or
- an explicit, concrete justification why that dedicated file was not created

## Required final report
For each target class, report:
- production class
- dedicated test file
- status: created / updated / already existed / explicitly excluded
- behavior covered
- notes if any