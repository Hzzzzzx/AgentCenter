import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ExecutionSteps from './ExecutionSteps.vue'
import type { ExecutionStep } from './projection/types'

function makeStep(overrides: Partial<ExecutionStep> = {}): ExecutionStep {
  return {
    id: 'step-1',
    order: 1,
    kind: 'tool',
    title: 'hld-design',
    status: 'running',
    parts: [],
    rawEventRefs: [],
    ...overrides,
  }
}

describe('ExecutionSteps.vue', () => {
  it('keeps execution details collapsed when answer text is present', () => {
    const wrapper = mount(ExecutionSteps, {
      props: {
        steps: [makeStep()],
        status: 'running',
        currentAction: { label: '调用 hld-design' },
        hasAnswer: true,
      },
      global: {
        stubs: { ExecutionStepItem: true },
      },
    })

    expect(wrapper.find('details').attributes('open')).toBeUndefined()
    expect(wrapper.text()).toContain('调用 hld-design')
    expect(wrapper.text()).toContain('进行中')
    expect(wrapper.find('.execution-steps__current').exists()).toBe(false)
  })

  it('keeps the completed execution summary copy while collapsed', () => {
    const wrapper = mount(ExecutionSteps, {
      props: {
        steps: [makeStep({ status: 'completed' })],
        status: 'completed',
        hasAnswer: true,
      },
      global: {
        stubs: { ExecutionStepItem: true },
      },
    })

    expect(wrapper.text()).toContain('已处理 1 个步骤')
    expect(wrapper.text()).toContain('调用 1 个工具/Skill')
    expect(wrapper.text()).toContain('已收起')
  })

  it('opens execution details when there is no answer text yet', () => {
    const wrapper = mount(ExecutionSteps, {
      props: {
        steps: [makeStep()],
        status: 'running',
        currentAction: { label: '调用 hld-design' },
        hasAnswer: false,
      },
      global: {
        stubs: { ExecutionStepItem: true },
      },
    })

    expect(wrapper.find('details').attributes('open')).toBeDefined()
    expect(wrapper.find('.execution-steps__summary').text()).toContain('执行过程')
    expect(wrapper.find('.execution-steps__summary').text()).not.toContain('调用 hld-design')
  })

  it('summarizes read and search tool categories in the collapsed copy', () => {
    const wrapper = mount(ExecutionSteps, {
      props: {
        steps: [
          makeStep({
            id: 'step-read',
            parts: [{
              type: 'tool',
              toolCallId: 'read-1',
              rawName: 'read',
              displayName: '读取文件 MessageList.vue',
              category: 'read',
              status: 'completed',
              rawPayloadRef: { eventId: 'ev-read', eventType: 'PROCESS_TRACE' },
              defaultExpanded: false,
            }],
          }),
          makeStep({
            id: 'step-grep',
            order: 2,
            parts: [{
              type: 'tool',
              toolCallId: 'grep-1',
              rawName: 'grep',
              displayName: '搜索代码 ASSISTANT_DELTA',
              category: 'search',
              status: 'completed',
              rawPayloadRef: { eventId: 'ev-grep', eventType: 'PROCESS_TRACE' },
              defaultExpanded: false,
            }],
          }),
        ],
        status: 'completed',
        hasAnswer: true,
      },
      global: {
        stubs: { ExecutionStepItem: true },
      },
    })

    expect(wrapper.text()).toContain('读取 1 个文件')
    expect(wrapper.text()).toContain('搜索 1 次')
  })
})
