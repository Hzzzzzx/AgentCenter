<script setup lang="ts">
import { computed } from 'vue'
import type { AgentMessageDto, RuntimeEventDto, ConfirmationRequestDto } from '../../api/types'
import type { ProjectorInput, ConversationTurnProjection } from './projection/types'
import { projectConversationTurns } from './projection/conversationTurnProjector'
import MarkdownContent from './MarkdownContent.vue'
import AssistantTurn from './AssistantTurn.vue'

const props = withDefaults(defineProps<{
  messages: AgentMessageDto[]
  streamingText?: string
  runtimeEvents?: RuntimeEventDto[]
  activeNodeId?: string | null
  activeNodeState?: string | null
  activeSessionId?: string | null
  confirmations?: ConfirmationRequestDto[]
  running?: boolean
}>(), {
  streamingText: '',
  runtimeEvents: () => [],
  activeNodeId: null,
  activeNodeState: null,
  activeSessionId: null,
  confirmations: () => [],
  running: false,
})

const emit = defineEmits<{
  'open-artifact': [title: string]
  'resolve-confirmation': [confirmationId: string, value: string, meta: { requestType?: string; interactionType?: string }]
}>()

// ─── Display item types ─────────────────────────────────────

type SystemLinePart = {
  kind: 'text' | 'artifact'
  text: string
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

type InteractionResponseSummary = {
  title: string
  value: string
}

type DisplayItem =
  | { type: 'user-message'; id: string; message: AgentMessageDto }
  | { type: 'system-message'; id: string; message: AgentMessageDto }
  | { type: 'assistant-turn'; id: string; turn: ConversationTurnProjection }

// ─── Persisted messages (filtered + sorted) ─────────────────

const normalizedMessages = computed(() =>
  dedupeAssistantArtifacts(
    props.messages
    .map((message, index) => ({ message, index }))
    .filter(({ message }) => Boolean(message.content?.trim()))
    .sort((a, b) => compareMessages(a.message, b.message, a.index, b.index))
    .map(({ message }) => message)
  )
)

const persistedMessages = computed(() =>
  normalizedMessages.value
)

// ─── Projector integration ──────────────────────────────────

const resolvedSessionId = computed(() =>
  props.activeSessionId
  ?? persistedMessages.value.at(-1)?.sessionId
  ?? ''
)

const projectorInput = computed<ProjectorInput>(() => ({
  messages: normalizedMessages.value.map(m => ({
    id: m.id,
    sessionId: m.sessionId,
    role: m.role,
    content: m.content,
    contentFormat: m.contentFormat,
    status: m.status,
    seqNo: m.seqNo,
    createdAt: m.createdAt,
    workflowNodeInstanceId: m.workflowNodeInstanceId,
  })),
  runtimeEvents: (props.runtimeEvents ?? []).map(e => ({
    id: e.id,
    sessionId: e.sessionId,
    workItemId: e.workItemId,
    workflowInstanceId: e.workflowInstanceId,
    workflowNodeInstanceId: e.workflowNodeInstanceId,
    eventType: e.eventType as string,
    eventSource: e.eventSource,
    payloadJson: e.payloadJson,
    seqNo: e.seqNo,
    createdAt: e.createdAt,
  })),
  confirmations: props.confirmations.map(c => ({
    id: c.id,
    requestType: c.requestType,
    status: c.status,
    title: c.title,
    content: c.content,
    optionsJson: c.optionsJson,
    interactionType: c.interactionType ?? null,
    agentSessionId: c.agentSessionId,
  })),
  streamingText: props.streamingText,
  activeSessionId: resolvedSessionId.value,
  running: props.running,
}))

const projectedTurns = computed(() =>
  projectConversationTurns(projectorInput.value)
)

// ─── Display items (interleaved) ────────────────────────────

const displayItems = computed<DisplayItem[]>(() => {
  const items: DisplayItem[] = []
  const sorted = persistedMessages.value

  // Collect assistant turns with actual content
  const contentTurns = projectedTurns.value.filter(t =>
    t.answer.text || t.steps.length > 0 || t.status === 'running'
  )

  let turnIdx = 0
  let lastWasAssistant = false

  for (const msg of sorted) {
    if (msg.role === 'USER') {
      items.push({ type: 'user-message', id: msg.id, message: msg })
      lastWasAssistant = false
    } else if (msg.role === 'SYSTEM') {
      items.push({ type: 'system-message', id: msg.id, message: msg })
      lastWasAssistant = false
    } else {
      // ASSISTANT or TOOL — group consecutive ones into one projector turn
      if (!lastWasAssistant && turnIdx < contentTurns.length) {
        items.push({ type: 'assistant-turn', id: contentTurns[turnIdx].turnId, turn: contentTurns[turnIdx] })
        turnIdx++
        lastWasAssistant = true
      }
    }
  }

  // Remaining turns (runtime events with no corresponding messages)
  while (turnIdx < contentTurns.length) {
    items.push({ type: 'assistant-turn', id: contentTurns[turnIdx].turnId, turn: contentTurns[turnIdx] })
    turnIdx++
  }

  return items
})

const hasContent = computed(() =>
  displayItems.value.length > 0
)

// ─── Helpers (preserved) ────────────────────────────────────

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

function dedupeAssistantArtifacts(messages: AgentMessageDto[]): AgentMessageDto[] {
  const result: AgentMessageDto[] = []
  const assistantByContent = new Map<string, number>()

  for (const message of messages) {
    if (message.role !== 'ASSISTANT' || !message.content?.trim()) {
      result.push(message)
      continue
    }

    const key = message.content.trim()
    const existingIndex = assistantByContent.get(key)
    if (existingIndex === undefined) {
      assistantByContent.set(key, result.length)
      result.push(message)
      continue
    }

    const existing = result[existingIndex]
    if (!existing.workflowNodeInstanceId && message.workflowNodeInstanceId) {
      result[existingIndex] = message
    }
  }

  return result
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function shouldRenderMarkdown(message: AgentMessageDto): boolean {
  return message.role === 'ASSISTANT' || message.contentFormat === 'MARKDOWN'
}

function isWorkflowInputMessage(message: AgentMessageDto): boolean {
  return message.role === 'USER'
    && message.contentFormat === 'MARKDOWN'
    && Boolean(message.content?.startsWith('请执行工作流节点：'))
}

function isInteractionResponseMessage(message: AgentMessageDto): boolean {
  return message.role === 'USER'
    && message.contentFormat === 'TEXT'
    && Boolean(message.content?.startsWith('用户输入：'))
}

function interactionResponseSummary(message: AgentMessageDto): InteractionResponseSummary {
  const content = message.content ?? ''
  const value = findWorkflowInputValue(content, /^用户输入：(.+)$/m)
    .replace(/^用户选择：/, '选择：')
    .replace(/^用户补充：/, '补充：')
  const title = findWorkflowInputValue(content, /^确认项：(.+)$/m)
  return {
    title: title === '未提供' ? '已提交交互反馈' : title,
    value: value === '未提供' ? '已提交' : value,
  }
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
        v-for="item in displayItems"
        :key="item.id"
      >
        <!-- USER message -->
        <article
          v-if="item.type === 'user-message'"
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
            <template v-else-if="isInteractionResponseMessage(item.message)">
              <div class="interaction-response-card">
                <div class="interaction-response-card__eyebrow">用户反馈</div>
                <div class="interaction-response-card__title">
                  {{ interactionResponseSummary(item.message).title }}
                </div>
                <p>{{ interactionResponseSummary(item.message).value }}</p>
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

        <!-- SYSTEM message -->
        <article
          v-else-if="item.type === 'system-message'"
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

        <!-- ASSISTANT turn -->
        <AssistantTurn
          v-else-if="item.type === 'assistant-turn'"
          :turn="item.turn"
          @open-artifact="emit('open-artifact', $event)"
          @resolve="(confirmationId, value, meta) => emit('resolve-confirmation', confirmationId, value, meta)"
        />
      </template>
    </template>
  </div>
</template>

<style scoped>
.message-list {
  width: min(920px, 100%);
  min-width: 0;
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
  min-width: 0;
}

.user-bubble {
  box-sizing: border-box;
  min-width: 0;
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
  min-width: 0;
  width: min(620px, 72%);
  max-width: 100%;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent-blue) 72%, var(--border-color));
  border-radius: 16px 16px 4px 16px;
  background: var(--accent-blue);
  color: var(--on-brand);
  font-weight: 650;
  white-space: normal;
}

.workflow-input-card {
  display: grid;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}

.workflow-input-card__eyebrow {
  color: color-mix(in srgb, var(--on-brand) 78%, transparent);
  font-size: 12px;
  font-weight: 850;
}

.workflow-input-card__title {
  min-width: 0;
  color: var(--on-brand);
  font-size: 14px;
  font-weight: 850;
  overflow-wrap: anywhere;
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
  min-width: 0;
  max-width: 100%;
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
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
  max-height: 260px;
  margin-top: 8px;
  overflow: auto;
  padding: 8px;
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
}

.workflow-input-card__details :deep(.markdown-content pre),
.workflow-input-card__details :deep(.markdown-content code) {
  min-width: 0;
  max-width: 100%;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.workflow-input-card__details :deep(.markdown-content table) {
  min-width: 0;
  width: max-content;
}

.interaction-response-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  white-space: normal;
}

.interaction-response-card__eyebrow {
  color: color-mix(in srgb, var(--on-brand) 76%, transparent);
  font-size: 11px;
  font-weight: 850;
}

.interaction-response-card__title {
  color: var(--on-brand);
  font-size: 13px;
  font-weight: 900;
}

.interaction-response-card p {
  margin: 0;
  color: color-mix(in srgb, var(--on-brand) 90%, transparent);
  font-size: 13px;
  line-height: 1.5;
}

.message-time {
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

.system-dot {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 6px;
  background: var(--info-soft);
  color: var(--info);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 900;
}

@media (max-width: 760px) {
  .message-list {
    padding-inline: 2px;
  }

  .user-bubble {
    max-width: 86%;
  }
}
</style>
