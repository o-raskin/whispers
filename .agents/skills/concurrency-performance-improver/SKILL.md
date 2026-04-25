---
name: concurrency-performance-improver
description: Use to improve throughput and reduce contention in shared runtime code such as WebSocket dispatch, session registries, and concurrent state handling while preserving correctness.
---

# Concurrency Performance Improver

## Purpose
Improve concurrency behavior safely by reducing contention and preserving correctness.

## Use this skill when
- shared mutable state becomes a bottleneck
- locks or synchronization are too broad
- WebSocket dispatch throughput is poor
- session registry or presence handling is under contention
- the task asks for concurrency or throughput improvements

## Mandatory workflow
1. Identify the concurrent flow and shared state.
2. Explain the current concurrency model before editing.
3. Inspect:
    - calling path
    - shared data structures
    - locking/synchronization scope
    - thread-safety assumptions
    - tests covering concurrency-sensitive behavior
4. Optimize for correctness first, then throughput.
5. Reduce contention without weakening invariants.
6. Keep the change as local as possible.
7. Summarize tradeoffs clearly.

## Candidate improvements
- narrowing synchronized scope
- using more appropriate concurrent collections
- reducing unnecessary shared writes
- avoiding redundant work inside critical sections
- separating read-heavy and write-heavy paths
- removing accidental contention hotspots

## Rules
- Do not change concurrency semantics casually.
- Do not replace simple correct code with clever code unless the benefit is real.
- Do not sacrifice correctness for speculative speed.
- Add tests when behavior becomes easier to break.
- Be explicit about memory-visibility or ordering assumptions if relevant.

## Repository-specific attention areas
Inspect likely hotspots in:
- `websocket/**`
- `user/**` session/presence handling
- `common/**` shared runtime infrastructure
- startup/shared singleton wiring if relevant

## Validation
- Run relevant tests.
- Add focused regression tests when changing thread-safety-sensitive behavior.
- Run `mvn test` if shared runtime code changed.

## Required final summary
Report:
- concurrency bottleneck or contention source
- change made
- correctness constraints preserved
- tests run
- remaining risk or missing load validation