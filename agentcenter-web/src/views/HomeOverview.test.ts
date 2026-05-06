import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HomeOverview from './HomeOverview.vue'
import { useWorkItemStore } from '../stores/workItems'
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
    currentWorkflowInstanceId: null,
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
    currentWorkflowInstanceId: null,
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
    expect(stats.length).toBe(6) // FE, US, TASK, WORK, BUG, VULN

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
})
