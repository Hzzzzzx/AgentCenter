<script setup lang="ts">
import { computed } from 'vue'
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

const shouldOpen = computed(() =>
  props.status === 'failed'
  || props.status === 'running'
  || props.status === 'waiting_input'
  || !props.collapsedByDefault
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
    return shouldOpen.value
      ? '执行过程'
      : conciseActionLabel(props.currentAction?.label) ?? '正在处理当前请求'
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
  <details
    class="execution-steps"
    :class="[
      `execution-steps--${status}`,
      { 'execution-steps--compact': hasAnswer },
      { 'execution-steps--breathing': status === 'running' || status === 'waiting_input' },
    ]"
    :open="shouldOpen"
  >
    <summary class="execution-steps__summary">
      <span class="execution-steps__summary-left">
        <span class="execution-steps__chev">›</span>
      <span>{{ summaryText }}</span>
      </span>
      <span class="execution-steps__badge">
        {{ status === 'running' ? '进行中' : status === 'waiting_input' ? '需确认' : status === 'failed' ? '异常' : shouldOpen ? '过程' : '已收起' }}
      </span>
    </summary>

    <div v-if="steps.length > 0" class="execution-steps__body">
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
  margin: 4px 0 12px;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid transparent;
}

.execution-steps--compact {
  max-width: 620px;
  margin: 2px 0 12px;
  border-top-color: color-mix(in srgb, var(--border-color) 62%, transparent);
}

.execution-steps--completed:not([open]) {
  border-bottom-color: transparent;
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

.execution-steps--compact .execution-steps__summary {
  min-height: 34px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
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

.execution-steps--compact .execution-steps__summary-left {
  color: var(--text-secondary);
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

.execution-steps--breathing .execution-steps__summary-left > span:last-child {
  animation: execution-breathe 1.6s ease-in-out infinite;
}

.execution-steps--running .execution-steps__chev,
.execution-steps--waiting_input .execution-steps__chev {
  animation: execution-pulse 1.8s ease-in-out infinite;
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

  .execution-steps__current {
    width: 100%;
  }

  .execution-steps__current small {
    max-width: 100%;
  }
}

@keyframes execution-breathe {
  0%, 100% { opacity: 0.68; }
  50% { opacity: 1; }
}

@keyframes execution-pulse {
  0%, 100% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent-blue) 26%, transparent); }
  50% { box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent-blue) 0%, transparent); }
}
</style>
