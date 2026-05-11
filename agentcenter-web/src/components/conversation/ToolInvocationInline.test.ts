import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ToolInvocationInline from './ToolInvocationInline.vue'
import type { ToolInvocationPart } from './projection/types'

function makePart(overrides: Partial<ToolInvocationPart> = {}): ToolInvocationPart {
  return {
    type: 'tool',
    toolCallId: 'call-1',
    rawName: 'read',
    displayName: 'read',
    status: 'completed',
    rawPayloadRef: {
      eventId: 'evt-1',
      eventType: 'PROCESS_TRACE',
    },
    defaultExpanded: true,
    ...overrides,
  }
}

describe('ToolInvocationInline', () => {
  it('formats compact XML-like tool output onto separate lines', () => {
    const wrapper = mount(ToolInvocationInline, {
      props: {
        part: makePart({
          outputSummary: '<path>/Users/hzz/workspace/AgentCenter/runtime-workspace/.opencode/skills/hld-design/SKILL.md</path><type>file</type><content># HLD</content>',
        }),
      },
    })

    const output = wrapper.find('.tool-invocation__code').text()
    expect(output).toContain('<path>/Users/hzz/workspace/AgentCenter/runtime-workspace/.opencode/skills/hld-design/SKILL.md</path>\n<type>file</type>\n<content># HLD</content>')
  })

  it('breaks absolute path lists into readable lines', () => {
    const wrapper = mount(ToolInvocationInline, {
      props: {
        part: makePart({
          outputSummary: 'Found 2 match(es) in 2 file(s) /Users/hzz/workspace/AgentCenter/a.vue /Users/hzz/workspace/AgentCenter/b.vue',
        }),
      },
    })

    const output = wrapper.find('.tool-invocation__code').text()
    expect(output).toContain('Found 2 match(es) in 2 file(s)\n/Users/hzz/workspace/AgentCenter/a.vue\n/Users/hzz/workspace/AgentCenter/b.vue')
  })

  it('shows a quiet running state before details return', () => {
    const wrapper = mount(ToolInvocationInline, {
      props: {
        part: makePart({
          status: 'running',
          displayName: '读取文件 MessageList.vue',
          inputSummary: undefined,
          outputSummary: undefined,
        }),
      },
    })

    expect(wrapper.find('.tool-invocation--running').exists()).toBe(true)
    expect(wrapper.text()).toContain('正在执行，详细信息会在返回后更新。')
    expect(wrapper.text()).not.toContain('Runtime')
  })
})
