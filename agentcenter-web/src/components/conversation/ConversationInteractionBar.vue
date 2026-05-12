<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useNotificationStore } from '../../stores/notifications'
import { parseInteractionSchema, type InteractionSchema, type InteractionOption, type InteractionField } from './interactions/interactionSchema'
import type {
  ConfirmationActionType,
  ConfirmationRequestDto,
  ConfirmationRequestType,
} from '../../api/types'

const props = defineProps<{
  interactions: ConfirmationRequestDto[]
}>()

const emit = defineEmits<{
  submitting: [id: string]
  resolved: [id: string]
  rejected: [id: string]
  open: [id: string]
}>()

const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const activeId = ref<string | null>(null)
const REVIEW_TAB_ID = '__review__'
const busyAction = ref<string | null>(null)
const selectedOptions = ref<Record<string, string>>({})
const inputValues = ref<Record<string, string>>({})
const selectedChoices = ref<Record<string, Set<string>>>({})
const customInput = ref<Record<string, string>>({})
const fieldValues = ref<Record<string, Record<string, string>>>({})

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

const activeInteraction = computed(() => {
  if (activeId.value === REVIEW_TAB_ID) return null
  return visibleInteractions.value.find((item) => item.id === activeId.value)
    ?? visibleInteractions.value[0]
    ?? null
})

const reviewMode = computed(() =>
  activeId.value === REVIEW_TAB_ID && visibleInteractions.value.length > 1
)

const activeSchema = computed<InteractionSchema | null>(() =>
  activeInteraction.value ? parseInteractionSchema(activeInteraction.value) : null
)

const activeOptions = computed<InteractionOption[]>(() =>
  activeSchema.value?.options ?? []
)

const activeFields = computed<InteractionField[]>(() =>
  activeSchema.value?.fields ?? []
)

const isMultiSelect = computed(() =>
  activeSchema.value?.selection === 'multi'
)

const allowCustomChoice = computed(() =>
  activeSchema.value?.allowCustom === true
)

const activeUsesOptionControl = computed(() => {
  const requestType = activeInteraction.value?.requestType
  return requestType === 'DECISION'
    || ((requestType === 'APPROVAL' || requestType === 'CONFIRM') && activeOptions.value.length > 0)
})

const activeFieldValues = computed(() => {
  if (!activeInteraction.value) return {}
  return fieldValues.value[activeInteraction.value.id] ?? {}
})

const activeInput = computed({
  get: () => activeInteraction.value ? inputValues.value[activeInteraction.value.id] ?? '' : '',
  set: (value: string) => {
    if (activeInteraction.value) {
      inputValues.value = { ...inputValues.value, [activeInteraction.value.id]: value }
    }
  },
})

const activeOption = computed({
  get: () => {
    if (!activeInteraction.value) return ''
    const id = activeInteraction.value.id
    // If custom input has content, mark as custom-selected so it takes precedence over preset options
    if (allowCustomChoice.value && (customInput.value[id]?.trim()?.length ?? 0) > 0) {
      return '__custom__'
    }
    return selectedOptions.value[id] ?? (activeOptions.value[0]?.id ?? '')
  },
  set: (value: string) => {
    if (activeInteraction.value) {
      selectedOptions.value = { ...selectedOptions.value, [activeInteraction.value.id]: value }
    }
  },
})

function setFieldValue(fieldId: string, value: string) {
  if (!activeInteraction.value) return
  const id = activeInteraction.value.id
  fieldValues.value = {
    ...fieldValues.value,
    [id]: { ...(fieldValues.value[id] ?? {}), [fieldId]: value }
  }
}

function toggleChoice(optionId: string) {
  if (!activeInteraction.value) return
  const id = activeInteraction.value.id
  const current = new Set(selectedChoices.value[id] ?? [])
  if (current.has(optionId)) current.delete(optionId)
  else current.add(optionId)
  selectedChoices.value = { ...selectedChoices.value, [id]: current }
}

const canSubmitInput = computed(() => {
  if (!activeInteraction.value) return false
  if (activeFields.value.length > 0) {
    const vals = fieldValues.value[activeInteraction.value.id] ?? {}
    return activeFields.value.every(f => !f.required || (vals[f.id]?.trim()?.length ?? 0) > 0)
  }
  return activeInput.value.trim().length > 0
})
const canSubmitChoice = computed(() => {
  if (!activeUsesOptionControl.value) return true
  if (!activeInteraction.value) return false
  if (activeOptions.value.length === 0) {
    if (allowCustomChoice.value) {
      return (customInput.value[activeInteraction.value.id]?.trim()?.length ?? 0) > 0
    }
    return activeInput.value.trim().length > 0
  }
  if (isMultiSelect.value) {
    const chosen = selectedChoices.value[activeInteraction.value.id]
    return (chosen?.size ?? 0) > 0 || (allowCustomChoice.value && (customInput.value[activeInteraction.value.id]?.trim()?.length ?? 0) > 0)
  }
  return activeOption.value.length > 0
})

watch(
  visibleInteractions,
  (items) => {
    if (!items.length) {
      activeId.value = null
      return
    }
    if (!activeId.value || (activeId.value !== REVIEW_TAB_ID && !items.some((item) => item.id === activeId.value))) {
      activeId.value = items[0].id
    }
  },
  { immediate: true }
)

function interactionIndex(item: ConfirmationRequestDto): number {
  const index = visibleInteractions.value.findIndex((candidate) => candidate.id === item.id)
  return index >= 0 ? index + 1 : 1
}

function schemaFor(item: ConfirmationRequestDto): InteractionSchema | null {
  return parseInteractionSchema(item)
}

function removeKnownContext(value: string, item: ConfirmationRequestDto): string {
  const tokens = [
    item.workItemCode,
    item.workItemTitle,
    item.workflowNodeName,
  ]
    .map((token) => token?.trim())
    .filter((token): token is string => Boolean(token))

  let result = value
  for (const token of tokens) {
    result = result.replaceAll(token, '')
  }
  return result
    .replace(/[·•|｜:：,，、-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function parseJsonRecord(value: string | null | undefined): Record<string, unknown> | null {
  if (!value) return null
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null
  } catch {
    return null
  }
}

function collectPermissionText(value: unknown, keyHint = '', depth = 0): string[] {
  if (depth > 4 || value === null || value === undefined) return []
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed ? [trimmed] : []
  }
  if (Array.isArray(value)) {
    return value.flatMap(item => collectPermissionText(item, keyHint, depth + 1))
  }
  if (typeof value !== 'object') return []

  const result: string[] = []
  for (const [key, nestedValue] of Object.entries(value as Record<string, unknown>)) {
    const normalizedKey = key.toLowerCase()
    const nextHint = keyHint ? `${keyHint}.${normalizedKey}` : normalizedKey
    const likelyTargetKey = /(file|path|target|name|title|description|command|input|args|argument|permission)/.test(normalizedKey)
    if (likelyTargetKey || depth < 2) {
      result.push(...collectPermissionText(nestedValue, nextHint, depth + 1))
    }
    if (/(filepath|file_path|path|target|filename|file)/.test(nextHint)) {
      result.push(...collectPermissionText(nestedValue, nextHint, depth + 1))
    }
  }
  return result
}

function extractPathFromText(value: string): string | null {
  const windowsPath = value.match(/[A-Za-z]:[\\/][^\s"'`<>|]+(?:[\\/][^\s"'`<>|]+)*/)
  if (windowsPath) return windowsPath[0]

  const posixPath = value.match(/(?:~|\.{1,2}|\/)[/\w@.+-]+(?:\/[\w@.+-]+)*(?:\.[A-Za-z0-9]+)?/)
  if (posixPath) return posixPath[0]

  return null
}

function concisePath(value: string): string {
  const normalized = value.replaceAll('\\', '/')
  const segments = normalized.split('/').filter(Boolean)
  if (segments.length <= 4) return value
  const prefix = /^[A-Za-z]:/.test(value) ? value.slice(0, 2) + '/.../' : '.../'
  return prefix + segments.slice(-4).join('/')
}

function permissionActionLabel(item: ConfirmationRequestDto, schema: InteractionSchema | null): string {
  const text = [
    item.title,
    item.content,
    item.contextSummary,
    schema?.title,
    schema?.question,
    item.interactionSchemaJson,
    item.interactionContextJson,
  ]
    .filter((value): value is string => Boolean(value))
    .join(' ')
    .toLowerCase()

  if (text.includes('external_directory')) return '访问外部目录'
  if (/(write|edit|update|modify|patch|delete|remove|rename|move|create|保存|写入|编辑|修改|删除|重命名|移动|创建)/.test(text)) {
    return '编辑文件'
  }
  if (/(read|open|view|读取|查看)/.test(text)) return '读取文件'
  return '执行受限操作'
}

function permissionTargetPath(item: ConfirmationRequestDto, schema: InteractionSchema | null): string | null {
  const records = [
    parseJsonRecord(item.interactionContextJson),
    parseJsonRecord(item.interactionSchemaJson),
  ].filter((record): record is Record<string, unknown> => Boolean(record))

  const candidates = [
    ...records.flatMap(record => collectPermissionText(record)),
    schema?.question,
    schema?.title,
    item.title,
    item.content,
    item.contextSummary,
  ].filter((value): value is string => Boolean(value))

  for (const candidate of candidates) {
    const path = extractPathFromText(candidate)
    if (path) return concisePath(path)
  }
  return null
}

function permissionScopeText(item: ConfirmationRequestDto): string | null {
  const records = [
    parseJsonRecord(item.interactionContextJson),
    parseJsonRecord(item.interactionSchemaJson),
  ].filter((record): record is Record<string, unknown> => Boolean(record))

  for (const record of records) {
    const scope = record.always ?? record.patterns
    if (typeof scope === 'string' && scope.trim()) return scope.trim()
  }
  return null
}

function permissionQuestion(item: ConfirmationRequestDto, schema: InteractionSchema | null): string | null {
  if (item.requestType !== 'PERMISSION') return null
  const targetPath = permissionTargetPath(item, schema)
  if (!targetPath) return null
  return `允许 Agent ${permissionActionLabel(item, schema)}：${targetPath}？`
}

function interactionQuestion(item: ConfirmationRequestDto): string {
  const schema = schemaFor(item)
  const permissionCopy = permissionQuestion(item, schema)
  if (permissionCopy) return permissionCopy

  const candidates = [
    item.interactionSchemaJson ? schema?.question : null,
    item.title,
    item.content,
    item.contextSummary,
  ]

  for (const candidate of candidates) {
    const cleaned = removeKnownContext(candidate?.trim() ?? '', item)
    if (cleaned) return cleaned
  }

  return '请处理当前待交互项'
}

function interactionTabTitle(item: ConfirmationRequestDto): string {
  return `问题 ${interactionIndex(item)}`
}

function activeTabTitle(item: ConfirmationRequestDto): string {
  const schema = schemaFor(item)
  return schema?.title?.trim() || item.title?.trim() || interactionTabTitle(item)
}

function fieldQuestion(field: InteractionField): string | null {
  const placeholder = field.placeholder?.trim()
  if (!placeholder || placeholder === field.label.trim()) return null
  return placeholder
}

function fieldPlaceholder(field: InteractionField): string {
  return field.type === 'textarea' ? '输入你的回答...' : '输入答案...'
}

function primaryActionLabel(item: ConfirmationRequestDto): string {
  if (item.requestType === 'INPUT_REQUIRED') return '提交补充'
  if (item.requestType === 'DECISION') return '提交选择'
  if ((item.requestType === 'APPROVAL' || item.requestType === 'CONFIRM') && (schemaFor(item)?.options?.length ?? 0) > 0) return '提交选择'
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

function previewValueFor(item: ConfirmationRequestDto): string {
  const schema = schemaFor(item)
  if (item.requestType === 'PERMISSION') return '待选择：允许一次 / 本次会话允许同类请求 / 拒绝'
  if (item.requestType === 'INPUT_REQUIRED') {
    const fields = schema?.fields ?? []
    if (fields.length) {
      const values = fieldValues.value[item.id] ?? {}
      const answered = fields
        .map(field => values[field.id]?.trim())
        .filter((value): value is string => Boolean(value))
      return answered.length ? answered.join('；') : '尚未填写'
    }
    return inputValues.value[item.id]?.trim() || '尚未填写'
  }
  if (item.requestType === 'DECISION') {
    const options = schema?.options ?? []
    if (schema?.selection === 'multi') {
      const chosen = Array.from(selectedChoices.value[item.id] ?? [])
      const labels = chosen.map(id => options.find(option => option.id === id)?.label ?? id)
      const custom = customInput.value[item.id]?.trim()
      const values = custom ? [...labels, custom] : labels
      return values.length ? values.join('，') : '尚未选择'
    }
    const custom = customInput.value[item.id]?.trim()
    if (schema?.allowCustom && custom) return custom
    const choice = selectedOptions.value[item.id] ?? options[0]?.id ?? ''
    const selected = options.find(option => option.id === choice || option.label === choice)
    return selected?.label ?? (choice || inputValues.value[item.id]?.trim() || '尚未选择')
  }
  if ((item.requestType === 'APPROVAL' || item.requestType === 'CONFIRM') && (schema?.options?.length ?? 0) > 0) {
    const options = schema?.options ?? []
    const choice = selectedOptions.value[item.id] ?? options[0]?.id ?? ''
    const selected = options.find(option => option.id === choice || option.label === choice)
    return selected?.label ?? (choice || '尚未选择')
  }
  return '待确认'
}

function permissionBusyLabel(reply: 'once' | 'always' | 'reject'): string {
  if (reply === 'always') return '正在允许本次会话...'
  if (reply === 'reject') return '正在拒绝...'
  return '正在允许一次...'
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function approvalActionForChoice(option?: InteractionOption, fallback = ''): ConfirmationActionType {
  const text = `${option?.id ?? ''} ${option?.label ?? ''} ${fallback}`.toLowerCase()
  if (/(reject|revise|return|fail|no|denied|不通过|退回|驳回|调整|拒绝|否)/.test(text)) {
    return 'REJECT'
  }
  return 'APPROVE'
}

async function resolveActive(actionType: ConfirmationActionType, payload?: Record<string, unknown>, comment?: string) {
  if (!activeInteraction.value || busyAction.value) return
  const interaction = activeInteraction.value
  busyAction.value = `${interaction.id}:${actionType}`
  emit('submitting', interaction.id)
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

async function handlePermissionDecision(reply: 'once' | 'always' | 'reject') {
  const interaction = activeInteraction.value
  if (!interaction || busyAction.value) return
  const actionType: ConfirmationActionType = reply === 'reject' ? 'REJECT' : 'APPROVE'
  busyAction.value = `${interaction.id}:PERMISSION:${reply}`
  emit('submitting', interaction.id)
  try {
    await confirmationStore.resolveConfirmation(interaction.id, {
      actionType,
      payload: { reply },
      comment: reply,
    })
    notificationStore.push({
      anchor: 'right-panel',
      tone: reply === 'reject' ? 'warning' : 'success',
      title: reply === 'reject' ? '权限已拒绝' : '权限已允许',
      message: reply === 'always' ? 'OpenCode 本次会话内的同类请求会自动允许' : `${interaction.title} 已收到处理结果`,
    })
    if (reply === 'reject') emit('rejected', interaction.id)
    else emit('resolved', interaction.id)
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
  if (interaction.requestType === 'PERMISSION') {
    void handlePermissionDecision('once')
    return
  }
  if (interaction.requestType === 'INPUT_REQUIRED') {
    if (activeFields.value.length > 0) {
      const vals = fieldValues.value[interaction.id] ?? {}
      void resolveActive('SUPPLEMENT', { input: Object.values(vals).join('\n'), fields: vals })
      return
    }
    const input = activeInput.value.trim()
    if (!input) return
    void resolveActive('SUPPLEMENT', { input }, input)
    return
  }
  if (interaction.requestType === 'DECISION') {
    if (isMultiSelect.value) {
      const chosen = selectedChoices.value[interaction.id]
      const selectedIds = Array.from(chosen ?? [])
      const selectedLabels = selectedIds.map(id => activeOptions.value.find(o => o.id === id)?.label ?? id)
      const payload: Record<string, unknown> = {
        choiceIds: selectedIds,
        choiceLabels: selectedLabels,
        choices: selectedLabels,
      }
      const customVal = customInput.value[interaction.id]?.trim()
      if (allowCustomChoice.value && customVal) {
        payload.customChoice = customVal
        payload.choices = [...selectedLabels, customVal]
      }
      const firstSelectedId = selectedIds[0]
      const isWorkflowAdvance = interaction.interactionType === 'WORKFLOW_ADVANCE'
      const VALID_ACTION_TYPES: ConfirmationActionType[] = ['ENTER_SESSION', 'APPROVE', 'REJECT', 'SUPPLEMENT', 'CHOOSE', 'RETRY', 'SKIP', 'ADVANCE']
      const actionType: ConfirmationActionType = (isWorkflowAdvance && VALID_ACTION_TYPES.includes(firstSelectedId as ConfirmationActionType))
        ? firstSelectedId as ConfirmationActionType
        : 'CHOOSE'
      void resolveActive(actionType, payload, JSON.stringify(payload.choices))
      return
    }
    if (activeOption.value === '__custom__') {
      // Custom input selected — use custom input value instead of preset option
      const customVal = customInput.value[interaction.id]?.trim()
      if (!customVal) return
      const payload: Record<string, unknown> = { choice: customVal, customChoice: customVal }
      void resolveActive('CHOOSE', payload, customVal)
      return
    }
    const choice = activeOptions.value.length > 0 ? activeOption.value : activeInput.value.trim()
    if (!choice) return
    const selectedOpt = activeOptions.value.find(o => o.id === choice || o.label === choice)
    const payload: Record<string, unknown> = { choice }
    if (selectedOpt) {
      payload.choiceId = selectedOpt.id
      payload.choiceLabel = selectedOpt.label
    }
    const selectedOptionId = selectedOpt?.id || choice
    const isWorkflowAdvance = interaction.interactionType === 'WORKFLOW_ADVANCE'
    const VALID_ACTION_TYPES: ConfirmationActionType[] = ['ENTER_SESSION', 'APPROVE', 'REJECT', 'SUPPLEMENT', 'CHOOSE', 'RETRY', 'SKIP', 'ADVANCE']
    const actionType: ConfirmationActionType = (isWorkflowAdvance && VALID_ACTION_TYPES.includes(selectedOptionId as ConfirmationActionType))
      ? selectedOptionId as ConfirmationActionType
      : 'CHOOSE'
    void resolveActive(actionType, payload, selectedOpt?.label ?? choice)
    return
  }
  if ((interaction.requestType === 'APPROVAL' || interaction.requestType === 'CONFIRM') && activeOptions.value.length > 0) {
    if (activeOption.value === '__custom__') {
      const customVal = customInput.value[interaction.id]?.trim()
      if (!customVal) return
      const payload: Record<string, unknown> = { choice: customVal, customChoice: customVal }
      void resolveActive(approvalActionForChoice(undefined, customVal), payload, customVal)
      return
    }
    const choice = activeOption.value
    const selectedOpt = activeOptions.value.find(o => o.id === choice || o.label === choice)
    if (!choice || !selectedOpt) return
    const payload: Record<string, unknown> = {
      choice,
      choiceId: selectedOpt.id,
      choiceLabel: selectedOpt.label,
    }
    void resolveActive(approvalActionForChoice(selectedOpt), payload, selectedOpt.label)
    return
  }
  if (interaction.requestType === 'EXCEPTION') {
    const input = activeInput.value.trim()
    if (input) {
      void resolveActive('SUPPLEMENT', { input }, input)
      return
    }
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
  if (interaction.requestType === 'PERMISSION') {
    void handlePermissionDecision('reject')
    return
  }
  busyAction.value = `${interaction.id}:REJECT`
  emit('submitting', interaction.id)
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
  <section v-if="visibleInteractions.length" class="interaction-bar">
    <div v-if="visibleInteractions.length > 1" class="interaction-bar__header">
      <div class="interaction-bar__tabs" role="tablist" aria-label="当前交互列表">
        <button
          v-for="item in visibleInteractions"
          :key="item.id"
          type="button"
          class="interaction-bar__tab"
          :class="{ 'interaction-bar__tab--active': item.id === activeInteraction?.id }"
          @click="activeId = item.id"
        >
          <span>{{ interactionTabTitle(item) }}</span>
        </button>
        <button
          type="button"
          class="interaction-bar__tab"
          :class="{ 'interaction-bar__tab--active': reviewMode }"
          @click="activeId = REVIEW_TAB_ID"
        >
          <span>确认</span>
        </button>
      </div>
    </div>

    <div v-if="reviewMode" class="interaction-bar__body">
      <div class="interaction-bar__review">
        <div class="interaction-bar__copy">
          <span class="interaction-bar__type">预览</span>
          <strong>请确认本轮交互的待提交选择。</strong>
        </div>
        <div class="interaction-bar__review-list">
          <button
            v-for="item in visibleInteractions"
            :key="item.id"
            type="button"
            class="interaction-bar__review-item"
            @click="activeId = item.id"
          >
            <span>{{ interactionTabTitle(item) }}</span>
            <strong>{{ activeTabTitle(item) }}</strong>
            <em>{{ previewValueFor(item) }}</em>
          </button>
        </div>
        <div class="interaction-bar__actions">
          <button type="button" class="interaction-bar__ghost" @click="activeId = visibleInteractions[0]?.id ?? null">
            返回修改
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="activeInteraction" class="interaction-bar__body">
      <div class="interaction-bar__main">
        <div class="interaction-bar__copy">
          <span class="interaction-bar__type">{{ typeLabels[activeInteraction.requestType] }}</span>
          <strong>{{ interactionQuestion(activeInteraction) }}</strong>
        </div>

        <div v-if="activeUsesOptionControl" class="interaction-bar__control interaction-bar__control--options">
          <template v-if="activeOptions.length">
            <button
              v-for="(option, optionIndex) in activeOptions"
              :key="option.id"
              type="button"
              class="interaction-bar__option"
              :class="{
                'interaction-bar__option--selected': isMultiSelect
                  ? (selectedChoices[activeInteraction.id]?.has(option.id) ?? false)
                  : activeOption === option.id
              }"
              @click="isMultiSelect ? toggleChoice(option.id) : (activeOption = option.id)"
            >
              <span class="interaction-bar__option-index">{{ optionIndex + 1 }}</span>
              <span class="interaction-bar__option-copy">
                <strong>{{ option.label }}</strong>
                <span v-if="option.description" class="interaction-bar__option-desc">{{ option.description }}</span>
              </span>
            </button>
          </template>
          <label v-if="allowCustomChoice" class="interaction-bar__custom-choice">
            <span>自定义选择</span>
            <input
              :value="customInput[activeInteraction.id] ?? ''"
              class="interaction-bar__input"
              type="text"
              placeholder="自定义输入..."
              @input="(e: Event) => { if (activeInteraction) { customInput = { ...customInput, [activeInteraction.id]: (e.target as HTMLInputElement).value } } }"
            >
          </label>
          <input
            v-if="!activeOptions.length && !allowCustomChoice"
            v-model="activeInput"
            class="interaction-bar__input"
            type="text"
            placeholder="输入你的选择..."
          >
        </div>

        <div v-else-if="activeInteraction.requestType === 'INPUT_REQUIRED'" class="interaction-bar__control">
          <template v-if="activeFields.length > 0">
            <div class="interaction-bar__fields">
              <div v-for="field in activeFields" :key="field.id" class="interaction-bar__field">
                <label :for="'field-' + field.id" class="interaction-bar__field-label">
                  {{ field.label }}
                  <span v-if="field.required" class="interaction-bar__field-required">*</span>
                </label>
                <p v-if="fieldQuestion(field)" class="interaction-bar__field-question">
                  {{ fieldQuestion(field) }}
                </p>
                <textarea
                  v-if="field.type === 'textarea'"
                  :id="'field-' + field.id"
                  :value="activeFieldValues[field.id] ?? ''"
                  class="interaction-bar__textarea interaction-bar__field-input"
                  :placeholder="fieldPlaceholder(field)"
                  rows="2"
                  @input="(e: Event) => setFieldValue(field.id, (e.target as HTMLTextAreaElement).value)"
                ></textarea>
                <input
                  v-else
                  :id="'field-' + field.id"
                  :value="activeFieldValues[field.id] ?? ''"
                  class="interaction-bar__input interaction-bar__field-input"
                  :type="field.type === 'number' ? 'number' : 'text'"
                  :placeholder="fieldPlaceholder(field)"
                  @input="(e: Event) => setFieldValue(field.id, (e.target as HTMLInputElement).value)"
                >
              </div>
            </div>
          </template>
          <textarea
            v-else
            v-model="activeInput"
            class="interaction-bar__textarea"
            placeholder="输入你的补充要求..."
            rows="3"
          ></textarea>
        </div>

        <div v-else-if="activeInteraction.requestType === 'PERMISSION'" class="interaction-bar__hint interaction-bar__permission-hint">
          <span>这是 OpenCode 原生工具授权，处理后才会继续执行当前工具。</span>
          <span v-if="permissionScopeText(activeInteraction)">同类请求范围：{{ permissionScopeText(activeInteraction) }}</span>
        </div>

        <div v-else-if="activeInteraction.requestType === 'APPROVAL' || activeInteraction.requestType === 'CONFIRM'" class="interaction-bar__hint">
          审批当前节点产物或结论。
        </div>

        <div v-else-if="activeInteraction.requestType === 'EXCEPTION'" class="interaction-bar__control">
          <textarea
            v-model="activeInput"
            class="interaction-bar__textarea"
            placeholder="补充异常处理信息后继续当前节点..."
            rows="3"
          ></textarea>
        </div>

        <div v-else class="interaction-bar__hint">
          处理后 Agent 会继续推进当前节点。
        </div>

        <div class="interaction-bar__actions" :class="{ 'interaction-bar__actions--permission': activeInteraction.requestType === 'PERMISSION' }">
          <template v-if="activeInteraction.requestType === 'PERMISSION'">
            <button type="button" class="interaction-bar__primary" :disabled="!!busyAction" @click="handlePermissionDecision('once')">
              {{ busyAction?.includes(':once') ? permissionBusyLabel('once') : '允许一次' }}
            </button>
            <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="handlePermissionDecision('always')">
              {{ busyAction?.includes(':always') ? permissionBusyLabel('always') : '本次会话允许同类请求' }}
            </button>
            <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="handlePermissionDecision('reject')">
              {{ busyAction?.includes(':reject') ? permissionBusyLabel('reject') : '拒绝' }}
            </button>
            <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="emit('open', activeInteraction.id)">
              详情
            </button>
          </template>
          <template v-else>
            <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="emit('open', activeInteraction.id)">
              详情
            </button>
            <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="handleSecondary">
              {{ secondaryActionLabel(activeInteraction) }}
            </button>
            <button
              type="button"
              class="interaction-bar__primary"
              :disabled="!!busyAction || (activeInteraction.requestType === 'INPUT_REQUIRED' && !canSubmitInput) || (activeUsesOptionControl && !canSubmitChoice)"
              @click="handlePrimary"
            >
              {{ busyAction ? '提交中...' : (activeInteraction.requestType === 'EXCEPTION' && activeInput.trim() ? '提交补充' : primaryActionLabel(activeInteraction)) }}
            </button>
          </template>
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
  min-height: 40px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: color-mix(in srgb, var(--brand-soft) 55%, var(--bg-card));
  color: var(--text-primary);
}

.interaction-bar__title,
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

.interaction-bar__body {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.interaction-bar__header + .interaction-bar__body {
  border-top: 1px solid var(--border-color);
}

.interaction-bar__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.interaction-bar__tabs::-webkit-scrollbar {
  display: none;
}

.interaction-bar__tab {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--surface-overlay);
  color: var(--text-secondary);
  text-align: center;
  cursor: pointer;
}

.interaction-bar__tab span {
  font-size: 12px;
  font-weight: 900;
  color: inherit;
  white-space: nowrap;
}

.interaction-bar__tab--active {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
}

.interaction-bar__main {
  display: grid;
  gap: 12px;
}

.interaction-bar__copy {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.interaction-bar__type {
  flex: 0 0 auto;
  width: max-content;
  padding: 3px 8px;
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
  font-size: 14px;
  font-weight: 900;
  line-height: 1.45;
}

.interaction-bar__copy p,
.interaction-bar__hint,
.interaction-bar__context,
.interaction-bar__question {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.interaction-bar__permission-hint {
  display: grid;
  gap: 4px;
}

.interaction-bar__context {
  color: var(--text-muted);
  font-weight: 400;
}

.interaction-bar__question {
  margin-bottom: 6px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

.interaction-bar__control {
  display: flex;
  gap: 8px;
}

.interaction-bar__control--options {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: flex-start;
  gap: 8px;
  min-height: 58px;
  padding: 10px;
  border: 1px solid var(--border-color);
  background: var(--surface-overlay);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.interaction-bar__option:hover {
  border-color: var(--brand-border);
  background: var(--surface-hover);
}

.interaction-bar__option-index {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--bg-primary);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 900;
}

.interaction-bar__option-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.interaction-bar__option strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__option-desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 400;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.interaction-bar__option--selected {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
  color: var(--accent-blue);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent-blue) 35%, transparent);
}

.interaction-bar__option--selected .interaction-bar__option-index {
  background: var(--accent-blue);
  color: var(--on-brand);
}

.interaction-bar__custom-choice {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.interaction-bar__review {
  display: grid;
  gap: 12px;
}

.interaction-bar__review-list {
  display: grid;
  gap: 8px;
}

.interaction-bar__review-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 10px;
  align-items: center;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--surface-overlay);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
}

.interaction-bar__review-item:hover {
  border-color: var(--brand-border);
  background: var(--surface-hover);
}

.interaction-bar__review-item > span {
  grid-row: span 2;
  align-self: start;
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 900;
}

.interaction-bar__review-item > strong,
.interaction-bar__review-item > em {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__review-item > strong {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
}

.interaction-bar__review-item > em {
  color: var(--text-muted);
  font-size: 12px;
  font-style: normal;
  font-weight: 650;
}

.interaction-bar__custom-choice > span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
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

.interaction-bar__textarea {
  flex: 1;
  min-width: 180px;
  min-height: 60px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 12px;
  resize: vertical;
  outline: none;
}

.interaction-bar__textarea:focus {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.interaction-bar__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 2px;
}

.interaction-bar__actions--permission {
  flex-direction: column;
  align-items: stretch;
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

.interaction-bar__fields {
  display: grid;
  gap: 8px;
  width: 100%;
}

.interaction-bar__field {
  display: grid;
  gap: 3px;
}

.interaction-bar__field-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.interaction-bar__field-question {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-primary);
}

.interaction-bar__field-required {
  color: var(--warning);
}

.interaction-bar__field-input {
  width: 100%;
}

@media (max-width: 760px) {
  .interaction-bar__body {
    grid-template-columns: 1fr;
  }

  .interaction-bar__actions {
    justify-content: stretch;
  }

  .interaction-bar__actions > button {
    flex: 1;
  }
}
</style>
