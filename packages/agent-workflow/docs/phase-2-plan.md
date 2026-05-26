# Agent Workflow Phase 2 Plan

Status: implemented as the phase-2 skeleton in this package.

## Goal

Build a non-invasive multi-agent collaboration layer for OpenCode:

- normal OpenCode tasks stay normal by default;
- workflow starts only when explicitly requested or confirmed;
- one workflow run can fan out into multiple agent branches;
- a waiting node does not freeze unrelated ready branches;
- workflow deviations are explicit, reasoned, and approval-gated by risk;
- state survives context compaction through persistent run state and a richer capsule.

## In Scope

Phase 2 intentionally implements the stable skeleton first:

1. Activation policy
   - `workflow_route` can recommend a workflow without starting it.
   - `workflow_run` is described as explicit-or-confirmed only.
   - system prompt tells the main agent not to route ordinary tasks into workflow by default.

2. Static fan-out and reduce
   - the default code-change workflow now runs `code_scout`, `test_scout`, and `history_scout` in parallel after `planner`.
   - `scout_join` auto-completes when all scout branches have completed or been skipped.
   - `synthesizer` merges the joined branch results before risk review.

3. Local waiting
   - a node can wait for user input while other ready branches keep running.
   - the run is only globally `waiting` when no other node can progress.

4. Deviation handling
   - `workflow_deviation_request` records reason, action, risk, proposal, and approval state.
   - low-risk deviations can be recorded.
   - medium/high-risk deviations create a review wait on the requesting node.

5. Safer state persistence
   - run state has a version field.
   - filesystem writes use a temp file plus rename.
   - update operations are serialized per run in the plugin process.

6. Recovery capsule
   - compact/session capsules include run status, ready nodes, waiting nodes, task ids, questions, and recent event types.

## Out of Scope

These remain later phases:

- dynamic natural-language workflow generation and saving;
- dynamic map/reduce;
- race and vote strategies;
- automatic task execution without main-agent participation;
- visual workflow editor;
- full Codex adapter.

## Acceptance Checks

The phase is considered done when these pass:

- scheduler test covers parallel scout branches, join/reduce readiness, local waiting, skipped branches, and deviation approval;
- OpenCode plugin test covers workflow routing, hook loop, wait/resume, deviation request, skip completion, capsule injection, and concurrent branch writes;
- package test and typecheck pass;
- local package can still be packed for trial installation.
