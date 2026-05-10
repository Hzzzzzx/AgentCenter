<script setup lang="ts">
import type { ExecutionStep } from './projection/types'
import ExecutionStepItem from './ExecutionStepItem.vue'

const props = defineProps<{
  steps: ExecutionStep[]
}>()

defineEmits<{
  'open-artifact': [artifactId: string]
  'resolve': [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()
</script>

<template>
  <details
    class="execution-steps"
    :open="steps.length <= 3"
  >
    <summary class="execution-steps__summary">
      <span class="execution-steps__summary-left">
        <span class="execution-steps__chev">›</span>
        <span>关键进展 ({{ steps.length }} 项)</span>
      </span>
      <span class="execution-steps__badge">已降噪</span>
    </summary>

    <div class="execution-steps__body">
      <div class="execution-steps__list">
        <ExecutionStepItem
          v-for="(step, index) in steps"
          :key="step.id"
          :step="step"
          :order="step.order"
          :is-last="index === steps.length - 1"
          @open-artifact="(id) => $emit('open-artifact', id)"
          @resolve="(confirmationId, value, meta) => $emit('resolve', confirmationId, value, meta)"
        />
      </div>
    </div>
  </details>
</template>

<style scoped>
.execution-steps {
  max-width: 780px;
  margin-top: 14px;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid transparent;
}

.execution-steps[open] {
  padding-bottom: 8px;
  border-bottom-color: var(--border-color);
}

.execution-steps__summary {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 950;
  cursor: pointer;
  list-style: none;
}

.execution-steps__summary::-webkit-details-marker {
  display: none;
}

.execution-steps__summary-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.execution-steps__chev {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--surface-card, var(--bg-card));
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  transition: transform 0.15s ease;
}

.execution-steps[open] .execution-steps__chev {
  transform: rotate(90deg);
}

.execution-steps__badge {
  flex: 0 0 auto;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--surface-card, var(--bg-card));
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.execution-steps__body {
  padding: 4px 0 0 28px;
}

.execution-steps__list {
  display: grid;
  gap: 12px;
}

@media (max-width: 760px) {
  .execution-steps__body {
    padding-left: 0;
  }
}
</style>
