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

  it('renders reasoning as a single deep-thinking label without repeating the title', () => {
    const wrapper = mount(ExecutionStepItem, {
      props: {
        step: {
          id: 'step-reasoning',
          order: 1,
          kind: 'reasoning',
          title: '思考',
          summary: '这是一段很长的推理摘要，不应该直接铺在步骤列表中。',
          status: 'completed',
          parts: [{
            type: 'reasoning',
            summary: '整理上下文',
            defaultExpanded: false,
            rawEventRef: { eventId: 'event-reasoning', eventType: 'PROCESS_TRACE' },
          }],
          rawEventRefs: [],
        },
        order: 1,
        isLast: false,
      },
      global: {
        stubs: {
          MarkdownContent: {
            template: '<div>{{ content }}</div>',
            props: ['content'],
          },
          ToolInvocationInline: true,
        },
      },
    })

    expect(wrapper.find('.step-item__title-wrap').text()).toBe('深度思考')
    expect(wrapper.text()).not.toContain('思考 思考')
    expect(wrapper.find('.step-item__summary').exists()).toBe(false)
    expect(wrapper.find('.step-item__reasoning summary').text()).toBe('推理摘要')
    expect(wrapper.find('.step-item__reasoning').attributes('open')).toBeUndefined()
  })
})
