<script setup lang="ts">
import { computed } from 'vue'
import type { AgentMessageDto, RuntimeEventDto } from '../../api/types'
import MarkdownContent from './MarkdownContent.vue'

type RuntimePayload = Record<string, unknown>
type ActivityStatus = 'running' | 'done' | 'waiting' | 'error'

interface ActivityItem {
  id: string
  title: string
  detail: string
  status: ActivityStatus
}

const props = withDefaults(defineProps<{
  messages: AgentMessageDto[]
  streamingText?: string
  runtimeEvents?: RuntimeEventDto[]
}>(), {
  streamingText: '',
  runtimeEvents: () => [],
})

const persistedMessages = computed(() =>
  props.messages.filter((message) => Boolean(message.content?.trim()))
)

const liveMessage = computed<AgentMessageDto | null>(() => {
  if (!props.streamingText.trim()) return null
  return {
    id: 'streaming-assistant',
    sessionId: persistedMessages.value.at(-1)?.sessionId ?? '',
    role: 'ASSISTANT',
    content: props.streamingText,
    contentFormat: 'MARKDOWN',
    status: 'STREAMING',
    seqNo: (persistedMessages.value.at(-1)?.seqNo ?? 0) + 1,
    createdAt: new Date().toISOString(),
  }
})

const activityItems = computed(() =>
  dedupeActivityItems(props.runtimeEvents
    .filter((event) => event.eventType !== 'ASSISTANT_DELTA')
    .map(toActivityItem)
    .filter((item): item is ActivityItem => Boolean(item)))
    .slice(-6)
)

const hasContent = computed(() =>
  persistedMessages.value.length > 0 || Boolean(liveMessage.value) || activityItems.value.length > 0
)

const activityRunning = computed(() => activityItems.value.some((item) => item.status === 'running'))

const activitySummary = computed(() => {
  const items = activityItems.value
  if (items.length === 0) return ''
  const running = items.filter((item) => item.status === 'running').length
  const done = items.filter((item) => item.status === 'done').length
  const waiting = items.filter((item) => item.status === 'waiting').length
  const failed = items.filter((item) => item.status === 'error').length
  const parts = []
  if (running) parts.push(`正在处理 ${running} 项`)
  if (done) parts.push(`已完成 ${done} 项`)
  if (waiting) parts.push(`等待确认 ${waiting} 项`)
  if (failed) parts.push(`异常 ${failed} 项`)
  return parts.join('，') || `已记录 ${items.length} 条运行活动`
})

function formatTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '刚刚'
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return '刚刚'
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
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
  if (message.status === 'STREAMING') return '流式中'
  if (message.status === 'FAILED') return '失败'
  return message.role === 'ASSISTANT' ? '已完成' : '已记录'
}

function shouldRenderMarkdown(message: AgentMessageDto): boolean {
  return message.role === 'ASSISTANT' || message.contentFormat === 'MARKDOWN'
}

function toActivityItem(event: RuntimeEventDto): ActivityItem | null {
  const payload = parsePayload(event.payloadJson)
  const label = textField(payload, ['skillName', 'label', 'title', 'type'])
  const detail = textField(payload, ['output', 'reason', 'summary', 'content', 'permissionId', 'toolCallId'])

  if (event.eventType === 'STATUS') {
    const status = textField(payload, ['status', 'label'])
    if (status === 'running') {
      return {
        id: event.id,
        title: 'OpenCode 正在处理',
        detail: '命令已下发，正在等待模型或工具输出。',
        status: 'running',
      }
    }
    if (status === 'waiting_user' || status === 'idle') {
      return {
        id: event.id,
        title: 'OpenCode 等待下一步',
        detail: '本轮输出已结束，可以继续输入或处理待确认项。',
        status: 'waiting',
      }
    }
    return null
  }

  if (event.eventType === 'SKILL_STARTED') {
    return {
      id: event.id,
      title: `正在调用 ${label || '工具'}`,
      detail: detail || '运行时工具已启动。',
      status: 'running',
    }
  }

  if (event.eventType === 'SKILL_COMPLETED') {
    const isError = payload.isError === true
    return {
      id: event.id,
      title: `${label || '工具'} ${isError ? '执行失败' : '执行完成'}`,
      detail: detail ? trimDetail(detail) : '工具调用已返回结果。',
      status: isError ? 'error' : 'done',
    }
  }

  if (event.eventType === 'PERMISSION_REQUIRED' || event.eventType === 'CONFIRMATION_CREATED') {
    return {
      id: event.id,
      title: label || '等待用户确认',
      detail: detail || '运行时需要用户确认后继续。',
      status: 'waiting',
    }
  }

  if (event.eventType === 'ERROR') {
    return {
      id: event.id,
      title: '运行时异常',
      detail: detail || label || 'OpenCode 运行过程中出现异常。',
      status: 'error',
    }
  }

  return null
}

function dedupeActivityItems(items: ActivityItem[]): ActivityItem[] {
  const out: ActivityItem[] = []
  items.forEach((item) => {
    const last = out.at(-1)
    if (last && last.title === item.title && last.detail === item.detail && last.status === item.status) {
      return
    }
    out.push(item)
  })
  return out
}

function parsePayload(payloadJson: string | null): RuntimePayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return isRuntimePayload(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function isRuntimePayload(value: unknown): value is RuntimePayload {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function textField(payload: RuntimePayload, keys: string[]): string {
  for (const key of keys) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) {
      return value
    }
  }
  return ''
}

function trimDetail(value: string): string {
  const compact = value.replace(/\s+/g, ' ').trim()
  return compact.length > 180 ? `${compact.slice(0, 180)}...` : compact
}
</script>

<template>
  <div class="message-list" aria-live="polite">
    <div v-if="!hasContent" class="message-list__empty">
      <strong>会话已就绪</strong>
      <span>输入问题，或点击上方场景开始和 OpenCode Runtime 对话。</span>
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
          <span v-else>{{ msg.content }}</span>
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
        v-if="activityItems.length"
        class="assistant-turn assistant-turn--activity"
      >
        <div class="assistant-rail">
          <div class="assistant-avatar">A</div>
        </div>
        <details class="activity-group" :open="activityRunning">
          <summary class="activity-summary">
            <span class="activity-summary__icon">↳</span>
            <span>{{ activitySummary }}</span>
          </summary>
          <div class="activity-body">
            <div
              v-for="item in activityItems"
              :key="item.id"
              class="activity-item"
              :class="`activity-item--${item.status}`"
            >
              <span class="activity-item__state"></span>
              <div class="activity-item__copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.detail }}</span>
              </div>
            </div>
          </div>
        </details>
      </article>

      <article
        v-if="liveMessage"
        class="assistant-turn assistant-turn--live"
      >
        <div class="assistant-rail">
          <div class="assistant-avatar assistant-avatar--live">A</div>
        </div>
        <section class="assistant-card assistant-card--live">
          <header class="assistant-card__head">
            <div class="assistant-card__title">
              <span class="assistant-card__glyph">A</span>
              <span>助手正在回复</span>
            </div>
            <span class="assistant-card__pill assistant-card__pill--live">流式中</span>
          </header>
          <div class="assistant-card__live-content">
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

.activity-group {
  min-width: 0;
  padding: 2px 0 10px;
}

.activity-summary {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  gap: 7px;
  min-height: 28px;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
  list-style: none;
}

.activity-summary::-webkit-details-marker {
  display: none;
}

.activity-summary__icon {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.activity-summary::after {
  content: '›';
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  transition: transform 160ms ease;
}

.activity-group[open] .activity-summary::after {
  transform: rotate(90deg);
}

.activity-body {
  display: grid;
  gap: 8px;
  margin: 6px 0 2px 28px;
  padding-left: 12px;
  border-left: 1px solid var(--border-color);
}

.activity-item {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  padding: 9px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-overlay);
}

.activity-item__state {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 999px;
  background: var(--text-muted);
}

.activity-item--running .activity-item__state {
  background: var(--warning);
  box-shadow: 0 0 0 5px var(--warning-soft);
}

.activity-item--done .activity-item__state {
  background: var(--success);
}

.activity-item--waiting .activity-item__state {
  background: var(--accent-blue);
}

.activity-item--error .activity-item__state {
  background: var(--error);
}

.activity-item__copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.activity-item__copy strong {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
}

.activity-item__copy span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
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
