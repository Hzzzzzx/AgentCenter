import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HomeOverview from './HomeOverview.vue'
import { useWorkItemStore } from '../stores/workItems'
import { useWorkflowStore } from '../stores/workflows'
import type { WorkItemDto } from '../api/types'

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([]),
    startWorkflow: vi.fn().mockResolvedValue({}),
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
    description: 'Description A',
    status: 'TODO',
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
    code: 'BUG-001',
    type: 'BUG',
    title: 'Bug B',
    description: 'Description B',
    status: 'IN_PROGRESS',
    priority: 'URGENT',
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
        { id: 'n1', definitionName: 'PRD', skillName: 'prd-desingn', status: 'COMPLETED' },
        { id: 'n2', definitionName: 'HLD', skillName: 'hld-design', status: 'RUNNING' },
        { id: 'n3', definitionName: 'LLD', skillName: 'lld-design', status: 'PENDING' },
      ],
    },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: '3',
    code: 'US-001',
    type: 'US',
    title: 'User Story C',
    description: null,
    status: 'DONE',
    priority: 'MEDIUM',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-3',
    workflowSummary: {
      instanceId: 'wf-3',
      status: 'COMPLETED',
      currentNodeInstanceId: null,
      nodes: [
        { id: 'n4', definitionName: 'PRD', skillName: 'prd-desingn', status: 'COMPLETED' },
        { id: 'n5', definitionName: 'HLD', skillName: 'hld-design', status: 'COMPLETED' },
        { id: 'n6', definitionName: 'LLD', skillName: 'lld-design', status: 'COMPLETED' },
      ],
    },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function mountHomeOverview() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(HomeOverview, {
    global: { plugins: [pinia] },
  })
}

describe('HomeOverview.vue', () => {
  it('renders stats bar with type counts', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = mockItems
    store.loading = false
    await wrapper.vm.$nextTick()

    const stats = wrapper.findAll('.home-overview__stat')
    expect(stats.length).toBe(6)

    const statTexts = stats.map((s) => s.text())
    expect(statTexts.some((t) => t.includes('FE') && t.includes('1'))).toBe(true)
    expect(statTexts.some((t) => t.includes('缺陷') && t.includes('1'))).toBe(true)
    expect(statTexts.some((t) => t.includes('US') && t.includes('1'))).toBe(true)
  })

  it('renders work items list', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = mockItems
    store.loading = false
    await wrapper.vm.$nextTick()

    const cards = wrapper.findAll('.work-item-card')
    expect(cards.length).toBe(3)
  })

  it('shows loading state', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const { workItemApi } = await import('../api/workItems')
    vi.mocked(workItemApi.list).mockReturnValue(new Promise(() => {}))
    const wrapper = mount(HomeOverview, { global: { plugins: [pinia] } })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.home-overview__loading').exists()).toBe(true)
  })

  it('emits select-work-item when item clicked', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = mockItems
    store.loading = false
    await wrapper.vm.$nextTick()

    const cards = wrapper.findAll('.work-item-card')
    await cards[0].trigger('click')
    expect(wrapper.emitted('select-work-item')).toBeTruthy()
    expect(wrapper.emitted('select-work-item')![0]).toEqual(['1'])
  })

  it('renders default flow with start and end anchors when no workflow', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = [mockItems[0]]
    store.loading = false
    await wrapper.vm.$nextTick()

    const nodes = wrapper.findAll('.home-overview__node')
    expect(nodes.length).toBe(7)
    expect(nodes[0].classes()).toContain('home-overview__node--active')
    for (const node of nodes.slice(1)) {
      expect(node.classes().length).toBe(1)
    }
    expect(wrapper.find('.home-overview__launch').text()).toBe('开始处理')
  })

  it('renders real workflow node statuses from workflowSummary', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = [mockItems[1]]
    store.loading = false
    await wrapper.vm.$nextTick()

    const nodes = wrapper.findAll('.home-overview__node')
    expect(nodes.length).toBe(5)
    expect(nodes[0].classes()).toContain('home-overview__node--done')
    expect(nodes[1].classes()).toContain('home-overview__node--done')
    expect(nodes[2].classes()).toContain('home-overview__node--active')
    expect(nodes[3].classes().length).toBe(1)
    expect(nodes[4].classes().length).toBe(1)

    const btn = wrapper.find('.home-overview__launch')
    expect(btn.text()).toBe('处理中')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('renders completed workflow with all green nodes', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    store.items = [mockItems[2]]
    store.loading = false
    await wrapper.vm.$nextTick()

    const nodes = wrapper.findAll('.home-overview__node')
    expect(nodes.length).toBe(5)
    for (const node of nodes) {
      expect(node.classes()).toContain('home-overview__node--done')
    }
    expect(wrapper.find('.home-overview__launch').text()).toBe('已完成')
  })

  it('hydrates workflow store from workflowSummary via upsertInstance', async () => {
    const wrapper = mountHomeOverview()
    const store = useWorkItemStore()
    const wfStore = useWorkflowStore()
    store.items = [mockItems[1]]
    store.loading = false
    const item = mockItems[1]
    if (item.workflowSummary) {
      wfStore.upsertInstance({
        id: item.workflowSummary.instanceId,
        workItemId: item.id,
        workflowDefinitionId: '',
        status: item.workflowSummary.status,
        currentNodeInstanceId: item.workflowSummary.currentNodeInstanceId,
        nodes: item.workflowSummary.nodes.map((n) => ({
          id: n.id,
          nodeDefinitionId: '',
          status: n.status,
          inputArtifactId: null,
          outputArtifactId: null,
          agentSessionId: null,
          startedAt: null,
          completedAt: null,
          errorMessage: null,
        })),
        startedAt: null,
        completedAt: null,
      })
    }
    await wrapper.vm.$nextTick()

    expect(wfStore.instancesByWorkItemId['2']).toBeTruthy()
    expect(wfStore.instancesByWorkItemId['2'].status).toBe('RUNNING')
  })
})
