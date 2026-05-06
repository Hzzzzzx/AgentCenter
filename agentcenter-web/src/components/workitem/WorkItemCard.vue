<script setup lang="ts">
import type { WorkItemDto, WorkItemType, WorkItemStatus, Priority } from '../../api/types'

const props = defineProps<{
  workItem: WorkItemDto
}>()

const emit = defineEmits<{
  click: [id: string]
}>()

const typeColors: Record<WorkItemType, string> = {
  FE: '#3b82f6',
  US: '#10b981',
  TASK: '#f59e0b',
  WORK: '#8b5cf6',
  BUG: '#ef4444',
  VULN: '#991b1b',
}

const statusLabels: Record<WorkItemStatus, string> = {
  BACKLOG: '待办',
  TODO: '计划',
  IN_PROGRESS: '进行中',
  IN_REVIEW: '评审中',
  DONE: '完成',
}

const statusColors: Record<WorkItemStatus, string> = {
  BACKLOG: '#94a3b8',
  TODO: '#64748b',
  IN_PROGRESS: '#3b82f6',
  IN_REVIEW: '#f59e0b',
  DONE: '#10b981',
}

const priorityDots: Record<Priority, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
  URGENT: 4,
}

const priorityColors: Record<Priority, string> = {
  LOW: '#94a3b8',
  MEDIUM: '#3b82f6',
  HIGH: '#f59e0b',
  URGENT: '#ef4444',
}
</script>

<template>
  <div class="work-item-card" @click="emit('click', workItem.id)">
    <div class="work-item-card__header">
      <span
        class="work-item-card__code"
        :style="{ backgroundColor: typeColors[workItem.type] + '18', color: typeColors[workItem.type] }"
      >
        {{ workItem.code }}
      </span>
      <span
        class="work-item-card__status"
        :style="{ backgroundColor: statusColors[workItem.status] + '18', color: statusColors[workItem.status] }"
      >
        {{ statusLabels[workItem.status] }}
      </span>
    </div>
    <div class="work-item-card__title">{{ workItem.title }}</div>
    <div class="work-item-card__footer">
      <div class="work-item-card__priority" :title="workItem.priority">
        <span
          v-for="n in priorityDots[workItem.priority]"
          :key="n"
          class="work-item-card__priority-dot"
          :style="{ backgroundColor: priorityColors[workItem.priority] }"
        />
      </div>
      <span class="work-item-card__type">{{ workItem.type }}</span>
    </div>
  </div>
</template>

<style scoped>
.work-item-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.work-item-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.work-item-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.work-item-card__code {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.work-item-card__status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.work-item-card__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.work-item-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.work-item-card__priority {
  display: flex;
  gap: 3px;
}

.work-item-card__priority-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.work-item-card__type {
  font-size: 11px;
  color: var(--text-secondary);
}
</style>
