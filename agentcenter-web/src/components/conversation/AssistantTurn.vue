<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationDisplayItem, ConversationTurnProjection, TurnStatus } from './projection/types'
import AssistantAnswer from './AssistantAnswer.vue'
import ExecutionSteps from './ExecutionSteps.vue'

type ArtifactOpenRef = {
  artifactId?: string
  title?: string
}

const props = defineProps<{
  turn: ConversationTurnProjection
}>()

defineEmits<{
  'open-artifact': [ref: ArtifactOpenRef]
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

const renderItems = computed<ConversationDisplayItem[]>(() => {
  if (props.turn.displayItems?.length) return props.turn.displayItems
  const items: ConversationDisplayItem[] = []
  if (props.turn.steps.length > 0 || props.turn.currentAction) {
    items.push({
      type: 'agent-activity',
      id: `${props.turn.turnId}:activity`,
      steps: props.turn.steps,
      status: props.turn.status,
      currentAction: props.turn.currentAction,
      collapsedByDefault: Boolean(props.turn.answer.text) || props.turn.status === 'running',
    })
  }
  if (props.turn.answer.text) {
    items.push({
      type: 'assistant-message',
      id: `${props.turn.turnId}:assistant`,
      answer: props.turn.answer,
    })
  }
  if (props.turn.pendingInteraction) {
    items.push({
      type: 'interaction-request',
      id: `${props.turn.turnId}:interaction:${props.turn.pendingInteraction.confirmationId}`,
      interaction: props.turn.pendingInteraction,
    })
  }
  return items
})

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

      <template v-for="item in renderItems" :key="item.id">
        <ExecutionSteps
          v-if="item.type === 'agent-activity'"
          :steps="item.steps"
          :status="item.status"
          :current-action="item.currentAction"
          :has-answer="Boolean(turn.answer.text)"
          :collapsed-by-default="item.collapsedByDefault"
          @open-artifact="(id) => $emit('open-artifact', { artifactId: id })"
          @resolve="(confirmationId, value, meta) => $emit('resolve', confirmationId, value, meta)"
        />

        <AssistantAnswer
          v-else-if="item.type === 'assistant-message'"
          :text="item.answer.text"
          :streaming="item.answer.streaming"
        />

        <div v-else-if="item.type === 'interaction-request'" class="assistant-turn__interaction-marker">
          <span class="assistant-turn__interaction-dot" />
          <span>
            需要你处理当前交互
            <strong>{{ item.interaction.question }}</strong>
          </span>
        </div>
      </template>
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

.assistant-turn__interaction-marker {
  max-width: 620px;
  margin: 6px 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
}

.assistant-turn__interaction-marker strong {
  margin-left: 4px;
  color: var(--text-primary);
  font-weight: 850;
}

.assistant-turn__interaction-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--warning) 14%, transparent);
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
