<script setup lang="ts">
import { computed, ref } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useNotificationStore } from '../../stores/notifications'
import type { ConfirmationRequestDto, ConfirmationRequestType, WorkItemDto } from '../../api/types'

const props = defineProps<{
  confirmation: ConfirmationRequestDto
  workItem?: WorkItemDto | null
}>()

const emit = defineEmits<{
  handle: [id: string]
  resolved: [id: string]
  rejected: [id: string]
}>()

const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const busyAction = ref<'approve' | 'reject' | null>(null)

const typeLabels: Record<ConfirmationRequestType, string> = {
  CONFIRM: '确认',
  APPROVAL: '审批',
  INPUT_REQUIRED: '输入',
  DECISION: '决策',
  EXCEPTION: '异常',
  PERMISSION: '权限',
}

const typeColors: Record<ConfirmationRequestType, string> = {
  CONFIRM: '#3b82f6',
  APPROVAL: '#8b5cf6',
  INPUT_REQUIRED: '#f59e0b',
  DECISION: '#10b981',
  EXCEPTION: '#ef4444',
  PERMISSION: '#64748b',
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const hour = d.getHours().toString().padStart(2, '0')
  const minute = d.getMinutes().toString().padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const workItemCode = computed(() => props.confirmation.workItemCode ?? props.workItem?.code ?? '未关联')
const workItemType = computed(() => props.confirmation.workItemType ?? props.workItem?.type ?? '事项')
const workItemTitle = computed(() => props.confirmation.workItemTitle ?? props.workItem?.title ?? '未关联工作项')
const workflowNodeName = computed(() => props.confirmation.workflowNodeName ?? props.confirmation.title)

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function handleApprove() {
  if (busyAction.value) return
  busyAction.value = 'approve'
  try {
    await confirmationStore.resolveConfirmation(props.confirmation.id, { actionType: 'APPROVE' })
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'success',
      title: '确认已通过',
      message: `${workItemCode.value} 已进入后续流程`,
    })
    emit('resolved', props.confirmation.id)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '通过失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function handleReject() {
  if (busyAction.value) return
  busyAction.value = 'reject'
  try {
    await confirmationStore.rejectConfirmation(props.confirmation.id)
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'warning',
      title: '已拒绝确认',
      message: `${workItemCode.value} 已暂停推进`,
    })
    emit('rejected', props.confirmation.id)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '拒绝失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}
</script>

<template>
  <div class="confirmation-card">
    <div class="confirmation-card__header">
      <span
        class="confirmation-card__type"
        :style="{
          backgroundColor: typeColors[confirmation.requestType] + '18',
          color: typeColors[confirmation.requestType]
        }"
      >
        {{ typeLabels[confirmation.requestType] }}
      </span>
      <span class="confirmation-card__time">{{ formatTime(confirmation.createdAt) }}</span>
    </div>
    <div class="confirmation-card__work-item">
      <span>{{ workItemCode }}</span>
      <em>{{ workItemType }}</em>
    </div>
    <div class="confirmation-card__title">{{ workItemTitle }}</div>
    <div class="confirmation-card__node">待确认：{{ workflowNodeName }}</div>
    <div v-if="confirmation.contextSummary" class="confirmation-card__summary">
      {{ confirmation.contextSummary }}
    </div>
    <div v-if="confirmation.content" class="confirmation-card__content">{{ confirmation.content }}</div>
    <div v-if="confirmation.skillName" class="confirmation-card__skill">
      Skill：{{ confirmation.skillName }}
    </div>
    <div class="confirmation-card__actions">
      <button
        v-if="confirmation.status === 'PENDING'"
        class="confirmation-card__action confirmation-card__action--approve"
        :disabled="!!busyAction"
        @click="handleApprove"
      >
        {{ busyAction === 'approve' ? '推进中...' : '通过' }}
      </button>
      <button
        v-if="confirmation.status === 'PENDING'"
        class="confirmation-card__action confirmation-card__action--reject"
        :disabled="!!busyAction"
        @click="handleReject"
      >
        {{ busyAction === 'reject' ? '处理中...' : '拒绝' }}
      </button>
      <button class="confirmation-card__action" :disabled="!!busyAction" @click="emit('handle', confirmation.id)">
        处理
      </button>
    </div>
  </div>
</template>

<style scoped>
.confirmation-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.confirmation-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.confirmation-card__type {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.confirmation-card__time {
  font-size: 11px;
  color: var(--text-secondary);
}

.confirmation-card__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.4;
}

.confirmation-card__work-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.confirmation-card__work-item span {
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #eef2ff;
  color: #3b5bdb;
  font-size: 12px;
  font-weight: 700;
}

.confirmation-card__work-item em {
  font-style: normal;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.confirmation-card__node,
.confirmation-card__summary {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.confirmation-card__node {
  font-weight: 600;
}

.confirmation-card__content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  max-height: 60px;
  overflow: hidden;
}

.confirmation-card__skill {
  font-size: 11px;
  color: var(--text-secondary);
}

.confirmation-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
}

.confirmation-card__action {
  padding: 4px 14px;
  border: none;
  border-radius: 4px;
  background-color: var(--accent-blue);
  color: #ffffff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.confirmation-card__action:disabled {
  cursor: wait;
  opacity: 0.65;
}

.confirmation-card__action:hover {
  opacity: 0.85;
}

.confirmation-card__action--approve {
  background-color: #10b981;
}

.confirmation-card__action--reject {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.confirmation-card__action--reject:hover {
  background-color: #fef2f2;
  color: #ef4444;
  border-color: #fca5a5;
}
</style>
