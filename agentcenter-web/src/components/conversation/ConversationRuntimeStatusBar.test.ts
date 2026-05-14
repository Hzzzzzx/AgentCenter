import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ConversationRuntimeStatusBar from './ConversationRuntimeStatusBar.vue'
import type { RuntimeStatusProjection } from './projection/runtimeStatusProjector'

function makeStatus(overrides: Partial<RuntimeStatusProjection> = {}): RuntimeStatusProjection {
  return {
    tone: 'ok',
    label: 'Runtime 已连接',
    detail: '运行事件会在当前会话中实时同步。',
    badge: '正常',
    expandable: false,
    ...overrides,
  }
}

describe('ConversationRuntimeStatusBar.vue', () => {
  it('renders compact connected state without details toggle', () => {
    const wrapper = mount(ConversationRuntimeStatusBar, {
      props: {
        status: makeStatus(),
      },
    })

    expect(wrapper.text()).toContain('Runtime 已连接')
    expect(wrapper.text()).toContain('正常')
    expect(wrapper.text()).not.toContain('详情')
  })

  it('auto-expands task errors with details', () => {
    const wrapper = mount(ConversationRuntimeStatusBar, {
      props: {
        status: makeStatus({
          tone: 'error',
          label: 'Runtime 响应超时',
          detail: 'Agent Runtime 已接收请求，但没有返回可用输出。',
          badge: '异常',
          rawEventType: 'session.messages.timeout',
          expandable: true,
        }),
      },
    })

    expect(wrapper.classes()).toContain('runtime-status-bar--error')
    expect(wrapper.find('.runtime-status-bar__detail').exists()).toBe(true)
    expect(wrapper.text()).toContain('session.messages.timeout')
  })

  it('toggles warning details on click', async () => {
    const wrapper = mount(ConversationRuntimeStatusBar, {
      props: {
        status: makeStatus({
          tone: 'warning',
          label: 'OpenCode 事件流异常',
          detail: 'SSE connection lost: stream dropped',
          badge: '可恢复',
          rawEventType: 'event.stream.error',
          expandable: true,
        }),
      },
    })

    expect(wrapper.find('.runtime-status-bar__detail').exists()).toBe(false)

    await wrapper.find('.runtime-status-bar__summary').trigger('click')

    expect(wrapper.find('.runtime-status-bar__detail').exists()).toBe(true)
    expect(wrapper.text()).toContain('event.stream.error')
  })
})
