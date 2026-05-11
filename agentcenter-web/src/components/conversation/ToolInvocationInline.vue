<script setup lang="ts">
import { computed } from 'vue'
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

function formatStructuredOutput(value: string | undefined): string {
  if (!value) return ''
  return value
    .replace(/><(?=\/?\w)/g, '>\n<')
    .replace(/\s+(?=\/Users\/)/g, '\n')
    .replace(/\s+(?=\/[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.-]+)+)/g, '\n')
    .trim()
}
</script>

<template>
  <div
    class="tool-invocation"
    :class="{ 'tool-invocation--running': part.status === 'running' }"
  >
    <details :open="part.defaultExpanded">
      <summary class="tool-invocation__head">
        <span class="tool-invocation__name">{{ part.displayName }}</span>
        <span
          class="tool-invocation__status"
          :class="`tool-invocation__status--${part.status}`"
        >
          {{ statusLabel(part.status) }}
        </span>
      </summary>

      <div class="tool-invocation__body">
        <div v-if="part.inputSummary" class="tool-invocation__section">
          <span class="tool-invocation__label">输入</span>
          <pre class="tool-invocation__code">{{ formattedInputSummary }}</pre>
        </div>

        <div v-if="part.outputSummary" class="tool-invocation__section">
          <span class="tool-invocation__label">输出</span>
          <pre class="tool-invocation__code">{{ formattedOutputSummary }}</pre>
        </div>

        <div v-if="!part.inputSummary && !part.outputSummary" class="tool-invocation__empty">
          {{ part.status === 'running' ? '正在执行，详细信息会在返回后更新。' : '暂无详细输出' }}
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.tool-invocation {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card, var(--bg-card));
  overflow: hidden;
}

.tool-invocation details {
  overflow: hidden;
}

.tool-invocation__head {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px;
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.tool-invocation__name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.tool-invocation__body {
  border-top: 1px solid var(--border-color);
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
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
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
</style>
