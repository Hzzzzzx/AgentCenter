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
const modalOpen = ref(false)

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

function enterSession() {
  modalOpen.value = false
  emit('handle', props.confirmation.id)
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
    <div class="confirmation-card__actions">
      <button class="confirmation-card__action" :disabled="!!busyAction" @click="modalOpen = true">
        处理
      </button>
    </div>
  </div>

  <Teleport to="body">
    <div v-if="modalOpen" class="confirmation-dialog" role="dialog" aria-modal="true" aria-labelledby="confirmation-dialog-title">
      <button class="confirmation-dialog__scrim" aria-label="关闭确认详情" @click="modalOpen = false"></button>
      <section class="confirmation-dialog__panel">
        <header class="confirmation-dialog__header">
          <div>
            <span class="confirmation-dialog__eyebrow">{{ typeLabels[confirmation.requestType] }}</span>
            <h3 id="confirmation-dialog-title">{{ workItemTitle }}</h3>
          </div>
          <button class="confirmation-dialog__close" aria-label="关闭" @click="modalOpen = false">×</button>
        </header>

        <div class="confirmation-dialog__meta">
          <span>{{ workItemCode }}</span>
          <span>{{ workItemType }}</span>
          <span>{{ formatTime(confirmation.createdAt) }}</span>
        </div>

        <dl class="confirmation-dialog__details">
          <div>
            <dt>确认节点</dt>
            <dd>{{ workflowNodeName }}</dd>
          </div>
          <div v-if="confirmation.contextSummary">
            <dt>上下文</dt>
            <dd>{{ confirmation.contextSummary }}</dd>
          </div>
          <div v-if="confirmation.content">
            <dt>详情</dt>
            <dd class="confirmation-dialog__content">{{ confirmation.content }}</dd>
          </div>
          <div v-if="confirmation.skillName">
            <dt>Skill</dt>
            <dd>{{ confirmation.skillName }}</dd>
          </div>
        </dl>

        <footer class="confirmation-dialog__actions">
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
          <button class="confirmation-card__action" :disabled="!!busyAction" @click="enterSession">
            进入会话
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
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
  background-color: var(--brand-soft);
  color: var(--brand-primary);
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
  color: var(--on-brand);
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
  background-color: var(--success);
  color: var(--on-success);
}

.confirmation-card__action--reject {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.confirmation-card__action--reject:hover {
  background-color: var(--error-soft);
  color: var(--error);
  border-color: var(--error);
}

.confirmation-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
}

.confirmation-dialog__scrim {
  position: absolute;
  inset: 0;
  border: 0;
  background: color-mix(in srgb, var(--text-primary) 28%, transparent);
  cursor: pointer;
}

.confirmation-dialog__panel {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: min(560px, calc(100vw - 48px));
  max-height: min(720px, calc(100vh - 48px));
  padding: 18px;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  scrollbar-color: color-mix(in srgb, var(--brand-primary) 46%, var(--border-color)) transparent;
}

.confirmation-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.confirmation-dialog__eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--brand-soft);
  color: var(--brand-primary);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__header h3 {
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 900;
  line-height: 1.35;
}

.confirmation-dialog__close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 20px;
  cursor: pointer;
}

.confirmation-dialog__close:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.confirmation-dialog__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.confirmation-dialog__meta span {
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--surface-muted);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.confirmation-dialog__details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.confirmation-dialog__details div {
  display: grid;
  gap: 4px;
}

.confirmation-dialog__details dt {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__details dd {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.6;
  white-space: pre-wrap;
}

.confirmation-dialog__content {
  max-height: 240px;
  overflow: auto;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.confirmation-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 2px;
}
</style>
