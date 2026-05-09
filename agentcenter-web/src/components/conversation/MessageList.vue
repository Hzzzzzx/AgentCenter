<script setup lang="ts">
import { computed } from 'vue'
import type { AgentMessageDto, RuntimeEventDto } from '../../api/types'
import MarkdownContent from './MarkdownContent.vue'

const props = withDefaults(defineProps<{
  messages: AgentMessageDto[]
  streamingText?: string
  runtimeEvents?: RuntimeEventDto[]
  activeNodeId?: string | null
  activeNodeState?: string | null
  activeSessionId?: string | null
  running?: boolean
}>(), {
  streamingText: '',
  runtimeEvents: () => [],
  activeNodeId: null,
  activeNodeState: null,
  activeSessionId: null,
  running: false,
})

const emit = defineEmits<{
  'open-artifact': [title: string]
}>()

type SystemLinePart = {
  kind: 'text' | 'artifact'
  text: string
}

type RuntimePayload = {
  kind?: string
  status?: string
  type?: string
  title?: string
  summary?: string
  toolName?: string | null
  actionType?: string
  reason?: string
  errorMessage?: string
  label?: string
  output?: string
  isError?: boolean
  success?: boolean
  skillName?: string
  toolCallId?: string
  confirmationId?: string
  requestType?: string
  actionDescription?: string
  question?: string
  contextSummary?: string
  options?: string
  comment?: string
  resolutionPayload?: string
  command?: string
  input?: string
  args?: unknown
  arguments?: unknown
  result?: string
  detail?: string
  nodeName?: string
  visibility?: string
}

type WorkflowInputSummary = {
  node: string
  code: string
  title: string
  description: string
  status: string
  priority: string
  skill: string
}

type TimelineItem =
  | { type: 'message'; id: string; createdAt: string | null; order: number; message: AgentMessageDto }
  | { type: 'runtime-event'; id: string; createdAt: string | null; order: number; event: RuntimeEventDto; payload: RuntimePayload }
  | { type: 'tool-call'; id: string; createdAt: string | null; order: number; events: RuntimeEventDto[]; payload: RuntimePayload }
  | { type: 'streaming'; id: string; createdAt: string | null; order: number; content: string }

const persistedMessages = computed(() =>
  props.messages
    .map((message, index) => ({ message, index }))
    .filter(({ message }) => Boolean(message.content?.trim()))
    .sort((a, b) => compareMessages(a.message, b.message, a.index, b.index))
    .map(({ message }) => message)
)

const activeSessionId = computed(() =>
  props.activeSessionId
  ?? persistedMessages.value.at(-1)?.sessionId
  ?? ''
)

const timelineItems = computed<TimelineItem[]>(() => {
  const visibleRuntimeEvents = dedupeRuntimeEvents(
    props.runtimeEvents
      .filter((event) => belongsToActiveSession(event))
      .filter((event) => isVisibleRuntimeEvent(event))
  )
  const runtimeItems = buildRuntimeTimelineItems(visibleRuntimeEvents)

  const items: TimelineItem[] = [
    ...persistedMessages.value.map((message, index) => ({
      type: 'message' as const,
      id: message.id,
      message,
      createdAt: message.createdAt,
      order: index * 10 + messagePriority(message),
    })),
    ...runtimeItems,
  ]

  const streamingText = props.streamingText.trim()
  if (streamingText) {
    items.push({
      type: 'streaming',
      id: `streaming-${activeSessionId.value || 'session'}`,
      createdAt: new Date().toISOString(),
      order: 200000,
      content: streamingText,
    })
  }

  return items.sort((a, b) => {
    const timeDiff = timestamp(a.createdAt) - timestamp(b.createdAt)
    if (timeDiff !== 0) return timeDiff
    return a.order - b.order
  })
})

const hasContent = computed(() =>
  timelineItems.value.length > 0
)

function buildRuntimeTimelineItems(events: RuntimeEventDto[]): TimelineItem[] {
  const items: TimelineItem[] = []
  const toolGroups = new Map<string, { key: string; events: RuntimeEventDto[]; firstIndex: number }>()

  events.forEach((event, index) => {
    const payload = parsePayload(event.payloadJson)
    const toolKey = toolLifecycleKey(event, payload)
    if (toolKey) {
      const group = toolGroups.get(toolKey)
      if (group) {
        group.events.push(event)
      } else {
        toolGroups.set(toolKey, { key: toolKey, events: [event], firstIndex: index })
      }
      return
    }

    items.push({
      type: 'runtime-event',
      id: event.id,
      event,
      payload,
      createdAt: event.createdAt,
      order: 100000 + ((event.seqNo ?? index) * 10) + eventPriority(event),
    })
  })

  toolGroups.forEach((group) => {
    const sortedEvents = [...group.events].sort(compareRuntimeEvents)
    const event = sortedEvents[0]
    items.push({
      type: 'tool-call',
      id: `tool-call-${group.key}`,
      events: sortedEvents,
      payload: mergeToolPayload(sortedEvents),
      createdAt: event.createdAt,
      order: 100000 + ((event.seqNo ?? group.firstIndex) * 10) + 3,
    })
  })

  return items
}

function toolLifecycleKey(event: RuntimeEventDto, payload: RuntimePayload): string | null {
  if (!isToolLifecycleEvent(event, payload)) return null
  const name = payload.toolName || payload.skillName || payload.label || payload.command || 'tool'
  const callKey = shouldGroupToolByName(event, payload, name)
    ? name
    : payload.toolCallId || name
  return [
    event.sessionId || '',
    event.workflowNodeInstanceId || '',
    callKey,
    name,
  ].join('|')
}

function shouldGroupToolByName(event: RuntimeEventDto, payload: RuntimePayload, name: string): boolean {
  return event.eventType === 'SKILL_STARTED'
    || event.eventType === 'SKILL_COMPLETED'
    || name === 'skill'
    || payload.label === 'skill'
}

function isToolLifecycleEvent(event: RuntimeEventDto, payload: RuntimePayload): boolean {
  return event.eventType === 'SKILL_STARTED'
    || event.eventType === 'SKILL_COMPLETED'
    || event.eventType === 'MCP_CALL'
    || payload.kind === 'tool_call'
}

function mergeToolPayload(events: RuntimeEventDto[]): RuntimePayload {
  return events.reduce<RuntimePayload>((merged, event) => {
    const payload = parsePayload(event.payloadJson)
    return {
      ...merged,
      ...payload,
      output: payload.output || merged.output,
      result: payload.result || merged.result,
      status: toolStatusFromEvent(event, payload) || merged.status,
    }
  }, {})
}

function toolStatusFromEvent(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (event.eventType === 'SKILL_COMPLETED') {
    return payload.isError === true || payload.success === false ? 'failed' : 'completed'
  }
  if (event.eventType === 'SKILL_STARTED') return 'running'
  return payload.status || ''
}

function compareRuntimeEvents(a: RuntimeEventDto, b: RuntimeEventDto): number {
  const timeDiff = timestamp(a.createdAt) - timestamp(b.createdAt)
  if (timeDiff !== 0) return timeDiff
  return (a.seqNo ?? 0) - (b.seqNo ?? 0)
}

function dedupeRuntimeEvents(events: RuntimeEventDto[]): RuntimeEventDto[] {
  const seenStatusKeys = new Set<string>()
  return events.filter((event) => {
    const payload = parsePayload(event.payloadJson)
    if (!isRepeatableNodeStatus(event, payload)) return true

    const key = [
      event.sessionId || '',
      event.workflowNodeInstanceId || '',
      payload.kind || '',
      payload.status || '',
      payload.title || '',
      payload.summary || '',
    ].join('|')
    if (seenStatusKeys.has(key)) return false
    seenStatusKeys.add(key)
    return true
  })
}

function isRepeatableNodeStatus(event: RuntimeEventDto, payload: RuntimePayload): boolean {
  return event.eventType === 'PROCESS_TRACE'
    && payload.kind === 'node_status'
    && payload.status === 'running'
}

function isVisibleRuntimeEvent(event: RuntimeEventDto): boolean {
  const payload = parsePayload(event.payloadJson)
  if (payload.kind === 'prompt_debug') return false
  return [
    'PROCESS_TRACE',
    'SKILL_STARTED',
    'SKILL_COMPLETED',
    'MCP_CALL',
    'PERMISSION_REQUIRED',
    'ERROR',
    'CONFIRMATION_RESOLVED',
  ].includes(event.eventType)
}

function belongsToActiveSession(event: RuntimeEventDto): boolean {
  if (activeSessionId.value && event.sessionId && event.sessionId !== activeSessionId.value) {
    return false
  }
  return true
}

function isGeneratedArtifactNote(content: string | null | undefined): boolean {
  return Boolean(content?.startsWith('已生成 '))
}

function normalizeToolOutput(output: string): string {
  return output.replace(/\r\n/g, '\n').trim()
}

function parsePayload(payloadJson: string | null): RuntimePayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as RuntimePayload
      : {}
  } catch {
    return {}
  }
}

function messagePriority(message: AgentMessageDto): number {
  if (message.role === 'USER') return 0
  if (message.role === 'SYSTEM' && !isGeneratedArtifactNote(message.content)) return 1
  if (message.role === 'ASSISTANT') return 6
  if (message.role === 'TOOL') return 5
  return 7
}

function eventPriority(event: RuntimeEventDto): number {
  switch (event.eventType) {
    case 'SKILL_STARTED': return 2
    case 'SKILL_COMPLETED': return 3
    case 'MCP_CALL': return 4
    case 'PROCESS_TRACE': return 4
    case 'ASSISTANT_DELTA': return 5
    case 'PERMISSION_REQUIRED': return 8
    case 'CONFIRMATION_RESOLVED': return 9
    case 'ERROR': return 10
    default: return 6
  }
}

function formatTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '刚刚'
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return '刚刚'
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function compareMessages(a: AgentMessageDto, b: AgentMessageDto, aIndex: number, bIndex: number): number {
  if (a.seqNo !== b.seqNo) return a.seqNo - b.seqNo
  const timeDiff = timestamp(a.createdAt) - timestamp(b.createdAt)
  if (timeDiff !== 0) return timeDiff
  return aIndex - bIndex
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function roleTitle(message: AgentMessageDto): string {
  if (message.role === 'TOOL') return '工具输出'
  if (message.role === 'SYSTEM') return '上下文'
  return '助手'
}

function roleGlyph(message: AgentMessageDto): string {
  if (message.role === 'TOOL') return 'T'
  if (message.role === 'SYSTEM') return 'i'
  return 'A'
}

function statusLabel(message: AgentMessageDto): string {
  if (message.status === 'FAILED') return '回复失败'
  if (message.role === 'ASSISTANT') return '回复完成'
  if (message.role === 'TOOL') return '工具输出'
  return '已记录'
}

function timelineRuntimeEvent(item: Extract<TimelineItem, { type: 'runtime-event' | 'tool-call' }>): RuntimeEventDto {
  return item.type === 'tool-call' ? item.events.at(-1) ?? item.events[0] : item.event
}

function isUserInteractionInputEvent(event: RuntimeEventDto): boolean {
  return event.eventType === 'CONFIRMATION_RESOLVED'
}

function interactionInputTitle(payload: RuntimePayload): string {
  if (payload.title?.trim()) return `处理交互：${payload.title.trim()}`
  if (payload.confirmationId?.trim()) return `处理交互：${payload.confirmationId.trim()}`
  return '处理交互'
}

function interactionInputContent(payload: RuntimePayload): string {
  const lines = [
    payload.question ? `原始问题：${payload.question}` : '',
    payload.contextSummary ? `上下文：${payload.contextSummary}` : '',
    payload.options ? `可选项：${formatInteractionOptions(payload.options)}` : '',
    payload.actionDescription || actionTypeLabel(payload.actionType),
    payload.resolutionPayload ? `提交内容：${formatResolutionPayload(payload.resolutionPayload)}` : '',
    payload.comment ? `备注：${payload.comment}` : '',
    payload.requestType ? `交互类型：${payload.requestType}` : '',
  ].filter(Boolean)
  return lines.join('\n')
}

function formatInteractionOptions(value: string): string {
  try {
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => formatOptionItem(item)).join('；')
    }
  } catch {
    // use original options text below
  }
  return value
}

function formatOptionItem(item: unknown): string {
  if (typeof item === 'string') return item
  if (typeof item === 'object' && item !== null && !Array.isArray(item)) {
    const record = item as Record<string, unknown>
    const value = record.value ?? record.key ?? record.id
    const label = record.label ?? record.name ?? record.title
    if (value && label) return `${String(value)}: ${String(label)}`
    if (label) return String(label)
    if (value) return String(value)
  }
  return formatPayloadValue(item)
}

function actionTypeLabel(actionType: string | undefined): string {
  switch (actionType) {
    case 'APPROVE': return '用户确认通过'
    case 'REJECT': return '用户拒绝'
    case 'CHOOSE': return '用户选择'
    case 'SUPPLEMENT': return '用户补充'
    case 'RETRY': return '用户重试'
    case 'SKIP': return '用户跳过'
    default: return actionType ? `用户操作：${actionType}` : '用户已处理交互'
  }
}

function formatResolutionPayload(value: string): string {
  try {
    const parsed: unknown = JSON.parse(value)
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      return Object.entries(parsed)
        .map(([key, item]) => `${key}=${String(item)}`)
        .join('，')
    }
  } catch {
    // keep the original submitted value below
  }
  return value
}

function runtimeEventTitle(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (event.eventType === 'PROCESS_TRACE') {
    if (payload.kind === 'reasoning_summary') return 'Agent 思考'
    if (payload.kind === 'tool_call') return `工具调用：${payload.toolName || payload.title || payload.label || '未命名工具'}`
    if (payload.kind === 'node_status') return `节点状态：${payload.title || payload.nodeName || '工作流节点'}`
    if (payload.kind === 'confirmation') return '触发用户交互'
    if (payload.kind === 'error') return '运行错误'
    return payload.title || '运行事件'
  }
  if (event.eventType === 'SKILL_STARTED') return `开始执行 Skill：${payload.skillName || payload.label || '未命名'}`
  if (event.eventType === 'SKILL_COMPLETED') return `Skill 执行完成：${payload.skillName || payload.label || '未命名'}`
  if (event.eventType === 'MCP_CALL') return `工具调用：${payload.toolName || payload.command || payload.label || payload.title || 'MCP'}`
  if (event.eventType === 'PERMISSION_REQUIRED') return '需要授权'
  if (event.eventType === 'CONFIRMATION_CREATED') return '触发用户交互'
  if (event.eventType === 'CONFIRMATION_RESOLVED') return '用户交互已处理'
  if (event.eventType === 'ERROR') return '运行错误'
  return event.eventType
}

function runtimeEventSummary(event: RuntimeEventDto, payload: RuntimePayload): string {
  return toolInvocationSummary(event, payload)
    || payload.summary
    || payload.reason
    || payload.errorMessage
    || payload.detail
    || payload.title
    || payload.label
    || event.eventType
}

function runtimeEventOutput(payload: RuntimePayload): string {
  return normalizeToolOutput(payload.output || payload.result || '')
}

function runtimeEventGlyph(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (event.eventType === 'MCP_CALL' || event.eventType === 'SKILL_STARTED' || event.eventType === 'SKILL_COMPLETED' || payload.kind === 'tool_call') return 'T'
  if (event.eventType === 'CONFIRMATION_CREATED' || event.eventType === 'CONFIRMATION_RESOLVED' || event.eventType === 'PERMISSION_REQUIRED') return '!'
  if (event.eventType === 'ERROR' || payload.kind === 'error') return 'E'
  return 'A'
}

function runtimeEventPill(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (payload.status === 'running' || event.eventType === 'SKILL_STARTED') return '进行中'
  if (payload.status === 'failed' || payload.isError === true || payload.success === false || event.eventType === 'ERROR') return '失败'
  if (event.eventType === 'CONFIRMATION_CREATED' || event.eventType === 'PERMISSION_REQUIRED') return '待处理'
  if (event.eventType === 'CONFIRMATION_RESOLVED') return '已处理'
  if (event.eventType === 'SKILL_COMPLETED') return '完成'
  return payload.status || '事件'
}

function runtimeEventClass(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (event.eventType === 'ERROR' || payload.kind === 'error' || payload.isError === true || payload.success === false) return 'runtime-event--error'
  if (event.eventType === 'CONFIRMATION_CREATED' || event.eventType === 'PERMISSION_REQUIRED') return 'runtime-event--interaction'
  if (event.eventType === 'MCP_CALL' || event.eventType === 'SKILL_STARTED' || event.eventType === 'SKILL_COMPLETED' || payload.kind === 'tool_call') return 'runtime-event--tool'
  return 'runtime-event--trace'
}

function toolInvocationSummary(event: RuntimeEventDto, payload: RuntimePayload): string {
  if (event.eventType !== 'MCP_CALL' && payload.kind !== 'tool_call') return ''
  const parts = [
    payload.command ? `命令：${payload.command}` : '',
    payload.input ? `输入：${payload.input}` : '',
    payload.args !== undefined ? `参数：${formatPayloadValue(payload.args)}` : '',
    payload.arguments !== undefined ? `参数：${formatPayloadValue(payload.arguments)}` : '',
  ].filter(Boolean)
  return parts.join('\n')
}

function formatPayloadValue(value: unknown): string {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function shouldRenderMarkdown(message: AgentMessageDto): boolean {
  return message.role === 'ASSISTANT' || message.contentFormat === 'MARKDOWN'
}

function isWorkflowInputMessage(message: AgentMessageDto): boolean {
  return message.role === 'USER'
    && message.contentFormat === 'MARKDOWN'
    && Boolean(message.content?.startsWith('请执行工作流节点：'))
}

function workflowInputSummary(message: AgentMessageDto): WorkflowInputSummary {
  const content = message.content ?? ''
  return {
    node: findWorkflowInputValue(content, /^请执行工作流节点：(.+)$/m),
    code: findWorkflowInputValue(content, /^- 工作项编号：(.+)$/m),
    title: findWorkflowInputValue(content, /^- 工作项标题：(.+)$/m),
    description: findWorkflowTaskInfo(content),
    status: findWorkflowInputValue(content, /^- 工作项状态：(.+)$/m),
    priority: findWorkflowInputValue(content, /^- 优先级：(.+)$/m),
    skill: findWorkflowInputValue(content, /^- 使用 Skill：(.+)$/m),
  }
}

function findWorkflowInputValue(content: string, pattern: RegExp): string {
  return content.match(pattern)?.[1]?.trim() || '未提供'
}

function findWorkflowTaskInfo(content: string): string {
  const match = content.match(/## 任务信息\s+```text\s+([\s\S]*?)\s+```/m)
  return match?.[1]?.trim() || '未提供'
}

function systemLineParts(content: string): SystemLinePart[] {
  const artifactPattern = /([A-Z]+[0-9]+-[^，。\s]+(?:\s\([^)]*\))?\.md)/g
  const parts: SystemLinePart[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = artifactPattern.exec(content)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ kind: 'text', text: content.slice(lastIndex, match.index) })
    }
    parts.push({ kind: 'artifact', text: match[1] })
    lastIndex = match.index + match[1].length
  }
  if (lastIndex < content.length) {
    parts.push({ kind: 'text', text: content.slice(lastIndex) })
  }
  return parts.length > 0 ? parts : [{ kind: 'text', text: content }]
}
</script>

<template>
  <div class="message-list" aria-live="polite">
    <div v-if="!hasContent" class="message-list__empty">
      <strong>会话已就绪</strong>
      <span>输入问题，或点击上方场景开始和 Agent Runtime 对话。</span>
    </div>

    <template v-else>
      <template
        v-for="item in timelineItems"
        :key="item.id"
      >
        <article
          v-if="item.type === 'message' && item.message.role === 'USER'"
          class="user-turn"
        >
          <div
            class="user-bubble"
            :class="{ 'user-bubble--workflow': isWorkflowInputMessage(item.message) }"
          >
            <template v-if="isWorkflowInputMessage(item.message)">
              <div class="workflow-input-card">
                <div class="workflow-input-card__eyebrow">用户输入</div>
                <div class="workflow-input-card__title">
                  请基于 {{ workflowInputSummary(item.message).code }} · {{ workflowInputSummary(item.message).title }}，
                  使用 Skill {{ workflowInputSummary(item.message).skill }} 执行 {{ workflowInputSummary(item.message).node }}
                </div>
                <p class="workflow-input-card__desc">
                  任务信息：{{ workflowInputSummary(item.message).description }}
                </p>
                <dl class="workflow-input-card__meta">
                  <div>
                    <dt>编号</dt>
                    <dd>{{ workflowInputSummary(item.message).code }}</dd>
                  </div>
                  <div>
                    <dt>标题</dt>
                    <dd>{{ workflowInputSummary(item.message).title }}</dd>
                  </div>
                  <div>
                    <dt>Skill</dt>
                    <dd>{{ workflowInputSummary(item.message).skill }}</dd>
                  </div>
                  <div>
                    <dt>状态</dt>
                    <dd>{{ workflowInputSummary(item.message).status }}</dd>
                  </div>
                  <div>
                    <dt>优先级</dt>
                    <dd>{{ workflowInputSummary(item.message).priority }}</dd>
                  </div>
                </dl>
                <details class="workflow-input-card__details">
                  <summary>查看完整输入上下文</summary>
                  <MarkdownContent
                    :content="item.message.content"
                    :render-mermaid="false"
                  />
                </details>
              </div>
            </template>
            <MarkdownContent
              v-else-if="shouldRenderMarkdown(item.message)"
              :content="item.message.content"
            />
            <template v-else>{{ item.message.content }}</template>
          </div>
          <div class="message-time">{{ formatTime(item.message.createdAt) }}</div>
        </article>

        <article
          v-else-if="item.type === 'message' && item.message.role === 'SYSTEM'"
          class="system-line"
        >
          <span class="system-dot">i</span>
          <MarkdownContent
            v-if="shouldRenderMarkdown(item.message)"
            class="system-line__content"
            :content="item.message.content"
          />
          <span v-else class="system-line__content">
            <template
              v-for="(part, index) in systemLineParts(item.message.content ?? '')"
              :key="`${item.message.id}-${index}`"
            >
              <button
                v-if="part.kind === 'artifact'"
                type="button"
                class="system-line__artifact"
                @click="emit('open-artifact', part.text)"
              >
                {{ part.text }}
              </button>
              <span v-else>{{ part.text }}</span>
            </template>
          </span>
        </article>

        <article
          v-else-if="item.type === 'message'"
          class="assistant-turn"
          :class="{ 'assistant-turn--tool': item.message.role === 'TOOL' }"
        >
          <div class="assistant-rail">
            <div class="assistant-avatar">{{ roleGlyph(item.message) }}</div>
          </div>
          <section class="assistant-card">
            <header class="assistant-card__head">
              <div class="assistant-card__title">
                <span class="assistant-card__glyph">{{ roleGlyph(item.message) }}</span>
                <span>{{ roleTitle(item.message) }}</span>
              </div>
              <span class="assistant-card__pill">{{ statusLabel(item.message) }}</span>
            </header>
            <MarkdownContent
              v-if="shouldRenderMarkdown(item.message)"
              class="assistant-card__markdown"
              :content="item.message.content"
            />
            <pre v-else class="assistant-card__content">{{ item.message.content }}</pre>
            <div class="assistant-card__time">{{ formatTime(item.message.createdAt) }}</div>
          </section>
        </article>

        <article
          v-else-if="item.type === 'runtime-event' && isUserInteractionInputEvent(item.event)"
          class="user-turn"
        >
          <div class="user-bubble user-bubble--interaction">
            <div class="interaction-input-card">
              <div class="interaction-input-card__eyebrow">用户输入</div>
              <div class="interaction-input-card__title">
                {{ interactionInputTitle(item.payload) }}
              </div>
              <p class="interaction-input-card__desc">
                {{ interactionInputContent(item.payload) }}
              </p>
            </div>
          </div>
          <div class="message-time">{{ formatTime(item.event.createdAt) }}</div>
        </article>

        <article
          v-else-if="item.type === 'runtime-event' || item.type === 'tool-call'"
          class="assistant-turn"
          :class="runtimeEventClass(timelineRuntimeEvent(item), item.payload)"
        >
          <div class="assistant-rail">
            <div class="assistant-avatar">{{ runtimeEventGlyph(timelineRuntimeEvent(item), item.payload) }}</div>
          </div>
          <section class="assistant-card">
            <header class="assistant-card__head">
              <div class="assistant-card__title">
                <span class="assistant-card__glyph">{{ runtimeEventGlyph(timelineRuntimeEvent(item), item.payload) }}</span>
                <span>{{ runtimeEventTitle(timelineRuntimeEvent(item), item.payload) }}</span>
              </div>
              <span class="assistant-card__pill">
                {{ runtimeEventPill(timelineRuntimeEvent(item), item.payload) }}
              </span>
            </header>

            <pre class="assistant-card__content">{{ runtimeEventSummary(timelineRuntimeEvent(item), item.payload) }}</pre>
            <pre
              v-if="runtimeEventOutput(item.payload)"
              class="runtime-event__output"
            >{{ runtimeEventOutput(item.payload) }}</pre>
            <div class="assistant-card__time">{{ formatTime(item.createdAt) }}</div>
          </section>
        </article>

        <article
          v-else-if="item.type === 'streaming'"
          class="assistant-turn assistant-turn--live"
        >
          <div class="assistant-rail">
            <div class="assistant-avatar">A</div>
          </div>
          <section class="assistant-card">
            <header class="assistant-card__head">
              <div class="assistant-card__title">
                <span class="assistant-card__glyph">A</span>
                <span>助手</span>
              </div>
              <span class="assistant-card__pill assistant-card__pill--running">回复中</span>
            </header>
            <div class="assistant-card__live-content">
              <MarkdownContent
                class="assistant-card__markdown"
                :content="item.content"
              />
              <span class="stream-cursor">▍</span>
            </div>
            <div class="assistant-card__time">{{ formatTime(item.createdAt) }}</div>
          </section>
        </article>
      </template>
    </template>
  </div>
</template>

<style scoped>
.message-list {
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 18px 0 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-list__empty {
  min-height: 280px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  text-align: center;
}

.message-list__empty strong {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 900;
}

.user-turn {
  display: grid;
  justify-items: end;
  gap: 5px;
}

.user-bubble {
  max-width: min(620px, 72%);
  padding: 10px 14px;
  border-radius: 18px 18px 4px 18px;
  background: var(--accent-blue);
  color: var(--on-brand);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.58;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.user-bubble--workflow {
  width: min(620px, 72%);
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent-blue) 72%, var(--border-color));
  border-radius: 16px 16px 4px 16px;
  background: var(--accent-blue);
  color: var(--on-brand);
  font-weight: 650;
  white-space: normal;
}

.user-bubble--interaction {
  width: min(520px, 72%);
  padding: 12px 14px;
  white-space: pre-wrap;
}

.workflow-input-card {
  display: grid;
  gap: 8px;
}

.interaction-input-card {
  display: grid;
  gap: 6px;
}

.interaction-input-card__eyebrow {
  color: color-mix(in srgb, var(--on-brand) 78%, transparent);
  font-size: 12px;
  font-weight: 850;
}

.interaction-input-card__title {
  color: var(--on-brand);
  font-size: 14px;
  font-weight: 850;
}

.interaction-input-card__desc {
  margin: 0;
  color: color-mix(in srgb, var(--on-brand) 90%, transparent);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.workflow-input-card__eyebrow {
  color: color-mix(in srgb, var(--on-brand) 78%, transparent);
  font-size: 12px;
  font-weight: 850;
}

.workflow-input-card__title {
  color: var(--on-brand);
  font-size: 14px;
  font-weight: 850;
}

.workflow-input-card__desc {
  display: -webkit-box;
  margin: 0;
  color: color-mix(in srgb, var(--on-brand) 86%, transparent);
  font-size: 12px;
  line-height: 1.55;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.workflow-input-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin: 0;
  font-size: 12px;
}

.workflow-input-card__meta div {
  min-width: 0;
  display: flex;
  gap: 6px;
}

.workflow-input-card__meta dt {
  flex: 0 0 auto;
  color: color-mix(in srgb, var(--on-brand) 70%, transparent);
  font-weight: 750;
}

.workflow-input-card__meta dd {
  min-width: 0;
  margin: 0;
  color: var(--on-brand);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-input-card__details {
  border-top: 1px solid color-mix(in srgb, var(--on-brand) 24%, transparent);
  padding-top: 8px;
}

.workflow-input-card__details summary {
  cursor: pointer;
  color: var(--on-brand);
  font-size: 12px;
  font-weight: 800;
}

.workflow-input-card__details .markdown-content {
  max-height: 260px;
  margin-top: 8px;
  overflow: auto;
  padding: 8px;
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
}

.message-time,
.assistant-card__time {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 650;
}

.system-line,
.turn-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 760px;
  margin: 0 auto;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-overlay);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.turn-note {
  max-width: none;
  margin: 12px 0 0;
}

.system-line__content,
.turn-note__content {
  min-width: 0;
  flex: 1;
}

.system-line__artifact {
  display: inline;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-blue);
  font: inherit;
  font-weight: 850;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.system-line__artifact:hover {
  color: var(--brand-primary);
}

.system-dot,
.assistant-card__glyph,
.assistant-avatar {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.system-dot {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 6px;
  background: var(--info-soft);
  color: var(--info);
  font-size: 11px;
  font-weight: 900;
}

.assistant-turn {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.assistant-rail {
  position: relative;
  min-height: 100%;
}

.assistant-rail::before {
  content: '';
  position: absolute;
  top: 36px;
  bottom: -12px;
  left: 14px;
  width: 1px;
  background: linear-gradient(180deg, var(--success-soft), transparent);
}

.assistant-avatar {
  position: sticky;
  top: 10px;
  z-index: 1;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--success);
  color: var(--on-success);
  font-size: 12px;
  font-weight: 900;
  box-shadow: 0 0 0 4px var(--bg-card);
}

.assistant-card {
  min-width: 0;
  padding: 0 0 10px;
}

.assistant-card__head {
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.assistant-card__title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
}

.assistant-card__title span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-card__glyph {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 7px;
  background: var(--success-soft);
  color: var(--success);
  font-size: 11px;
  font-weight: 900;
}

.assistant-card__pill {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 999px;
  color: var(--text-muted);
  background: var(--surface-card);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.assistant-card__pill--running {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
  background: var(--brand-soft);
}

.assistant-card__pill--waiting_confirmation {
  border-color: color-mix(in srgb, var(--warning) 55%, var(--border-color));
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-card));
}

.assistant-card__pill--failed {
  border-color: var(--error);
  color: var(--error);
  background: var(--error-soft);
}

.assistant-card__content {
  margin: 0;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.72;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.assistant-card__markdown,
.assistant-card__live-content {
  min-width: 0;
}

.assistant-turn--tool .assistant-card__content {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-hover);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.runtime-event--tool .assistant-avatar,
.runtime-event--tool .assistant-card__glyph {
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.runtime-event--interaction .assistant-avatar,
.runtime-event--interaction .assistant-card__glyph {
  background: color-mix(in srgb, var(--warning) 16%, var(--bg-card));
  color: var(--warning);
}

.runtime-event--error .assistant-avatar,
.runtime-event--error .assistant-card__glyph {
  background: var(--error-soft);
  color: var(--error);
}

.runtime-event__output {
  max-height: 320px;
  margin: 8px 0 0;
  padding: 10px 12px;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-hover);
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.assistant-card__time {
  margin-top: 6px;
}

.turn-section {
  margin: 8px 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-overlay);
  overflow: hidden;
}

.turn-section > summary {
  min-height: 34px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.reasoning-line {
  padding: 8px 10px 10px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  border-top: 1px solid var(--border-color);
}

.tool-list {
  display: grid;
  gap: 8px;
  padding: 8px;
  border-top: 1px solid var(--border-color);
}

.tool-block {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  overflow: hidden;
}

.tool-block > summary {
  min-height: 34px;
  display: grid;
  grid-template-columns: minmax(120px, auto) minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.tool-block__summary {
  min-width: 0;
  color: var(--text-secondary);
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-block__status {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  font-size: 11px;
  font-weight: 850;
}

.tool-block__status--running {
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.tool-block__status--completed {
  background: var(--success-soft);
  color: var(--success);
}

.tool-block__status--failed {
  background: var(--error-soft);
  color: var(--error);
}

.tool-block__output {
  max-height: 300px;
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  border-top: 1px solid var(--border-color);
  background: var(--bg-primary);
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tool-block__empty {
  padding: 8px 10px;
  border-top: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 12px;
}

.turn-error {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.turn-error {
  border: 1px solid var(--error);
  background: var(--error-soft);
  color: var(--error);
}

.turn-error p {
  margin: 4px 0 0;
}

.turn-note--interaction {
  margin-top: 12px;
  border: 1px solid color-mix(in srgb, var(--warning) 42%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-card));
}

.stream-cursor {
  color: var(--accent-blue);
  animation: stream-blink 0.8s step-end infinite;
}

@keyframes stream-blink {
  50% {
    opacity: 0;
  }
}

@media (max-width: 760px) {
  .message-list {
    padding-inline: 2px;
  }

  .user-bubble {
    max-width: 86%;
  }

  .assistant-turn {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 10px;
  }

  .tool-block > summary {
    grid-template-columns: 1fr;
    padding: 8px 10px;
  }
}
</style>
