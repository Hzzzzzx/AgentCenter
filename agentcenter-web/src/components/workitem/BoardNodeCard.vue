<script setup lang="ts">
import type { WorkItemType, WorkflowNodeStatus } from '../../api/types'

interface BoardNodeCardData {
  id: string
  workItemId: string
  workItemCode: string
  workItemType: WorkItemType
  workItemTitle: string
  nodeName: string
  nodeStatus: WorkflowNodeStatus
}

defineProps<{
  node: BoardNodeCardData
}>()

const emit = defineEmits<{
  select: [workItemId: string]
}>()

const typeColors: Record<WorkItemType, string> = {
  FE: '#3b82f6',
  US: '#10b981',
  TASK: '#f59e0b',
  WORK: '#8b5cf6',
  BUG: '#ef4444',
  VULN: '#991b1b',
}

const statusLabels: Record<WorkflowNodeStatus, string> = {
  PENDING: '待处理',
  RUNNING: '运行中',
  WAITING_CONFIRMATION: '阻塞中',
  FAILED: '异常',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
}

const statusColors: Record<WorkflowNodeStatus, string> = {
  PENDING: '#64748b',
  RUNNING: '#2563eb',
  WAITING_CONFIRMATION: '#d97706',
  FAILED: '#dc2626',
  COMPLETED: '#059669',
  SKIPPED: '#94a3b8',
}
</script>

<template>
  <button class="board-node-card" type="button" @click="emit('select', node.workItemId)">
    <div class="board-node-card__title">{{ node.workItemTitle }}</div>

    <div class="board-node-card__tags" aria-label="任务标签">
      <span class="board-node-card__code">{{ node.workItemCode }}</span>
      <span
        class="board-node-card__type"
        :style="{ backgroundColor: typeColors[node.workItemType] + '18', color: typeColors[node.workItemType] }"
      >
        {{ node.workItemType }}
      </span>
    </div>

    <div class="board-node-card__node">
      <span class="board-node-card__node-name">{{ node.nodeName }}</span>
      <span
        class="board-node-card__status"
        :style="{ backgroundColor: statusColors[node.nodeStatus] + '18', color: statusColors[node.nodeStatus] }"
      >
        {{ statusLabels[node.nodeStatus] }}
      </span>
    </div>
  </button>
</template>

<style scoped>
.board-node-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-height: 116px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s, transform 0.15s;
}

.board-node-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.board-node-card:focus-visible {
  outline: 2px solid var(--accent-blue);
  outline-offset: 2px;
}

.board-node-card__title {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.board-node-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
}

.board-node-card__code,
.board-node-card__type,
.board-node-card__status {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.board-node-card__code {
  background-color: rgba(59, 130, 246, 0.12);
  color: var(--accent-blue);
}

.board-node-card__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}

.board-node-card__node-name {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-node-card__status {
  flex: 0 0 auto;
}
</style>
