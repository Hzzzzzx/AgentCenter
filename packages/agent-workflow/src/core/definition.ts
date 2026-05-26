export type WorkflowNodeType = "agent" | "gate" | "join" | "human"

export type WorkflowActivation = {
  mode: "explicit_or_confirmed"
  applicability: string[]
  nonApplicability: string[]
}

export type WorkflowExecutionStrategy =
  | {
      mode: "single"
    }
  | {
      mode: "static_parallel_branch"
      group: string
    }
  | {
      mode: "join"
      group: string
    }
  | {
      mode: "reduce"
      group: string
    }
  | {
      mode: "human_gate"
    }

export type WorkflowEdge = {
  from: string
  to: string
  condition?: string
}

export type WorkflowNode = {
  id: string
  type: WorkflowNodeType
  label: string
  agent?: string
  prompt: string
  dependsOn?: string[]
  strategy?: WorkflowExecutionStrategy
  outputContract?: string
  retry?: {
    max: number
  }
}

export type WorkflowDefinition = {
  id: string
  version: string
  name: string
  description: string
  activation: WorkflowActivation
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export type WorkflowDefinitionValidation = {
  valid: boolean
  errors: string[]
}

const safeIdPattern = /^[A-Za-z0-9_-]+$/
const supportedAgents = new Set([
  "workflow_planner",
  "workflow_scout",
  "workflow_synthesizer",
  "workflow_coder",
  "workflow_tester",
  "workflow_reviewer",
])

export function createCodeChangeWorkflow(input: { workflowId?: string; name?: string; task: string }): WorkflowDefinition {
  const id = input.workflowId?.trim() || "agentcenter-code-change-v1"
  return {
    id,
    version: "0.1.0",
    name: input.name?.trim() || "AgentCenter Code Change Workflow",
    description:
      "Understand the task, inspect code through parallel scouts, synthesize findings, implement, test, and review with resumable workflow state.",
    activation: {
      mode: "explicit_or_confirmed",
      applicability: ["bug fixes", "feature work", "refactors", "code changes that need validation"],
      nonApplicability: [
        "simple Q&A",
        "pure explanation",
        "small command lookup",
        "tasks where the user did not request or confirm workflow use",
      ],
    },
    nodes: [
      {
        id: "planner",
        type: "agent",
        label: "Planner",
        agent: "workflow_planner",
        strategy: { mode: "single" },
        prompt: [
          "Turn the user request into a concrete implementation plan.",
          "Identify scope, non-goals, likely files, validation commands, and risk points.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Plan summary, scope, risks, validation strategy.",
        retry: { max: 1 },
      },
      {
        id: "code_scout",
        type: "agent",
        label: "Code Scout",
        agent: "workflow_scout",
        dependsOn: ["planner"],
        strategy: { mode: "static_parallel_branch", group: "scout" },
        prompt: [
          "Inspect the existing codebase for the planned change.",
          "Return touched areas, important symbols, unknowns, and recommended implementation path.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Code map, affected files, integration points, risks.",
        retry: { max: 1 },
      },
      {
        id: "test_scout",
        type: "agent",
        label: "Validation Scout",
        agent: "workflow_scout",
        dependsOn: ["planner"],
        strategy: { mode: "static_parallel_branch", group: "scout" },
        prompt: [
          "Inspect validation options for the planned change.",
          "Return relevant tests, typechecks, build commands, fixtures, and likely failure modes.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Validation map, commands, fixtures, and risk points.",
        retry: { max: 1 },
      },
      {
        id: "history_scout",
        type: "agent",
        label: "Context Scout",
        agent: "workflow_scout",
        dependsOn: ["planner"],
        strategy: { mode: "static_parallel_branch", group: "scout" },
        prompt: [
          "Inspect local conventions, nearby documentation, and existing patterns relevant to this task.",
          "Do not change files. Return constraints, style expectations, and context that should shape implementation.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Pattern/context summary, constraints, and open questions.",
        retry: { max: 1 },
      },
      {
        id: "scout_join",
        type: "join",
        label: "Scout Join",
        dependsOn: ["code_scout", "test_scout", "history_scout"],
        strategy: { mode: "join", group: "scout" },
        prompt: "Wait until all scout branches have either succeeded or been explicitly skipped.",
        outputContract: "Joined scout branch summaries.",
      },
      {
        id: "synthesizer",
        type: "agent",
        label: "Scout Synthesizer",
        agent: "workflow_synthesizer",
        dependsOn: ["scout_join"],
        strategy: { mode: "reduce", group: "scout" },
        prompt: [
          "Merge the parallel scout results into one implementation brief.",
          "Call out agreements, conflicts, missing information, and the safest next step.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Merged brief with conflicts, decisions, and recommended next step.",
        retry: { max: 1 },
      },
      {
        id: "risk_review",
        type: "agent",
        label: "Risk Review",
        agent: "workflow_reviewer",
        dependsOn: ["synthesizer"],
        strategy: { mode: "single" },
        prompt: [
          "Review the plan and synthesized scout result before implementation.",
          "Default to passing low-risk, well-scoped work without interrupting the user.",
          "If the task itself needs a user decision, use the native question tool and continue this node after the answer.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Pass/fail decision with reason, risk level, and any user decision gathered by native question.",
        retry: { max: 1 },
      },
      {
        id: "coder",
        type: "agent",
        label: "Coder",
        agent: "workflow_coder",
        dependsOn: ["risk_review"],
        strategy: { mode: "single" },
        prompt: [
          "Implement the approved change in the codebase.",
          "Keep scope tight and produce a concise summary of files changed.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Change summary, files changed, residual risks.",
        retry: { max: 2 },
      },
      {
        id: "tester",
        type: "agent",
        label: "Tester",
        agent: "workflow_tester",
        dependsOn: ["coder"],
        strategy: { mode: "single" },
        prompt: [
          "Run the relevant validation for the implementation.",
          "If validation fails, explain the failure and recommended fix path.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Validation commands, pass/fail result, logs summary.",
        retry: { max: 1 },
      },
      {
        id: "reviewer",
        type: "agent",
        label: "Reviewer",
        agent: "workflow_reviewer",
        dependsOn: ["tester"],
        strategy: { mode: "single" },
        prompt: [
          "Review the final implementation and validation evidence.",
          "Return whether the workflow can be considered complete.",
          `User task: ${input.task}`,
        ].join("\n"),
        outputContract: "Approval status, findings, follow-up recommendations.",
        retry: { max: 1 },
      },
    ],
    edges: [
      { from: "planner", to: "code_scout" },
      { from: "planner", to: "test_scout" },
      { from: "planner", to: "history_scout" },
      { from: "code_scout", to: "scout_join" },
      { from: "test_scout", to: "scout_join" },
      { from: "history_scout", to: "scout_join" },
      { from: "scout_join", to: "synthesizer" },
      { from: "synthesizer", to: "risk_review" },
      { from: "risk_review", to: "coder" },
      { from: "coder", to: "tester" },
      { from: "tester", to: "reviewer" },
    ],
  }
}

export function createWorkflowDraft(input: { description: string; workflowId?: string; name?: string }): WorkflowDefinition {
  const id = normalizeDefinitionId(input.workflowId || input.name || "custom-workflow")
  const normalized = input.description.toLowerCase()
  const needsArchitecture = includesAny(normalized, ["架构", "影响", "impact", "architecture", "design", "分析", "理解"])
  const needsTesting = includesAny(normalized, ["测试", "验证", "test", "validation", "verify"])
  const needsHumanGate = includesAny(normalized, [
    "必须先问我",
    "必须问我",
    "必须确认",
    "必须批准",
    "人工确认",
    "人工审批",
    "每次确认",
    "每次审批",
    "批准",
    "问我",
    "approval",
    "must approve",
    "must confirm",
    "ask me",
    "human gate",
  ])
  const wantsParallel = includesAny(normalized, ["同时", "并行", "parallel", "concurrent"])
  const nodes: WorkflowNode[] = [
    {
      id: "planner",
      type: "agent",
      label: "Planner",
      agent: "workflow_planner",
      strategy: { mode: "single" },
      prompt: ["Turn the workflow task into a concrete plan.", "Workflow intent:", input.description, "User task: {{task}}"].join(
        "\n",
      ),
      outputContract: "Plan, scope, risk points, and next nodes.",
      retry: { max: 1 },
    },
  ]

  if (needsArchitecture) {
    nodes.push({
      id: "architecture_review",
      type: "agent",
      label: "Architecture Review",
      agent: "workflow_scout",
      dependsOn: ["planner"],
      strategy: { mode: "single" },
      prompt: ["Analyze architecture, impact, constraints, and integration points.", "User task: {{task}}"].join("\n"),
      outputContract: "Architecture and impact summary with risks.",
      retry: { max: 1 },
    })
  }

  if (needsHumanGate) {
    nodes.push({
      id: "risk_gate",
      type: "agent",
      label: "Risk Gate",
      agent: "workflow_reviewer",
      dependsOn: [needsArchitecture ? "architecture_review" : "planner"],
      strategy: { mode: "human_gate" },
      prompt: [
        "Review risk before implementation.",
        "This workflow description explicitly asks for user approval. Use the native question tool for that approval, then continue this node after the answer.",
        "User task: {{task}}",
      ].join("\n"),
      outputContract: "Risk decision with the user approval answer captured by the native question tool.",
      retry: { max: 1 },
    })
  }

  const implementationDependency = needsHumanGate ? "risk_gate" : needsArchitecture ? "architecture_review" : "planner"
  nodes.push({
    id: "coder",
    type: "agent",
    label: "Coder",
    agent: "workflow_coder",
    dependsOn: [implementationDependency],
    strategy: wantsParallel && needsTesting ? { mode: "static_parallel_branch", group: "build_and_validate" } : { mode: "single" },
    prompt: ["Implement the approved workflow task with tight scope.", "User task: {{task}}"].join("\n"),
    outputContract: "Change summary, files changed, and residual risks.",
    retry: { max: 2 },
  })

  if (needsTesting) {
    nodes.push({
      id: "test_planner",
      type: "agent",
      label: "Test Planner",
      agent: "workflow_tester",
      dependsOn: [implementationDependency],
      strategy: wantsParallel ? { mode: "static_parallel_branch", group: "build_and_validate" } : { mode: "single" },
      prompt: ["Prepare the validation approach and relevant commands.", "User task: {{task}}"].join("\n"),
      outputContract: "Validation plan and commands.",
      retry: { max: 1 },
    })
    nodes.push({
      id: "implementation_join",
      type: "join",
      label: "Implementation Join",
      dependsOn: wantsParallel ? ["coder", "test_planner"] : ["coder"],
      strategy: { mode: "join", group: "build_and_validate" },
      prompt: "Wait for implementation and validation planning to finish.",
      outputContract: "Joined implementation and validation plan.",
    })
    nodes.push({
      id: "tester",
      type: "agent",
      label: "Tester",
      agent: "workflow_tester",
      dependsOn: ["implementation_join"],
      strategy: { mode: "single" },
      prompt: ["Run or describe the relevant validation and summarize evidence.", "User task: {{task}}"].join("\n"),
      outputContract: "Validation result, command evidence, and failure path if any.",
      retry: { max: 1 },
    })
  }

  nodes.push({
    id: "reviewer",
    type: "agent",
    label: "Reviewer",
    agent: "workflow_reviewer",
    dependsOn: [needsTesting ? "tester" : "coder"],
    strategy: { mode: "single" },
    prompt: ["Review the final result and decide whether the workflow is complete.", "User task: {{task}}"].join("\n"),
    outputContract: "Review decision, findings, and follow-up recommendations.",
    retry: { max: 1 },
  })

  return {
    id,
    version: "0.1.0",
    name: input.name?.trim() || titleFromId(id),
    description: input.description.trim(),
    activation: {
      mode: "explicit_or_confirmed",
      applicability: ["tasks explicitly requesting this workflow", "similar tasks confirmed by the user"],
      nonApplicability: ["ordinary tasks without explicit or confirmed workflow use"],
    },
    nodes,
    edges: nodes.flatMap((node) => (node.dependsOn ?? []).map((from) => ({ from, to: node.id }))),
  }
}

export function materializeWorkflowDefinition(definition: WorkflowDefinition, task: string): WorkflowDefinition {
  return {
    ...definition,
    nodes: definition.nodes.map((node) => ({
      ...node,
      prompt: node.prompt.replaceAll("{{task}}", task),
    })),
  }
}

export function parseWorkflowDefinitionJson(text: string): WorkflowDefinition {
  return normalizeWorkflowDefinition(JSON.parse(text) as unknown)
}

export function normalizeWorkflowDefinition(value: unknown): WorkflowDefinition {
  if (!isRecord(value)) throw new Error("Workflow definition must be a JSON object.")
  const nodes = Array.isArray(value.nodes) ? value.nodes.map(normalizeWorkflowNode) : []
  return {
    id: readString(value.id),
    version: readString(value.version) || "0.1.0",
    name: readString(value.name),
    description: readString(value.description),
    activation: normalizeActivation(value.activation),
    nodes,
    edges: Array.isArray(value.edges) ? value.edges.map(normalizeWorkflowEdge) : nodesToEdges(nodes),
  }
}

export function validateWorkflowDefinition(definition: WorkflowDefinition): WorkflowDefinitionValidation {
  const ids = new Set<string>()
  const errors = [
    !definition.id ? "Workflow id is required." : undefined,
    definition.id && !safeIdPattern.test(definition.id) ? `Workflow id is unsafe: ${definition.id}` : undefined,
    !definition.name.trim() ? "Workflow name is required." : undefined,
    !definition.nodes.length ? "Workflow must contain at least one node." : undefined,
    ...definition.nodes.flatMap((node) => validateNode(node, ids)),
  ].filter((error): error is string => Boolean(error))
  const nodeIds = new Set(definition.nodes.map((node) => node.id))
  errors.push(
    ...definition.nodes.flatMap((node) =>
      (node.dependsOn ?? [])
        .filter((dependency) => !nodeIds.has(dependency))
        .map((dependency) => `Node ${node.id} depends on unknown node ${dependency}.`),
    ),
  )
  errors.push(...validateAcyclic(definition.nodes))
  return { valid: !errors.length, errors }
}

export function formatWorkflowDefinition(definition: WorkflowDefinition) {
  return [
    `Workflow: ${definition.id}`,
    `Name: ${definition.name}`,
    `Description: ${definition.description}`,
    "",
    "Nodes:",
    ...definition.nodes.map((node) => {
      const depends = node.dependsOn?.length ? ` dependsOn=[${node.dependsOn.join(", ")}]` : ""
      const agent = node.agent ? ` agent=${node.agent}` : ""
      const strategy = node.strategy ? ` strategy=${node.strategy.mode}` : ""
      return `- ${node.id}: ${node.type}${agent}${strategy}${depends}`
    }),
  ].join("\n")
}

function normalizeWorkflowNode(value: unknown): WorkflowNode {
  if (!isRecord(value)) throw new Error("Workflow node must be a JSON object.")
  return {
    id: readString(value.id),
    type: readNodeType(value.type),
    label: readString(value.label),
    agent: readString(value.agent) || undefined,
    prompt: readString(value.prompt),
    dependsOn: readStringArray(value.dependsOn),
    strategy: normalizeStrategy(value.strategy),
    outputContract: readString(value.outputContract) || undefined,
    retry: normalizeRetry(value.retry),
  }
}

function normalizeWorkflowEdge(value: unknown): WorkflowEdge {
  if (!isRecord(value)) throw new Error("Workflow edge must be a JSON object.")
  return {
    from: readString(value.from),
    to: readString(value.to),
    condition: readString(value.condition) || undefined,
  }
}

function normalizeActivation(value: unknown): WorkflowActivation {
  if (!isRecord(value)) {
    return {
      mode: "explicit_or_confirmed",
      applicability: [],
      nonApplicability: ["ordinary tasks without explicit or confirmed workflow use"],
    }
  }
  return {
    mode: "explicit_or_confirmed",
    applicability: readStringArray(value.applicability),
    nonApplicability: readStringArray(value.nonApplicability),
  }
}

function normalizeStrategy(value: unknown): WorkflowExecutionStrategy | undefined {
  if (!isRecord(value)) return
  if (value.mode === "single") return { mode: "single" }
  if (value.mode === "human_gate") return { mode: "human_gate" }
  if (value.mode === "static_parallel_branch" || value.mode === "join" || value.mode === "reduce") {
    return { mode: value.mode, group: readString(value.group) || "default" }
  }
}

function normalizeRetry(value: unknown) {
  if (!isRecord(value) || typeof value.max !== "number" || value.max < 0) return undefined
  return { max: Math.floor(value.max) }
}

function validateNode(node: WorkflowNode, ids: Set<string>) {
  const errors = [
    !node.id ? "Node id is required." : undefined,
    node.id && !safeIdPattern.test(node.id) ? `Node id is unsafe: ${node.id}` : undefined,
    ids.has(node.id) ? `Duplicate node id: ${node.id}` : undefined,
    node.type !== "agent" && node.type !== "join" ? `Node ${node.id} uses unsupported type ${node.type}.` : undefined,
    node.type === "agent" && !node.agent ? `Agent node ${node.id} must define agent.` : undefined,
    node.agent && !supportedAgents.has(node.agent) ? `Node ${node.id} uses unsupported agent ${node.agent}.` : undefined,
    !node.prompt.trim() ? `Node ${node.id} must define prompt.` : undefined,
  ].filter((error): error is string => Boolean(error))
  ids.add(node.id)
  return errors
}

function validateAcyclic(nodes: WorkflowNode[]) {
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const visiting = new Set<string>()
  const visited = new Set<string>()
  return nodes.flatMap((node) => visitNode(node.id, byId, visiting, visited, []))
}

function visitNode(
  id: string,
  byId: Map<string, WorkflowNode>,
  visiting: Set<string>,
  visited: Set<string>,
  path: string[],
): string[] {
  if (visited.has(id) || !byId.has(id)) return []
  if (visiting.has(id)) return [`Workflow contains a dependency cycle: ${[...path, id].join(" -> ")}.`]
  visiting.add(id)
  const errors = (byId.get(id)?.dependsOn ?? []).flatMap((dependency) =>
    visitNode(dependency, byId, visiting, visited, [...path, id]),
  )
  visiting.delete(id)
  visited.add(id)
  return errors
}

function nodesToEdges(nodes: WorkflowNode[]) {
  return nodes.flatMap((node) => (node.dependsOn ?? []).map((from) => ({ from, to: node.id })))
}

function readNodeType(value: unknown): WorkflowNodeType {
  if (value === "agent" || value === "join") return value
  if (value === "gate" || value === "human") return value
  return "agent"
}

function normalizeDefinitionId(value: string) {
  return (
    value
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 64) || "custom-workflow"
  )
}

function titleFromId(id: string) {
  return id
    .split(/[-_]+/)
    .filter(Boolean)
    .map((part) => `${part.slice(0, 1).toUpperCase()}${part.slice(1)}`)
    .join(" ")
}

function includesAny(text: string, patterns: string[]) {
  return patterns.some((pattern) => text.includes(pattern))
}

function readString(value: unknown) {
  return typeof value === "string" ? value.trim() : ""
}

function readStringArray(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === "string" && Boolean(item.trim()))
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value)
}
