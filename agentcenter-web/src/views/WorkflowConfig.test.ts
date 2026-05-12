import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import WorkflowConfig from './WorkflowConfig.vue'
import { workflowApi } from '../api/workflows'
import { skillApi } from '../api/runtimeResources'
import type { RuntimeSkillDetailDto, WorkflowDefinitionDto } from '../api/types'

const mocks = vi.hoisted(() => ({
  definition: {
    id: 'wf-fe-v1',
    projectId: '01DEFAULTPROJECT0000000000001',
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
        skillName: 'prd-design',
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
    catalog: vi.fn().mockResolvedValue([
      { id: 'skill-1', name: 'prd-design', status: 'ENABLED', validationStatus: 'VALID' },
      { id: 'skill-2', name: 'hld-design', status: 'ENABLED', validationStatus: 'VALID' },
      { id: 'skill-3', name: 'fe-us-spitter', status: 'ENABLED', validationStatus: 'VALID' },
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
    expect(wrapper.text()).not.toContain('建议审阅产物')
    expect(wrapper.text()).not.toContain('允许 Agent 动态动作')
    expect(wrapper.text()).toContain('运行时交互交给 Agent')

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
      expect.objectContaining({
        name: 'FE 自定义编排',
        nodes: expect.arrayContaining([
          expect.objectContaining({
            requiredConfirmation: false,
            allowDynamicActions: true,
            confirmationPolicy: 'EVENT_DRIVEN',
          }),
        ]),
      })
    )
  })

  it('does not create a new version when nothing changed', async () => {
    const wrapper = await mountWorkflowConfig()

    await wrapper.find('button.workflow-config__button--primary').trigger('click')

    const saveButton = wrapper.findAll('button').find((button) => button.text() === '无改动')
    expect(saveButton?.attributes('disabled')).toBeDefined()
    expect(workflowApi.updateDefinition).not.toHaveBeenCalled()
  })

  it('preserves user stage order when generating a draft and saving', async () => {
    const wrapper = await mountWorkflowConfig()

    await wrapper.find('button.workflow-config__button--primary').trigger('click')
    await wrapper.find('button[aria-label="把 fe-us-spitter 加入阶段"]').trigger('click')

    const stageCardsAfterAdd = wrapper.findAll('.workflow-editor__stage-card')
    expect(stageCardsAfterAdd).toHaveLength(3)
    expect(stageCardsAfterAdd[2].text()).toContain('fe-us-spitter')
    expect((stageCardsAfterAdd[2].find('input[aria-label="阶段名称"]').element as HTMLInputElement).value)
      .toBe('FE/US 拆分授权测试')

    await stageCardsAfterAdd[2].findAll('button').find((button) => button.text() === '上移')?.trigger('click')
    const movedCards = wrapper.findAll('.workflow-editor__stage-card')
    expect(movedCards[1].text()).toContain('fe-us-spitter')

    await wrapper.findAll('button').find((button) => button.text() === '生成阶段草案')?.trigger('click')

    const generatedCards = wrapper.findAll('.workflow-editor__stage-card')
    expect(generatedCards[0].text()).toContain('prd-design')
    expect(generatedCards[1].text()).toContain('fe-us-spitter')
    expect(generatedCards[2].text()).toContain('hld-design')

    const nameInput = wrapper.find('input[type="text"]')
    await nameInput.setValue('FE 插入授权测试编排')
    await wrapper.findAll('button').find((button) => button.text() === '保存新版')?.trigger('click')
    await flushPromises()

    expect(workflowApi.updateDefinition).toHaveBeenCalledWith(
      'wf-fe-v1',
      expect.objectContaining({
        nodes: [
          expect.objectContaining({ skillName: 'prd-design', inputPolicy: 'WORK_ITEM_ONLY' }),
          expect.objectContaining({
            skillName: 'fe-us-spitter',
            nodeKey: 'fe_us_splitter',
            stageKey: 'fe_us_splitter',
            inputPolicy: 'PREVIOUS_ARTIFACT',
          }),
          expect.objectContaining({ skillName: 'hld-design', inputPolicy: 'PREVIOUS_ARTIFACT' }),
        ],
      })
    )
  })

  it('flags workflow skills missing from the realtime project skill catalog', async () => {
    const prdSkill: RuntimeSkillDetailDto = {
      id: 'skill-1',
      projectId: '01DEFAULTPROJECT0000000000001',
      name: 'prd-design',
      displayName: null,
      description: null,
      currentVersionId: null,
      status: 'ENABLED',
      source: 'LOCAL_SCAN',
      relativePath: '.opencode/skills/prd-design',
      checksum: 'checksum-prd',
      validationStatus: 'VALID',
      validationMessage: null,
      createdBy: null,
      createdAt: '2026-05-11T00:00:00Z',
      updatedAt: '2026-05-11T00:00:00Z',
      version: null,
      referenceCount: 0,
    }
    vi.mocked(skillApi.catalog).mockResolvedValueOnce([prdSkill])

    const wrapper = await mountWorkflowConfig()

    expect(wrapper.text()).toContain('编排引用的 Skill 当前不可用：hld-design')
    await wrapper.find('button.workflow-config__button--primary').trigger('click')
    await wrapper.find('input[type="text"]').setValue('FE 缺失 Skill 编排')
    await wrapper.findAll('button').find((button) => button.text() === '保存新版')?.trigger('click')

    expect(wrapper.text()).toContain('Skill "hld-design" 当前未在 Skill 管理中启用')
    expect(workflowApi.updateDefinition).not.toHaveBeenCalled()
  })
})
