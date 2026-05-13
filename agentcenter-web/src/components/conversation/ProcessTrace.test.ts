import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProcessTrace from './ProcessTrace.vue'
import type { RuntimeEventDto } from '../../api/types'

function makeTraceEvent(overrides: Partial<RuntimeEventDto> = {}): RuntimeEventDto {
  return {
    id: 'evt-1',
    sessionId: 'sess-1',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: 'node-1',
    eventType: 'PROCESS_TRACE',
    eventSource: 'OPENCODE',
    payloadJson: '{"kind":"tool_call","status":"completed","title":"调用工具","summary":"read_file 调用完成","toolName":"read_file","toolCallId":"call_1","visibility":"public_summary"}',
    createdAt: '2026-05-09T00:00:00Z',
    ...overrides,
  }
}

describe('ProcessTrace', () => {
  it('renders nothing when no matching trace events', () => {
    const wrapper = mount(ProcessTrace, {
      props: {
        events: [],
        nodeId: 'node-1',
      },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(false)
  })

  it('renders trace with matching nodeId', () => {
    const events = [
      makeTraceEvent({ workflowNodeInstanceId: 'node-1' }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1' },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(true)
    expect(wrapper.text()).toContain('过程')
    expect(wrapper.text()).toContain('1 步')
  })

  it('filters out events with different nodeId', () => {
    const events = [
      makeTraceEvent({ workflowNodeInstanceId: 'node-2' }),
      makeTraceEvent({ workflowNodeInstanceId: 'node-1', id: 'evt-2' }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1' },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(true)
    expect(wrapper.text()).toContain('1 步')
  })

  it('defaults to collapsed and expands on click', async () => {
    const events = [
      makeTraceEvent({
        payloadJson: '{"kind":"reasoning_summary","status":"running","title":"思考摘要","summary":"分析方案"}',
      }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1' },
    })
    expect(wrapper.find('.process-trace__timeline').exists()).toBe(false)
    await wrapper.find('.process-trace__toggle').trigger('click')
    expect(wrapper.find('.process-trace__timeline').exists()).toBe(true)
    expect(wrapper.text()).toContain('分析方案')
  })

  it('sorts trace rows by createdAt before rendering', () => {
    const events = [
      makeTraceEvent({
        id: 'evt-2',
        createdAt: '2026-05-09T00:00:03Z',
        payloadJson: '{"kind":"tool_call","status":"completed","title":"调用工具","summary":"第二步"}',
      }),
      makeTraceEvent({
        id: 'evt-1',
        createdAt: '2026-05-09T00:00:01Z',
        payloadJson: '{"kind":"reasoning_summary","status":"running","title":"思考摘要","summary":"第一步"}',
      }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1', defaultExpanded: true },
    })

    const rows = wrapper.findAll('.trace-item__text')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('第一步')
    expect(rows[1].text()).toContain('第二步')
  })

  it('collapses when defaultExpanded turns false before manual override', async () => {
    const wrapper = mount(ProcessTrace, {
      props: {
        events: [makeTraceEvent()],
        nodeId: 'node-1',
        defaultExpanded: true,
      },
    })
    expect(wrapper.find('.process-trace__timeline').exists()).toBe(true)

    await wrapper.setProps({ defaultExpanded: false })

    expect(wrapper.find('.process-trace__timeline').exists()).toBe(false)
  })

  it('shows tool call details when expanded', async () => {
    const events = [
      makeTraceEvent(),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1' },
    })
    await wrapper.find('.process-trace__toggle').trigger('click')
    expect(wrapper.text()).toContain('read_file')
    expect(wrapper.text()).toContain('调用完成')
  })

  it('does not render legacy skill/status events as process rows', () => {
    const events = [
      makeTraceEvent({
        id: 'evt-skill',
        eventType: 'SKILL_STARTED',
        payloadJson: '{"skillName":"hld-design","label":"hld-design"}',
      }),
      makeTraceEvent({
        id: 'evt-status',
        eventType: 'STATUS',
        payloadJson: '{"status":"busy"}',
      }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1', defaultExpanded: true },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(false)
  })

  it('does not render generic node status process traces', () => {
    const events = [
      makeTraceEvent({
        id: 'evt-node-status',
        eventType: 'PROCESS_TRACE',
        payloadJson: '{"kind":"node_status","status":"running","title":"状态","summary":"Agent 正在处理当前请求"}',
      }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, nodeId: 'node-1', defaultExpanded: true },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(false)
  })

  it('labels context anchor traces as context recovery', async () => {
    const wrapper = mount(ProcessTrace, {
      props: {
        events: [
          makeTraceEvent({
            id: 'evt-context-anchor',
            payloadJson: '{"kind":"context_anchor","status":"completed","title":"已恢复工作流上下文","summary":"已重新注入当前节点和上游产物"}',
          }),
        ],
        nodeId: 'node-1',
      },
    })

    await wrapper.find('.process-trace__toggle').trigger('click')

    expect(wrapper.text()).toContain('上下文恢复')
    expect(wrapper.text()).toContain('已重新注入当前节点和上游产物')
  })

  it('does not create placeholder rows when showWhenEmpty is true', () => {
    const wrapper = mount(ProcessTrace, {
      props: {
        events: [],
        nodeId: 'node-1',
        showWhenEmpty: true,
        emptyText: '正在准备运行上下文...',
      },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(false)
  })

  it('falls back to sessionId when no nodeId', () => {
    const events = [
      makeTraceEvent({ workflowNodeInstanceId: null, sessionId: 'sess-1' }),
    ]
    const wrapper = mount(ProcessTrace, {
      props: { events, sessionId: 'sess-1' },
    })
    expect(wrapper.find('.process-trace').exists()).toBe(true)
  })
})
