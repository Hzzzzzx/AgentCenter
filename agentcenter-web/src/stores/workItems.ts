import { defineStore } from 'pinia'
import { ref } from 'vue'
import { workItemApi } from '../api/workItems'
import type { WorkItemScopeQuery } from '../api/workItems'
import type { WorkItemDto, CreateWorkItemRequest, WorkItemOverviewDto } from '../api/types'

export const useWorkItemStore = defineStore('workItems', () => {
  const items = ref<WorkItemDto[]>([])
  const selectedItem = ref<WorkItemDto | null>(null)
  const overview = ref<WorkItemOverviewDto | null>(null)
  const loading = ref(false)
  const overviewLoading = ref(false)
  const activeScope = ref<WorkItemScopeQuery>({})

  function setScope(scope: WorkItemScopeQuery) {
    activeScope.value = {
      providerId: scope.providerId || null,
      projectId: scope.projectId || null,
      spaceId: scope.spaceId || null,
      iterationId: scope.iterationId || null,
    }
  }

  async function loadItems(scope: WorkItemScopeQuery = activeScope.value) {
    loading.value = true
    try {
      items.value = await workItemApi.list(scope)
    } finally {
      loading.value = false
    }
  }

  async function loadOverview(scope: WorkItemScopeQuery = activeScope.value) {
    overviewLoading.value = true
    try {
      overview.value = await workItemApi.overview(scope)
    } finally {
      overviewLoading.value = false
    }
  }

  async function selectItem(id: string) {
    selectedItem.value = await workItemApi.getById(id)
  }

  async function refreshItem(id: string) {
    const updated = await workItemApi.getById(id)
    const index = items.value.findIndex((item) => item.id === id)
    if (index >= 0) {
      items.value.splice(index, 1, updated)
    } else {
      items.value.push(updated)
    }
    if (selectedItem.value?.id === id) {
      selectedItem.value = updated
    }
    return updated
  }

  async function createItem(data: CreateWorkItemRequest) {
    const item = await workItemApi.create(data)
    items.value.push(item)
    return item
  }

  return {
    items,
    selectedItem,
    overview,
    loading,
    overviewLoading,
    activeScope,
    setScope,
    loadItems,
    loadOverview,
    selectItem,
    refreshItem,
    createItem,
  }
})
