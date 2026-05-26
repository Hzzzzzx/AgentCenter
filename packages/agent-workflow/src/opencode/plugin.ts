import path from "node:path"
import { tool, type Config, type Plugin } from "@opencode-ai/plugin"
import {
  createCodeChangeWorkflow,
  createWorkflowDraft,
  formatWorkflowDefinition,
  materializeWorkflowDefinition,
  parseWorkflowDefinitionJson,
  validateWorkflowDefinition,
  type WorkflowDefinition,
} from "../core/definition"
import {
  advanceRun,
  answerNodeWait,
  cancelRun,
  getReadyNodes,
  parseWorkflowCommand,
  parseTaskIdFromTaskOutput,
  parseWorkflowTaskOutput,
  recordNodeResult,
  renderRunSummary,
  requestWorkflowDeviation,
  startNode,
} from "../core/scheduler"
import { createWorkflowRun, type WorkflowNodeResult } from "../core/state"
import type { WorkflowRun } from "../core/state"
import { FsWorkflowStore } from "../store/fs-store"
import { workflowAgents } from "./agents"
import { ensureWorkflowObserveServer } from "./observe-server"
import {
  renderRunCreated,
  renderRuns,
  renderTaskInstructions,
  renderWorkflowCapsule,
  renderWorkflowInteractionInstructions,
} from "./render"
import { readRunnerMode, selectOpenCodeRunner, type OpenCodeRunnerOptions } from "./runner-selection"
import { renderWorkflowDebug } from "../core/inspect"

type Options = OpenCodeRunnerOptions & {
  stateDir?: string
}

const AgentWorkflowPlugin: Plugin = async (input, options) => {
  const pluginOptions = readOptions(options)
  const store = new FsWorkflowStore(resolveStateDir(input.worktree, pluginOptions))
  const runnerSelection = selectOpenCodeRunner(pluginOptions)
  const runner = runnerSelection.runner

  return {
    config: async (cfg: Config) => {
      cfg.agent = {
        ...cfg.agent,
        ...workflowAgents(),
      }
    },

    tool: {
      workflow_route: tool({
        description:
          "Assess whether a user task should use Agent Workflow and which saved workflow may fit. This never starts a workflow; it only recommends explicit user confirmation.",
        args: {
          task: tool.schema.string().describe("The user task to classify before deciding whether to start a workflow."),
        },
        async execute(args) {
          return renderRouteDecision(args.task, await store.listWorkflowDefinitions())
        },
      }),

      workflow_define: tool({
        description:
          "Create a controlled workflow definition draft from a natural-language workflow description. The returned JSON must be shown to the user and saved only after confirmation.",
        args: {
          description: tool.schema.string().describe("Natural-language workflow description from the user."),
          workflow_id: tool.schema.string().optional().describe("Optional stable workflow id."),
          name: tool.schema.string().optional().describe("Optional human-friendly workflow name."),
        },
        async execute(args) {
          return renderDefinitionDraft(
            createWorkflowDraft({
              description: args.description,
              workflowId: args.workflow_id,
              name: args.name,
            }),
          )
        },
      }),

      workflow_validate: tool({
        description: "Validate a workflow definition JSON string before saving or running it.",
        args: {
          definition_json: tool.schema.string().describe("WorkflowDefinition JSON to validate."),
        },
        async execute(args) {
          return renderDefinitionValidation(readDefinitionJson(args.definition_json))
        },
      }),

      workflow_save: tool({
        description: "Save a validated workflow definition JSON after the user confirms the draft.",
        args: {
          definition_json: tool.schema.string().describe("WorkflowDefinition JSON to save."),
          confirmed_by_question: tool.schema
            .boolean()
            .optional()
            .describe("Must be true after the built-in question tool confirms the user wants to save this workflow."),
          overwrite: tool.schema.boolean().optional().describe("Overwrite an existing workflow definition with the same id."),
        },
        async execute(args) {
          const result = readDefinitionJson(args.definition_json)
          if (!result.definition) return renderDefinitionValidation(result)
          if (args.confirmed_by_question !== true) {
            return renderSaveNeedsQuestion(result.definition)
          }
          if (!args.overwrite && (await store.getWorkflowDefinition(result.definition.id))) {
            return [`Workflow definition already exists: ${result.definition.id}`, "Pass overwrite=true to replace it."].join("\n")
          }
          const validation = validateWorkflowDefinition(result.definition)
          if (!validation.valid) return renderDefinitionValidation({ validation })
          await store.saveWorkflowDefinition(result.definition)
          return ["Workflow definition saved.", "", formatWorkflowDefinition(result.definition)].join("\n")
        },
      }),

      workflow_list: tool({
        description: "List saved reusable workflow definitions.",
        args: {
          query: tool.schema.string().optional().describe("Optional id/name text filter."),
        },
        async execute(args) {
          return renderDefinitionList(await store.listWorkflowDefinitions(), args.query)
        },
      }),

      workflow_show: tool({
        description: "Show a saved workflow definition by id.",
        args: {
          workflow_id: tool.schema.string().describe("Saved workflow definition id."),
        },
        async execute(args) {
          const definition = await store.getWorkflowDefinition(args.workflow_id)
          if (!definition) return `Workflow definition not found: ${args.workflow_id}`
          return renderDefinitionDraft(definition)
        },
      }),

      workflow_debug: tool({
        description: "Explain ready, blocked, waiting, failed, and dependency state for a workflow run or one node.",
        args: {
          run_id: tool.schema.string().describe("Workflow run id."),
          node_id: tool.schema.string().optional().describe("Optional node id to debug."),
        },
        async execute(args) {
          const run = await store.updateRun(args.run_id, (current) => {
            advanceRun(current)
          })
          if (!run) return `Workflow run not found: ${args.run_id}`
          return renderWorkflowDebug(run, args.node_id)
        },
      }),

      workflow_observe: tool({
        description: "Start or reuse a local realtime workflow dashboard for one workflow run or saved workflow definition.",
        args: {
          run_id: tool.schema.string().optional().describe("Workflow run id to observe."),
          workflow_id: tool.schema.string().optional().describe("Saved workflow definition id to observe."),
        },
        async execute(args) {
          const server = await ensureWorkflowObserveServer(store)
          if (args.run_id) {
            const run = await store.updateRun(args.run_id, (current) => {
              advanceRun(current)
            })
            if (!run) return `Workflow run not found: ${args.run_id}`
            return renderObserveStarted({
              kind: "run",
              dashboardUrl: `${server.baseUrl}/runs/${encodeURIComponent(run.id)}`,
              apiUrl: `${server.baseUrl}/api/runs/${encodeURIComponent(run.id)}`,
            })
          }

          if (args.workflow_id) {
            const definition = await store.getWorkflowDefinition(args.workflow_id)
            if (!definition) return `Workflow definition not found: ${args.workflow_id}`
            return renderObserveStarted({
              kind: "definition",
              dashboardUrl: `${server.baseUrl}/definitions/${encodeURIComponent(definition.id)}`,
              apiUrl: `${server.baseUrl}/api/definitions/${encodeURIComponent(definition.id)}`,
            })
          }

          return "Provide run_id or workflow_id."
        },
      }),

      workflow_run: tool({
        description:
          "Create or start an Agent Workflow run only after the user explicitly requested workflow use or confirmed a recommendation. Returns ready DAG nodes that the main agent should execute with the OpenCode task tool.",
        args: {
          task: tool.schema.string().describe("The concrete user task to run through the workflow."),
          title: tool.schema.string().optional().describe("Optional human-friendly run title."),
          workflow_id: tool.schema.string().optional().describe("Optional workflow definition id."),
        },
        async execute(args, context) {
          const definition = await resolveWorkflowDefinition(store, args.workflow_id, args.task)
          const run = createWorkflowRun({
            sessionId: context.sessionID,
            title: args.title,
            task: args.task,
            definition,
          })
          await store.saveRun(run)
          context.metadata({
            title: `Workflow ${run.id}`,
            metadata: { workflowRunId: run.id, status: run.status },
          })
          return {
            title: `Workflow ${run.id}`,
            output: renderRunCreated(run, runnerSelection),
            metadata: { workflowRunId: run.id, status: run.status, stateDir: store.directory(), runner: runner.kind },
          }
        },
      }),

      workflow_state_get: tool({
        description: "Read workflow run state, node status, waiting questions, and ready node task instructions.",
        args: {
          run_id: tool.schema.string().optional().describe("Workflow run id. Omit to list runs for this session."),
        },
        async execute(args, context) {
          if (!args.run_id) {
            return renderRuns(await store.listRuns({ sessionId: context.sessionID }))
          }

          const run = await store.updateRun(args.run_id, (current) => {
            advanceRun(current)
          })
          if (!run) return `Workflow run not found: ${args.run_id}`
          return renderRunResponse(run, runner)
        },
      }),

      workflow_answer: tool({
        description: "Answer a workflow node that is waiting for user input, then return newly ready nodes.",
        args: {
          run_id: tool.schema.string().describe("Workflow run id."),
          node_id: tool.schema.string().describe("Waiting node id."),
          answer: tool.schema.string().describe("User answer or decision."),
          source: tool.schema
            .enum(["question", "manual"])
            .describe("Use question when the answer came from OpenCode's built-in question tool. Use manual only for explicit chat override."),
        },
        async execute(args) {
          const run = await updateRequiredRun(store, args.run_id, (current) => {
            answerNodeWait(current, { nodeId: args.node_id, answer: args.answer, source: args.source })
          })
          return renderRunResponse(run, runner)
        },
      }),

      workflow_node_result: tool({
        description:
          "Manually record a workflow node result when automatic task hooks are unavailable or when an external runner completed the node.",
        args: {
          run_id: tool.schema.string().describe("Workflow run id."),
          node_id: tool.schema.string().describe("Node id."),
          status: tool.schema.enum(["succeeded", "skipped", "failed", "needs_input"]).describe("Node result status."),
          summary: tool.schema.string().describe("Short node result summary."),
          question: tool.schema.string().optional().describe("Question when status is needs_input."),
          choices: tool.schema.array(tool.schema.string()).optional().describe("Choices when status is needs_input."),
          error: tool.schema.string().optional().describe("Error details when status is failed."),
        },
        async execute(args) {
          const run = await updateRequiredRun(store, args.run_id, (current) => {
            recordNodeResult(current, {
              nodeId: args.node_id,
              result: manualResult(args),
            })
          })
          return renderRunResponse(run, runner)
        },
      }),

      workflow_deviation_request: tool({
        description:
          "Record a workflow deviation such as insert_step, skip_node, detach, or change_plan. Medium/high risk deviations wait for user approval.",
        args: {
          run_id: tool.schema.string().describe("Workflow run id."),
          node_id: tool.schema.string().optional().describe("Node id requesting the deviation."),
          action: tool.schema.enum(["continue", "insert_step", "skip_node", "detach", "change_plan"]).describe("Deviation action."),
          risk: tool.schema.enum(["low", "medium", "high"]).describe("Deviation risk level."),
          reason: tool.schema.string().describe("Concrete reason the original workflow is insufficient."),
          proposal: tool.schema.string().describe("Proposed next step or change."),
          requires_user_approval: tool.schema.boolean().optional().describe("Force user approval even for low risk deviations."),
        },
        async execute(args) {
          const run = await updateRequiredRun(store, args.run_id, (current) => {
            requestWorkflowDeviation(current, {
              nodeId: args.node_id,
              action: args.action,
              risk: args.risk,
              reason: args.reason,
              proposal: args.proposal,
              requiresUserApproval: args.requires_user_approval,
            })
          })
          return renderRunResponse(run, runner)
        },
      }),

      workflow_cancel: tool({
        description: "Cancel a workflow run.",
        args: {
          run_id: tool.schema.string().describe("Workflow run id."),
        },
        async execute(args) {
          const run = await updateRequiredRun(store, args.run_id, (current) => {
            cancelRun(current)
          })
          return renderRunSummary(run)
        },
      }),
    },

    "tool.execute.before": async (hookInput, output) => {
      if (hookInput.tool !== "task") return
      const ref = (runner.parseCommand ?? parseWorkflowCommand)(readCommand(output.args))
      if (!ref) return
      await store.updateRun(ref.runId, (run) => {
        startNode(run, { nodeId: ref.nodeId, callId: hookInput.callID, taskId: readTaskId(output.args), runner: runner.kind })
      })
    },

    "tool.execute.after": async (hookInput, output) => {
      if (hookInput.tool !== "task") return
      const ref = (runner.parseCommand ?? parseWorkflowCommand)(readCommand(hookInput.args))
      if (!ref) return
      const run = await store.updateRun(ref.runId, (current) => {
        recordNodeResult(current, {
          nodeId: ref.nodeId,
          result: parseWorkflowTaskOutput(output.output),
          taskId: parseTaskIdFromTaskOutput(output.output),
          callId: hookInput.callID,
        })
      })
      if (!run) return
      output.title = `${output.title || "task"} -> workflow ${run.id}/${ref.nodeId}`
      output.metadata = {
        ...(isRecord(output.metadata) ? output.metadata : {}),
        workflowRunId: run.id,
        workflowNodeId: ref.nodeId,
        workflowStatus: run.status,
      }
    },

    "experimental.chat.system.transform": async (hookInput, output) => {
      const runs = hookInput.sessionID ? await store.listRuns({ sessionId: hookInput.sessionID }) : []
      output.system.push(
        [
          "Agent Workflow Runtime is installed.",
          `Agent Workflow runner: ${runner.kind}.`,
          runnerSelection.note ? `Agent Workflow runner note: ${runnerSelection.note}` : undefined,
          "Do not route ordinary tasks into workflow by default.",
          "Only call workflow_run when the user explicitly requested workflow use or confirmed a workflow_route recommendation.",
          "To create reusable workflows, call workflow_define, show the draft to the user, then call workflow_save only after confirmation.",
          "Use workflow_list and workflow_show to inspect saved workflows. Pass workflow_id to workflow_run only for saved definitions.",
          "Use workflow_debug to explain blocked nodes, and workflow_observe to open a local realtime dashboard.",
          "When workflow_run returns ready nodes, follow the returned runner instructions for each ready node.",
          'For workflow task calls, copy the command field exactly, for example command="workflow:<runId>:<nodeId>".',
          "After task completion, call workflow_state_get to continue scheduling.",
          "If workflow output says Native OpenCode question required, you MUST call the built-in question tool before workflow_answer.",
          "After question returns, call workflow_answer with source=\"question\". Do not resolve workflow waits by plain text.",
          "If an active workflow needs to change plan, detach, insert a step, or otherwise deviate, call workflow_deviation_request with the reason and risk.",
          "If a workflow node is not applicable, call workflow_node_result with status=\"skipped\" instead of marking it succeeded.",
        ]
          .filter((line): line is string => Boolean(line))
          .join("\n"),
      )
      if (!runs.length) return
      output.system.push(renderWorkflowCapsule(runs))
    },

    "experimental.session.compacting": async (hookInput, output) => {
      const runs = await store.listRuns({ sessionId: hookInput.sessionID })
      if (!runs.length) return
      output.context.push(["Agent Workflow checkpoint capsule:", renderWorkflowCapsule(runs.slice(0, 10))].join("\n"))
    },
  }
}

export default AgentWorkflowPlugin

function resolveStateDir(worktree: string, options: Options) {
  if (options.stateDir && path.isAbsolute(options.stateDir)) return options.stateDir
  if (options.stateDir) return path.join(worktree, options.stateDir)
  return path.join(worktree, ".opencode", "agent-workflow")
}

function readOptions(options: Record<string, unknown> | undefined): Options {
  return {
    stateDir: typeof options?.stateDir === "string" && options.stateDir.trim() ? options.stateDir : undefined,
    runner: readRunnerMode(options?.runner),
    omoTaskBridge: options?.omoTaskBridge === true || options?.omo_task_bridge === true,
    omoTaskTool: typeof options?.omoTaskTool === "string" && options.omoTaskTool.trim() ? options.omoTaskTool : undefined,
  }
}

async function updateRequiredRun(store: FsWorkflowStore, runId: string, update: (run: WorkflowRun) => void | Promise<void>) {
  const run = await store.updateRun(runId, update)
  if (!run) throw new Error(`Workflow run not found: ${runId}`)
  return run
}

async function resolveWorkflowDefinition(store: FsWorkflowStore, workflowId: string | undefined, task: string) {
  if (!workflowId) return createCodeChangeWorkflow({ task })
  const saved = await store.getWorkflowDefinition(workflowId)
  if (!saved) throw new Error(`Workflow definition not found: ${workflowId}`)
  return materializeWorkflowDefinition(saved, task)
}

function renderRouteDecision(task: string, definitions: WorkflowDefinition[]) {
  const normalized = task.toLowerCase()
  const likelyCodeWork =
    normalized.includes("bug") ||
    normalized.includes("fix") ||
    normalized.includes("implement") ||
    normalized.includes("refactor") ||
    normalized.includes("test") ||
    normalized.includes("代码") ||
    normalized.includes("修复") ||
    normalized.includes("实现") ||
    normalized.includes("重构")
  const matches = definitions.filter((definition) => workflowMatchesTask(definition, normalized)).slice(0, 3)
  return [
    likelyCodeWork ? "Recommendation: workflow may fit this task." : "Recommendation: keep this as a normal OpenCode task.",
    "Activation policy: do not start workflow unless the user explicitly asks for it or confirms this recommendation.",
    matches.length
      ? `Suggested saved workflow: ${matches.map((definition) => definition.id).join(", ")}`
      : "Suggested saved workflow: none",
    likelyCodeWork
      ? "Reason: the task appears to involve code change or validation, where planner/scout/reviewer coordination can help."
      : "Reason: the task does not clearly need multi-agent workflow coordination.",
    "",
    likelyCodeWork
      ? [
          "Native OpenCode question required before workflow_run unless the user already explicitly requested workflow execution.",
          "Call the built-in question tool with:",
          "```json",
          JSON.stringify(
            {
              questions: [
                {
                  header: "Use Workflow",
                  question: `Use an Agent Workflow for this task?\n\n${task}`,
                  options: [
                    { label: "Use workflow", description: "Start a workflow run for this task." },
                    { label: "Normal task", description: "Handle this as an ordinary OpenCode task." },
                  ],
                  multiple: false,
                },
              ],
            },
            null,
            2,
          ),
          "```",
        ].join("\n")
      : "No workflow activation question is needed unless the user asks to use workflow.",
  ].join("\n")
}

function workflowMatchesTask(definition: WorkflowDefinition, normalizedTask: string) {
  const haystack = `${definition.id} ${definition.name} ${definition.description} ${definition.activation.applicability.join(" ")}`.toLowerCase()
  return normalizedTask
    .split(/\s+/)
    .filter((part) => part.length >= 3)
    .some((part) => haystack.includes(part))
}

function renderDefinitionDraft(definition: WorkflowDefinition) {
  const validation = validateWorkflowDefinition(definition)
  return [
    "Workflow definition draft:",
    "",
    formatWorkflowDefinition(definition),
    "",
    renderDefinitionValidation({ definition, validation }),
    "",
    "Definition JSON:",
    "```json",
    JSON.stringify(definition, null, 2),
    "```",
    "",
    "Before saving this reusable workflow, call the built-in question tool to confirm the draft with the user.",
    "If the user approves, call workflow_save with confirmed_by_question=true.",
  ].join("\n")
}

function renderDefinitionValidation(input: {
  definition?: WorkflowDefinition
  validation?: ReturnType<typeof validateWorkflowDefinition>
  error?: string
}) {
  if (input.error) return [`Workflow definition invalid.`, `- ${input.error}`].join("\n")
  const validation = input.validation ?? (input.definition ? validateWorkflowDefinition(input.definition) : undefined)
  if (!validation) return "Workflow definition invalid."
  if (validation.valid) return "Workflow definition valid."
  return ["Workflow definition invalid:", ...validation.errors.map((error) => `- ${error}`)].join("\n")
}

function renderDefinitionList(definitions: WorkflowDefinition[], query: string | undefined) {
  const normalized = query?.trim().toLowerCase()
  const filtered = normalized
    ? definitions.filter((definition) => `${definition.id} ${definition.name}`.toLowerCase().includes(normalized))
    : definitions
  if (!filtered.length) return "No workflow definitions found."
  return [
    "Saved workflow definitions:",
    ...filtered.map(
      (definition) =>
        `- ${definition.id}: ${definition.name} (${definition.nodes.length} nodes) - ${definition.description.slice(0, 120)}`,
    ),
  ].join("\n")
}

function renderObserveStarted(input: { kind: "run" | "definition"; dashboardUrl: string; apiUrl: string }) {
  return [
    "Workflow observe dashboard ready.",
    `Kind: ${input.kind}`,
    `Dashboard: ${input.dashboardUrl}`,
    `API: ${input.apiUrl}`,
    "Mode: local realtime HTTP view; no snapshot files are written.",
  ].join("\n")
}

function renderRunResponse(run: WorkflowRun, runner = selectOpenCodeRunner().runner) {
  return [
    renderRunSummary(run),
    "",
    renderWorkflowInteractionInstructions(run),
    "",
    renderTaskInstructions(getReadyNodes(run), runner),
    "",
    renderReadyNodePrompts(run),
  ]
    .filter((part) => part.trim())
    .join("\n")
}

function renderSaveNeedsQuestion(definition: WorkflowDefinition) {
  return [
    "Workflow definition was not saved.",
    "Native OpenCode question required before saving a reusable workflow definition.",
    "Call the built-in question tool with:",
    "```json",
    JSON.stringify(
      {
        questions: [
          {
            header: "Save Workflow",
            question: `Save ${definition.name} as a reusable Agent Workflow definition?`,
            options: [
              { label: "Save", description: "Save this workflow definition for reuse." },
              { label: "Revise", description: "Revise the workflow draft before saving." },
              { label: "Cancel", description: "Do not save this workflow definition." },
            ],
            multiple: false,
          },
        ],
      },
      null,
      2,
    ),
    "```",
    "After the user selects Save, call workflow_save again with confirmed_by_question=true.",
  ].join("\n")
}

function readDefinitionJson(definitionJson: string) {
  try {
    const definition = parseWorkflowDefinitionJson(definitionJson)
    return { definition, validation: validateWorkflowDefinition(definition) }
  } catch (error) {
    return { error: error instanceof Error ? error.message : String(error) }
  }
}

function manualResult(input: {
  status: "succeeded" | "skipped" | "failed" | "needs_input"
  summary: string
  question?: string
  choices?: string[]
  error?: string
}): WorkflowNodeResult {
  if (input.status === "needs_input") {
    return {
      status: "needs_input",
      summary: input.summary,
      question: input.question ?? input.summary,
      choices: input.choices,
    }
  }
  if (input.status === "failed") {
    return {
      status: "failed",
      summary: input.summary,
      error: input.error,
    }
  }
  if (input.status === "skipped") {
    return {
      status: "skipped",
      summary: input.summary,
    }
  }
  return {
    status: "succeeded",
    summary: input.summary,
  }
}

function renderReadyNodePrompts(run: WorkflowRun) {
  return getReadyNodes(run).flatMap((node) => ["", `## Ready node prompt: ${node.nodeId}`, node.prompt]).join("\n")
}

function readCommand(value: unknown) {
  if (!isRecord(value)) return
  return value.command
}

function readTaskId(value: unknown) {
  if (!isRecord(value)) return
  return typeof value.task_id === "string" ? value.task_id : undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value)
}
