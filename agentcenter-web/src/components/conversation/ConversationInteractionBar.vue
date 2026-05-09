<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useNotificationStore } from '../../stores/notifications'
import type {
  ConfirmationActionType,
  ConfirmationRequestDto,
  ConfirmationRequestType,
} from '../../api/types'

const props = defineProps<{
  interactions: ConfirmationRequestDto[]
}>()

const emit = defineEmits<{
  resolved: [id: string]
  rejected: [id: string]
  open: [id: string]
}>()

const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const expanded = ref(true)
const activeId = ref<string | null>(null)
const busyAction = ref<string | null>(null)
const selectedOptions = ref<Record<string, string>>({})
const inputValues = ref<Record<string, string>>({})

const typeLabels: Record<ConfirmationRequestType, string> = {
  CONFIRM: '确认',
  APPROVAL: '审批',
  INPUT_REQUIRED: '输入',
  DECISION: '选择',
  EXCEPTION: '异常',
  PERMISSION: '权限',
}

const visibleInteractions = computed(() =>
  props.interactions.filter((item) => item.status === 'PENDING' || item.status === 'IN_CONVERSATION')
)

const activeInteraction = computed(() =>
  visibleInteractions.value.find((item) => item.id === activeId.value)
  ?? visibleInteractions.value[0]
  ?? null
)

const activeOptions = computed(() =>
  activeInteraction.value ? parseOptions(activeInteraction.value.optionsJson) : []
)

const activeInput = computed({
  get: () => activeInteraction.value ? inputValues.value[activeInteraction.value.id] ?? '' : '',
  set: (value: string) => {
    if (activeInteraction.value) {
      inputValues.value = { ...inputValues.value, [activeInteraction.value.id]: value }
    }
  },
})

const activeOption = computed({
  get: () => activeInteraction.value ? selectedOptions.value[activeInteraction.value.id] ?? activeOptions.value[0] ?? '' : '',
  set: (value: string) => {
    if (activeInteraction.value) {
      selectedOptions.value = { ...selectedOptions.value, [activeInteraction.value.id]: value }
    }
  },
})

const canSubmitInput = computed(() => activeInput.value.trim().length > 0)
const canSubmitChoice = computed(() => activeOptions.value.length > 0 ? activeOption.value.length > 0 : activeInput.value.trim().length > 0)

watch(
  visibleInteractions,
  (items) => {
    if (!items.length) {
      activeId.value = null
      return
    }
    if (!activeId.value || !items.some((item) => item.id === activeId.value)) {
      activeId.value = items[0].id
      expanded.value = true
    }
  },
  { immediate: true }
)

function parseOptions(raw: string | null): string[] {
  if (!raw || !raw.trim()) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item).trim()).filter(Boolean)
    }
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as { options?: unknown[] }).options)) {
      return (parsed as { options: unknown[] }).options.map((item) => String(item).trim()).filter(Boolean)
    }
  } catch {
    // Fall through to tolerant text splitting.
  }
  return raw
    .split(/\s*(?:\/|、|，|,|；|;)\s*/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function interactionSummary(item: ConfirmationRequestDto): string {
  return item.contextSummary || item.content || item.title || '等待你完成当前交互'
}

function interactionTaskLabel(item: ConfirmationRequestDto): string {
  const code = item.workItemCode?.trim()
  const title = item.workItemTitle?.trim()
  if (code && title) return `${code} · ${title}`
  if (title) return title
  if (code) return code
  return '未关联任务'
}

function interactionTabTitle(item: ConfirmationRequestDto): string {
  return `${interactionTaskLabel(item)} · ${item.workflowNodeName || item.title}`
}

function primaryActionLabel(item: ConfirmationRequestDto): string {
  if (item.requestType === 'INPUT_REQUIRED') return '提交补充'
  if (item.requestType === 'DECISION') return '提交选择'
  if (item.requestType === 'EXCEPTION') return '重试'
  if (item.requestType === 'PERMISSION') return '授权'
  return '确认'
}

function secondaryActionLabel(item: ConfirmationRequestDto): string {
  if (item.requestType === 'EXCEPTION') return '跳过'
  if (item.requestType === 'DECISION') return '稍后'
  if (item.requestType === 'PERMISSION') return '拒绝'
  return '退回'
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function resolveActive(actionType: ConfirmationActionType, payload?: Record<string, unknown>, comment?: string) {
  if (!activeInteraction.value || busyAction.value) return
  const interaction = activeInteraction.value
  busyAction.value = `${interaction.id}:${actionType}`
  try {
    await confirmationStore.resolveConfirmation(interaction.id, { actionType, payload, comment })
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'success',
      title: '交互已提交',
      message: `${interaction.title} 已收到处理结果`,
    })
    emit('resolved', interaction.id)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '提交失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

function handlePrimary() {
  const interaction = activeInteraction.value
  if (!interaction) return
  if (interaction.requestType === 'INPUT_REQUIRED') {
    const input = activeInput.value.trim()
    if (!input) return
    void resolveActive('SUPPLEMENT', { input }, input)
    return
  }
  if (interaction.requestType === 'DECISION') {
    const choice = activeOptions.value.length > 0 ? activeOption.value : activeInput.value.trim()
    if (!choice) return
    void resolveActive('CHOOSE', { choice }, choice)
    return
  }
  if (interaction.requestType === 'EXCEPTION') {
    void resolveActive('RETRY')
    return
  }
  void resolveActive('APPROVE')
}

function handleSecondary() {
  const interaction = activeInteraction.value
  if (!interaction || busyAction.value) return
  if (interaction.requestType === 'EXCEPTION') {
    void resolveActive('SKIP')
    return
  }
  busyAction.value = `${interaction.id}:REJECT`
  confirmationStore.resolveConfirmation(interaction.id, { actionType: 'REJECT' })
    .then(() => {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'warning',
        title: '已暂缓交互',
        message: `${interaction.title} 已记录为未通过`,
      })
      emit('rejected', interaction.id)
    })
    .catch((error) => {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'error',
        title: '处理失败',
        message: errorMessage(error),
        durationMs: 5200,
      })
    })
    .finally(() => {
      busyAction.value = null
    })
}
</script>

<template>
  <section v-if="visibleInteractions.length" class="interaction-bar" :class="{ 'interaction-bar--collapsed': !expanded }">
    <button
      type="button"
      class="interaction-bar__header"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="interaction-bar__title">
        <span class="interaction-bar__dot"></span>
        当前需要交互
        <span class="interaction-bar__count">{{ visibleInteractions.length }}</span>
      </span>
      <span class="interaction-bar__summary">
        {{ activeInteraction ? `${typeLabels[activeInteraction.requestType]} · ${interactionTabTitle(activeInteraction)}` : '暂无待处理交互' }}
      </span>
      <span class="interaction-bar__chevron" :class="{ 'interaction-bar__chevron--open': expanded }">v</span>
    </button>

    <div v-if="expanded && activeInteraction" class="interaction-bar__body">
      <div class="interaction-bar__tabs" role="tablist" aria-label="当前交互列表">
        <button
          v-for="item in visibleInteractions"
          :key="item.id"
          type="button"
          class="interaction-bar__tab"
          :class="{ 'interaction-bar__tab--active': item.id === activeInteraction.id }"
          @click="activeId = item.id"
        >
          <span>{{ typeLabels[item.requestType] }}</span>
          <strong>{{ interactionTaskLabel(item) }}</strong>
          <em>{{ item.workflowNodeName || item.title }}</em>
        </button>
      </div>

      <div class="interaction-bar__main">
        <div class="interaction-bar__copy">
          <span class="interaction-bar__type">{{ typeLabels[activeInteraction.requestType] }}</span>
          <div class="interaction-bar__task">{{ interactionTaskLabel(activeInteraction) }}</div>
          <strong>{{ activeInteraction.title }}</strong>
          <p>{{ interactionSummary(activeInteraction) }}</p>
        </div>

        <div v-if="activeInteraction.requestType === 'DECISION'" class="interaction-bar__control interaction-bar__control--options">
          <button
            v-for="option in activeOptions"
            :key="option"
            type="button"
            class="interaction-bar__option"
            :class="{ 'interaction-bar__option--selected': activeOption === option }"
            @click="activeOption = option"
          >
            {{ option }}
          </button>
          <input
            v-if="!activeOptions.length"
            v-model="activeInput"
            class="interaction-bar__input"
            type="text"
            placeholder="输入你的选择..."
          >
        </div>

        <div v-else-if="activeInteraction.requestType === 'INPUT_REQUIRED'" class="interaction-bar__control">
          <input
            v-model="activeInput"
            class="interaction-bar__input"
            type="text"
            placeholder="输入你的补充要求..."
          >
        </div>

        <div v-else class="interaction-bar__hint">
          处理后 Agent 会继续推进当前节点。
        </div>

        <div class="interaction-bar__actions">
          <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="emit('open', activeInteraction.id)">
            详情
          </button>
          <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="handleSecondary">
            {{ secondaryActionLabel(activeInteraction) }}
          </button>
          <button
            type="button"
            class="interaction-bar__primary"
            :disabled="!!busyAction || (activeInteraction.requestType === 'INPUT_REQUIRED' && !canSubmitInput) || (activeInteraction.requestType === 'DECISION' && !canSubmitChoice)"
            @click="handlePrimary"
          >
            {{ busyAction ? '提交中...' : primaryActionLabel(activeInteraction) }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.interaction-bar {
  width: min(920px, 100%);
  margin: 0 auto 8px;
  border: 1px solid color-mix(in srgb, var(--accent-blue) 38%, var(--border-color));
  border-radius: 8px;
  background: var(--bg-card);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--accent-blue) 12%, transparent);
  overflow: hidden;
}

.interaction-bar__header {
  width: 100%;
  min-height: 42px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  border: 0;
  background: color-mix(in srgb, var(--brand-soft) 55%, var(--bg-card));
  color: var(--text-primary);
  cursor: pointer;
}

.interaction-bar__title,
.interaction-bar__summary,
.interaction-bar__tab,
.interaction-bar__actions {
  min-width: 0;
}

.interaction-bar__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.interaction-bar__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--warning) 16%, transparent);
}

.interaction-bar__count {
  min-width: 20px;
  height: 20px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: var(--accent-blue);
  color: var(--on-brand);
  font-size: 11px;
  font-weight: 900;
}

.interaction-bar__summary {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__chevron {
  color: var(--text-secondary);
  font-size: 18px;
  line-height: 1;
  transform: rotate(-90deg);
  transition: transform 0.16s ease;
}

.interaction-bar__chevron--open {
  transform: rotate(0deg);
}

.interaction-bar__body {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(0, 1fr);
  gap: 12px;
  padding: 10px;
  border-top: 1px solid var(--border-color);
}

.interaction-bar__tabs {
  display: grid;
  gap: 6px;
}

.interaction-bar__tab {
  display: grid;
  gap: 3px;
  min-height: 48px;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--surface-overlay);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
}

.interaction-bar__tab span {
  font-size: 11px;
  font-weight: 900;
  color: var(--accent-blue);
}

.interaction-bar__tab strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__tab em {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__tab--active {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
}

.interaction-bar__main {
  display: grid;
  gap: 10px;
}

.interaction-bar__copy {
  display: grid;
  gap: 4px;
}

.interaction-bar__type {
  width: max-content;
  padding: 2px 7px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--warning) 14%, var(--bg-card));
  color: var(--warning);
  font-size: 11px;
  font-weight: 900;
}

.interaction-bar__task {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 900;
}

.interaction-bar__copy strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.interaction-bar__copy p,
.interaction-bar__hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.interaction-bar__control {
  display: flex;
  gap: 8px;
}

.interaction-bar__control--options {
  flex-wrap: wrap;
}

.interaction-bar__option,
.interaction-bar__input,
.interaction-bar__ghost,
.interaction-bar__primary {
  min-height: 34px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 800;
}

.interaction-bar__option {
  padding: 0 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
}

.interaction-bar__option--selected {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.interaction-bar__input {
  flex: 1;
  min-width: 180px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-primary);
  color: var(--text-primary);
  outline: none;
}

.interaction-bar__input:focus {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.interaction-bar__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.interaction-bar__ghost,
.interaction-bar__primary {
  padding: 0 13px;
  border: 1px solid var(--border-color);
  cursor: pointer;
}

.interaction-bar__ghost {
  background: var(--bg-card);
  color: var(--text-secondary);
}

.interaction-bar__primary {
  border-color: var(--accent-blue);
  background: var(--accent-blue);
  color: var(--on-brand);
}

.interaction-bar__ghost:disabled,
.interaction-bar__primary:disabled {
  cursor: wait;
  opacity: 0.58;
}

@media (max-width: 760px) {
  .interaction-bar__header {
    grid-template-columns: 1fr auto;
  }

  .interaction-bar__summary {
    grid-column: 1 / -1;
    padding-bottom: 8px;
  }

  .interaction-bar__body {
    grid-template-columns: 1fr;
  }

  .interaction-bar__tabs {
    grid-auto-flow: column;
    grid-auto-columns: minmax(140px, 1fr);
    overflow-x: auto;
  }

  .interaction-bar__actions {
    justify-content: stretch;
  }

  .interaction-bar__actions > button {
    flex: 1;
  }
}
</style>
