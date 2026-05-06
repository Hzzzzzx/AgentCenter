import { defineStore } from 'pinia'
import { ref } from 'vue'
import { workItemApi } from '../api/workItems'
import type { WorkItemDto, CreateWorkItemRequest } from '../api/types'

export const useWorkItemStore = defineStore('workItems', () => {
  const items = ref<WorkItemDto[]>([])
  const selectedItem = ref<WorkItemDto | null>(null)
  const loading = ref(false)

  async function loadItems() {
    loading.value = true
    try {
      items.value = await workItemApi.list()
    } finally {
      loading.value = false
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

  return { items, selectedItem, loading, loadItems, selectItem, refreshItem, createItem }
})
