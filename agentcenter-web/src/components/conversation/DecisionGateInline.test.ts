import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DecisionGateInline from './DecisionGateInline.vue'
import type { DecisionGatePart } from './projection/types'

function makePart(overrides: Partial<DecisionGatePart> = {}): DecisionGatePart {
  return {
    type: 'decision',
    confirmationId: 'confirm-1',
    question: '请选择本次 LLD 流程测试的实现路线。',
    prompt: '请选择本次 LLD 流程测试的实现路线。',
    options: [{ value: 'FAST', label: '快速验证', description: '只列最小文件和测试点。' }],
    recommended: 'FAST',
    status: 'waiting',
    defaultExpanded: true,
    ...overrides,
  }
}

describe('DecisionGateInline.vue', () => {
  it('does not repeat the prompt when it matches the question', () => {
    const wrapper = mount(DecisionGateInline, {
      props: { part: makePart() },
    })

    expect(wrapper.find('.decision-gate__question').text()).toBe('请选择本次 LLD 流程测试的实现路线。')
    expect(wrapper.find('.decision-gate__prompt').exists()).toBe(false)
  })

  it('keeps a distinct prompt visible', () => {
    const wrapper = mount(DecisionGateInline, {
      props: {
        part: makePart({
          prompt: '请在进入最终产物前确认路线。',
        }),
      },
    })

    expect(wrapper.find('.decision-gate__prompt').text()).toBe('请在进入最终产物前确认路线。')
  })

  it('renders resolved decisions as a compact result instead of replaying all choices', () => {
    const wrapper = mount(DecisionGateInline, {
      props: {
        part: makePart({
          requestType: 'PERMISSION',
          status: 'resolved',
          recommended: 'REJECT',
          options: [
            { value: 'ONCE', label: '允许一次' },
            { value: 'ALWAYS', label: '本次会话允许同类请求' },
            { value: 'REJECT', label: '拒绝' },
          ],
        }),
      },
    })

    expect(wrapper.find('.decision-gate__result-title').text()).toBe('权限已拒绝')
    expect(wrapper.find('.decision-gate__result-detail').text()).toBe('拒绝')
    expect(wrapper.findAll('.decision-gate__choice')).toHaveLength(0)
  })
})
