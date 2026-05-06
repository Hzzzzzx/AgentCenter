import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BoardView from './BoardView.vue'
import { useWorkItemStore } from '../stores/workItems'
import type { WorkItemDto } from '../api/types'

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([]),
  },
}))

const mockItems: WorkItemDto[] = [
  {
    id: '1',
    code: 'FE-001',
    type: 'FE',
    title: 'Feature A',
    description: null,
    status: 'BACKLOG',
    priority: 'HIGH',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: null,
    workflowSummary: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: '2',
    code: 'US-002',
    type: 'US',
    title: 'Story B',
    description: null,
    status: 'IN_PROGRESS',
    priority: 'MEDIUM',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-2',
    workflowSummary: {
      instanceId: 'wf-2',
      status: 'RUNNING',
      currentNodeInstanceId: 'node-2',
      nodes: [
        { id: 'n1', definitionName: 'PRD', skillName: 'prd-design', status: 'COMPLETED' },
        { id: 'n2', definitionName: 'HLD', skillName: 'hld-design', status: 'RUNNING' },
        { id: 'n3', definitionName: 'LLD', skillName: 'lld-design', status: 'WAITING_CONFIRMATION' },
      ],
    },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

async function mountBoardView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(BoardView, {
    global: { plugins: [pinia] },
  })
  await flushPromises()
  const store = useWorkItemStore()
  const { useWorkflowStore } = await import('../stores/workflows')
  const workflowStore = useWorkflowStore()
  workflowStore.definitions = [
    {
      id: 'wf-def-fe',
      workItemType: 'FE',
      name: 'FE 标准工作流',
      versionNo: 1,
      status: 'ENABLED',
      isDefault: true,
      nodes: [
        {
          id: 'def-prd',
          nodeKey: 'prd',
          name: '需求整理 (PRD)',
          orderNo: 1,
          skillName: 'prd-design',
          inputPolicy: 'WORK_ITEM_ONLY',
          outputArtifactType: 'MARKDOWN',
          requiredConfirmation: false,
        },
        {
          id: 'def-hld',
          nodeKey: 'hld',
          name: '方案设计 (HLD)',
          orderNo: 2,
          skillName: 'hld-design',
          inputPolicy: 'PREVIOUS_ARTIFACT',
          outputArtifactType: 'MARKDOWN',
          requiredConfirmation: true,
        },
        {
          id: 'def-lld',
          nodeKey: 'lld',
          name: '详细设计 (LLD)',
          orderNo: 3,
          skillName: 'lld-design',
          inputPolicy: 'PREVIOUS_ARTIFACT',
          outputArtifactType: 'MARKDOWN',
          requiredConfirmation: false,
        },
      ],
    },
  ]
  store.items = mockItems
  store.loading = false
  await wrapper.vm.$nextTick()
  return wrapper
}

describe('BoardView.vue', () => {
  it('renders work items as cards grouped by current phase status', async () => {
    const wrapper = await mountBoardView()

    const columns = wrapper.findAll('.board-column')
    expect(columns.length).toBe(6)
    expect(columns[0].find('.board-column__label').text()).toBe('待处理')
    expect(columns[0].find('.board-column__count').text()).toBe('1')
    expect(columns[1].find('.board-column__label').text()).toBe('运行中')
    expect(columns[1].find('.board-column__count').text()).toBe('1')
    expect(columns[2].find('.board-column__label').text()).toBe('阻塞中')
    expect(columns[2].find('.board-column__count').text()).toBe('0')

    expect(wrapper.text()).toContain('Feature A')
    expect(wrapper.text()).toContain('FE-001')
    expect(wrapper.text()).toContain('FE')
    expect(wrapper.text()).toContain('HLD')
    expect(wrapper.text()).toContain('运行中')
  })

  it('emits selected work item id when a work item card is clicked', async () => {
    const wrapper = await mountBoardView()

    await wrapper.find('.board-work-card').trigger('click')

    expect(wrapper.emitted('select-work-item')).toBeTruthy()
    expect(wrapper.emitted('select-work-item')![0]).toEqual(['1'])
  })
})
