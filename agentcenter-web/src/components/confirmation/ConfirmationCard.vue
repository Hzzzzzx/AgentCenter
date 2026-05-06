<script setup lang="ts">
import type { ConfirmationRequestDto, ConfirmationRequestType } from '../../api/types'

const props = defineProps<{
  confirmation: ConfirmationRequestDto
}>()

const emit = defineEmits<{
  handle: [id: string]
}>()

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
    <div class="confirmation-card__title">{{ confirmation.title }}</div>
    <div v-if="confirmation.content" class="confirmation-card__content">{{ confirmation.content }}</div>
    <div v-if="confirmation.skillName" class="confirmation-card__skill">
      技能: {{ confirmation.skillName }}
    </div>
    <button class="confirmation-card__action" @click="emit('handle', confirmation.id)">
      处理
    </button>
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
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.4;
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

.confirmation-card__action {
  align-self: flex-end;
  padding: 4px 16px;
  border: none;
  border-radius: 4px;
  background-color: var(--accent-blue);
  color: #ffffff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.confirmation-card__action:hover {
  opacity: 0.85;
}
</style>
