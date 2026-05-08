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
    expect(wrapper.text()).not.toContain('skill_started')
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

  it('accepts runtimeEvents prop without rendering them as message cards', () => {
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
    expect(wrapper.find('.mocked-process-trace').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('reasoning_summary')
    expect(wrapper.text()).not.toContain('skill_started')
  })

  it('shows empty state when no messages are provided', () => {
    const wrapper = mount(MessageList, {
      props: { messages: [] },
    })
    expect(wrapper.find('.message-list__empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('会话已就绪')
  })

  it('renders ProcessTrace under ASSISTANT message with workflowNodeInstanceId', () => {
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
    const trace = wrapper.findComponent({ name: 'ProcessTrace' })
    expect(trace.exists()).toBe(true)
    expect(trace.attributes('data-node-id')).toBe('node-1')
    const card = wrapper.find('.assistant-card')
    expect(card.findComponent({ name: 'ProcessTrace' }).exists()).toBe(true)
  })

  it('renders a live process turn before final assistant text is available', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [makeMessage({ role: 'USER', content: '开始工作流' })],
        runtimeEvents: [],
        activeNodeId: 'node-running',
        activeNodeState: 'RUNNING',
        activeSessionId: 'session-1',
        running: true,
      },
    })

    const traces = wrapper.findAll('.mocked-process-trace')
    expect(traces).toHaveLength(1)
    expect(traces[0].attributes('data-node-id')).toBe('node-running')
    expect(traces[0].attributes('data-expanded')).toBe('true')
    expect(traces[0].attributes('data-empty')).toBe('true')
    expect(wrapper.text()).toContain('助手正在处理')
  })
})
