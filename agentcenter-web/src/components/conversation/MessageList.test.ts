import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageList from './MessageList.vue'
import type { AgentMessageDto, RuntimeEventDto } from '../../api/types'

vi.mock('./MarkdownContent.vue', () => ({
  default: {
    name: 'MarkdownContent',
    props: ['content'],
    template: '<pre class="mocked-markdown">{{ content }}</pre>',
  },
}))

vi.mock('./AssistantTurn.vue', () => ({
  default: {
    name: 'AssistantTurn',
    props: ['turn'],
    template: '<div class="mocked-assistant-turn">{{ JSON.stringify(turn) }}</div>',
  },
}))

function makeMessage(overrides: Partial<AgentMessageDto> = {}): AgentMessageDto {
  return {
    id: 'msg-1',
    sessionId: 'session-1',
    role: 'USER',
    content: 'Hello',
    contentFormat: 'TEXT',
    status: 'COMPLETED',
    seqNo: 1,
    createdAt: '2026-05-09T00:00:00Z',
    workflowNodeInstanceId: null,
    ...overrides,
  }
}

function makeRuntimeEvent(overrides: Partial<RuntimeEventDto> = {}): RuntimeEventDto {
  return {
    id: 'evt-1',
    sessionId: 'session-1',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    eventType: 'PROCESS_TRACE',
    eventSource: 'OPENCODE',
    payloadJson: '{"kind":"reasoning_summary","title":"test"}',
    createdAt: '2026-05-09T00:00:00Z',
    ...overrides,
  }
}

function parseTurnFromMock(wrapper: ReturnType<typeof mount>) {
  const el = wrapper.find('.mocked-assistant-turn')
  if (!el.exists()) return null
  return JSON.parse(el.text())
}

describe('MessageList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not render activity-group or activity-card elements', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: 'Hello' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-1',
            eventType: 'SKILL_STARTED',
            payloadJson: '{"type":"skill_started","label":"test-skill"}',
          }),
        ],
      },
    })
    expect(wrapper.find('.activity-group').exists()).toBe(false)
    expect(wrapper.find('.activity-card').exists()).toBe(false)
    expect(wrapper.find('[data-testid="activity-group"]').exists()).toBe(false)
    expect(wrapper.find('.mocked-assistant-turn').exists()).toBe(true)
  })

  it('renders user messages correctly', () => {
    const wrapper = mount(MessageList, {
      props: { messages: [makeMessage({ role: 'USER', content: 'Hello world' })] },
    })
    expect(wrapper.text()).toContain('Hello world')
    expect(wrapper.find('.user-turn').exists()).toBe(true)
  })

  it('renders markdown user messages as structured workflow input', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({
          role: 'USER',
          contentFormat: 'MARKDOWN',
          content: '请执行工作流节点：需求整理 (PRD)\n\n## 用户输入\n\n- 工作项编号：FE1234\n- 工作项标题：用户登录优化\n- 工作项状态：BACKLOG\n- 优先级：HIGH\n- 使用 Skill：prd-desingn\n\n## 任务信息\n\n```text\n用户输入：作为产品负责人，我要验证工作流上下文。\n```',
        })],
      },
    })
    expect(wrapper.find('.user-turn').exists()).toBe(true)
    expect(wrapper.find('.user-bubble--workflow').exists()).toBe(true)
    expect(wrapper.find('.workflow-input-card__eyebrow').text()).toBe('用户输入')
    expect(wrapper.find('.workflow-input-card__title').text()).toContain('请基于 FE1234')
    expect(wrapper.find('.workflow-input-card__desc').text()).toContain('用户输入：作为产品负责人')
    expect(wrapper.find('.workflow-input-card__details').attributes('open')).toBeUndefined()
  })

  it('renders assistant messages via AssistantTurn projection', () => {
    const wrapper = mount(MessageList, {
      props: { messages: [makeMessage({ role: 'ASSISTANT', content: '# Response' })] },
    })
    expect(wrapper.find('.mocked-assistant-turn').exists()).toBe(true)
  })

  it('renders persisted messages in seqNo order even if input arrives out of order', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({ id: 'msg-2', role: 'ASSISTANT', content: '后面的回复', seqNo: 2, createdAt: '2026-05-09T00:00:02Z' }),
          makeMessage({ id: 'msg-1', role: 'USER', content: '前面的提问', seqNo: 1, createdAt: '2026-05-09T00:00:01Z' }),
        ],
      },
    })
    const text = wrapper.text()
    expect(text.indexOf('前面的提问')).toBeLessThan(text.indexOf('后面的回复'))
  })

  it('renders system messages correctly', () => {
    const wrapper = mount(MessageList, {
      props: { messages: [makeMessage({ role: 'SYSTEM', content: '节点已完成' })] },
    })
    expect(wrapper.text()).toContain('节点已完成')
    expect(wrapper.find('.system-line').exists()).toBe(true)
  })

  it('renders runtime process events as ordered execution steps', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: 'reply' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-2', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"reasoning_summary","title":"thinking"}' }),
          makeRuntimeEvent({ id: 'evt-3', eventType: 'SKILL_STARTED', payloadJson: '{"type":"skill_started","label":"build-skill"}' }),
        ],
      },
    })
    expect(wrapper.find('.message-list').exists()).toBe(true)
    expect(wrapper.find('.mocked-assistant-turn').exists()).toBe(true)
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    expect(turn.steps.length).toBeGreaterThanOrEqual(1)
  })

  it('aggregates one skill lifecycle into a single execution step', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-s1', eventType: 'SKILL_STARTED', payloadJson: '{"type":"skill_started","label":"skill","toolCallId":"call_1"}', seqNo: 1 }),
          makeRuntimeEvent({ id: 'evt-s2', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"tool_call","status":"running","summary":"正在调用 skill","toolName":"skill","toolCallId":"call_1"}', seqNo: 2 }),
          makeRuntimeEvent({ id: 'evt-s3', eventType: 'SKILL_COMPLETED', payloadJson: '{"type":"skill_completed","label":"skill","toolCallId":"call_1","output":"skill 输出"}', seqNo: 3 }),
          makeRuntimeEvent({ id: 'evt-s4', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"tool_call","status":"completed","summary":"skill 调用完成","toolName":"skill","toolCallId":"call_1"}', seqNo: 4 }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const toolSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'tool')
    expect(toolSteps.length).toBe(1)
  })

  it('renders real tool completed output as tool invocation', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-tool-output',
            eventType: 'SKILL_COMPLETED',
            payloadJson: '{"type":"skill_completed","label":"skill","output":"## Skill Output\\n\\n真实工具返回内容","isError":false,"toolCallId":"call_1"}',
          }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const toolSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'tool')
    expect(toolSteps.length).toBeGreaterThanOrEqual(1)
  })

  it('hides node status runtime events from execution steps', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: 'reply' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-status',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}',
          }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const statusSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'status')
    expect(statusSteps.length).toBe(0)
  })

  it('filters repeated node status heartbeat events', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: 'reply' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-status-1', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}', seqNo: 1 }),
          makeRuntimeEvent({ id: 'evt-status-2', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}', seqNo: 2 }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const statusSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'status')
    expect(statusSteps.length).toBe(0)
  })

  it('keeps pending confirmation prompts out of the chat timeline', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ id: 'msg-a', role: 'ASSISTANT', content: '# PRD', seqNo: 2, createdAt: '2026-05-09T00:00:02Z' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-c1', eventType: 'CONFIRMATION_CREATED', payloadJson: '{"confirmationId":"confirm-1"}', createdAt: '2026-05-09T00:00:03Z' }),
          makeRuntimeEvent({ id: 'evt-c2', eventType: 'CONFIRMATION_CREATED', payloadJson: '{"confirmationId":"confirm-2"}', createdAt: '2026-05-09T00:00:03Z' }),
        ],
      },
    })
    expect(wrapper.find('.confirmation-gate').exists()).toBe(false)
    expect(wrapper.find('.turn-note--interaction').exists()).toBe(false)
    expect(wrapper.findAll('.mocked-assistant-turn')).toHaveLength(1)
  })

  it('renders confirmation resolution as decision step', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ id: 'msg-a', role: 'ASSISTANT', content: '# PRD', seqNo: 2, createdAt: '2026-05-09T00:00:02Z' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-c1', eventType: 'CONFIRMATION_CREATED', payloadJson: '{"confirmationId":"confirm-1"}', seqNo: 1 }),
          makeRuntimeEvent({ id: 'evt-c2', eventType: 'CONFIRMATION_CREATED', payloadJson: '{"confirmationId":"confirm-2"}', seqNo: 2 }),
          makeRuntimeEvent({
            id: 'evt-cr',
            eventType: 'CONFIRMATION_RESOLVED',
            payloadJson: '{"confirmationId":"confirm-1","actionType":"CHOOSE","requestType":"DECISION","title":"选择方案","question":"请选择 HLD 推进方案"}',
            seqNo: 3,
          }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const decisionSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'decision')
    expect(decisionSteps.length).toBeGreaterThanOrEqual(1)
  })

  it('renders confirmation response messages as compact user feedback', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({
            id: 'msg-response',
            role: 'USER',
            contentFormat: 'TEXT',
            content: '用户输入：用户选择：快速验证（FAST）\n确认项：LLD 实现路线\n类型：DECISION\n节点：01NODE_INTERNAL',
          }),
        ],
      },
    })

    expect(wrapper.find('.interaction-response-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('用户反馈')
    expect(wrapper.text()).toContain('LLD 实现路线')
    expect(wrapper.text()).toContain('选择：快速验证（FAST）')
    expect(wrapper.text()).not.toContain('类型：DECISION')
    expect(wrapper.text()).not.toContain('01NODE_INTERNAL')
  })

  it('dedupes identical assistant artifact messages before projection', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({ id: 'msg-a1', role: 'ASSISTANT', content: '# PRD\n\n同一份产物', seqNo: 1, workflowNodeInstanceId: null }),
          makeMessage({ id: 'msg-a2', role: 'ASSISTANT', content: '# PRD\n\n同一份产物', seqNo: 2, workflowNodeInstanceId: 'node-1' }),
        ],
      },
    })

    expect(wrapper.findAll('.mocked-assistant-turn')).toHaveLength(1)
    const turn = parseTurnFromMock(wrapper)
    expect(turn?.answer.text).toBe('# PRD\n\n同一份产物')
  })

  it('shows empty state when no messages are provided', () => {
    const wrapper = mount(MessageList, { props: { messages: [] } })
    expect(wrapper.find('.message-list__empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('会话已就绪')
  })

  it('renders tool_call process trace as execution step', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ id: 'msg-a', role: 'ASSISTANT', content: '# Output', workflowNodeInstanceId: 'node-1' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-t', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"tool_call","status":"completed","summary":"read_file done","toolName":"read_file"}', workflowNodeInstanceId: 'node-1' }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const toolSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'tool')
    expect(toolSteps.length).toBe(1)
  })

  it('does not render prompt debug in the conversation timeline', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '检查 prompt' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-pd', eventType: 'PROCESS_TRACE', payloadJson: JSON.stringify({ kind: 'prompt_debug', systemPrompt: 'debug system prompt', userPrompt: 'debug user prompt' }) }),
        ],
      },
    })
    expect(wrapper.text()).toContain('检查 prompt')
    expect(wrapper.text()).not.toContain('debug system prompt')
    expect(wrapper.text()).not.toContain('debug user prompt')
  })

  it('renders MCP call details as tool step', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '查一下文件' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-mcp', eventType: 'MCP_CALL', payloadJson: '{"toolName":"bash","command":"ls","args":{"cwd":"/tmp"},"output":"a.txt\\nb.txt"}' }),
        ],
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const toolSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'tool')
    expect(toolSteps.length).toBe(1)
  })

  it('renders running tool calls via projection', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [
          makeRuntimeEvent({ id: 'evt-r', eventType: 'PROCESS_TRACE', payloadJson: '{"kind":"tool_call","status":"running","summary":"真实工具调用"}', workflowNodeInstanceId: 'node-running' }),
        ],
        running: true,
      },
    })
    const turn = parseTurnFromMock(wrapper)
    expect(turn).not.toBeNull()
    const toolSteps = turn.steps.filter((s: { kind: string }) => s.kind === 'tool')
    expect(toolSteps.length).toBe(1)
  })
})
