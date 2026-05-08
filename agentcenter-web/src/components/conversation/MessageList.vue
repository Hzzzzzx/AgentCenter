<script setup lang="ts">
import { computed } from 'vue'
import type { AgentMessageDto, RuntimeEventDto } from '../../api/types'
import MarkdownContent from './MarkdownContent.vue'
import ProcessTrace from './ProcessTrace.vue'

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

const persistedMessages = computed(() =>
  props.messages
    .map((message, index) => ({ message, index }))
    .filter(({ message }) => Boolean(message.content?.trim()))
    .sort((a, b) => compareMessages(a.message, b.message, a.index, b.index))
    .map(({ message }) => message)
)

const liveMessage = computed<AgentMessageDto | null>(() => {
  if (!props.streamingText.trim()) return null
  return {
    id: 'streaming-assistant',
    sessionId: activeSessionId.value,
    role: 'ASSISTANT',
    content: props.streamingText,
    contentFormat: 'MARKDOWN',
    status: 'STREAMING',
    seqNo: (persistedMessages.value.at(-1)?.seqNo ?? 0) + 1,
    createdAt: new Date().toISOString(),
    workflowNodeInstanceId: props.activeNodeId,
  }
})

const activeSessionId = computed(() =>
  props.activeSessionId
  ?? persistedMessages.value.at(-1)?.sessionId
  ?? ''
)

const hasAssistantForActiveNode = computed(() =>
  Boolean(props.activeNodeId)
  && persistedMessages.value.some((message) =>
    message.role === 'ASSISTANT' && message.workflowNodeInstanceId === props.activeNodeId
  )
)

const shouldShowActiveProcessTurn = computed(() =>
  Boolean(activeSessionId.value)
  && (
    Boolean(props.activeNodeId)
      ? !hasAssistantForActiveNode.value && ['RUNNING', 'WAITING_CONFIRMATION', 'FAILED'].includes(props.activeNodeState ?? '')
      : props.running
  )
)

const liveTurnVisible = computed(() =>
  Boolean(liveMessage.value) || shouldShowActiveProcessTurn.value
)

const hasContent = computed(() =>
  persistedMessages.value.length > 0 || liveTurnVisible.value
)

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
  if (message.status === 'STREAMING') return '助手正在回复'
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
  if (message.status === 'STREAMING') return '回复中'
  if (message.status === 'FAILED') return '回复失败'
  if (message.role === 'ASSISTANT') return '回复完成'
  if (message.role === 'TOOL') return '工具输出'
  return '已记录'
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

function traceWindowStart(message: AgentMessageDto): string | null {
  const index = persistedMessages.value.findIndex((item) => item.id === message.id)
  if (index <= 0) return null
  return persistedMessages.value[index - 1]?.createdAt ?? null
}

function traceWindowEnd(message: AgentMessageDto): string | null {
  const index = persistedMessages.value.findIndex((item) => item.id === message.id)
  if (index < 0) return null
  const nextTurn = persistedMessages.value
    .slice(index + 1)
    .find((item) => item.role === 'USER' || item.role === 'ASSISTANT')
  return nextTurn?.createdAt ?? null
}

function shouldExpandTrace(message: AgentMessageDto): boolean {
  return Boolean(props.activeNodeId && message.workflowNodeInstanceId === props.activeNodeId)
    && ['RUNNING', 'WAITING_CONFIRMATION', 'FAILED'].includes(props.activeNodeState ?? '')
}

function activeProcessTitle(): string {
  switch (props.activeNodeState) {
    case 'WAITING_CONFIRMATION': return '助手等待用户确认'
    case 'FAILED': return '助手执行遇到异常'
    default: return '助手正在处理'
  }
}

function activeProcessPill(): string {
  switch (props.activeNodeState) {
    case 'WAITING_CONFIRMATION': return '等待确认'
    case 'FAILED': return '执行失败'
    default: return '运行中'
  }
}

function activeProcessEmptyText(): string {
  switch (props.activeNodeState) {
    case 'WAITING_CONFIRMATION': return '正在等待用户处理确认项...'
    case 'FAILED': return '节点执行失败，正在等待用户选择重试、跳过或停止...'
    default: return '正在准备运行上下文...'
  }
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
        v-for="msg in persistedMessages"
        :key="msg.id"
      >
        <article
          v-if="msg.role === 'USER'"
          class="user-turn"
        >
          <div class="user-bubble">{{ msg.content }}</div>
          <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
        </article>

        <article
          v-else-if="msg.role === 'SYSTEM'"
          class="system-line"
        >
          <span class="system-dot">i</span>
          <MarkdownContent
            v-if="shouldRenderMarkdown(msg)"
            class="system-line__content"
            :content="msg.content"
          />
          <span v-else class="system-line__content">
            <template
              v-for="(part, index) in systemLineParts(msg.content ?? '')"
              :key="`${msg.id}-${index}`"
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
          v-else
          class="assistant-turn"
          :class="{ 'assistant-turn--tool': msg.role === 'TOOL' }"
        >
          <div class="assistant-rail">
            <div class="assistant-avatar">{{ roleGlyph(msg) }}</div>
          </div>
          <section class="assistant-card">
            <header class="assistant-card__head">
              <div class="assistant-card__title">
                <span class="assistant-card__glyph">{{ roleGlyph(msg) }}</span>
                <span>{{ roleTitle(msg) }}</span>
              </div>
              <span class="assistant-card__pill">{{ statusLabel(msg) }}</span>
            </header>
            <ProcessTrace
              v-if="msg.role === 'ASSISTANT'"
              :events="runtimeEvents"
              :node-id="msg.workflowNodeInstanceId"
              :session-id="msg.sessionId"
              :from="traceWindowStart(msg)"
              :to="traceWindowEnd(msg)"
              :default-expanded="shouldExpandTrace(msg)"
              :include-session-window="!msg.workflowNodeInstanceId"
            />
            <MarkdownContent
              v-if="shouldRenderMarkdown(msg)"
              class="assistant-card__markdown"
              :content="msg.content"
            />
            <pre v-else class="assistant-card__content">{{ msg.content }}</pre>
            <div class="assistant-card__time">{{ formatTime(msg.createdAt) }}</div>
          </section>
        </article>
      </template>

      <article
        v-if="liveTurnVisible"
        class="assistant-turn assistant-turn--live"
      >
        <div class="assistant-rail">
          <div class="assistant-avatar assistant-avatar--live">A</div>
        </div>
        <section class="assistant-card assistant-card--live">
          <header class="assistant-card__head">
            <div class="assistant-card__title">
              <span class="assistant-card__glyph">A</span>
              <span>{{ activeProcessTitle() }}</span>
            </div>
            <span class="assistant-card__pill assistant-card__pill--live">{{ liveMessage ? '流式中' : activeProcessPill() }}</span>
          </header>
          <ProcessTrace
            :events="runtimeEvents"
            :node-id="activeNodeId"
            :session-id="activeSessionId"
            :from="persistedMessages.at(-1)?.createdAt"
            :default-expanded="true"
            :show-when-empty="true"
            :empty-text="activeProcessEmptyText()"
            include-session-window
          />
          <div v-if="liveMessage" class="assistant-card__live-content">
            <MarkdownContent
              class="assistant-card__markdown"
              :content="liveMessage.content"
            />
            <span class="stream-cursor">▍</span>
          </div>
        </section>
      </article>

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

.system-line {
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

.system-line__content {
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

.assistant-avatar--live {
  box-shadow: 0 0 0 4px var(--bg-card), 0 0 0 8px var(--success-soft);
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

.assistant-card__pill--live {
  border-color: var(--success);
  color: var(--success);
  background: var(--success-soft);
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

.assistant-card__markdown {
  min-width: 0;
}

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
}
</style>
