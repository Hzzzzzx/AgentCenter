<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ToolInvocationPart } from './projection/types'

const props = defineProps<{
  part: ToolInvocationPart
}>()

function statusLabel(status: string): string {
  switch (status) {
    case 'running': return '执行中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    default: return status
  }
}

const formattedInputSummary = computed(() => formatStructuredOutput(props.part.inputSummary))
const formattedOutputSummary = computed(() => formatStructuredOutput(props.part.outputSummary))
const hasDetail = computed(() =>
  Boolean(props.part.inputSummary || props.part.outputSummary || props.part.status === 'failed')
)
const autoOpen = computed(() =>
  hasDetail.value && (
    props.part.status === 'failed'
    || props.part.defaultExpanded
  )
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

const showBody = computed(() => hasDetail.value && isOpen.value)
const collapsedSummary = computed(() =>
  formattedOutputSummary.value
  || formattedInputSummary.value
  || (props.part.status === 'completed' ? '已完成，详情已收起。' : '')
)

function toggleOpen(): void {
  const next = !isOpen.value
  isOpen.value = next
  manuallyPinned.value = !autoOpen.value && next
}

function formatStructuredOutput(value: string | undefined): string {
  if (!value) return ''
  return value
    .replace(/\uFFFD+/g, '[无法解码字符]')
    .replace(/><(?=\/?\w)/g, '>\n<')
    .replace(/\s+(?=\/Users\/)/g, '\n')
    .replace(/\s+(?=\/[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.-]+)+)/g, '\n')
    .trim()
}
</script>

<template>
  <section
    class="tool-invocation"
    :class="{
      'tool-invocation--running': part.status === 'running',
      'tool-invocation--open': showBody,
      'tool-invocation--failed': part.status === 'failed',
      'tool-invocation--empty-running': part.status === 'running' && !hasDetail,
    }"
  >
    <button
      type="button"
      class="tool-invocation__head"
      :aria-expanded="showBody"
      @click="toggleOpen"
    >
      <span class="tool-invocation__name">{{ part.displayName }}</span>
      <span class="tool-invocation__meta">
        <span
          class="tool-invocation__status"
          :class="`tool-invocation__status--${part.status}`"
        >
          {{ statusLabel(part.status) }}
        </span>
        <span v-if="hasDetail" class="tool-invocation__toggle">
          {{ showBody ? '收起详情' : '展开详情' }}
        </span>
      </span>
    </button>

    <p v-if="!showBody && collapsedSummary" class="tool-invocation__summary">
      {{ collapsedSummary }}
    </p>

    <div v-if="showBody" class="tool-invocation__body">
      <div v-if="part.inputSummary" class="tool-invocation__section">
        <span class="tool-invocation__label">输入</span>
        <pre class="tool-invocation__code">{{ formattedInputSummary }}</pre>
      </div>

      <div v-if="part.outputSummary" class="tool-invocation__section">
        <span class="tool-invocation__label">输出</span>
        <pre class="tool-invocation__code">{{ formattedOutputSummary }}</pre>
      </div>

      <div v-if="!part.inputSummary && !part.outputSummary" class="tool-invocation__empty">
        {{ part.status === 'running' ? '正在执行，详细信息会在返回后更新。' : part.status === 'failed' ? '执行失败，暂无更多错误详情。' : '暂无详细输出' }}
      </div>
    </div>
  </section>
</template>

<style scoped>
.tool-invocation {
  margin-top: 8px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.tool-invocation--open {
  border-color: transparent;
}

.tool-invocation--failed {
  border-color: transparent;
}

.tool-invocation__head {
  min-height: 34px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  text-align: left;
}

.tool-invocation__name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Segoe UI Emoji", "Segoe UI Symbol", "Apple Color Emoji", "Noto Color Emoji", monospace;
  font-size: 12px;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-invocation__meta {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tool-invocation__status {
  flex: 0 0 auto;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 850;
  white-space: nowrap;
}

.tool-invocation__status--running {
  background: var(--brand-soft);
  color: var(--accent-blue);
  animation: tool-status-breathe 1.45s ease-in-out infinite;
}

.tool-invocation__status--completed {
  background: var(--success-soft);
  color: var(--success);
}

.tool-invocation__status--failed {
  background: var(--error-soft);
  color: var(--error);
}

.tool-invocation__toggle {
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 850;
  white-space: nowrap;
}

.tool-invocation__summary {
  margin: 0;
  padding: 0 10px 8px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.tool-invocation__body {
  margin-top: 6px;
  border: 1px solid color-mix(in srgb, var(--border-color) 70%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-card, var(--bg-card)) 72%, transparent);
  animation: tool-detail-reveal 0.18s ease-out;
}

.tool-invocation__section {
  padding: 0;
}

.tool-invocation__section + .tool-invocation__section {
  border-top: 1px solid var(--border-color);
}

.tool-invocation__label {
  display: block;
  padding: 6px 10px 0;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.tool-invocation__code {
  margin: 0;
  padding: 6px 10px 8px;
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Segoe UI Emoji", "Segoe UI Symbol", "Apple Color Emoji", "Noto Color Emoji", monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tool-invocation__empty {
  padding: 8px 10px;
  border-top: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 12px;
}

@keyframes tool-status-breathe {
  0%, 100% { opacity: 0.68; }
  50% { opacity: 1; }
}

@keyframes tool-detail-reveal {
  from {
    opacity: 0;
    transform: translateY(3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
