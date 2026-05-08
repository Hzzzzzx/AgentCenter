import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageList from './MessageList.vue'
import type { RuntimeEventDto } from '../../api/types'

function runtimeEvent(overrides: Partial<RuntimeEventDto>): RuntimeEventDto {
  return {
    id: 'evt-1',
    sessionId: 'session-1',
    workItemId: 'work-1',
    workflowInstanceId: 'workflow-1',
    workflowNodeInstanceId: 'node-1',
    eventType: 'SKILL_COMPLETED',
    eventSource: 'WORKFLOW',
    payloadJson: null,
    createdAt: '2026-05-08T10:00:00Z',
    ...overrides,
  }
}

describe('MessageList.vue', () => {
  it('keeps workflow failure activity visible with the error reason', () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [],
        runtimeEvents: [
          runtimeEvent({
            payloadJson: JSON.stringify({
              skillName: 'lld-design',
              success: false,
              errorMessage: 'Agent Runtime 超时',
            }),
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('异常 1 项')
    expect(wrapper.text()).toContain('lld-design 执行失败')
    expect(wrapper.text()).toContain('Agent Runtime 超时')
  })
})
