<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CurrentActionProjection, ExecutionStep, TurnStatus } from './projection/types'
import ExecutionStepItem from './ExecutionStepItem.vue'

const props = defineProps<{
  steps: ExecutionStep[]
  status: TurnStatus
  currentAction?: CurrentActionProjection
  hasAnswer?: boolean
  collapsedByDefault?: boolean
}>()

defineEmits<{
  'open-artifact': [artifactId: string]
  'resolve': [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()

const autoOpen = computed(() =>
  props.status === 'running'
  || props.status === 'failed'
  || props.status === 'waiting_input'
  || !props.collapsedByDefault
)

const isOpen = ref(false)
const manuallyPinned = ref(false)

watch(autoOpen, (value) => {
  if (value) {
    isOpen.value = true
    manuallyPinned.value = false
    return
  }
  if (!manuallyPinned.value) {
    isOpen.value = false
  }
}, { immediate: true })

function toggleOpen(): void {
  const next = !isOpen.value
  isOpen.value = next
  manuallyPinned.value = !autoOpen.value && next
}

const shouldShowBody = computed(() =>
  props.status === 'failed'
  || props.status === 'waiting_input'
  || isOpen.value
)

const toolCount = computed(() => props.steps.filter(step => step.kind === 'tool').length)
const toolCategoryCounts = computed(() => {
  const counts = {
    read: 0,
    search: 0,
    list: 0,
    command: 0,
    skill: 0,
    tool: 0,
  }
  for (const step of props.steps) {
    for (const part of step.parts) {
      if (part.type !== 'tool') continue
      counts[part.category ?? 'tool'] += 1
    }
  }
  return counts
})
const artifactCount = computed(() => props.steps.reduce((count, step) =>
  count + step.parts.filter(part => part.type === 'artifact').length, 0))
const interactionCount = computed(() => props.steps.reduce((count, step) =>
  count + step.parts.filter(part => part.type === 'decision').length, 0))

const summaryText = computed(() => {
  if (props.status === 'running') {
    return toolCategorySummary()
      || conciseActionLabel(props.currentAction?.label)
      || '正在处理当前请求'
  }
  if (props.status === 'waiting_input') {
    const detail = props.currentAction?.detail
    return detail ? `等待你处理当前交互 · ${detail}` : '等待你处理当前交互'
  }
  if (props.status === 'failed') return '执行遇到问题'

  const parts = [`已处理 ${props.steps.length} 个步骤`]
  const categorySummary = toolCategorySummary()
  if (categorySummary) parts.push(categorySummary)
  else if (toolCount.value) parts.push(`调用 ${toolCount.value} 个工具/Skill`)
  if (artifactCount.value) parts.push(`生成 ${artifactCount.value} 个产物`)
  if (interactionCount.value) parts.push(`${interactionCount.value} 次确认`)
  return parts.join(' · ')
})

function conciseActionLabel(label?: string): string | undefined {
  if (!label) return undefined
  return label.replace(/^调用\s+调用\s+/, '调用 ').trim()
}

function toolCategorySummary(): string {
  const counts = toolCategoryCounts.value
  const parts: string[] = []
  if (counts.read) parts.push(`读取 ${counts.read} 个文件`)
  if (counts.search) parts.push(`搜索 ${counts.search} 次`)
  if (counts.list) parts.push(`查看 ${counts.list} 个目录`)
  if (counts.command) parts.push(`执行 ${counts.command} 条命令`)
  if (counts.skill) parts.push(`调用 ${counts.skill} 个 Skill`)
  if (counts.tool) parts.push(`调用 ${counts.tool} 个工具`)
  return parts.join(' · ')
}
</script>

<template>
  <section
    class="execution-steps"
    :class="[
      `execution-steps--${status}`,
      { 'execution-steps--compact': hasAnswer },
      { 'execution-steps--breathing': status === 'running' || status === 'waiting_input' },
      { 'execution-steps--open': shouldShowBody },
      { 'execution-steps--pinned': manuallyPinned },
    ]"
  >
    <button
      type="button"
      class="execution-steps__summary"
      :aria-expanded="shouldShowBody"
      @click="toggleOpen"
    >
      <span class="execution-steps__summary-left">
        <span
          v-if="status === 'running' || status === 'waiting_input'"
          class="execution-steps__beacon"
          aria-hidden="true"
        />
        <span>{{ summaryText }}</span>
      </span>
      <span class="execution-steps__badge">
        {{ status === 'running' ? '进行中' : status === 'waiting_input' ? '需确认' : status === 'failed' ? '异常' : shouldShowBody ? '收起详情' : '展开详情' }}
      </span>
    </button>

    <div v-if="steps.length > 0 && shouldShowBody" class="execution-steps__body">
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
  </section>
</template>

<style scoped>
.execution-steps {
  max-width: 100%;
  margin: 6px 0 12px;
  border: 0;
  border-radius: 0;
  background: transparent;
  overflow: visible;
}

.execution-steps--compact {
  max-width: 100%;
  margin: 2px 0 12px;
}

.execution-steps--completed:not(.execution-steps--open) {
  background: transparent;
}

.execution-steps--open {
  border-color: transparent;
}

.execution-steps__summary {
  min-height: 42px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 950;
  cursor: pointer;
  text-align: left;
}

.execution-steps--compact .execution-steps__summary {
  min-height: 34px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
}

.execution-steps__summary-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.execution-steps--compact .execution-steps__summary-left {
  color: var(--text-secondary);
}

.execution-steps__beacon {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--accent-blue);
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent-blue) 24%, transparent);
  animation: execution-pulse 1.55s ease-in-out infinite;
}

.execution-steps--breathing .execution-steps__summary-left > span:last-child {
  animation: execution-breathe 1.6s ease-in-out infinite;
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

.execution-steps--compact .execution-steps__badge {
  min-height: 20px;
  padding: 0 7px;
  font-size: 10px;
}

.execution-steps--running .execution-steps__badge {
  border-color: color-mix(in srgb, var(--accent-blue) 42%, var(--border-color));
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.execution-steps--waiting_input .execution-steps__badge {
  border-color: color-mix(in srgb, var(--warning) 50%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
  color: var(--warning);
}

.execution-steps--failed .execution-steps__badge {
  border-color: var(--error);
  background: var(--error-soft);
  color: var(--error);
}

.execution-steps__body {
  padding: 6px 0 0;
  border-top: 0;
  animation: execution-reveal 0.18s ease-out;
}

.execution-steps__list {
  display: grid;
  gap: 12px;
}

@media (max-width: 760px) {
  .execution-steps__body {
    padding: 6px 0 0;
  }
}

@keyframes execution-breathe {
  0%, 100% { opacity: 0.68; }
  50% { opacity: 1; }
}

@keyframes execution-pulse {
  0%, 100% {
    opacity: 0.68;
    transform: scale(0.88);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent-blue) 26%, transparent);
  }
  50% {
    opacity: 1;
    transform: scale(1);
    box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent-blue) 0%, transparent);
  }
}

@keyframes execution-reveal {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
