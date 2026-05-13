<script setup lang="ts">
import { computed, ref } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useNotificationStore } from '../../stores/notifications'
import { parseInteractionSchema, type InteractionField, type InteractionOption } from '../conversation/interactions/interactionSchema'
import type {
  ConfirmationActionType,
  ConfirmationRequestDto,
  ConfirmationRequestType,
  WorkItemDto,
} from '../../api/types'

const props = defineProps<{
  confirmation: ConfirmationRequestDto
  workItem?: WorkItemDto | null
}>()

const emit = defineEmits<{
  handle: [id: string]
  resolved: [id: string]
  rejected: [id: string]
}>()

const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const busyAction = ref<'approve' | 'reject' | 'submit' | 'retry' | 'skip' | 'permission-once' | 'permission-always' | null>(null)
const modalOpen = ref(false)
const selectedOption = ref<InteractionOption | null>(null)
const selectedChoiceIds = ref<Set<string>>(new Set())
const customChoice = ref('')
const reviewComment = ref('')
const supplementText = ref('')
const fieldValues = ref<Record<string, string>>({})
const recovering = ref(false)

const typeLabels: Record<ConfirmationRequestType, string> = {
  CONFIRM: '确认',
  APPROVAL: '审批',
  INPUT_REQUIRED: '输入',
  DECISION: '决策',
  EXCEPTION: '异常',
  PERMISSION: '权限',
}

const typeColors: Record<ConfirmationRequestType, string> = {
  CONFIRM: '#3b82f6',
  APPROVAL: '#8b5cf6',
  INPUT_REQUIRED: '#f59e0b',
  DECISION: '#10b981',
  EXCEPTION: '#ef4444',
  PERMISSION: '#64748b',
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const hour = d.getHours().toString().padStart(2, '0')
  const minute = d.getMinutes().toString().padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const workItemCode = computed(() => props.confirmation.workItemCode ?? props.workItem?.code ?? '未关联')
const workItemType = computed(() => props.confirmation.workItemType ?? props.workItem?.type ?? '事项')
const workItemTitle = computed(() => props.confirmation.workItemTitle ?? props.workItem?.title ?? '未关联工作项')
const workflowNodeName = computed(() => props.confirmation.workflowNodeName ?? props.confirmation.title)
const parsedOptions = computed<InteractionOption[]>(() => interactionSchema.value?.options ?? [])
const isDecision = computed(() => props.confirmation.requestType === 'DECISION')
const isInputRequired = computed(() => props.confirmation.requestType === 'INPUT_REQUIRED')
const isException = computed(() => props.confirmation.requestType === 'EXCEPTION')
const isPermission = computed(() => props.confirmation.requestType === 'PERMISSION')
const interactionSchema = computed(() => parseInteractionSchema(props.confirmation))
const isMultiSelect = computed(() => interactionSchema.value?.selection === 'multi')
const allowCustomChoice = computed(() => interactionSchema.value?.allowCustom === true)
const isArtifactReview = computed(() => props.confirmation.interactionType === 'ARTIFACT_REVIEW')
const usesOptionControl = computed(() => {
  if (isDecision.value) return true
  return (props.confirmation.requestType === 'APPROVAL' || props.confirmation.requestType === 'CONFIRM')
    && parsedOptions.value.length > 0
})
const inputFields = computed<InteractionField[]>(() => interactionSchema.value?.fields ?? [])
const interactionQuestion = computed(() => interactionSchema.value?.question?.trim() ?? '')
const interactionTitle = computed(() => interactionSchema.value?.title?.trim() || '补充信息')
const choiceInteractionTitle = computed(() => {
  if (isArtifactReview.value) return '审阅产物'
  if (isDecision.value) return '选择处理路径'
  return '选择处理结果'
})
const canSubmitChoice = computed(() => {
  if (!usesOptionControl.value) return true
  const hasCustom = allowCustomChoice.value && customChoice.value.trim().length > 0
  if (parsedOptions.value.length === 0) return hasCustom || supplementText.value.trim().length > 0
  if (isMultiSelect.value) return selectedChoiceIds.value.size > 0 || hasCustom
  return !!selectedOption.value || hasCustom
})
const canSubmitInput = computed(() => {
  if (inputFields.value.length > 0) {
    return inputFields.value.every(field => isFieldComplete(field))
  }
  return !!supplementText.value.trim()
})
const rejectLabel = computed(() => {
  if (props.confirmation.requestType === 'PERMISSION') return '拒绝授权'
  if (props.confirmation.requestType === 'APPROVAL') return '退回'
  if (props.confirmation.requestType === 'DECISION') return '暂不选择'
  return '拒绝'
})

function openDialog() {
  selectedOption.value = parsedOptions.value[0] ?? null
  selectedChoiceIds.value = new Set()
  customChoice.value = ''
  reviewComment.value = ''
  supplementText.value = ''
  fieldValues.value = {}
  modalOpen.value = true
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function setFieldValue(fieldId: string, value: string) {
  fieldValues.value = { ...fieldValues.value, [fieldId]: value }
}

function fieldInputId(field: InteractionField): string {
  return `confirmation-field-${field.id}`
}

function fieldQuestion(field: InteractionField): string | null {
  const placeholder = field.placeholder?.trim()
  if (!placeholder || placeholder === field.label.trim()) return null
  return placeholder
}

function fieldPlaceholder(field: InteractionField): string {
  return field.type === 'textarea' ? '输入你的回答...' : '输入答案...'
}

function fieldRawValue(field: InteractionField): string {
  if (field.type === 'checkbox') return fieldValues.value[field.id] === 'true' ? 'true' : 'false'
  return fieldValues.value[field.id]?.trim() ?? ''
}

function fieldDisplayValue(field: InteractionField, value: string): string {
  if (field.type === 'checkbox') return value === 'true' ? '是' : (field.required ? '否' : '')
  if (field.type === 'select') return field.options?.find(option => option.value === value)?.label ?? value
  return value
}

function isFieldComplete(field: InteractionField): boolean {
  if (!field.required) return true
  if (field.type === 'checkbox') return fieldRawValue(field) === 'true'
  return fieldRawValue(field).length > 0
}

function collectFieldPayload() {
  const fields = inputFields.value.reduce<Record<string, string>>((acc, field) => {
    acc[field.id] = fieldRawValue(field)
    return acc
  }, {})
  const input = inputFields.value
    .map(field => fieldDisplayValue(field, fields[field.id]))
    .filter(Boolean)
    .join('\n')
  return { input, fields }
}

function isChoiceSelected(option: InteractionOption): boolean {
  return isMultiSelect.value ? selectedChoiceIds.value.has(option.id) : selectedOption.value?.id === option.id
}

function toggleChoice(optionId: string) {
  const next = new Set(selectedChoiceIds.value)
  if (next.has(optionId)) next.delete(optionId)
  else next.add(optionId)
  selectedChoiceIds.value = next
}

function approvalActionForChoice(option?: InteractionOption, fallback = ''): ConfirmationActionType {
  const text = `${option?.id ?? ''} ${option?.label ?? ''} ${fallback}`.toLowerCase()
  if (/(reject|revise|return|fail|no|denied|不通过|退回|驳回|调整|拒绝|否)/.test(text)) {
    return 'REJECT'
  }
  return 'APPROVE'
}

function workflowActionForChoice(optionId: string | undefined): ConfirmationActionType | null {
  if (props.confirmation.interactionType !== 'WORKFLOW_ADVANCE' || !optionId) return null
  const validActions: ConfirmationActionType[] = ['ENTER_SESSION', 'APPROVE', 'REJECT', 'SUPPLEMENT', 'CHOOSE', 'RETRY', 'SKIP', 'ADVANCE']
  return validActions.includes(optionId as ConfirmationActionType) ? optionId as ConfirmationActionType : null
}

function choiceActionType(option?: InteractionOption, fallback = ''): ConfirmationActionType {
  const workflowAction = workflowActionForChoice(option?.id)
  if (workflowAction) return workflowAction
  if (isDecision.value) return 'CHOOSE'
  return approvalActionForChoice(option, fallback)
}

function withReviewComment(payload: Record<string, unknown>) {
  const remark = reviewComment.value.trim()
  if (remark) payload.remark = remark
  return payload
}

function choiceComment(label: string): string {
  const remark = reviewComment.value.trim()
  return remark ? `${label}\n${remark}` : label
}

async function handleApprove() {
  if (busyAction.value) return
  busyAction.value = 'approve'
  try {
    await confirmationStore.resolveConfirmation(props.confirmation.id, { actionType: 'APPROVE' }, { remove: false })
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'success',
      title: '确认已通过',
      message: `${workItemCode.value} 已进入后续流程`,
    })
    confirmationStore.markRecovering(props.confirmation.id)
    recovering.value = true
    emit('resolved', props.confirmation.id)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '通过失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function resolveWith(actionType: ConfirmationActionType, payload?: Record<string, unknown>, comment?: string) {
  await confirmationStore.resolveConfirmation(props.confirmation.id, { actionType, payload, comment }, { remove: false })
  notificationStore.push({
    anchor: 'right-panel',
    tone: 'success',
    title: '交互已提交',
    message: `${workItemCode.value} 已收到你的处理结果`,
  })
  confirmationStore.markRecovering(props.confirmation.id)
  recovering.value = true
  emit('resolved', props.confirmation.id)
}

async function handleChoice() {
  if (busyAction.value || !canSubmitChoice.value) return
  busyAction.value = 'submit'
  try {
    const custom = customChoice.value.trim()
    if (isMultiSelect.value) {
      const selected = parsedOptions.value.filter(option => selectedChoiceIds.value.has(option.id))
      const choiceIds = selected.map(option => option.id)
      const choiceLabels = selected.map(option => option.label)
      const choices = custom ? [...choiceLabels, custom] : choiceLabels
      const payload: Record<string, unknown> = {
        choiceIds,
        choiceLabels,
        choices,
      }
      if (custom) payload.customChoice = custom
      const label = choices.join('，')
      await resolveWith(choiceActionType(selected[0], custom), withReviewComment(payload), choiceComment(label))
      return
    }

    if (custom) {
      const payload = withReviewComment({ choice: custom, customChoice: custom })
      await resolveWith(choiceActionType(undefined, custom), payload, choiceComment(custom))
      return
    }

    const opt = parsedOptions.value.length > 0 ? selectedOption.value : null
    if (opt) {
      const payload = withReviewComment({ choice: opt.id, choiceId: opt.id, choiceLabel: opt.label })
      await resolveWith(choiceActionType(opt), payload, choiceComment(opt.label))
      return
    }

    const choice = supplementText.value.trim()
    await resolveWith(choiceActionType(undefined, choice), { choice }, choice)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '提交选择失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function handleSupplement() {
  if (busyAction.value || !canSubmitInput.value) return
  busyAction.value = 'submit'
  try {
    if (inputFields.value.length > 0) {
      const { input, fields } = collectFieldPayload()
      await resolveWith('SUPPLEMENT', { input, fields }, input)
      return
    }
    const input = supplementText.value.trim()
    await resolveWith('SUPPLEMENT', { input }, input)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '提交补充失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function handleRetry() {
  if (busyAction.value) return
  busyAction.value = 'retry'
  try {
    await resolveWith('RETRY')
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '重试失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function handleSkip() {
  if (busyAction.value) return
  busyAction.value = 'skip'
  try {
    await resolveWith('SKIP')
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '跳过失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

async function handleReject() {
  if (busyAction.value) return
  busyAction.value = 'reject'
  try {
    await confirmationStore.resolveConfirmation(props.confirmation.id, { actionType: 'REJECT' }, { remove: false })
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'warning',
      title: '已拒绝确认',
      message: `${workItemCode.value} 已暂停推进`,
    })
    emit('rejected', props.confirmation.id)
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '拒绝失败',
      message: errorMessage(error),
      durationMs: 5200,
    })
  } finally {
    busyAction.value = null
  }
}

function enterSession() {
  modalOpen.value = false
  emit('handle', props.confirmation.id)
}

async function handlePermissionDecision(reply: 'once' | 'always' | 'reject') {
  if (busyAction.value) return
  busyAction.value = reply === 'reject' ? 'reject' : `permission-${reply}`
  const actionType: ConfirmationActionType = reply === 'reject' ? 'REJECT' : 'APPROVE'
  try {
    await confirmationStore.resolveConfirmation(props.confirmation.id, {
      actionType,
      payload: { reply },
      comment: reply,
    }, { remove: false })
    notificationStore.push({
      anchor: 'right-panel',
      tone: reply === 'reject' ? 'warning' : 'success',
      title: reply === 'reject' ? '权限已拒绝' : '权限已允许',
      message: reply === 'always' ? 'OpenCode 本次会话内的同类请求会自动允许' : `${workItemCode.value} 已收到处理结果`,
    })
    if (reply !== 'reject') {
      confirmationStore.markRecovering(props.confirmation.id)
      recovering.value = true
    }
    if (reply === 'reject') emit('rejected', props.confirmation.id)
    else emit('resolved', props.confirmation.id)
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
</script>

<template>
  <div class="confirmation-card">
    <div class="confirmation-card__header">
      <div class="confirmation-card__badges">
        <span
          class="confirmation-card__type"
          :style="{
            backgroundColor: typeColors[confirmation.requestType] + '18',
            color: typeColors[confirmation.requestType]
          }"
        >
          {{ typeLabels[confirmation.requestType] }}
        </span>
        <span
          v-if="confirmation.status === 'IN_CONVERSATION'"
          class="confirmation-card__status confirmation-card__status--in-conversation"
        >
          处理中
        </span>
        <span
          v-else-if="confirmation.status === 'PENDING'"
          class="confirmation-card__status confirmation-card__status--pending"
        >
          待处理
        </span>
      </div>
      <span class="confirmation-card__time">{{ formatTime(confirmation.createdAt) }}</span>
    </div>
    <div class="confirmation-card__work-item">
      <span>{{ workItemCode }}</span>
      <em>{{ workItemType }}</em>
    </div>
    <div class="confirmation-card__title">{{ workItemTitle }}</div>
    <div class="confirmation-card__node">待确认：{{ workflowNodeName }}</div>
    <div class="confirmation-card__actions">
      <button class="confirmation-card__action" :disabled="!!busyAction" @click="openDialog">
        处理
      </button>
    </div>
  </div>

  <Teleport to="body">
    <div v-if="modalOpen" class="confirmation-dialog" role="dialog" aria-modal="true" aria-labelledby="confirmation-dialog-title">
      <button class="confirmation-dialog__scrim" aria-label="关闭确认详情" @click="modalOpen = false"></button>
      <section class="confirmation-dialog__panel">
        <header class="confirmation-dialog__header">
          <div>
            <span class="confirmation-dialog__eyebrow">{{ typeLabels[confirmation.requestType] }}</span>
            <h3 id="confirmation-dialog-title">{{ workItemTitle }}</h3>
          </div>
          <button class="confirmation-dialog__close" aria-label="关闭" @click="modalOpen = false">×</button>
        </header>

        <div class="confirmation-dialog__meta">
          <span>{{ workItemCode }}</span>
          <span>{{ workItemType }}</span>
          <span>{{ formatTime(confirmation.createdAt) }}</span>
        </div>

        <dl class="confirmation-dialog__details">
          <div>
            <dt>确认节点</dt>
            <dd>{{ workflowNodeName }}</dd>
          </div>
          <div v-if="confirmation.contextSummary">
            <dt>上下文</dt>
            <dd>{{ confirmation.contextSummary }}</dd>
          </div>
          <div v-if="confirmation.content">
            <dt>详情</dt>
            <dd class="confirmation-dialog__content">{{ confirmation.content }}</dd>
          </div>
          <div v-if="confirmation.skillName">
            <dt>Skill</dt>
            <dd>{{ confirmation.skillName }}</dd>
          </div>
        </dl>

        <section v-if="recovering" class="confirmation-dialog__interaction">
          <div class="confirmation-dialog__recovering">
            <span class="confirmation-dialog__recovering-dot"></span>
            <strong>已提交，等待 Agent 继续</strong>
          </div>
        </section>

        <section v-else-if="isPermission" class="confirmation-dialog__interaction">
          <div class="confirmation-dialog__interaction-title">权限授权</div>
          <p v-if="interactionQuestion" class="confirmation-dialog__question">{{ interactionQuestion }}</p>
          <div class="confirmation-dialog__permission-actions">
            <button class="confirmation-card__action confirmation-card__action--approve" :disabled="!!busyAction" @click="handlePermissionDecision('once')">
              {{ busyAction === 'permission-once' ? '允许中...' : '允许一次' }}
            </button>
            <button class="confirmation-card__action confirmation-card__action--approve" :disabled="!!busyAction" @click="handlePermissionDecision('always')">
              {{ busyAction === 'permission-always' ? '允许中...' : '本次会话允许同类请求' }}
            </button>
            <button class="confirmation-card__action confirmation-card__action--reject" :disabled="!!busyAction" @click="handlePermissionDecision('reject')">
              {{ busyAction === 'reject' ? '处理中...' : '拒绝授权' }}
            </button>
          </div>
        </section>

        <section v-else-if="usesOptionControl" class="confirmation-dialog__interaction">
          <div class="confirmation-dialog__interaction-title">{{ choiceInteractionTitle }}</div>
          <p v-if="interactionQuestion" class="confirmation-dialog__question">{{ interactionQuestion }}</p>
          <div v-if="parsedOptions.length" class="confirmation-dialog__options" :role="isMultiSelect ? 'group' : 'radiogroup'" aria-label="选择处理路径">
            <label
              v-for="option in parsedOptions"
              :key="option.id"
              class="confirmation-dialog__option"
              :class="{ 'confirmation-dialog__option--selected': isChoiceSelected(option) }"
            >
              <input
                :type="isMultiSelect ? 'checkbox' : 'radio'"
                name="confirmation-option"
                :value="option.id"
                :checked="isChoiceSelected(option)"
                @change="isMultiSelect ? toggleChoice(option.id) : (selectedOption = option)"
              >
              <span class="confirmation-dialog__option-copy">
                <strong>{{ option.label }}</strong>
                <span v-if="option.description" class="confirmation-dialog__option-desc">{{ option.description }}</span>
              </span>
            </label>
          </div>
          <label v-if="allowCustomChoice" class="confirmation-dialog__custom-choice">
            <span>自定义选择</span>
            <input
              v-model="customChoice"
              class="confirmation-dialog__input"
              type="text"
              placeholder="输入自定义处理方式..."
            >
          </label>
          <textarea
            v-if="!parsedOptions.length && !allowCustomChoice"
            v-model="supplementText"
            class="confirmation-dialog__textarea"
            rows="4"
            placeholder="输入你希望 Agent 采用的处理方式..."
          />
          <textarea
            v-if="isArtifactReview"
            v-model="reviewComment"
            class="confirmation-dialog__textarea confirmation-dialog__review-note"
            rows="3"
            placeholder="补充审阅备注..."
          />
        </section>

        <section v-else-if="isException" class="confirmation-dialog__interaction">
          <div class="confirmation-dialog__interaction-title">异常处理</div>
          <p v-if="confirmation.contextSummary" class="confirmation-dialog__question">{{ confirmation.contextSummary }}</p>
          <textarea
            v-model="supplementText"
            class="confirmation-dialog__textarea"
            rows="3"
            placeholder="补充异常处理信息后继续当前节点..."
          />
        </section>

        <section v-else-if="isInputRequired" class="confirmation-dialog__interaction">
          <div class="confirmation-dialog__interaction-title">{{ inputFields.length ? interactionTitle : '补充信息' }}</div>
          <p v-if="inputFields.length && interactionQuestion" class="confirmation-dialog__question">
            {{ interactionQuestion }}
          </p>
          <div v-if="inputFields.length" class="confirmation-dialog__fields">
            <div v-for="field in inputFields" :key="field.id" class="confirmation-dialog__field">
              <label :for="fieldInputId(field)" class="confirmation-dialog__field-label">
                {{ field.label }}
                <span v-if="field.required" class="confirmation-dialog__field-required">*</span>
              </label>
              <p v-if="fieldQuestion(field)" class="confirmation-dialog__field-question">
                {{ fieldQuestion(field) }}
              </p>
              <textarea
                v-if="field.type === 'textarea'"
                :id="fieldInputId(field)"
                :value="fieldValues[field.id] ?? ''"
                class="confirmation-dialog__textarea confirmation-dialog__field-input"
                rows="3"
                :placeholder="fieldPlaceholder(field)"
                @input="(event: Event) => setFieldValue(field.id, (event.target as HTMLTextAreaElement).value)"
              />
              <select
                v-else-if="field.type === 'select'"
                :id="fieldInputId(field)"
                :value="fieldValues[field.id] ?? ''"
                class="confirmation-dialog__input confirmation-dialog__field-input"
                @change="(event: Event) => setFieldValue(field.id, (event.target as HTMLSelectElement).value)"
              >
                <option value="">请选择...</option>
                <option
                  v-for="option in field.options ?? []"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
              <label
                v-else-if="field.type === 'checkbox'"
                :for="fieldInputId(field)"
                class="confirmation-dialog__checkbox"
              >
                <input
                  :id="fieldInputId(field)"
                  type="checkbox"
                  :checked="fieldValues[field.id] === 'true'"
                  @change="(event: Event) => setFieldValue(field.id, (event.target as HTMLInputElement).checked ? 'true' : 'false')"
                >
                <span>已确认</span>
              </label>
              <input
                v-else
                :id="fieldInputId(field)"
                :value="fieldValues[field.id] ?? ''"
                class="confirmation-dialog__input confirmation-dialog__field-input"
                :type="field.type === 'number' ? 'number' : 'text'"
                :placeholder="fieldPlaceholder(field)"
                @input="(event: Event) => setFieldValue(field.id, (event.target as HTMLInputElement).value)"
              >
            </div>
          </div>
          <textarea
            v-else
            v-model="supplementText"
            class="confirmation-dialog__textarea"
            rows="5"
            placeholder="补充 Agent 继续执行所需的信息..."
          />
        </section>

        <footer class="confirmation-dialog__actions">
          <button
            v-if="(confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION') && usesOptionControl"
            class="confirmation-card__action confirmation-card__action--approve"
            :disabled="!!busyAction || !canSubmitChoice"
            @click="handleChoice"
          >
            {{ busyAction === 'submit' ? '提交中...' : (isArtifactReview ? '提交审阅' : '提交选择') }}
          </button>
          <button
            v-else-if="(confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION') && isInputRequired"
            class="confirmation-card__action confirmation-card__action--approve"
            :disabled="!!busyAction || !canSubmitInput"
            @click="handleSupplement"
          >
            {{ busyAction === 'submit' ? '提交中...' : '提交补充' }}
          </button>
          <button
            v-else-if="(confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION') && isException"
            class="confirmation-card__action confirmation-card__action--approve"
            :disabled="!!busyAction"
            @click="supplementText.trim() ? handleSupplement() : handleRetry()"
          >
            {{ busyAction === 'submit' ? '提交中...' : (supplementText.trim() ? '提交补充' : '重试') }}
          </button>
          <button
            v-else-if="confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION'"
            class="confirmation-card__action confirmation-card__action--approve"
            :disabled="!!busyAction"
            @click="handleApprove"
          >
            {{ busyAction === 'approve' ? '推进中...' : '通过' }}
          </button>
          <button
            v-if="(confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION') && isException"
            class="confirmation-card__action confirmation-card__action--reject"
            :disabled="!!busyAction"
            @click="handleSkip"
          >
            {{ busyAction === 'skip' ? '处理中...' : '跳过' }}
          </button>
          <button
            v-else-if="confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION'"
            class="confirmation-card__action confirmation-card__action--reject"
            :disabled="!!busyAction"
            @click="handleReject"
          >
            {{ busyAction === 'reject' ? '处理中...' : rejectLabel }}
          </button>
          <button class="confirmation-card__action" :disabled="!!busyAction" @click="enterSession">
            {{ confirmation.status === 'IN_CONVERSATION' ? '已在会话中' : '进入会话' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.confirmation-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.confirmation-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.confirmation-card__badges {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confirmation-card__type {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.confirmation-card__status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.confirmation-card__status--pending {
  background-color: #fef3c7;
  color: #d97706;
}

.confirmation-card__status--in-conversation {
  background-color: #dbeafe;
  color: #2563eb;
}

.confirmation-card__time {
  font-size: 11px;
  color: var(--text-secondary);
}

.confirmation-card__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.4;
}

.confirmation-card__work-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.confirmation-card__work-item span {
  padding: 2px 8px;
  border-radius: 6px;
  background-color: var(--brand-soft);
  color: var(--brand-primary);
  font-size: 12px;
  font-weight: 700;
}

.confirmation-card__work-item em {
  font-style: normal;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.confirmation-card__node,
.confirmation-card__summary {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.confirmation-card__node {
  font-weight: 600;
}

.confirmation-card__content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  max-height: 60px;
  overflow: hidden;
}

.confirmation-card__skill {
  font-size: 11px;
  color: var(--text-secondary);
}

.confirmation-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
}

.confirmation-card__action {
  padding: 4px 14px;
  border: none;
  border-radius: 4px;
  background-color: var(--accent-blue);
  color: var(--on-brand);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.confirmation-card__action:disabled {
  cursor: wait;
  opacity: 0.65;
}

.confirmation-card__action:hover {
  opacity: 0.85;
}

.confirmation-card__action--approve {
  background-color: var(--success);
  color: var(--on-success);
}

.confirmation-card__action--reject {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.confirmation-card__action--reject:hover {
  background-color: var(--error-soft);
  color: var(--error);
  border-color: var(--error);
}

.confirmation-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
}

.confirmation-dialog__scrim {
  position: absolute;
  inset: 0;
  border: 0;
  background: color-mix(in srgb, var(--text-primary) 28%, transparent);
  cursor: pointer;
}

.confirmation-dialog__panel {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: min(560px, calc(100vw - 48px));
  max-height: min(720px, calc(100vh - 48px));
  padding: 18px;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  scrollbar-color: color-mix(in srgb, var(--brand-primary) 46%, var(--border-color)) transparent;
}

.confirmation-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.confirmation-dialog__eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--brand-soft);
  color: var(--brand-primary);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__header h3 {
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 900;
  line-height: 1.35;
}

.confirmation-dialog__close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 20px;
  cursor: pointer;
}

.confirmation-dialog__close:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.confirmation-dialog__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.confirmation-dialog__meta span {
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--surface-muted);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.confirmation-dialog__details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.confirmation-dialog__details div {
  display: grid;
  gap: 4px;
}

.confirmation-dialog__details dt {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__details dd {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.6;
  white-space: pre-wrap;
}

.confirmation-dialog__content {
  max-height: 240px;
  overflow: auto;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.confirmation-dialog__interaction {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.confirmation-dialog__interaction-title {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
}

.confirmation-dialog__question {
  margin: -2px 0 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.5;
}

.confirmation-dialog__options {
  display: grid;
  gap: 8px;
}

.confirmation-dialog__option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.confirmation-dialog__option--selected {
  border-color: var(--brand-primary);
  background: var(--brand-soft);
  color: var(--text-primary);
}

.confirmation-dialog__option input {
  flex: 0 0 auto;
}

.confirmation-dialog__option-copy {
  display: grid;
  gap: 3px;
  line-height: 1.35;
}

.confirmation-dialog__option-desc {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.confirmation-dialog__custom-choice {
  display: grid;
  gap: 6px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__fields {
  display: grid;
  gap: 12px;
}

.confirmation-dialog__field {
  display: grid;
  gap: 6px;
}

.confirmation-dialog__field-label {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__field-required {
  color: var(--error);
  margin-left: 2px;
}

.confirmation-dialog__field-question {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.confirmation-dialog__textarea {
  width: 100%;
  min-height: 112px;
  padding: 10px 12px;
  resize: vertical;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  outline: none;
  background: var(--bg-card);
  color: var(--text-primary);
  font: inherit;
  line-height: 1.6;
}

.confirmation-dialog__input {
  width: 100%;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  outline: none;
  background: var(--bg-card);
  color: var(--text-primary);
  font: inherit;
}

.confirmation-dialog__field-input {
  min-height: auto;
}

.confirmation-dialog__textarea:focus,
.confirmation-dialog__input:focus {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-primary) 16%, transparent);
}

.confirmation-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 2px;
}

.confirmation-dialog__permission-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.confirmation-dialog__checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.confirmation-dialog__checkbox input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-blue);
}

.confirmation-dialog__recovering {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confirmation-dialog__recovering-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent-blue);
  animation: pulse-dot 1.2s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
