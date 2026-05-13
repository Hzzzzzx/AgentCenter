<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RuntimeEventDto } from '../../api/types'

interface TracePayload {
  kind?: string
  status?: string
  title?: string
  summary?: string
  toolName?: string | null
  toolCallId?: string | null
  visibility?: string
  type?: string
  label?: string
  skillName?: string
  reason?: string
  errorMessage?: string
  output?: string
  isError?: boolean
  success?: boolean
  actionType?: string
  requestType?: string
  confirmationId?: string
  id?: string
}

interface TraceItem {
  id: string
  kind: string
  status: string
  title: string
  summary: string
  toolName?: string | null
  toolCallId?: string | null
  createdAt: string | null
}

const props = defineProps<{
  events: RuntimeEventDto[]
  nodeId?: string | null
  sessionId?: string | null
  from?: string | null
  to?: string | null
  defaultExpanded?: boolean
  showWhenEmpty?: boolean
  emptyText?: string
  includeSessionWindow?: boolean
}>()

const expanded = ref(false)
const manuallyToggled = ref(false)

const scopeKey = computed(() =>
  `${props.nodeId || ''}|${props.sessionId || ''}|${props.from || ''}|${props.to || ''}`
)

watch([() => props.defaultExpanded, scopeKey], ([defaultExpanded], previous) => {
  const scopeChanged = previous ? scopeKey.value !== previous[1] : true
  if (scopeChanged) manuallyToggled.value = false
  if (scopeChanged || defaultExpanded || !manuallyToggled.value) {
    expanded.value = Boolean(defaultExpanded)
  }
}, { immediate: true })

const traceItems = computed(() => {
  const items = props.events
    .map((event, index) => ({ event, index }))
    .filter(({ event }) => isProcessEvent(event))
    .filter(({ event }) => matchesScope(event))
    .sort((a, b) => {
      const at = timestamp(a.event.createdAt)
      const bt = timestamp(b.event.createdAt)
      if (at !== bt) return at - bt
      return a.index - b.index
    })
    .map(({ event }) => toTraceItem(event))
    .filter((item): item is TraceItem => Boolean(item))
  return compactTraceItems(items)
})

const visibleItems = computed<TraceItem[]>(() => {
  return traceItems.value
})

const hasTraces = computed(() => visibleItems.value.length > 0)

const summaryLine = computed(() => {
  const events = visibleItems.value
  if (events.length === 0) return ''
  const toolCalls = new Set(
    events
      .filter((e) => e.kind === 'tool_call')
      .map((e) => e.toolCallId || e.toolName || e.id)
  ).size
  const reasoning = events.filter((e) => e.kind === 'reasoning_summary').length
  const confirmations = events.filter((e) => e.kind === 'confirmation').length
  const errors = events.filter((e) => e.status === 'failed' || e.kind === 'error').length
  const parts: string[] = [`${events.length} 步`]
  if (toolCalls > 0) parts.push(`${toolCalls} 个工具`)
  if (reasoning > 0) parts.push(`${reasoning} 条思考`)
  if (confirmations > 0) parts.push(`${confirmations} 个确认点`)
  if (errors > 0) parts.push(`${errors} 个异常`)
  return `过程 · ${parts.join(' · ')}`
})

function isProcessEvent(event: RuntimeEventDto): boolean {
  return [
    'PROCESS_TRACE',
    'PERMISSION_REQUIRED',
    'ERROR',
    'CONFIRMATION_CREATED',
    'CONFIRMATION_RESOLVED',
  ].includes(event.eventType)
}

function matchesScope(event: RuntimeEventDto): boolean {
  if (props.nodeId) {
    if (event.workflowNodeInstanceId) {
      return event.workflowNodeInstanceId === props.nodeId
    }
    return Boolean(props.includeSessionWindow && props.sessionId && event.sessionId === props.sessionId && isWithinWindow(event))
  }
  if (props.sessionId && event.sessionId !== props.sessionId) {
    return false
  }
  return isWithinWindow(event)
}

function isWithinWindow(event: RuntimeEventDto): boolean {
  const eventTime = timestamp(event.createdAt)
  const from = props.from ? timestamp(props.from) - 1000 : Number.NEGATIVE_INFINITY
  const to = props.to ? timestamp(props.to) + 1000 : Number.POSITIVE_INFINITY
  return eventTime >= from && eventTime <= to
}

function toTraceItem(event: RuntimeEventDto): TraceItem | null {
  const payload = parsePayload(event.payloadJson)
  switch (event.eventType) {
    case 'PROCESS_TRACE':
      if ((payload.kind || 'node_status') === 'node_status') return null
      return {
        id: event.id,
        kind: payload.kind || 'node_status',
        status: payload.status || 'running',
        title: payload.title || kindLabel(payload.kind || 'node_status'),
        summary: payload.summary || payload.title || '正在处理',
        toolName: payload.toolName,
        toolCallId: payload.toolCallId,
        createdAt: event.createdAt,
      }
    case 'PERMISSION_REQUIRED':
      return {
        id: event.id,
        kind: 'confirmation',
        status: 'waiting',
        title: '权限确认',
        summary: payload.title || payload.label || '需要用户授权后继续',
        createdAt: event.createdAt,
      }
    case 'CONFIRMATION_CREATED':
      return {
        id: event.id,
        kind: 'confirmation',
        status: 'waiting',
        title: '确认点',
        summary: '需要用户处理确认项后继续',
        createdAt: event.createdAt,
      }
    case 'CONFIRMATION_RESOLVED':
      return {
        id: event.id,
        kind: 'confirmation',
        status: 'completed',
        title: '确认结果',
        summary: `用户已提交 ${payload.actionType || '确认结果'}`,
        createdAt: event.createdAt,
      }
    case 'ERROR':
      return {
        id: event.id,
        kind: 'error',
        status: 'failed',
        title: '异常',
        summary: payload.reason || payload.errorMessage || payload.label || 'Agent 运行过程中出现异常',
        createdAt: event.createdAt,
      }
    default:
      return null
  }
}

function parsePayload(payloadJson: string | null): TracePayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as TracePayload
      : {}
  } catch {
    return {}
  }
}

function compactTraceItems(items: TraceItem[]): TraceItem[] {
  const out: TraceItem[] = []
  items.forEach((item) => {
    const last = out.at(-1)
    if (last && traceDedupeKey(last) === traceDedupeKey(item)) {
      return
    }
    out.push(item)
  })
  return out
}

function traceDedupeKey(item: TraceItem): string {
  if (item.kind === 'tool_call' && item.toolCallId) {
    return `${item.kind}|${item.status}|${item.toolCallId}|${item.summary}`
  }
  return `${item.kind}|${item.status}|${item.summary}`
}

function kindLabel(kind: string): string {
  switch (kind) {
    case 'reasoning_summary': return '思考摘要'
    case 'tool_call': return '工具调用'
    case 'node_status': return '状态'
    case 'confirmation': return '确认点'
    case 'context_anchor': return '上下文恢复'
    case 'compaction': return '上下文压缩'
    case 'error': return '异常'
    default: return '过程'
  }
}

function statusIcon(status: string): string {
  switch (status) {
    case 'running': return '●'
    case 'completed': return '✓'
    case 'failed': return '✕'
    case 'waiting': return '◎'
    default: return '·'
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'running': return 'trace-item__icon--running'
    case 'completed': return 'trace-item__icon--completed'
    case 'failed': return 'trace-item__icon--failed'
    case 'waiting': return 'trace-item__icon--waiting'
    default: return ''
  }
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatTime(value: string | null): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function traceText(trace: TraceItem): string {
  const text = trace.summary || trace.title
  if (!trace.toolName) return text
  return text.startsWith(trace.toolName) ? text : `${trace.toolName}：${text}`
}

function toggleExpanded(): void {
  manuallyToggled.value = true
  expanded.value = !expanded.value
}
</script>

<template>
  <div v-if="hasTraces" class="process-trace">
    <button
      class="process-trace__toggle"
      :aria-expanded="expanded"
      @click="toggleExpanded"
    >
      <span class="process-trace__toggle-left">
        <span class="process-trace__chevron" :class="{ 'process-trace__chevron--open': expanded }">›</span>
        <span class="process-trace__summary">{{ summaryLine }}</span>
      </span>
      <span v-if="defaultExpanded" class="process-trace__live">实时更新</span>
    </button>
    <div v-if="expanded" class="process-trace__timeline">
      <div
        v-for="trace in visibleItems"
        :key="trace.id"
        class="trace-item"
      >
        <span class="trace-item__icon" :class="statusClass(trace.status)">
          {{ statusIcon(trace.status) }}
        </span>
        <span class="trace-item__kind">{{ kindLabel(trace.kind) }}</span>
        <span class="trace-item__text">{{ traceText(trace) }}</span>
        <span class="trace-item__time">{{ formatTime(trace.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.process-trace {
  margin: 4px 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-overlay);
  overflow: hidden;
}

.process-trace__toggle {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 0 10px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  text-align: left;
}

.process-trace__toggle-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
}

.process-trace__toggle:hover {
  background: var(--surface-hover);
}

.process-trace__chevron {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 5px;
  background: var(--surface-hover);
  font-size: 10px;
  transition: transform 0.15s ease;
}

.process-trace__chevron--open {
  transform: rotate(90deg);
}

.process-trace__summary {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-trace__live {
  flex: 0 0 auto;
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 850;
}

.process-trace__timeline {
  padding: 4px 0;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--surface-hover) 48%, transparent);
}

.trace-item {
  min-height: 32px;
  display: grid;
  grid-template-columns: 20px 76px minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 65%, transparent);
}

.trace-item:last-child {
  border-bottom: 0;
}

.trace-item__icon {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 900;
}

.trace-item__icon--running {
  color: var(--accent-blue);
  background: var(--brand-soft);
  animation: pulse-dot 1.5s ease-in-out infinite;
}

.trace-item__icon--completed {
  color: var(--success);
  background: var(--success-soft);
}

.trace-item__icon--failed {
  color: var(--error);
  background: var(--error-soft);
}

.trace-item__icon--waiting {
  color: var(--warning);
  background: var(--warning-soft);
}

.trace-item__kind {
  color: var(--text-primary);
  font-weight: 850;
  white-space: nowrap;
}

.trace-item__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-item__time {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

@media (max-width: 760px) {
  .trace-item {
    grid-template-columns: 20px minmax(0, 1fr);
  }

  .trace-item__kind,
  .trace-item__time {
    display: none;
  }
}
</style>
