# Blueprint Graph — SDD Tool Scaffold

> **SDD/Agent infrastructure.** This directory is NOT a TianYuan product feature.
> It provides generated graph artifacts and report-only diff for the SDD review loop.

## Guardrails

| Guardrail | Rule |
|-----------|------|
| **Scope** | SDD/Agent review infrastructure only — no product UI, no Workbench, no nebula viewer |
| **YAML** | No human-written Blueprint YAML. Artifacts are generated JSON only |
| **Storage** | File-based JSON snapshots. No SQLite for the current CLI tool |
| **Pilots** | IPC pilot remains Quest-oriented; frontend contract graph exists for Workbench planning |
| **Report-only** | Rule findings appear in report data. Default CLI exit is nonzero only for internal/tooling failures, not for rule mismatches |
| **Schema** | `GraphArtifactV1` with `schemaVersion: "graph-artifact-v1"` |
| **IPC node kinds** | `TSEntry`, `TauriCommand`, `RustHandler` |
| **Frontend node kinds** | `TSModule`, `VueComponent`, `Store`, `Composable`, `ViewDefinition`, `LayoutProfile`, `Test` |
| **IPC edge kinds** | `TS_INVOKE_IPC`, `IPC_HANDLED_BY` |
| **Frontend edge kinds** | `IMPORTS`, `RENDERS`, `USES_STORE`, `REGISTERS_VIEW`, `OPENS_VIEW`, `MUTATES_LAYOUT`, `TESTED_BY` |

## Domains

### IPC Contract Graph

The original Phase 1 domain answers:

> For a planned Tauri IPC chain, does the code contain the expected TS invoke,
> Rust handler, and Tauri command registration?

Use `extract` / `check` for this domain.

### Frontend Contract Graph

The v1.2 frontend domain answers:

> For a planned Workbench UI contract, do the expected Vue components, Pinia
> stores, view registrations, view openers, layout profile mutations, and tests
> exist in the static source graph?

Use `extract-frontend` / `check-frontend` for this domain. This is a static
contract extractor; it does not verify runtime drag/drop geometry, visual layout
correctness, focus behavior, or Playwright screenshots.

## Files

| File | Owner | Purpose |
|------|-------|---------|
| `schema.mjs` | T2 | `GraphArtifactV1` validation, ID helpers, factory functions |
| `schema.test.mjs` | T2 | Schema tests using `node:test` built-in |
| `target-from-plan.mjs` | T3 | Parse `BLUEPRINT_TARGET_V1` block from plan Markdown |
| `target-from-plan.test.mjs` | T3 | Target parser tests |
| `extract-ts.mjs` | T4 | Extract `tauriInvoke()` calls from TypeScript |
| `extract-ts.test.mjs` | T4 | TS extractor tests |
| `extract-frontend.mjs` | v1.2 | Extract Vue/TS frontend contract graph |
| `extract-frontend.test.mjs` | v1.2 | Frontend contract extractor tests |
| `diff.mjs` | T6 | Merge target + actual and apply hard rules |
| `report.mjs` | T6 | Render JSON + Markdown reports |
| `diff.test.mjs` | T6 | Diff + report tests |
| `cli.mjs` | T7 | Orchestrate extraction → diff → report |
| `cli.test.mjs` | T7 | CLI integration tests |

## Commands

### Run all blueprint-graph tests

```bash
pnpm blueprint-graph:test
```

### Extract artifacts

Generates target artifact, runs TS extractor, runs Rust extractor, and merges IPC results:

```bash
pnpm blueprint-graph:extract -- --pilot quest --out .sisyphus/evidence/blueprint-graph-phase-one
```

**Outputs:**
- `blueprint.quest.json` — target graph from plan
- `actual-ts.quest.json` — TypeScript IPC extraction
- `actual-rust.quest.json` — Rust handler extraction
- `actual-ipc.quest.json` — merged TS + Rust actual

### Extract frontend artifacts

Generates target artifact and static frontend actual graph:

```bash
pnpm blueprint-graph:extract-frontend -- --pilot workbench --source docs/plans/<frontend-wave-plan>.md --out .sisyphus/evidence/<frontend-wave> --src src
```

**Outputs:**
- `blueprint.workbench.json` — target graph from plan
- `actual-frontend.workbench.json` — Vue/TS frontend static contract extraction

### Check (extract + diff + report)

Full pipeline: extract → diff against target → write report:

```bash
pnpm blueprint-graph:check -- --pilot quest --source .sisyphus/plans/blueprint-graph-phase-one.md --out .sisyphus/evidence/blueprint-graph-phase-one
```

**Additional outputs:**
- `diff.quest.json` — diff artifact with findings
- `report.quest.md` — human-readable Markdown report

### Check frontend (extract + generic diff + report)

```bash
pnpm blueprint-graph:check-frontend -- --pilot workbench --source docs/plans/<frontend-wave-plan>.md --out .sisyphus/evidence/<frontend-wave> --src src
```

**Additional outputs:**
- `diff.workbench.json` — generic target-vs-actual diff artifact
- `report.workbench.md` — human-readable Markdown report

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--pilot` | `quest` | Pilot name |
| `--source` | `.sisyphus/plans/blueprint-graph-phase-one.md` | Plan Markdown path |
| `--out` | `.sisyphus/evidence/blueprint-graph-phase-one` | Output directory |
| `--src` | `src` | Source directory for TS/frontend extraction |

## Report-Only Default Behavior

The CLI exits **0** even when rule findings (errors, warnings) exist in the report.
Non-zero exit only occurs for internal tooling failures:
- Parse errors in the plan file
- Missing source files or directories
- Rust extractor binary compilation/execution failure

This is intentional for Phase 1: the CLI is a reporting tool, not a CI gate.

## Design Decisions

- Plain JavaScript validation — no `zod`, no `ts-morph`, no new dependencies.
- Deterministic node IDs: `{Kind}:{sourcePath}::{symbol}` format.
- Factory functions (`createNode`, `createEdge`, `createFinding`) enforce shape.
- Validation returns `{ ok, errors[] }` — callers decide how to handle failures.
- CLI uses `execFileSync` for Rust extractor — synchronous, simple error propagation.
- Merged `actual-ipc` artifact unions nodes/edges by deterministic ID (TS first, Rust fills gaps).
- Frontend extraction uses TypeScript AST plus conservative Vue SFC parsing; it prefers deterministic false negatives over broad false positives.
- Frontend diff uses a generic "required target node/edge must exist" rule because it has a single actual graph rather than split TS/Rust artifacts.
