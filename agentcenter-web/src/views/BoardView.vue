<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useWorkItemStore } from '../stores/workItems'
import WorkItemCard from '../components/workitem/WorkItemCard.vue'
import type { WorkItemStatus } from '../api/types'

const store = useWorkItemStore()

onMounted(() => {
  store.loadItems()
})

interface Column {
  status: WorkItemStatus
  label: string
  color: string
}

const columns: Column[] = [
  { status: 'BACKLOG', label: 'Backlog', color: '#94a3b8' },
  { status: 'TODO', label: 'Todo', color: '#64748b' },
  { status: 'IN_PROGRESS', label: 'In Progress', color: '#3b82f6' },
  { status: 'IN_REVIEW', label: 'In Review', color: '#f59e0b' },
  { status: 'DONE', label: 'Done', color: '#10b981' },
]

const groupedItems = computed(() => {
  const groups: Record<string, typeof store.items> = {}
  for (const col of columns) {
    groups[col.status] = store.items.filter((item) => item.status === col.status)
  }
  return groups
})
</script>

<template>
  <div class="board-view">
    <div v-if="store.loading" class="board-view__loading">加载中...</div>
    <div v-else class="board-view__columns">
      <div
        v-for="col in columns"
        :key="col.status"
        class="board-column"
      >
        <div class="board-column__header" :style="{ borderTopColor: col.color }">
          <span class="board-column__label">{{ col.label }}</span>
          <span class="board-column__count">{{ groupedItems[col.status].length }}</span>
        </div>
        <div class="board-column__body">
          <WorkItemCard
            v-for="item in groupedItems[col.status]"
            :key="item.id"
            :work-item="item"
          />
          <div v-if="groupedItems[col.status].length === 0" class="board-column__empty">
            暂无
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.board-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  overflow: hidden;
}

.board-view__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 14px;
}

.board-view__columns {
  display: flex;
  gap: 12px;
  flex: 1;
  overflow-x: auto;
}

.board-column {
  display: flex;
  flex-direction: column;
  min-width: 260px;
  flex: 1;
  background-color: var(--bg-primary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  overflow: hidden;
}

.board-column__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-top: 3px solid;
  background-color: var(--bg-secondary);
}

.board-column__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.board-column__count {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
}

.board-column__body {
  flex: 1;
  padding: 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.board-column__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
