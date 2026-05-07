<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useWorkItemStore } from '../stores/workItems'
import type { WorkItemDto, WorkflowNodeStatus, WorkItemType } from '../api/types'

const store = useWorkItemStore()

const emit = defineEmits<{
  'select-work-item': [id: string]
}>()

onMounted(() => {
  store.loadItems()
})

interface Column {
  status: WorkflowNodeStatus
  label: string
  color: string
}

interface BoardWorkItemCardData {
  id: string
  workItemCode: string
  workItemType: WorkItemDto['type']
  workItemTitle: string
  phaseName: string
  phaseStatus: WorkflowNodeStatus
  dynamicNodeCount: number
  recoveryCount: number
  pendingConfirmationCount: number
  latestSummary: string | null
}

const columns: Column[] = [
  { status: 'PENDING', label: '待处理', color: '#64748b' },
  { status: 'RUNNING', label: '运行中', color: '#2563eb' },
  { status: 'WAITING_CONFIRMATION', label: '阻塞中', color: '#d97706' },
  { status: 'FAILED', label: '异常', color: '#dc2626' },
  { status: 'COMPLETED', label: '已完成', color: '#059669' },
  { status: 'SKIPPED', label: '已跳过', color: '#94a3b8' },
]

const defaultStageLabels: Record<WorkItemType, string[]> = {
  FE: ['需求', '方案', '实施', '验证', '归档'],
  US: ['故事', '验收', '拆分', '评审', '归档'],
  TASK: ['理解', '计划', '执行', '验证', '总结'],
  WORK: ['分析', 'Runbook', '执行', '校验', '报告'],
  BUG: ['复现', '根因', '修复', '回归', '关闭'],
  VULN: ['分级', '影响', '修复', '验证', '归档'],
}

function cardForItem(item: WorkItemDto): BoardWorkItemCardData {
  const stages = item.workflowSummary?.stages?.length
    ? item.workflowSummary.stages
    : item.workflowSummary?.nodes.map((node) => ({
      id: node.id,
      name: node.definitionName,
      skillName: node.skillName,
      status: node.status,
      dynamicNodeCount: 0,
      recoveryCount: 0,
      pendingConfirmationCount: node.status === 'WAITING_CONFIRMATION' ? 1 : 0,
      latestSummary: node.definitionName ?? node.skillName,
    }))

  const current = stages?.find((stage) => stage.id === item.workflowSummary?.currentNodeInstanceId)
    ?? stages?.find((stage) => !['COMPLETED', 'SKIPPED'].includes(stage.status))
    ?? (stages?.length ? stages[stages.length - 1] : undefined)

  const phaseStatus = item.workflowSummary?.status === 'COMPLETED'
    ? 'COMPLETED'
    : (current?.status ?? 'PENDING')

  return {
    id: item.id,
    workItemCode: item.code,
    workItemType: item.type,
    workItemTitle: item.title,
    phaseName: current?.name ?? current?.skillName ?? defaultStageLabels[item.type][0],
    phaseStatus,
    dynamicNodeCount: current?.dynamicNodeCount ?? 0,
    recoveryCount: current?.recoveryCount ?? 0,
    pendingConfirmationCount: current?.pendingConfirmationCount ?? 0,
    latestSummary: current?.latestSummary ?? null,
  }
}

const boardItems = computed(() => store.items.map(cardForItem))

const groupedNodes = computed(() => {
  const groups: Record<WorkflowNodeStatus, BoardWorkItemCardData[]> = {
    PENDING: [],
    RUNNING: [],
    WAITING_CONFIRMATION: [],
    FAILED: [],
    COMPLETED: [],
    SKIPPED: [],
  }
  for (const col of columns) {
    groups[col.status] = boardItems.value.filter((node) => node.phaseStatus === col.status)
  }
  return groups
})

function handleSelectNode(workItemId: string) {
  emit('select-work-item', workItemId)
}

function stageMeta(item: BoardWorkItemCardData): string {
  const parts: string[] = []
  if (item.dynamicNodeCount) parts.push(`${item.dynamicNodeCount} 动态步骤`)
  if (item.recoveryCount) parts.push(`${item.recoveryCount} 修复`)
  if (item.pendingConfirmationCount) parts.push(`${item.pendingConfirmationCount} 待确认`)
  return parts.join(' · ')
}
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
          <span class="board-column__count">{{ groupedNodes[col.status].length }}</span>
        </div>
        <div class="board-column__body">
          <button
            v-for="item in groupedNodes[col.status]"
            :key="item.id"
            class="board-work-card"
            type="button"
            @click="handleSelectNode(item.id)"
          >
            <div class="board-work-card__title">{{ item.workItemTitle }}</div>
            <div class="board-work-card__tags">
              <span class="board-work-card__code">{{ item.workItemCode }}</span>
              <span class="board-work-card__type">{{ item.workItemType }}</span>
            </div>
            <div class="board-work-card__phase">
              <span>{{ item.phaseName }}</span>
              <strong>{{ col.label }}</strong>
            </div>
            <p v-if="item.latestSummary" class="board-work-card__summary">{{ item.latestSummary }}</p>
            <p v-if="stageMeta(item)" class="board-work-card__meta">{{ stageMeta(item) }}</p>
          </button>
          <div v-if="groupedNodes[col.status].length === 0" class="board-column__empty">
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
  min-width: 240px;
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

.board-work-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-height: 138px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s, transform 0.15s;
}

.board-work-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.board-work-card:focus-visible {
  outline: 2px solid var(--accent-blue);
  outline-offset: 2px;
}

.board-work-card__title {
  overflow: hidden;
  color: var(--text-primary);
  display: -webkit-box;
  font-size: 14px;
  font-weight: 750;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.board-work-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.board-work-card__code,
.board-work-card__type,
.board-work-card__phase strong {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 750;
  line-height: 1;
}

.board-work-card__code {
  background-color: var(--brand-soft);
  color: var(--accent-blue);
}

.board-work-card__type {
  background-color: var(--bg-primary);
  color: var(--text-secondary);
}

.board-work-card__phase {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
}

.board-work-card__phase span {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-work-card__phase strong {
  flex: 0 0 auto;
  background-color: var(--brand-soft);
  color: var(--accent-blue);
}

.board-work-card__summary,
.board-work-card__meta {
  margin: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
