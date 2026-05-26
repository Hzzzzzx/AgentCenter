type AgentConfig = {
  description: string
  mode: "subagent"
  prompt: string
  permission: Record<string, unknown>
}

export function workflowAgents(): Record<string, AgentConfig> {
  return {
    workflow_planner: {
      description: "Workflow planner that turns a user task into scope, risks, and validation steps.",
      mode: "subagent",
      prompt: [
        "You are a workflow planner. Produce a concise implementation plan with scope, non-goals, risks, and validation.",
        "Do not modify files. If you genuinely need user clarification, use the native question tool and continue after the answer.",
      ].join("\n"),
      permission: {
        "*": "deny",
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
      },
    },
    workflow_scout: {
      description: "Workflow code scout that reads code and maps impact without editing files.",
      mode: "subagent",
      prompt: [
        "You are a code scout. Inspect existing code and return affected files, symbols, execution paths, and risks.",
        "Do not modify files.",
      ].join("\n"),
      permission: {
        "*": "deny",
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
        bash: "ask",
      },
    },
    workflow_synthesizer: {
      description: "Workflow synthesizer that merges parallel branch results.",
      mode: "subagent",
      prompt: [
        "You are a workflow synthesizer. Merge upstream agent outputs into one concise, actionable brief.",
        "Highlight agreements, conflicts, missing information, and the safest next step.",
        "Do not modify files. Finish with a workflow_result block unless you need user input.",
      ].join("\n"),
      permission: {
        "*": "deny",
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
      },
    },
    workflow_coder: {
      description: "Workflow coder that implements the approved node task.",
      mode: "subagent",
      prompt: [
        "You are a workflow coder. Implement only the approved scope.",
        "Keep changes small, preserve user changes, and finish with a workflow_result or workflow_failed block.",
      ].join("\n"),
      permission: {
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
        edit: "ask",
        bash: "ask",
      },
    },
    workflow_tester: {
      description: "Workflow tester that runs validation and summarizes evidence.",
      mode: "subagent",
      prompt: [
        "You are a workflow tester. Run the relevant checks and summarize command results.",
        "If checks fail, return workflow_failed with actionable logs.",
      ].join("\n"),
      permission: {
        "*": "deny",
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
        bash: "ask",
      },
    },
    workflow_reviewer: {
      description: "Workflow reviewer that checks progress and can ask the user when the task itself needs it.",
      mode: "subagent",
      prompt: [
        "You are a workflow reviewer. Check risk, correctness, and whether user input is genuinely needed.",
        "Default to continuing with a clear result when risk is low and scope is clear. Use the native question tool only when the task itself needs a user decision.",
      ].join("\n"),
      permission: {
        "*": "deny",
        question: "allow",
        read: "allow",
        grep: "allow",
        list: "allow",
        bash: "ask",
      },
    },
  }
}
