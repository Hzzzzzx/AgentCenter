/**
 * conversationTurnProjector.test.ts
 *
 * TDD RED phase: all test cases for the pure projection function.
 * Tests are written BEFORE the implementation.
 *
 * Design reference: docs/architecture/CONVERSATION-UI-EVENT-MAPPING-DESIGN.md
 * Type reference: ./types.ts
 */
import { describe, it, expect } from 'vitest'
import type {
  ProjectorInput,
  ProjectorMessage,
  ProjectorRuntimeEvent,
  ProjectorConfirmation,
  ConversationTurnProjection,
  ToolInvocationPart,
} from './types'
import { projectConversationTurns } from './conversationTurnProjector'

// ─── Helpers ─────────────────────────────────────────────────────────

function makeEvent(overrides: Partial<ProjectorRuntimeEvent> & { id: string }): ProjectorRuntimeEvent {
  return {
    sessionId: 'sess-1',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    eventType: 'PROCESS_TRACE',
    eventSource: 'OPENCODE',
    payloadJson: null,
    seqNo: null,
    createdAt: '2026-05-10T10:00:00.000Z',
    ...overrides,
  }
}

function makeMessage(overrides: Partial<ProjectorMessage> & { id: string }): ProjectorMessage {
  return {
    sessionId: 'sess-1',
    role: 'ASSISTANT',
    content: 'Hello',
    contentFormat: 'PLAIN',
    status: 'COMPLETED',
    seqNo: 1,
    createdAt: '2026-05-10T10:00:00.000Z',
    workflowNodeInstanceId: null,
    ...overrides,
  }
}

function makeConfirmation(overrides: Partial<ProjectorConfirmation> & { id: string }): ProjectorConfirmation {
  return {
    requestType: 'CHOOSE',
    status: 'PENDING',
    title: 'Please choose',
    content: null,
    optionsJson: null,
    interactionType: null,
    agentSessionId: 'sess-1',
    ...overrides,
  }
}

function makeInput(overrides: Partial<ProjectorInput> = {}): ProjectorInput {
  return {
    messages: [],
    runtimeEvents: [],
    confirmations: [],
    streamingText: '',
    activeSessionId: 'sess-1',
    running: false,
    ...overrides,
  }
}

function findToolParts(turn: ConversationTurnProjection): ToolInvocationPart[] {
  return turn.steps.flatMap(s => s.parts.filter((p): p is ToolInvocationPart => p.type === 'tool'))
}

// ─── Tests ───────────────────────────────────────────────────────────

describe('projectConversationTurns', () => {
  // ── 1. Same-name tool twice: different toolCallId → two separate steps ──
  it('creates two separate ExecutionSteps for same-name tool calls with different toolCallIds', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-1',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          createdAt: '2026-05-10T10:00:01.000Z',
          payloadJson: JSON.stringify({ toolName: 'read_file', toolCallId: 'tc-1', label: 'read_file' }),
        }),
        makeEvent({
          id: 'ev-2',
          eventType: 'SKILL_STARTED',
          seqNo: 2,
          createdAt: '2026-05-10T10:00:02.000Z',
          payloadJson: JSON.stringify({ toolName: 'read_file', toolCallId: 'tc-2', label: 'read_file' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toHaveLength(1)

    const toolParts = findToolParts(turns[0])
    expect(toolParts).toHaveLength(2)
    expect(toolParts[0].toolCallId).toBe('tc-1')
    expect(toolParts[1].toolCallId).toBe('tc-2')
    // Must be separate steps, not merged
    expect(turns[0].steps.length).toBeGreaterThanOrEqual(2)
  })

  // ── 2. Same toolCallId lifecycle: merge SKILL_STARTED + SKILL_COMPLETED ──
  it('merges SKILL_STARTED and SKILL_COMPLETED with same toolCallId into one step', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-1',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          createdAt: '2026-05-10T10:00:01.000Z',
          payloadJson: JSON.stringify({ toolName: 'bash', toolCallId: 'tc-100', label: 'bash', command: 'ls' }),
        }),
        makeEvent({
          id: 'ev-2',
          eventType: 'SKILL_COMPLETED',
          seqNo: 2,
          createdAt: '2026-05-10T10:00:02.000Z',
          payloadJson: JSON.stringify({ toolName: 'bash', toolCallId: 'tc-100', label: 'bash', output: 'file1.txt\nfile2.txt', success: true }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toHaveLength(1)

    const toolParts = findToolParts(turns[0])
    expect(toolParts).toHaveLength(1)
    expect(toolParts[0].toolCallId).toBe('tc-100')
    expect(toolParts[0].status).toBe('completed')
    expect(toolParts[0].rawName).toBe('bash')
  })

  // ── 3. Same timestamp, different seqNo → ordered by seqNo ──
  it('orders events by seqNo when createdAt is identical', () => {
    const ts = '2026-05-10T10:00:00.000Z'
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-c',
          eventType: 'SKILL_STARTED',
          seqNo: 3,
          createdAt: ts,
          payloadJson: JSON.stringify({ toolName: 'tool-c', toolCallId: 'tc-c', label: 'tool-c' }),
        }),
        makeEvent({
          id: 'ev-a',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          createdAt: ts,
          payloadJson: JSON.stringify({ toolName: 'tool-a', toolCallId: 'tc-a', label: 'tool-a' }),
        }),
        makeEvent({
          id: 'ev-b',
          eventType: 'SKILL_STARTED',
          seqNo: 2,
          createdAt: ts,
          payloadJson: JSON.stringify({ toolName: 'tool-b', toolCallId: 'tc-b', label: 'tool-b' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    const toolParts = findToolParts(turns[0])
    expect(toolParts).toHaveLength(3)
    expect(toolParts[0].rawName).toBe('tool-a')
    expect(toolParts[1].rawName).toBe('tool-b')
    expect(toolParts[2].rawName).toBe('tool-c')
  })

  // ── 4. Tool failure: SKILL_COMPLETED with isError=true ──
  it('marks step as failed when SKILL_COMPLETED has isError=true', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-1',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          payloadJson: JSON.stringify({ toolName: 'deploy', toolCallId: 'tc-fail', label: 'deploy' }),
        }),
        makeEvent({
          id: 'ev-2',
          eventType: 'SKILL_COMPLETED',
          seqNo: 2,
          payloadJson: JSON.stringify({ toolName: 'deploy', toolCallId: 'tc-fail', label: 'deploy', isError: true, errorMessage: 'Connection refused' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    const toolParts = findToolParts(turns[0])
    expect(toolParts).toHaveLength(1)
    expect(toolParts[0].status).toBe('failed')
    expect(toolParts[0].rawName).toBe('deploy')
  })

  // ── 5. Waiting user input: CONFIRMATION_CREATED → InteractionProjection present ──
  it('creates pendingInteraction when CONFIRMATION_CREATED event is present', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-conf',
          eventType: 'CONFIRMATION_CREATED',
          seqNo: 1,
          payloadJson: JSON.stringify({
            confirmationId: 'conf-1',
            question: 'Deploy to production?',
            options: JSON.stringify([
              { value: 'yes', label: 'Yes, deploy' },
              { value: 'no', label: 'No, cancel' },
            ]),
          }),
        }),
      ],
      confirmations: [
        makeConfirmation({
          id: 'conf-1',
          status: 'PENDING',
          title: 'Deploy to production?',
          optionsJson: JSON.stringify([
            { value: 'yes', label: 'Yes, deploy' },
            { value: 'no', label: 'No, cancel' },
          ]),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toHaveLength(1)
    expect(turns[0].pendingInteraction).toBeDefined()
    expect(turns[0].status).toBe('waiting_input')
    const interaction = turns[0].pendingInteraction!
    expect(interaction.confirmationId).toBe('conf-1')
    expect(interaction.question).toBe('Deploy to production?')
    expect(interaction.options).toHaveLength(2)
    expect(turns[0].steps.some(step => step.kind === 'decision')).toBe(false)
  })

  it('keeps active confirmation choices out of execution steps', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-tool',
          eventType: 'SKILL_COMPLETED',
          seqNo: 1,
          payloadJson: JSON.stringify({ toolName: 'hld-design', toolCallId: 'tc-1', output: 'HLD failed', success: false }),
        }),
        makeEvent({
          id: 'ev-conf',
          eventType: 'CONFIRMATION_CREATED',
          seqNo: 2,
          payloadJson: JSON.stringify({
            confirmationId: 'conf-exception',
            question: 'FE2003 方案设计 (HLD) · 执行异常',
            options: JSON.stringify([
              { value: 'RETRY', label: '重试当前节点' },
              { value: 'SKIP', label: '跳过该节点继续' },
            ]),
          }),
        }),
      ],
      confirmations: [
        makeConfirmation({
          id: 'conf-exception',
          status: 'PENDING',
          title: 'FE2003 方案设计 (HLD) · 执行异常',
        }),
      ],
    })

    const turns = projectConversationTurns(input)

    expect(turns[0].pendingInteraction?.confirmationId).toBe('conf-exception')
    expect(turns[0].steps.map(step => step.kind)).toEqual(['tool'])
  })

  it('summarizes verbose tool output instead of using raw output as the step summary', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-grep',
          eventType: 'SKILL_COMPLETED',
          payloadJson: JSON.stringify({
            toolName: 'grep',
            toolCallId: 'grep-1',
            output: 'Found 25 match(es) in 25 file(s) /Users/hzz/workspace/AgentCenter/agentcenter-web/src/App.vue /Users/hzz/workspace/AgentCenter/agentcenter-web/src/main.ts',
          }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)

    expect(turns[0].steps[0].summary).toBe('找到 25 处匹配，涉及 25 个文件。')
    const toolPart = findToolParts(turns[0])[0]
    expect(toolPart.outputSummary).toContain('/Users/hzz/workspace/AgentCenter/agentcenter-web/src/App.vue')
  })

  it('hides stale confirmation-created events when there is no active confirmation', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-conf',
          eventType: 'CONFIRMATION_CREATED',
          seqNo: 1,
          payloadJson: JSON.stringify({
            id: 'conf-created-id',
            question: '进入下一步？',
            options: JSON.stringify([
              { value: 'ADVANCE', label: '进入下一步' },
            ]),
          }),
        }),
      ],
      confirmations: [],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toEqual([])
  })

  it('shows only the selected option for resolved workflow advance decisions', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-conf-resolved',
          eventType: 'CONFIRMATION_RESOLVED',
          seqNo: 1,
          payloadJson: JSON.stringify({
            confirmationId: 'conf-advance',
            actionType: 'ADVANCE',
            question: '当前节点已完成，请选择下一步操作。',
            options: JSON.stringify([
              { value: 'ADVANCE', label: '进入下一节点' },
              { value: 'SUPPLEMENT', label: '继续补充当前节点' },
              { value: 'RETRY', label: '重新执行' },
            ]),
          }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    const decisionPart = turns[0].steps
      .flatMap(step => step.parts)
      .find(part => part.type === 'decision')

    expect(decisionPart?.type).toBe('decision')
    if (decisionPart?.type === 'decision') {
      expect(decisionPart.status).toBe('resolved')
      expect(decisionPart.options).toEqual([{ value: 'ADVANCE', label: '进入下一节点' }])
      expect(decisionPart.recommended).toBe('ADVANCE')
    }
  })

  it('shows only the approved option for resolved artifact review decisions', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-review-resolved',
          eventType: 'CONFIRMATION_RESOLVED',
          seqNo: 1,
          payloadJson: JSON.stringify({
            confirmationId: 'conf-review',
            actionType: 'APPROVE',
            title: '审阅 LLD 草稿',
            question: '请确认 LLD 草稿是否可以作为最终产物。',
            options: JSON.stringify([
              { id: 'PASS', label: '通过' },
              { id: 'REVISE', label: '需要调整' },
            ]),
            actionDescription: '用户确认通过',
          }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    const step = turns[0].steps[0]
    const decisionPart = step.parts.find(part => part.type === 'decision')

    expect(step.title).toBe('用户确认通过')
    expect(decisionPart?.type).toBe('decision')
    if (decisionPart?.type === 'decision') {
      expect(decisionPart.status).toBe('resolved')
      expect(decisionPart.options).toEqual([{ value: 'PASS', label: '通过' }])
    }
  })

  // ── 6. Artifact before confirmation ──
  it('attaches artifact as step part and confirmation as pendingInteraction', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-artifact',
          eventType: 'PROCESS_TRACE',
          seqNo: 1,
          payloadJson: JSON.stringify({
            kind: 'artifact',
            title: 'Generated report.md',
            filePath: '/output/report.md',
            artifactId: 'art-1',
          }),
        }),
        makeEvent({
          id: 'ev-conf',
          eventType: 'CONFIRMATION_CREATED',
          seqNo: 2,
          payloadJson: JSON.stringify({
            confirmationId: 'conf-1',
            question: 'Review the generated report?',
          }),
        }),
      ],
      confirmations: [
        makeConfirmation({
          id: 'conf-1',
          status: 'PENDING',
          title: 'Review the generated report?',
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toHaveLength(1)
    // Artifact should be a step part
    const artifactParts = turns[0].steps.flatMap(s =>
      s.parts.filter(p => p.type === 'artifact')
    )
    expect(artifactParts.length).toBeGreaterThanOrEqual(1)
    // Confirmation should be pendingInteraction
    expect(turns[0].pendingInteraction).toBeDefined()
    expect(turns[0].pendingInteraction!.confirmationId).toBe('conf-1')
  })

  // ── 7. History vs live order consistency ──
  it('produces same visual order for events with and without seqNo', () => {
    const baseEvents: Array<Partial<ProjectorRuntimeEvent> & { id: string }> = [
      {
        id: 'ev-1',
        eventType: 'SKILL_STARTED',
        payloadJson: JSON.stringify({ toolName: 'read', toolCallId: 'tc-1', label: 'read' }),
        createdAt: '2026-05-10T10:00:01.000Z',
      },
      {
        id: 'ev-2',
        eventType: 'SKILL_COMPLETED',
        payloadJson: JSON.stringify({ toolName: 'read', toolCallId: 'tc-1', label: 'read', success: true }),
        createdAt: '2026-05-10T10:00:02.000Z',
      },
      {
        id: 'ev-3',
        eventType: 'SKILL_STARTED',
        payloadJson: JSON.stringify({ toolName: 'write', toolCallId: 'tc-2', label: 'write' }),
        createdAt: '2026-05-10T10:00:03.000Z',
      },
    ]

    // With seqNo (history)
    const withSeq = projectConversationTurns(makeInput({
      runtimeEvents: baseEvents.map((e, i) =>
        makeEvent({ ...e, seqNo: i + 1 })
      ),
    }))

    // Without seqNo (live stream, fallback to createdAt)
    const withoutSeq = projectConversationTurns(makeInput({
      runtimeEvents: baseEvents.map(e =>
        makeEvent({ ...e, seqNo: null })
      ),
    }))

    const toolNamesWith = findToolParts(withSeq[0]).map(p => p.rawName)
    const toolNamesWithout = findToolParts(withoutSeq[0]).map(p => p.rawName)

    expect(toolNamesWith).toEqual(toolNamesWithout)
  })

  // ── 8. User message creates turn boundary ──
  it('creates separate turns for USER message followed by ASSISTANT message + events', () => {
    const input = makeInput({
      messages: [
        makeMessage({
          id: 'msg-1',
          role: 'USER',
          content: 'Please analyze this code',
          seqNo: 1,
          createdAt: '2026-05-10T10:00:00.000Z',
        }),
        makeMessage({
          id: 'msg-2',
          role: 'ASSISTANT',
          content: 'I have analyzed the code.',
          seqNo: 2,
          createdAt: '2026-05-10T10:00:10.000Z',
        }),
      ],
      runtimeEvents: [
        makeEvent({
          id: 'ev-1',
          eventType: 'SKILL_STARTED',
          seqNo: 3,
          createdAt: '2026-05-10T10:00:05.000Z',
          payloadJson: JSON.stringify({ toolName: 'analyze', toolCallId: 'tc-1', label: 'analyze' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns.length).toBeGreaterThanOrEqual(1)
    // USER + ASSISTANT form a single conversational turn
    const assistantTurn = turns.find(t => t.answer.text.length > 0)
    expect(assistantTurn).toBeDefined()
    expect(assistantTurn!.answer.text).toContain('analyzed')
  })

  // ── 9. Streaming text ──
  it('sets answer.streaming = true when streamingText is present', () => {
    const input = makeInput({
      streamingText: 'I am currently thinking about',
      running: true,
    })

    const turns = projectConversationTurns(input)
    expect(turns.length).toBeGreaterThanOrEqual(1)
    // Find the turn with streaming text
    const streamingTurn = turns.find(t => t.answer.streaming === true)
    expect(streamingTurn).toBeDefined()
    expect(streamingTurn!.answer.text).toContain('thinking')
  })

  // ── 10. prompt_debug hidden ──
  it('hides PROCESS_TRACE with kind=prompt_debug from steps, only in debugRefs', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-debug',
          eventType: 'PROCESS_TRACE',
          seqNo: 1,
          payloadJson: JSON.stringify({ kind: 'prompt_debug', title: 'System prompt' }),
        }),
        makeEvent({
          id: 'ev-visible',
          eventType: 'PROCESS_TRACE',
          seqNo: 2,
          payloadJson: JSON.stringify({ kind: 'reasoning_summary', title: 'Thinking', summary: '整理上下文' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns).toHaveLength(1)
    // prompt_debug should NOT appear in step parts
    const allParts = turns[0].steps.flatMap(s => s.parts)
    const hasPromptDebug = allParts.some(p =>
      p.type === 'raw' && (p as any).payload?.kind === 'prompt_debug'
    )
    expect(hasPromptDebug).toBe(false)
    // prompt_debug should be in debugRefs
    expect(turns[0].debugRefs.some(r => r.eventId === 'ev-debug')).toBe(true)
    // But visible event should still produce a step
    expect(turns[0].steps.length).toBeGreaterThanOrEqual(1)
  })

  it('filters node_status heartbeat events out of execution steps', () => {
    const turns = projectConversationTurns(makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-status',
          eventType: 'PROCESS_TRACE',
          payloadJson: JSON.stringify({ kind: 'node_status', title: 'running', status: 'completed' }),
        }),
      ],
    }))

    expect(turns).toEqual([])
  })

  it('filters runtime STATUS heartbeat events out of execution steps', () => {
    const turns = projectConversationTurns(makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-status-running',
          eventType: 'STATUS',
          seqNo: 1,
          payloadJson: JSON.stringify({ type: 'status', label: 'running', rawEventType: 'session.status' }),
        }),
        makeEvent({
          id: 'ev-status-idle',
          eventType: 'STATUS',
          seqNo: 2,
          payloadJson: JSON.stringify({ type: 'status', label: 'idle', rawEventType: 'session.status' }),
        }),
      ],
    }))

    expect(turns).toEqual([])
  })

  it('hides OpenCode internal skill loader calls from execution steps', () => {
    const turns = projectConversationTurns(makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-skill-start',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          payloadJson: JSON.stringify({
            type: 'skill_started',
            label: 'skill',
            rawPartType: 'tool',
            toolName: 'skill',
            toolCallId: 'call-skill',
          }),
        }),
        makeEvent({
          id: 'ev-skill-done',
          eventType: 'SKILL_COMPLETED',
          seqNo: 2,
          payloadJson: JSON.stringify({
            type: 'skill_completed',
            label: 'skill',
            rawPartType: 'tool',
            toolName: 'skill',
            toolCallId: 'call-skill',
            output: '## Skill: prd-desingn\n\n**Base directory**: /runtime-workspace/.opencode/skills/prd-desingn',
          }),
        }),
        makeEvent({
          id: 'ev-node-start',
          eventType: 'SKILL_STARTED',
          seqNo: 3,
          payloadJson: JSON.stringify({ skillName: 'prd-desingn' }),
        }),
      ],
    }))

    expect(turns[0].steps).toHaveLength(1)
    expect(turns[0].steps[0].title).toBe('prd-desingn')
  })

  it('projects markdown-like context output as text instead of raw JSON', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-markdown',
          eventType: 'PROCESS_TRACE',
          payloadJson: JSON.stringify({
            summary: 'PRD 产物信息充分，直接生成 HLD： --- # HLD: FE2005 文件上传组件升级\\n\\n## 1. 设计目标\\n- **业务目标**: 升级前端文件上传组件',
          }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    expect(turns[0].steps[0].title).toBe('HLD: FE2005 文件上传组件升级')
    expect(turns[0].steps[0].parts[0].type).toBe('text')
    if (turns[0].steps[0].parts[0].type === 'text') {
      expect(turns[0].steps[0].parts[0].text).toContain('# HLD: FE2005 文件上传组件升级')
      expect(turns[0].steps[0].parts[0].text).toContain('---')
    }
  })

  // ── 11. Empty input ──
  it('returns empty array for empty input', () => {
    const turns = projectConversationTurns(makeInput())
    expect(turns).toEqual([])
  })

  // ── 12. Legacy SKILL_STARTED fallback (no toolCallId) ──
  it('creates separate steps for legacy SKILL_STARTED without toolCallId, does NOT merge by name', () => {
    const input = makeInput({
      runtimeEvents: [
        makeEvent({
          id: 'ev-1',
          eventType: 'SKILL_STARTED',
          seqNo: 1,
          payloadJson: JSON.stringify({ toolName: 'read_file', label: 'read_file' }),
        }),
        makeEvent({
          id: 'ev-2',
          eventType: 'SKILL_COMPLETED',
          seqNo: 2,
          payloadJson: JSON.stringify({ toolName: 'read_file', label: 'read_file', success: true, output: 'content1' }),
        }),
        makeEvent({
          id: 'ev-3',
          eventType: 'SKILL_STARTED',
          seqNo: 3,
          payloadJson: JSON.stringify({ toolName: 'read_file', label: 'read_file' }),
        }),
        makeEvent({
          id: 'ev-4',
          eventType: 'SKILL_COMPLETED',
          seqNo: 4,
          payloadJson: JSON.stringify({ toolName: 'read_file', label: 'read_file', success: true, output: 'content2' }),
        }),
      ],
    })

    const turns = projectConversationTurns(input)
    // Even though toolName is the same, without toolCallId they MUST NOT merge
    // Each STARTED+COMPLETED pair should become its own step
    const toolParts = findToolParts(turns[0])
    expect(toolParts).toHaveLength(2)
    expect(toolParts[0].rawName).toBe('read_file')
    expect(toolParts[1].rawName).toBe('read_file')
  })
})
