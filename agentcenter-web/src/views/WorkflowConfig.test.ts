import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import WorkflowConfig from './WorkflowConfig.vue'
import { workflowApi } from '../api/workflows'
import type { WorkflowDefinitionDto } from '../api/types'

const mocks = vi.hoisted(() => ({
  definition: {
    id: 'wf-fe-v1',
    workItemType: 'FE',
    name: 'FE 标准工作流',
    versionNo: 1,
    status: 'ENABLED',
    isDefault: true,
    nodes: [
      {
        id: 'node-prd',
        nodeKey: 'prd',
        name: '需求整理',
        orderNo: 1,
        skillName: 'prd-desingn',
        inputPolicy: 'WORK_ITEM_ONLY',
        outputArtifactType: 'MARKDOWN',
        requiredConfirmation: false,
      },
      {
        id: 'node-hld',
        nodeKey: 'hld',
        name: '方案设计',
        orderNo: 2,
        skillName: 'hld-design',
        inputPolicy: 'PREVIOUS_ARTIFACT',
        outputArtifactType: 'MARKDOWN',
        requiredConfirmation: true,
      },
    ],
  } as WorkflowDefinitionDto,
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([mocks.definition]),
    updateDefinition: vi.fn().mockResolvedValue({
      ...mocks.definition,
      id: 'wf-fe-v2',
      name: 'FE 自定义编排',
      versionNo: 2,
    }),
  },
}))

vi.mock('../api/runtimeResources', () => ({
  skillApi: {
    list: vi.fn().mockResolvedValue([
      { id: 'skill-1', name: 'prd-desingn', status: 'ENABLED' },
      { id: 'skill-2', name: 'hld-design', status: 'ENABLED' },
    ]),
  },
}))

async function mountWorkflowConfig() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(WorkflowConfig, {
    global: { plugins: [pinia] },
  })
  await flushPromises()
  return wrapper
}

describe('WorkflowConfig.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('edits workflow stages and saves a new version', async () => {
    const wrapper = await mountWorkflowConfig()

    expect(wrapper.text()).toContain('Agent-first 任务编排')
    expect(wrapper.text()).toContain('大阶段蓝图')
    expect(wrapper.text()).toContain('ASK_USER')

    await wrapper.find('button.workflow-config__button--primary').trigger('click')

    expect(wrapper.text()).toContain('自然语言编排意图')
    expect(wrapper.text()).toContain('Skill 池')
    expect(wrapper.text()).toContain('Agent 理解流程图')
    expect(wrapper.find('textarea[aria-label="Agent 理解 Mermaid 草图"]').exists()).toBe(true)
    const flowTextarea = wrapper.find('textarea[aria-label="Agent 理解 Mermaid 草图"]').element as HTMLTextAreaElement
    expect(flowTextarea.value).toContain('DECISION_REQUIRED')
    expect(wrapper.text()).toContain('无改动')
    expect(wrapper.findAll('.workflow-editor__stage-card').length).toBe(2)
    expect(wrapper.find('select[aria-label="选择 Skill"]').exists()).toBe(true)
    expect(wrapper.find('input[aria-label="阶段目标"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="查看交互事件说明"]').attributes('title')).toContain('不是固定阻塞门禁')
    expect(wrapper.find('button[aria-label="查看动态动作说明"]').attributes('title')).toContain('临时追加')

    await wrapper.find('button[aria-label="把 hld-design 加入阶段"]').trigger('click')
    expect(wrapper.findAll('.workflow-editor__stage-card').length).toBe(3)

    await wrapper.findAll('button').find((button) => button.text() === '生成阶段草案')?.trigger('click')
    expect(wrapper.text()).toContain('阶段草案')
    expect(flowTextarea.value).toContain('ARTIFACT_REVIEW_REQUESTED')

    const nameInput = wrapper.find('input[type="text"]')
    await nameInput.setValue('FE 自定义编排')
    await wrapper.findAll('button').find((button) => button.text() === '保存新版')?.trigger('click')
    await flushPromises()

    expect(workflowApi.updateDefinition).toHaveBeenCalledWith(
      'wf-fe-v1',
      expect.objectContaining({ name: 'FE 自定义编排' })
    )
  })

  it('does not create a new version when nothing changed', async () => {
    const wrapper = await mountWorkflowConfig()

    await wrapper.find('button.workflow-config__button--primary').trigger('click')

    const saveButton = wrapper.findAll('button').find((button) => button.text() === '无改动')
    expect(saveButton?.attributes('disabled')).toBeDefined()
    expect(workflowApi.updateDefinition).not.toHaveBeenCalled()
  })
})
