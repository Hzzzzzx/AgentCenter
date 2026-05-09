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
vi.mock('./ProcessTrace.vue', () => ({
  default: {
    name: 'ProcessTrace',
    props: ['events', 'nodeId', 'sessionId', 'defaultExpanded', 'showWhenEmpty'],
    template: '<div class="mocked-process-trace" :data-node-id="nodeId" :data-session-id="sessionId" :data-expanded="defaultExpanded" :data-empty="showWhenEmpty" />',
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

describe('MessageList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not render bottom activity group or activity card elements', () => {
    const events: RuntimeEventDto[] = [
      makeRuntimeEvent({
        id: 'evt-1',
        eventType: 'SKILL_STARTED',
        payloadJson: '{"type":"skill_started","label":"test-skill"}',
      }),
    ]
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: 'Hello' })],
        runtimeEvents: events,
      },
    })
    expect(wrapper.find('.activity-group').exists()).toBe(false)
    expect(wrapper.find('.activity-card').exists()).toBe(false)
    expect(wrapper.find('[data-testid="activity-group"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('开始执行 Skill')
    expect(wrapper.text()).toContain('test-skill')
  })

  it('renders user messages correctly', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: 'Hello world' })],
      },
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
    expect(wrapper.find('.workflow-input-card__details summary').text()).toContain('查看完整输入上下文')
    expect(wrapper.text()).toContain('工作项编号：FE1234')
    expect(wrapper.text()).toContain('使用 Skill：prd-desingn')
  })

  it('renders assistant (ASSISTANT) messages correctly', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: '# Response' })],
      },
    })
    expect(wrapper.find('.assistant-turn').exists()).toBe(true)
    expect(wrapper.find('.assistant-turn--tool').exists()).toBe(false)
  })

  it('renders persisted messages in seqNo order even if input arrives out of order', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({
            id: 'msg-2',
            role: 'ASSISTANT',
            content: '后面的回复',
            seqNo: 2,
            createdAt: '2026-05-09T00:00:02Z',
          }),
          makeMessage({
            id: 'msg-1',
            role: 'USER',
            content: '前面的提问',
            seqNo: 1,
            createdAt: '2026-05-09T00:00:01Z',
          }),
        ],
      },
    })

    const turns = wrapper.findAll('article')
    expect(turns[0].text()).toContain('前面的提问')
    expect(turns[1].text()).toContain('后面的回复')
  })

  it('renders system (SYSTEM) messages correctly', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'SYSTEM', content: '节点已完成' })],
      },
    })
    expect(wrapper.text()).toContain('节点已完成')
    expect(wrapper.find('.system-line').exists()).toBe(true)
  })

  it('renders runtime process events as standalone timeline messages', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: 'reply' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-2',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"reasoning_summary","title":"thinking"}',
          }),
          makeRuntimeEvent({
            id: 'evt-3',
            eventType: 'SKILL_STARTED',
            payloadJson: '{"type":"skill_started","label":"build-skill"}',
          }),
        ],
      },
    })
    expect(wrapper.find('.message-list').exists()).toBe(true)
    expect(wrapper.find('.assistant-turn').exists()).toBe(true)
    expect(wrapper.find('.turn-section').exists()).toBe(false)
    expect(wrapper.findAll('.runtime-event--trace')).toHaveLength(1)
    expect(wrapper.findAll('.runtime-event--tool')).toHaveLength(1)
    expect(wrapper.text()).toContain('thinking')
    expect(wrapper.text()).toContain('build-skill')
  })

  it('aggregates one skill lifecycle into a single tool message', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-skill-started',
            eventType: 'SKILL_STARTED',
            payloadJson: '{"type":"skill_started","label":"skill","toolCallId":"call_1"}',
            seqNo: 1,
          }),
          makeRuntimeEvent({
            id: 'evt-skill-running',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"tool_call","status":"running","summary":"正在调用 skill","toolName":"skill","toolCallId":"call_1"}',
            seqNo: 2,
          }),
          makeRuntimeEvent({
            id: 'evt-skill-completed',
            eventType: 'SKILL_COMPLETED',
            payloadJson: '{"type":"skill_completed","label":"skill","toolCallId":"call_1","output":"skill 输出"}',
            seqNo: 3,
          }),
          makeRuntimeEvent({
            id: 'evt-skill-trace-completed',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"tool_call","status":"completed","summary":"skill 调用完成","toolName":"skill","toolCallId":"trace_call_1"}',
            seqNo: 4,
          }),
        ],
      },
    })

    expect(wrapper.findAll('.runtime-event--tool')).toHaveLength(1)
    expect(wrapper.find('.runtime-event--tool').text()).toContain('工具调用：skill')
    expect(wrapper.find('.runtime-event--tool').text()).toContain('skill 调用完成')
    expect(wrapper.find('.runtime-event--tool').text()).toContain('skill 输出')
  })

  it('renders real tool completed output as its own tool message', () => {
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

    expect(wrapper.find('.tool-block').exists()).toBe(false)
    expect(wrapper.find('.runtime-event--tool').exists()).toBe(true)
    expect(wrapper.text()).toContain('Skill 执行完成')
    expect(wrapper.text()).toContain('真实工具返回内容')
  })

  it('renders node status runtime events without filtering them out', () => {
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

    expect(wrapper.find('.turn-section').exists()).toBe(false)
    expect(wrapper.text()).toContain('节点状态')
    expect(wrapper.text()).toContain('Agent 正在处理当前请求')
  })

  it('deduplicates repeated running node status heartbeat events', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'ASSISTANT', content: 'reply' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-status-1',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}',
            seqNo: 1,
          }),
          makeRuntimeEvent({
            id: 'evt-status-2',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}',
            seqNo: 2,
          }),
        ],
      },
    })

    expect(wrapper.findAll('.runtime-event--trace')).toHaveLength(1)
    expect(wrapper.text().match(/Agent 正在处理当前请求/g)).toHaveLength(1)
  })

  it('keeps pending confirmation prompts out of the chat timeline', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({
            id: 'msg-assistant',
            role: 'ASSISTANT',
            content: '# PRD',
            seqNo: 2,
            createdAt: '2026-05-09T00:00:02Z',
          }),
        ],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-confirm-1',
            eventType: 'CONFIRMATION_CREATED',
            payloadJson: '{"confirmationId":"confirm-1"}',
            createdAt: '2026-05-09T00:00:03Z',
          }),
          makeRuntimeEvent({
            id: 'evt-confirm-2',
            eventType: 'CONFIRMATION_CREATED',
            payloadJson: '{"confirmationId":"confirm-2"}',
            createdAt: '2026-05-09T00:00:03Z',
          }),
        ],
      },
    })

    expect(wrapper.find('.confirmation-gate').exists()).toBe(false)
    expect(wrapper.find('.turn-note--interaction').exists()).toBe(false)
    expect(wrapper.findAll('.runtime-event--interaction')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('触发用户交互')
    expect(wrapper.findAll('.assistant-turn')).toHaveLength(1)
  })

  it('renders confirmation resolution as user input with submitted interaction details', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({
            id: 'msg-assistant',
            role: 'ASSISTANT',
            content: '# PRD',
            seqNo: 2,
            createdAt: '2026-05-09T00:00:02Z',
          }),
        ],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-confirm-1',
            eventType: 'CONFIRMATION_CREATED',
            payloadJson: '{"confirmationId":"confirm-1"}',
            seqNo: 1,
            createdAt: '2026-05-09T00:00:03Z',
          }),
          makeRuntimeEvent({
            id: 'evt-confirm-2',
            eventType: 'CONFIRMATION_CREATED',
            payloadJson: '{"confirmationId":"confirm-2"}',
            seqNo: 2,
            createdAt: '2026-05-09T00:00:03Z',
          }),
          makeRuntimeEvent({
            id: 'evt-confirm-resolved',
            eventType: 'CONFIRMATION_RESOLVED',
            payloadJson: '{"confirmationId":"confirm-1","actionType":"CHOOSE","requestType":"DECISION","title":"选择方案","question":"请选择 HLD 推进方案","contextSummary":"方案 A 更安全，方案 B 成本更低","options":"[{\\"value\\":\\"A\\",\\"label\\":\\"双写（更安全）\\"},{\\"value\\":\\"B\\",\\"label\\":\\"直接切换\\"}]","actionDescription":"用户选择：A: 双写（更安全）","resolutionPayload":"{\\"choice\\":\\"A: 双写（更安全）\\"}"}',
            seqNo: 3,
            createdAt: '2026-05-09T00:00:04Z',
          }),
        ],
      },
    })

    expect(wrapper.find('.confirmation-gate').exists()).toBe(false)
    expect(wrapper.findAll('.runtime-event--interaction')).toHaveLength(0)
    expect(wrapper.find('.user-bubble--interaction').exists()).toBe(true)
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('用户输入')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('处理交互：选择方案')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('原始问题：请选择 HLD 推进方案')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('上下文：方案 A 更安全，方案 B 成本更低')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('可选项：A: 双写（更安全）；B: 直接切换')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('用户选择：A: 双写（更安全）')
    expect(wrapper.find('.user-bubble--interaction').text()).toContain('提交内容：choice=A: 双写（更安全）')
  })

  it('shows empty state when no messages are provided', () => {
    const wrapper = mount(MessageList, {
      props: { messages: [] },
    })
    expect(wrapper.find('.message-list__empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('会话已就绪')
  })

  it('renders tool_call process trace as a real timeline message', () => {
    const events: RuntimeEventDto[] = [
      makeRuntimeEvent({
        id: 'evt-trace',
        workflowNodeInstanceId: 'node-1',
        eventType: 'PROCESS_TRACE',
        payloadJson: '{"kind":"tool_call","status":"completed","summary":"read_file done","toolName":"read_file"}',
      }),
    ]
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          makeMessage({
            id: 'msg-assistant',
            role: 'ASSISTANT',
            content: '# Output',
            workflowNodeInstanceId: 'node-1',
          }),
        ],
        runtimeEvents: events,
      },
    })
    expect(wrapper.find('.process-turn').exists()).toBe(false)
    expect(wrapper.text()).toContain('read_file')
    expect(wrapper.find('.runtime-event--tool').exists()).toBe(true)
  })

  it('does not render prompt debug process trace in the conversation timeline', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '检查 prompt' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-prompt-debug',
            eventType: 'PROCESS_TRACE',
            payloadJson: JSON.stringify({
              kind: 'prompt_debug',
              systemPrompt: 'debug system prompt',
              userPrompt: 'debug user prompt',
            }),
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('检查 prompt')
    expect(wrapper.text()).not.toContain('debug system prompt')
    expect(wrapper.text()).not.toContain('debug user prompt')
    expect(wrapper.find('.runtime-event--trace').exists()).toBe(false)
  })

  it('renders MCP call details and output as tool timeline messages', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '查一下文件' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-mcp',
            eventType: 'MCP_CALL',
            payloadJson: '{"toolName":"bash","command":"ls","args":{"cwd":"/tmp"},"output":"a.txt\\nb.txt"}',
          }),
        ],
      },
    })

    expect(wrapper.find('.runtime-event--tool').exists()).toBe(true)
    expect(wrapper.text()).toContain('工具调用：bash')
    expect(wrapper.text()).toContain('命令：ls')
    expect(wrapper.text()).toContain('参数：{"cwd":"/tmp"}')
    expect(wrapper.text()).toContain('a.txt')
  })

  it('renders running tool calls as standalone timeline messages', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [
          makeRuntimeEvent({
            id: 'evt-running',
            workflowNodeInstanceId: 'node-running',
            eventType: 'PROCESS_TRACE',
            payloadJson: '{"kind":"tool_call","status":"running","summary":"真实工具调用"}',
          }),
        ],
        activeNodeId: 'node-running',
        activeNodeState: 'RUNNING',
        activeSessionId: 'session-1',
        running: true,
      },
    })

    const traces = wrapper.findAll('.mocked-process-trace')
    expect(traces).toHaveLength(0)
    expect(wrapper.find('.assistant-turn--live').exists()).toBe(false)
    expect(wrapper.find('.runtime-event--tool').exists()).toBe(true)
    expect(wrapper.text()).toContain('真实工具调用')
  })
})
