<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationDisplayItem, ConversationTurnProjection } from './projection/types'
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

const renderItems = computed<ConversationDisplayItem[]>(() => {
  if (props.turn.displayItems?.length) return props.turn.displayItems
  const items: ConversationDisplayItem[] = []
  const hasActivity = props.turn.steps.length > 0 || Boolean(props.turn.currentAction)
  const hasAnswer = Boolean(props.turn.answer.text)
  const shouldDeferAnswer = props.turn.status === 'running' && hasActivity && hasAnswer

  if (props.turn.steps.length > 0 || props.turn.currentAction) {
    items.push({
      type: 'agent-activity',
      id: `${props.turn.turnId}:activity`,
      steps: props.turn.steps,
      status: props.turn.status,
      currentAction: props.turn.currentAction,
      collapsedByDefault: hasAnswer && !shouldDeferAnswer,
    })
  }
  if (props.turn.answer.text && !shouldDeferAnswer) {
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
    <div class="assistant-turn__main">
      <TransitionGroup name="assistant-turn__item" tag="div" class="assistant-turn__items">
        <template v-for="item in renderItems" :key="item.id">
          <ExecutionSteps
            v-if="item.type === 'agent-activity'"
            :key="item.id"
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
            :key="item.id"
            :text="item.answer.text"
            :streaming="item.answer.streaming"
          />

          <div
            v-else-if="item.type === 'interaction-request'"
            :key="item.id"
            class="assistant-turn__interaction-marker"
          >
            <span class="assistant-turn__interaction-dot" />
            <span>
              需要你处理当前交互
              <strong>{{ item.interaction.question }}</strong>
            </span>
          </div>
        </template>
      </TransitionGroup>
    </div>
  </article>
</template>

<style scoped>
.assistant-turn {
  display: block;
}

.assistant-turn__main {
  min-width: 0;
  padding-top: 1px;
}

.assistant-turn__items {
  display: grid;
  gap: 0;
  min-width: 0;
}

.assistant-turn__item-move,
.assistant-turn__item-enter-active,
.assistant-turn__item-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.assistant-turn__item-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.assistant-turn__item-leave-to {
  opacity: 0;
  transform: translateY(-6px);
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
    display: block;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-turn__item-move,
  .assistant-turn__item-enter-active,
  .assistant-turn__item-leave-active {
    transition: none;
  }
}
</style>
