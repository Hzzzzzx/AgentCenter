import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useWorkItemStore } from './workItems'
import { workItemApi } from '../api/workItems'
import type { WorkItemDto } from '../api/types'

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    startWorkflow: vi.fn(),
  },
}))

const mockWorkItem: WorkItemDto = {
  id: 'wi-1',
  code: 'TASK-001',
  type: 'TASK',
  title: 'Test task',
  description: null,
  status: 'TODO',
  priority: 'MEDIUM',
  projectId: null,
  spaceId: null,
  iterationId: null,
  assigneeUserId: null,
  currentWorkflowInstanceId: null,
  workflowSummary: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('useWorkItemStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadItems populates items array', async () => {
    vi.mocked(workItemApi.list).mockResolvedValueOnce([mockWorkItem])

    const store = useWorkItemStore()
    await store.loadItems()

    expect(store.items).toHaveLength(1)
    expect(store.items[0]).toEqual(mockWorkItem)
    expect(store.loading).toBe(false)
  })

  it('selectItem sets selectedItem', async () => {
    vi.mocked(workItemApi.getById).mockResolvedValueOnce(mockWorkItem)

    const store = useWorkItemStore()
    await store.selectItem('wi-1')

    expect(store.selectedItem).toEqual(mockWorkItem)
  })

  it('createItem adds new item to list', async () => {
    vi.mocked(workItemApi.create).mockResolvedValueOnce(mockWorkItem)

    const store = useWorkItemStore()
    const created = await store.createItem({ type: 'TASK', title: 'Test task' })

    expect(created).toEqual(mockWorkItem)
    expect(store.items).toContainEqual(mockWorkItem)
  })

  it('loadItems sets loading to false even on error', async () => {
    vi.mocked(workItemApi.list).mockRejectedValueOnce(new Error('Network error'))

    const store = useWorkItemStore()
    await expect(store.loadItems()).rejects.toThrow('Network error')
    expect(store.loading).toBe(false)
  })
})
