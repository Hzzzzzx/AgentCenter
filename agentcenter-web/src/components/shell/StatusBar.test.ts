import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBar from './StatusBar.vue'

describe('StatusBar.vue', () => {
  it('renders runtime connection and resource status beside agent count', () => {
    const wrapper = mount(StatusBar, {
      props: {
        systemStatus: 'normal',
        toolConnections: 12,
        agentsOnline: 3,
        runtimeConnected: true,
        runtimeConnectionLabel: '已连接',
        resourceLabel: '9 Skill',
        resourceSyncLabel: '已同步',
        resourceTooltip: '项目 AgentCenter · Skill 刷新时间 未刷新',
        agentStateDot: '✓',
        agentStateText: '流程已完成',
        agentStateReason: '已完成 3 个节点并生成产物',
      },
    })

    const right = wrapper.find('.status-bar__right')
    expect(right.text()).toContain('流程已完成')
    expect(right.text()).toContain('已完成 3 个节点并生成产物')
    expect(right.text()).toContain('已连接')
    expect(right.text()).toContain('9 Skill')
    expect(right.text()).toContain('已同步')
    expect(right.text()).toContain('智能体在线: 3')
    expect(wrapper.find('.status-bar__pill--online').exists()).toBe(true)
  })
})
