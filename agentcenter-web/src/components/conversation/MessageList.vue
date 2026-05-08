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
}

type ReasoningBlock = {
  id: string
  summary: string
  createdAt: string | null
}

type ToolBlock = {
  callId: string
  rawName: string
  displayName: string
  summary: string
  status: 'running' | 'completed' | 'failed'
  output?: string
  outputKind: 'skill_context' | 'agent_result' | 'raw_tool_output'
  startedAt: string | null
  completedAt?: string | null
}

type ConfirmationGate = {
  id: string
  status: 'waiting' | 'completed'
  text: string
  createdAt: string | null
}

type AssistantTurn = {
  id: string
  sessionId: string | null
  workflowNodeInstanceId: string | null
  status: 'running' | 'waiting_confirmation' | 'failed' | 'completed'
  createdAt: string | null
  reasoning: ReasoningBlock[]
  tools: ToolBlock[]
  assistantMessage: AgentMessageDto | null
  streamingText: string
  confirmations: ConfirmationGate[]
  errors: string[]
  systemNotes: AgentMessageDto[]
}

type TimelineItem =
  | { type: 'message'; id: string; createdAt: string | null; order: number; message: AgentMessageDto }
  | { type: 'assistant-turn'; id: string; createdAt: string | null; order: number; turn: AssistantTurn }

type RawTimelineInput =
  | { type: 'message'; message: AgentMessageDto; createdAt: string | null; order: number }
  | { type: 'event'; event: RuntimeEventDto; createdAt: string | null; order: number }

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
  const rawItems: RawTimelineInput[] = [
    ...persistedMessages.value.map((message, index) => ({
      type: 'message' as const,
      message,
      createdAt: message.createdAt,
      order: index * 10 + messagePriority(message),
    })),
    ...props.runtimeEvents
      .map((event, index) => ({
        type: 'event' as const,
        event,
        createdAt: event.createdAt,
        order: 100000 + ((event.seqNo ?? index) * 10) + eventPriority(event),
      }))
      .filter(({ event }) => belongsToActiveSession(event)),
  ].sort((a, b) => {
    const timeDiff = timestamp(a.createdAt) - timestamp(b.createdAt)
    if (timeDiff !== 0) return timeDiff
    return a.order - b.order
  })

  const items: TimelineItem[] = []
  let currentTurn: AssistantTurn | null = null

  const ensureTurn = (seed: { sessionId?: string | null; workflowNodeInstanceId?: string | null; createdAt?: string | null }): AssistantTurn => {
    if (!currentTurn || shouldStartNewTurn(currentTurn, seed)) {
      currentTurn = createTurn({ ...seed, idSuffix: items.length })
      items.push({
        type: 'assistant-turn',
        id: currentTurn.id,
        createdAt: currentTurn.createdAt,
        order: items.length,
        turn: currentTurn,
      })
    }
    return currentTurn
  }

  rawItems.forEach((item) => {
    if (item.type === 'message') {
      const message = item.message
      if (message.role === 'ASSISTANT' || message.role === 'TOOL') {
        const turn = ensureTurn({
          sessionId: message.sessionId,
          workflowNodeInstanceId: message.workflowNodeInstanceId,
          createdAt: message.createdAt,
        })
        if (!turn.assistantMessage || message.role === 'ASSISTANT') {
          turn.assistantMessage = message
          turn.status = message.status === 'FAILED' ? 'failed' : inferCompletedStatus(turn)
        }
        return
      }

      if (message.role === 'SYSTEM' && isGeneratedArtifactNote(message.content)) {
        const turn = ensureTurn({
          sessionId: message.sessionId,
          workflowNodeInstanceId: message.workflowNodeInstanceId,
          createdAt: message.createdAt,
        })
        turn.systemNotes.push(message)
        return
      }

      items.push({
        type: 'message',
        id: message.id,
        createdAt: message.createdAt,
        order: items.length,
        message,
      })
      return
    }

    if (!isTurnEvent(item.event)) return
    const event = item.event
    const turn = ensureTurn({
      sessionId: event.sessionId,
      workflowNodeInstanceId: event.workflowNodeInstanceId,
      createdAt: event.createdAt,
    })
    applyEventToTurn(turn, event)
  })

  const streamingText = props.streamingText.trim()
  if (streamingText) {
    const turn = ensureTurn({
      sessionId: activeSessionId.value,
      workflowNodeInstanceId: props.activeNodeId,
      createdAt: new Date().toISOString(),
    })
    turn.streamingText = streamingText
    turn.status = 'running'
  }

  return items.filter((item) =>
    item.type === 'message' || hasVisibleAssistantTurn(item.turn)
  )
})

const hasContent = computed(() =>
  timelineItems.value.length > 0
)

function createTurn(seed: { sessionId?: string | null; workflowNodeInstanceId?: string | null; createdAt?: string | null; idSuffix?: number }): AssistantTurn {
  const suffix = `${seed.workflowNodeInstanceId || seed.sessionId || 'general'}-${timestamp(seed.createdAt)}-${seed.idSuffix ?? 0}`
  return {
    id: `assistant-turn-${suffix}`,
    sessionId: seed.sessionId ?? activeSessionId.value ?? null,
    workflowNodeInstanceId: seed.workflowNodeInstanceId ?? null,
    status: 'running',
    createdAt: seed.createdAt ?? null,
    reasoning: [],
    tools: [],
    assistantMessage: null,
    streamingText: '',
    confirmations: [],
    errors: [],
    systemNotes: [],
  }
}

function shouldStartNewTurn(turn: AssistantTurn, seed: { sessionId?: string | null; workflowNodeInstanceId?: string | null }): boolean {
  if (seed.workflowNodeInstanceId && turn.workflowNodeInstanceId && seed.workflowNodeInstanceId !== turn.workflowNodeInstanceId) {
    return true
  }
  return Boolean(turn.assistantMessage && turn.confirmations.length > 0 && seed.workflowNodeInstanceId !== turn.workflowNodeInstanceId)
}

function applyEventToTurn(turn: AssistantTurn, event: RuntimeEventDto): void {
  const payload = parsePayload(event.payloadJson)
  switch (event.eventType) {
    case 'PROCESS_TRACE':
      applyProcessTrace(turn, event, payload)
      return
    case 'SKILL_STARTED':
      upsertTool(turn, event, payload, 'running')
      turn.status = 'running'
      return
    case 'SKILL_COMPLETED':
      upsertTool(turn, event, payload, completedStatus(payload))
      turn.status = inferCompletedStatus(turn)
      return
    case 'PERMISSION_REQUIRED':
      turn.confirmations.push({
        id: event.id,
        status: 'waiting',
        text: payload.title || payload.label || '需要用户授权后继续',
        createdAt: event.createdAt,
      })
      turn.status = 'waiting_confirmation'
      return
    case 'CONFIRMATION_CREATED':
      turn.confirmations.push({
        id: payload.confirmationId || event.id,
        status: 'waiting',
        text: '确认后继续下一步',
        createdAt: event.createdAt,
      })
      turn.status = 'waiting_confirmation'
      return
    case 'CONFIRMATION_RESOLVED':
      resolveConfirmationGate(turn, payload)
      turn.status = inferCompletedStatus(turn)
      return
    case 'ERROR':
      turn.errors.push(payload.reason || payload.errorMessage || payload.label || 'Agent 运行过程中出现异常')
      turn.status = 'failed'
      return
    default:
      return
  }
}

function resolveConfirmationGate(turn: AssistantTurn, payload: RuntimePayload): void {
  const confirmationId = payload.confirmationId
  if (!confirmationId) {
    turn.confirmations.forEach((gate) => {
      gate.status = 'completed'
    })
    return
  }

  const gate = turn.confirmations.find((item) => item.id === confirmationId)
  if (gate) {
    gate.status = 'completed'
  }
}

function applyProcessTrace(turn: AssistantTurn, event: RuntimeEventDto, payload: RuntimePayload): void {
  switch (payload.kind) {
    case 'reasoning_summary': {
      const summary = payload.summary || payload.title || ''
      if (summary.trim()) {
        turn.reasoning.push({ id: event.id, summary, createdAt: event.createdAt })
      }
      return
    }
    case 'confirmation':
      turn.confirmations.push({
        id: payload.confirmationId || event.id,
        status: 'waiting',
        text: payload.summary || payload.title || '确认后继续下一步',
        createdAt: event.createdAt,
      })
      turn.status = 'waiting_confirmation'
      return
    case 'error':
      turn.errors.push(payload.summary || payload.reason || 'Agent 运行过程中出现异常')
      turn.status = 'failed'
      return
    default:
      return
  }
}

function upsertTool(turn: AssistantTurn, event: RuntimeEventDto, payload: RuntimePayload, status: ToolBlock['status']): void {
  const parsedSkill = parseSkillNameFromOutput(payload.output || '')
  const rawName = payload.skillName || payload.label || payload.toolName || 'tool'
  const callId = payload.toolCallId || `${event.eventSource}:${event.workflowNodeInstanceId || event.sessionId || 'session'}:${rawName}`
  const outputKind = parsedSkill ? 'skill_context' : inferToolOutputKind(rawName, payload.output || '')
  const displayName = displayToolName(rawName, parsedSkill, outputKind)
  const existing = turn.tools.find((tool) => tool.callId === callId)
  const output = normalizeToolOutput(payload.output || '')
  const summary = toolSummary(displayName, status, outputKind)

  if (existing) {
    existing.rawName = rawName
    existing.displayName = displayName
    existing.status = status
    existing.summary = summary
    existing.outputKind = outputKind
    if (output) existing.output = output
    if (status !== 'running') existing.completedAt = event.createdAt
    return
  }

  turn.tools.push({
    callId,
    rawName,
    displayName,
    summary,
    status,
    output: output || undefined,
    outputKind,
    startedAt: event.createdAt,
    completedAt: status === 'running' ? null : event.createdAt,
  })
}

function displayToolName(rawName: string, parsedSkill: string | null, outputKind: ToolBlock['outputKind']): string {
  if (parsedSkill) return `加载 Skill: ${parsedSkill}`
  if (rawName === 'skill') return '加载 Skill'
  if (outputKind === 'agent_result') return `执行 ${rawName}`
  return rawName
}

function toolSummary(displayName: string, status: ToolBlock['status'], outputKind: ToolBlock['outputKind']): string {
  if (outputKind === 'skill_context') return `${displayName} 的执行说明`
  if (status === 'running') return `${displayName} 运行中`
  if (status === 'failed') return `${displayName} 失败`
  return `${displayName} 完成`
}

function inferToolOutputKind(rawName: string, output: string): ToolBlock['outputKind'] {
  if (/^#\s|^##\s|```|^\|.+\|/m.test(output.trim())) return 'agent_result'
  if (rawName !== 'skill') return 'agent_result'
  return 'raw_tool_output'
}

function completedStatus(payload: RuntimePayload): ToolBlock['status'] {
  return payload.isError === true || payload.success === false ? 'failed' : 'completed'
}

function inferCompletedStatus(turn: AssistantTurn): AssistantTurn['status'] {
  if (turn.errors.length > 0 || turn.tools.some((tool) => tool.status === 'failed')) return 'failed'
  if (turn.confirmations.some((gate) => gate.status === 'waiting')) return 'waiting_confirmation'
  if (turn.tools.some((tool) => tool.status === 'running') || turn.streamingText.trim()) return 'running'
  return turn.assistantMessage ? 'completed' : turn.status
}

function hasVisibleAssistantTurn(turn: AssistantTurn): boolean {
  return Boolean(
    turn.reasoning.length
    || turn.tools.length
    || turn.assistantMessage?.content?.trim()
    || turn.streamingText.trim()
    || turn.confirmations.length
    || turn.errors.length
    || turn.systemNotes.length
  )
}

function isTurnEvent(event: RuntimeEventDto): boolean {
  if (!['PROCESS_TRACE', 'SKILL_STARTED', 'SKILL_COMPLETED', 'PERMISSION_REQUIRED', 'ERROR', 'CONFIRMATION_CREATED', 'CONFIRMATION_RESOLVED'].includes(event.eventType)) {
    return false
  }
  const payload = parsePayload(event.payloadJson)
  if (event.eventType === 'PROCESS_TRACE' && (payload.kind === 'node_status' || payload.kind === 'tool_call')) {
    return false
  }
  return true
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

function parseSkillNameFromOutput(output: string): string | null {
  const match = output.match(/^##\s+Skill:\s*([^\n]+)$/m)
  return match?.[1]?.trim() || null
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
    case 'PROCESS_TRACE': return 4
    case 'ASSISTANT_DELTA': return 5
    case 'CONFIRMATION_CREATED':
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

function turnTitle(turn: AssistantTurn): string {
  if (turn.status === 'running') return '助手正在处理'
  if (turn.status === 'waiting_confirmation') return '助手等待确认'
  if (turn.status === 'failed') return '助手执行失败'
  return '助手'
}

function turnPill(turn: AssistantTurn): string {
  switch (turn.status) {
    case 'running': return '运行中'
    case 'waiting_confirmation': return '待确认'
    case 'failed': return '失败'
    default: return '完成'
  }
}

function turnPillClass(turn: AssistantTurn): string {
  return `assistant-card__pill--${turn.status}`
}

function toolStatusLabel(status: ToolBlock['status']): string {
  switch (status) {
    case 'running': return '进行中'
    case 'failed': return '失败'
    default: return '完成'
  }
}

function shouldRenderMarkdown(message: AgentMessageDto): boolean {
  return message.role === 'ASSISTANT' || message.contentFormat === 'MARKDOWN'
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
          <div class="user-bubble">{{ item.message.content }}</div>
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
          v-else
          class="assistant-turn"
        >
          <div class="assistant-rail">
            <div class="assistant-avatar">A</div>
          </div>
          <section class="assistant-card">
            <header class="assistant-card__head">
              <div class="assistant-card__title">
                <span class="assistant-card__glyph">A</span>
                <span>{{ turnTitle(item.turn) }}</span>
              </div>
              <span class="assistant-card__pill" :class="turnPillClass(item.turn)">
                {{ turnPill(item.turn) }}
              </span>
            </header>

            <details
              v-if="item.turn.reasoning.length"
              class="turn-section"
            >
              <summary>思考 · {{ item.turn.reasoning.length }} 条</summary>
              <div
                v-for="reasoning in item.turn.reasoning"
                :key="reasoning.id"
                class="reasoning-line"
              >
                {{ reasoning.summary }}
              </div>
            </details>

            <details
              v-if="item.turn.tools.length"
              class="turn-section"
              :open="item.turn.status === 'running'"
            >
              <summary>工具 · {{ item.turn.tools.length }} 个</summary>
              <div class="tool-list">
                <details
                  v-for="tool in item.turn.tools"
                  :key="tool.callId"
                  class="tool-block"
                  :open="tool.status === 'running' && tool.outputKind !== 'skill_context'"
                >
                  <summary>
                    <span>{{ tool.displayName }}</span>
                    <span class="tool-block__summary">{{ tool.summary }}</span>
                    <span class="tool-block__status" :class="`tool-block__status--${tool.status}`">
                      {{ toolStatusLabel(tool.status) }}
                    </span>
                  </summary>
                  <pre v-if="tool.output" class="tool-block__output">{{ tool.output }}</pre>
                  <div v-else class="tool-block__empty">暂无工具输出</div>
                </details>
              </div>
            </details>

            <div
              v-if="item.turn.errors.length"
              class="turn-error"
            >
              <strong>执行异常</strong>
              <p
                v-for="error in item.turn.errors"
                :key="error"
              >
                {{ error }}
              </p>
            </div>

            <MarkdownContent
              v-if="item.turn.assistantMessage && shouldRenderMarkdown(item.turn.assistantMessage)"
              class="assistant-card__markdown"
              :content="item.turn.assistantMessage.content"
            />
            <pre
              v-else-if="item.turn.assistantMessage"
              class="assistant-card__content"
            >{{ item.turn.assistantMessage.content }}</pre>

            <div
              v-if="item.turn.streamingText"
              class="assistant-card__live-content"
            >
              <MarkdownContent
                class="assistant-card__markdown"
                :content="item.turn.streamingText"
              />
              <span class="stream-cursor">▍</span>
            </div>

            <div
              v-for="note in item.turn.systemNotes"
              :key="note.id"
              class="turn-note"
            >
              <span class="system-dot">i</span>
              <span class="turn-note__content">
                <template
                  v-for="(part, index) in systemLineParts(note.content ?? '')"
                  :key="`${note.id}-${index}`"
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
            </div>

            <div
              v-if="item.turn.confirmations.some((gate) => gate.status === 'waiting')"
              class="confirmation-gate"
            >
              <strong>需要确认</strong>
              <span>{{ item.turn.confirmations.filter((gate) => gate.status === 'waiting').length }} 个确认项等待处理，确认后继续下一步。</span>
            </div>

            <div class="assistant-card__time">
              {{ formatTime(item.turn.assistantMessage?.createdAt ?? item.turn.createdAt) }}
            </div>
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

.turn-error,
.confirmation-gate {
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

.confirmation-gate {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid color-mix(in srgb, var(--warning) 42%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-card));
  color: var(--text-secondary);
}

.confirmation-gate strong {
  color: var(--warning);
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
