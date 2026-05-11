<script setup lang="ts">
import { computed } from 'vue'
import type { DecisionGatePart } from './projection/types'

const props = defineProps<{
  part: DecisionGatePart
}>()

const emit = defineEmits<{
  resolve: [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()

function selectOption(value: string): void {
  emit('resolve', props.part.confirmationId, value, {
    requestType: props.part.requestType,
    interactionType: props.part.interactionType,
  })
}

const selectedOption = computed(() =>
  props.part.options.find(option => option.value === props.part.recommended)
)

const selectedLabel = computed(() => {
  if (selectedOption.value?.label) return selectedOption.value.label
  const value = props.part.recommended?.trim()
  if (!value) return ''
  if (/^(once|ALLOW_ONCE)$/i.test(value)) return '允许一次'
  if (/^(always|ALLOW_ALWAYS|SESSION)$/i.test(value)) return '本次会话允许同类请求'
  if (/^(approve|allow|APPROVE|ALLOW)$/i.test(value)) return '允许'
  if (/^(reject|REJECT|DENY)$/i.test(value)) return '拒绝'
  return value
})

const resolvedTitle = computed(() => {
  if (props.part.requestType === 'PERMISSION') {
    return /reject|deny|拒绝/i.test(`${props.part.recommended || ''} ${selectedLabel.value}`)
      ? '权限已拒绝'
      : '权限已允许'
  }
  return '交互已处理'
})

const resolvedDetail = computed(() => {
  const label = selectedLabel.value
  if (!label) return props.part.question
  if (props.part.requestType === 'PERMISSION') return label
  return `${props.part.question}：${label}`
})
</script>

<template>
  <section
    class="decision-gate"
    :class="{
      'decision-gate--waiting': part.status === 'waiting',
      'decision-gate--resolved': part.status === 'resolved',
      'decision-gate--compact': part.status === 'resolved',
    }"
  >
    <template v-if="part.status === 'resolved'">
      <span class="decision-gate__result-dot" />
      <strong class="decision-gate__result-title">{{ resolvedTitle }}</strong>
      <span v-if="resolvedDetail" class="decision-gate__result-detail">{{ resolvedDetail }}</span>
    </template>

    <template v-else>
    <div class="decision-gate__head">
      <strong class="decision-gate__question">{{ part.question }}</strong>
      <span class="decision-gate__pill" :class="`decision-gate__pill--${part.status}`">
        {{ part.status === 'waiting' ? '等待确认' : part.status === 'submitted' ? '已提交' : '已选择' }}
      </span>
    </div>

    <p v-if="part.prompt && part.prompt !== part.question" class="decision-gate__prompt">{{ part.prompt }}</p>

    <div class="decision-gate__choices">
      <div
        v-for="(option, index) in part.options"
        :key="option.value"
        class="decision-gate__choice"
        :class="{
          'decision-gate__choice--recommended': option.value === part.recommended,
          'decision-gate__choice--interactive': part.status === 'waiting',
        }"
        :role="part.status === 'waiting' ? 'button' : undefined"
        :tabindex="part.status === 'waiting' ? 0 : undefined"
        @click="part.status === 'waiting' && selectOption(option.value)"
        @keydown.enter="part.status === 'waiting' && selectOption(option.value)"
        @keydown.space.prevent="part.status === 'waiting' && selectOption(option.value)"
      >
        <span class="decision-gate__choice-num">{{ index + 1 }}</span>
        <div class="decision-gate__choice-content">
          <strong>{{ option.label }}</strong>
          <span v-if="option.description">{{ option.description }}</span>
        </div>
        <span v-if="option.value === part.recommended" class="decision-gate__recommend">推荐</span>
      </div>
    </div>
    </template>
  </section>
</template>

<style scoped>
.decision-gate {
  margin-top: 10px;
  border: 1px solid color-mix(in srgb, var(--warning) 48%, var(--border-color));
  border-radius: 8px;
  background: color-mix(in srgb, var(--warning) 6%, var(--bg-card));
  overflow: hidden;
}

.decision-gate--resolved {
  border-color: transparent;
  background: transparent;
}

.decision-gate--compact {
  min-height: 28px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  overflow: visible;
}

.decision-gate__result-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--success);
  box-shadow: 0 0 0 4px var(--success-soft);
}

.decision-gate__result-title {
  flex: 0 0 auto;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.decision-gate__result-detail {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.decision-gate__head {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--warning) 20%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-card));
}

.decision-gate--resolved .decision-gate__head {
  background: var(--surface-card, var(--bg-card));
  border-bottom-color: var(--border-color);
}

.decision-gate__question {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 950;
  line-height: 1.4;
}

.decision-gate__pill {
  flex: 0 0 auto;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--surface-card);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.decision-gate__pill--waiting {
  border-color: color-mix(in srgb, var(--warning) 55%, var(--border-color));
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
}

.decision-gate__pill--submitted {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
  background: var(--brand-soft);
}

.decision-gate__pill--resolved {
  border-color: var(--success);
  color: var(--success);
  background: var(--success-soft);
}

.decision-gate__prompt {
  margin: 0;
  padding: 10px 12px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.decision-gate__choices {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.decision-gate__choice {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: center;
  min-height: 48px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card, var(--bg-card));
}

.decision-gate__choice--recommended {
  border-color: color-mix(in srgb, var(--accent-blue) 50%, var(--border-color));
  background: var(--brand-soft);
}

.decision-gate__choice--interactive {
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.decision-gate__choice--interactive:hover {
  border-color: var(--accent-blue);
  background: var(--surface-hover);
}

.decision-gate__choice--interactive:focus-visible {
  outline: 2px solid var(--accent-blue);
  outline-offset: 2px;
}

.decision-gate__choice-num {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 950;
}

.decision-gate__choice-content {
  min-width: 0;
}

.decision-gate__choice-content strong {
  display: block;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 950;
}

.decision-gate__choice-content span {
  display: block;
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.decision-gate__recommend {
  flex: 0 0 auto;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .decision-gate__choice {
    grid-template-columns: 1fr;
  }

  .decision-gate__choice-num {
    display: none;
  }
}
</style>
