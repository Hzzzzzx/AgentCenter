<script setup lang="ts">
import type { ExecutionStep } from './projection/types'
import ToolInvocationInline from './ToolInvocationInline.vue'
import ArtifactEvidenceInline from './ArtifactEvidenceInline.vue'
import DecisionGateInline from './DecisionGateInline.vue'
import RuntimeErrorInline from './RuntimeErrorInline.vue'
import MarkdownContent from './MarkdownContent.vue'

const props = defineProps<{
  step: ExecutionStep
  order: number
  isLast: boolean
}>()

const emit = defineEmits<{
  'open-artifact': [artifactId: string]
  'resolve': [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()

function statusPillClass(status: string): string {
  switch (status) {
    case 'running': return 'step-item__pill--running'
    case 'completed': return 'step-item__pill--completed'
    case 'failed': return 'step-item__pill--failed'
    case 'waiting_input': return 'step-item__pill--waiting'
    case 'pending': return 'step-item__pill--pending'
    default: return ''
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'running': return '进行中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    case 'waiting_input': return '等待输入'
    case 'pending': return '待处理'
    default: return status
  }
}

function kindClass(kind: string): string {
  switch (kind) {
    case 'tool': return 'step-item__index--tool'
    case 'decision': return 'step-item__index--wait'
    case 'error': return 'step-item__index--error'
    default: return ''
  }
}

function shouldShowToolPart(part: {
  type: string
  status?: string
  displayName?: string
  inputSummary?: string
  outputSummary?: string
}, stepTitle?: string): boolean {
  if (part.type !== 'tool') return false
  if (part.status === 'failed') return true

  const hasDetail = Boolean(part.inputSummary?.trim()) || Boolean(part.outputSummary?.trim())
  if (hasDetail) return true

  const displayName = part.displayName?.trim()
  const title = stepTitle?.trim()
  const duplicatesStepTitle = Boolean(displayName && title && displayName === title)

  return part.status === 'running' && !duplicatesStepTitle
}
</script>

<template>
  <div class="step-item" :class="{ 'step-item--last': isLast }">
    <span class="step-item__index" :class="kindClass(step.kind)">{{ order }}</span>
    <div class="step-item__main">
      <div class="step-item__head">
        <strong class="step-item__title">{{ step.title }}</strong>
        <span class="step-item__pill" :class="statusPillClass(step.status)">
          {{ statusLabel(step.status) }}
        </span>
      </div>

      <p v-if="step.summary" class="step-item__summary">{{ step.summary }}</p>

      <template v-for="(part, index) in step.parts" :key="`${step.id}-part-${index}`">
        <!-- text part -->
        <div v-if="part.type === 'text'" class="step-item__text">
          <MarkdownContent :content="part.text" />
        </div>

        <!-- reasoning summary (collapsible) -->
        <details v-else-if="part.type === 'reasoning'" class="step-item__reasoning">
          <summary>思考摘要</summary>
          <div class="step-item__reasoning-body">{{ part.summary }}</div>
        </details>

        <!-- tool invocation -->
        <ToolInvocationInline
          v-else-if="part.type === 'tool' && shouldShowToolPart(part, step.title)"
          :part="part"
        />

        <!-- artifact evidence -->
        <div v-else-if="part.type === 'artifact'" class="step-item__artifact-row">
          <ArtifactEvidenceInline
            :part="part"
            @open-artifact="(id) => emit('open-artifact', id)"
          />
        </div>

        <!-- decision gate -->
        <DecisionGateInline
          v-else-if="part.type === 'decision'"
          :part="part"
          @resolve="(confirmationId, value, meta) => emit('resolve', confirmationId, value, meta)"
        />

        <!-- error -->
        <RuntimeErrorInline
          v-else-if="part.type === 'error'"
          :part="part"
        />

        <!-- status note -->
        <div v-else-if="part.type === 'status'" class="step-item__status-note">
          <span class="step-item__status-dot" />
          <span>{{ part.label }}</span>
          <span v-if="part.detail" class="step-item__status-detail">{{ part.detail }}</span>
        </div>

        <!-- raw event -->
        <details v-else-if="part.type === 'raw'" class="step-item__raw">
          <summary>{{ part.title }}</summary>
          <pre class="step-item__raw-body">{{ JSON.stringify(part.payload, null, 2) }}</pre>
        </details>
      </template>
    </div>
  </div>
</template>

<style scoped>
.step-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  position: relative;
}

.step-item:not(.step-item--last)::after {
  content: "";
  position: absolute;
  top: 30px;
  bottom: -12px;
  left: 13px;
  width: 1px;
  background: var(--border-color);
}

.step-item__index {
  position: relative;
  z-index: 1;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--surface-card, var(--bg-card));
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  font-weight: 950;
}

.step-item__index--tool {
  border-color: color-mix(in srgb, var(--accent-blue) 50%, var(--border-color));
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.step-item__index--wait {
  border-color: color-mix(in srgb, var(--warning) 50%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
  color: var(--warning);
}

.step-item__index--error {
  border-color: var(--error);
  background: var(--error-soft);
  color: var(--error);
}

.step-item__main {
  min-width: 0;
  padding-bottom: 2px;
}

.step-item__head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.step-item__title {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-item__pill {
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

.step-item__pill--running {
  background: var(--brand-soft);
  color: var(--accent-blue);
  border-color: color-mix(in srgb, var(--accent-blue) 40%, var(--border-color));
}

.step-item__pill--completed {
  background: var(--success-soft);
  color: var(--success);
  border-color: color-mix(in srgb, var(--success) 40%, var(--border-color));
}

.step-item__pill--failed {
  background: var(--error-soft);
  color: var(--error);
  border-color: var(--error);
}

.step-item__pill--waiting {
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
  color: var(--warning);
  border-color: color-mix(in srgb, var(--warning) 50%, var(--border-color));
}

.step-item__pill--pending {
  background: var(--surface-card, var(--bg-card));
  color: var(--text-muted);
}

.step-item__summary {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.step-item__text {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.step-item__text :deep(.markdown-content) {
  font-size: 13px;
  line-height: 1.62;
}

.step-item__artifact-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.step-item__reasoning {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card, var(--bg-card));
  overflow: hidden;
}

.step-item__reasoning summary {
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.step-item__reasoning-body {
  padding: 8px 10px;
  border-top: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.step-item__status-note {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.step-item__status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-blue);
  flex: 0 0 auto;
}

.step-item__status-detail {
  color: var(--text-muted);
  font-size: 11px;
}

.step-item__raw {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card, var(--bg-card));
  overflow: hidden;
}

.step-item__raw summary {
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.step-item__raw-body {
  margin: 0;
  padding: 8px 10px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-primary, #111827);
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 760px) {
  .step-item {
    grid-template-columns: 24px minmax(0, 1fr);
    gap: 8px;
  }

  .step-item__index {
    width: 24px;
    height: 24px;
    font-size: 11px;
  }

  .step-item:not(.step-item--last)::after {
    left: 11px;
  }
}
</style>
