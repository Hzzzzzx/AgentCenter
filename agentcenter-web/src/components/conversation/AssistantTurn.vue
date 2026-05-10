<script setup lang="ts">
import type { ConversationTurnProjection, TurnStatus } from './projection/types'
import AssistantAnswer from './AssistantAnswer.vue'
import ExecutionSteps from './ExecutionSteps.vue'

const props = defineProps<{
  turn: ConversationTurnProjection
}>()

defineEmits<{
  'open-artifact': [artifactId: string]
  'resolve': [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()

function statusPillClass(status: TurnStatus): string {
  switch (status) {
    case 'completed': return 'assistant-turn__pill--completed'
    case 'running': return 'assistant-turn__pill--running'
    case 'waiting_input': return 'assistant-turn__pill--waiting'
    case 'failed': return 'assistant-turn__pill--failed'
    default: return ''
  }
}

function statusLabel(status: TurnStatus): string {
  switch (status) {
    case 'completed': return '已完成'
    case 'running': return '进行中'
    case 'waiting_input': return '等待输入'
    case 'failed': return '失败'
    default: return status
  }
}

</script>

<template>
  <article class="assistant-turn">
    <div class="assistant-turn__rail">
      <div class="assistant-turn__avatar">A</div>
    </div>
    <div class="assistant-turn__main">
      <div class="assistant-turn__meta">
        <strong class="assistant-turn__label">Agent</strong>
        <span
          class="assistant-turn__pill"
          :class="statusPillClass(turn.status)"
        >
          {{ statusLabel(turn.status) }}
        </span>
      </div>

      <AssistantAnswer
        v-if="turn.answer.text"
        :text="turn.answer.text"
        :streaming="turn.answer.streaming"
      />

      <ExecutionSteps
        v-if="turn.steps.length > 0"
        :steps="turn.steps"
        @open-artifact="(id) => $emit('open-artifact', id)"
        @resolve="(confirmationId, value, meta) => $emit('resolve', confirmationId, value, meta)"
      />
    </div>
  </article>
</template>

<style scoped>
.assistant-turn {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.assistant-turn__rail {
  position: relative;
  min-height: 100%;
}

.assistant-turn__rail::after {
  content: '';
  position: absolute;
  top: 36px;
  bottom: -20px;
  left: 15px;
  width: 1px;
  background: linear-gradient(180deg, var(--success-soft), transparent);
}

.assistant-turn__avatar {
  position: sticky;
  top: 10px;
  z-index: 1;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #111827;
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 900;
  box-shadow: 0 0 0 4px var(--bg-card, var(--surface-card, #fbfdff));
}

.assistant-turn__main {
  min-width: 0;
  padding-top: 1px;
}

.assistant-turn__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  margin-bottom: 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 850;
}

.assistant-turn__label {
  color: var(--text-primary);
  font-weight: 950;
}

.assistant-turn__pill {
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

.assistant-turn__pill--completed {
  background: var(--success-soft);
  color: var(--success);
  border-color: color-mix(in srgb, var(--success) 40%, var(--border-color));
}

.assistant-turn__pill--running {
  background: var(--brand-soft);
  color: var(--accent-blue);
  border-color: color-mix(in srgb, var(--accent-blue) 40%, var(--border-color));
}

.assistant-turn__pill--waiting {
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
  color: var(--warning);
  border-color: color-mix(in srgb, var(--warning) 50%, var(--border-color));
}

.assistant-turn__pill--failed {
  background: var(--error-soft);
  color: var(--error);
  border-color: var(--error);
}

@media (max-width: 760px) {
  .assistant-turn {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 10px;
  }

  .assistant-turn__avatar {
    width: 28px;
    height: 28px;
  }

  .assistant-turn__rail::after {
    left: 13px;
  }
}
</style>
