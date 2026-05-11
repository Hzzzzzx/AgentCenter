import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ExecutionStepItem from './ExecutionStepItem.vue'
import type { ExecutionStep } from './projection/types'

function makeToolStep(overrides: Partial<ExecutionStep> = {}): ExecutionStep {
  return {
    id: 'step-hld',
    order: 2,
    kind: 'tool',
    title: '调用 hld-design',
    status: 'running',
    parts: [{
      type: 'tool',
      toolCallId: 'tool-hld',
      rawName: 'hld-design',
      displayName: '调用 hld-design',
      category: 'skill',
      status: 'running',
      rawPayloadRef: { eventId: 'event-hld', eventType: 'PROCESS_TRACE' },
      defaultExpanded: false,
    }],
    rawEventRefs: [],
    ...overrides,
  }
}

describe('ExecutionStepItem.vue', () => {
  it('renders the tool card even when the part title matches the step title', () => {
    const wrapper = mount(ExecutionStepItem, {
      props: {
        step: makeToolStep(),
        order: 2,
        isLast: false,
      },
      global: {
        stubs: {
          ToolInvocationInline: {
            template: '<div class="tool-invocation-stub">{{ part.displayName }}</div>',
            props: ['part'],
          },
          MarkdownContent: true,
        },
      },
    })

    expect(wrapper.find('.step-item__title').text()).toBe('调用 hld-design')
    expect(wrapper.find('.tool-invocation-stub').exists()).toBe(true)
  })

  it('keeps the tool card when the tool has detail worth showing', () => {
    const wrapper = mount(ExecutionStepItem, {
      props: {
        step: makeToolStep({
          parts: [{
            type: 'tool',
            toolCallId: 'tool-hld',
            rawName: 'hld-design',
            displayName: '调用 hld-design',
            category: 'skill',
            status: 'running',
            inputSummary: '使用 Skill hld-design 生成方案设计',
            rawPayloadRef: { eventId: 'event-hld', eventType: 'PROCESS_TRACE' },
            defaultExpanded: false,
          }],
        }),
        order: 2,
        isLast: false,
      },
      global: {
        stubs: {
          ToolInvocationInline: {
            template: '<div class="tool-invocation-stub">{{ part.inputSummary }}</div>',
            props: ['part'],
          },
          MarkdownContent: true,
        },
      },
    })

    expect(wrapper.find('.tool-invocation-stub').text()).toContain('使用 Skill hld-design')
  })
})
