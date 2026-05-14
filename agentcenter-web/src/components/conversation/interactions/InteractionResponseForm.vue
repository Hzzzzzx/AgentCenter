<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type {
  ConfirmationActionType,
  ConfirmationRequestDto,
} from '../../../api/types'
import type { InteractionField, InteractionOption, InteractionSchema } from './interactionSchema'
import {
  buildInputSubmission,
  buildMultiChoiceSubmission,
  buildSingleChoiceSubmission,
  fieldPlaceholder,
  fieldQuestion,
  isFieldComplete,
  type FieldValueMap,
  type InteractionFormSubmission,
} from './interactionSubmit'

type InteractionVariant = 'bar' | 'dialog'
type FieldSelectOption = NonNullable<InteractionField['options']>[number]

const props = withDefaults(defineProps<{
  confirmation: ConfirmationRequestDto
  schema: InteractionSchema | null
  variant: InteractionVariant
  typeLabel: string
  question: string
  busyAction: string | null
  recovering?: boolean
  inputText: string
  selectedOptionId: string
  selectedChoiceIds: string[]
  customChoice: string
  fieldValues: FieldValueMap
  reviewComment: string
  permissionScopeText?: string | null
  showDetailAction?: boolean
  showEnterSessionAction?: boolean
}>(), {
  recovering: false,
  permissionScopeText: null,
  showDetailAction: false,
  showEnterSessionAction: false,
})

const emit = defineEmits<{
  submit: [submission: InteractionFormSubmission]
  'update:inputText': [value: string]
  'update:selectedOptionId': [value: string]
  toggleChoice: [optionId: string]
  'update:customChoice': [value: string]
  'update:fieldValue': [fieldId: string, value: string]
  'update:reviewComment': [value: string]
  openDetail: []
  enterSession: []
}>()

const options = computed<InteractionOption[]>(() => props.schema?.options ?? [])
const fields = computed<InteractionField[]>(() => props.schema?.fields ?? [])
const activeFieldId = ref('')
const isMultiSelect = computed(() => props.schema?.selection === 'multi')
const allowCustomChoice = computed(() => props.schema?.allowCustom === true)
const customChoiceText = computed(() => props.customChoice.trim())
const selectedChoiceSet = computed(() => new Set(props.selectedChoiceIds))
const isPermission = computed(() => props.confirmation.requestType === 'PERMISSION')
const isException = computed(() => props.confirmation.requestType === 'EXCEPTION')
const isInputRequired = computed(() => props.confirmation.requestType === 'INPUT_REQUIRED')
const isArtifactReview = computed(() => props.schema?.interactionType === 'ARTIFACT_REVIEW')
const isActionable = computed(() =>
  props.confirmation.status === 'PENDING' || props.confirmation.status === 'IN_CONVERSATION'
)
const usesOptionControl = computed(() => {
  if (props.confirmation.requestType === 'DECISION') return true
  return (props.confirmation.requestType === 'APPROVAL' || props.confirmation.requestType === 'CONFIRM')
    && options.value.length > 0
})
const visibleFields = computed<InteractionField[]>(() => {
  if (fields.value.length <= 1) return fields.value
  const activeField = fields.value.find(field => field.id === activeFieldId.value) ?? fields.value[0]
  return activeField ? [activeField] : []
})
const effectiveSelectedOptionId = computed(() => {
  if (allowCustomChoice.value && customChoiceText.value) return '__custom__'
  return props.selectedOptionId || options.value[0]?.id || ''
})
const canSubmitChoice = computed(() => {
  if (!usesOptionControl.value) return true
  if (options.value.length === 0) {
    return allowCustomChoice.value ? customChoiceText.value.length > 0 : props.inputText.trim().length > 0
  }
  if (isMultiSelect.value) {
    return selectedChoiceSet.value.size > 0 || (allowCustomChoice.value && customChoiceText.value.length > 0)
  }
  return effectiveSelectedOptionId.value.length > 0
})
const canSubmitInput = computed(() => {
  if (fields.value.length > 0) {
    return fields.value.every(field => isFieldComplete(field, props.fieldValues))
  }
  return props.inputText.trim().length > 0
})
const choiceTitle = computed(() => {
  if (isArtifactReview.value) return '审阅产物'
  if (props.confirmation.requestType === 'DECISION') return '选择处理路径'
  return '选择处理结果'
})
const inputTitle = computed(() =>
  fields.value.length
    ? props.schema?.title?.trim() || props.confirmation.title?.trim() || '补充信息'
    : '补充信息'
)
const barQuestion = computed(() => {
  if (isInputRequired.value && fields.value.length > 1) return inputTitle.value
  return props.question
})
const dialogQuestion = computed(() => {
  if (isException.value) return props.confirmation.contextSummary ?? ''
  return props.question || props.schema?.question || ''
})
const showAggregateFieldQuestion = computed(() => fields.value.length === 1 && Boolean(dialogQuestion.value))
const primaryLabel = computed(() => {
  if (isInputRequired.value) return '提交补充'
  if (props.confirmation.requestType === 'DECISION') return '提交选择'
  if (isArtifactReview.value) return '提交审阅'
  if ((props.confirmation.requestType === 'APPROVAL' || props.confirmation.requestType === 'CONFIRM') && options.value.length > 0) return '提交选择'
  if (isException.value) return props.inputText.trim() ? '提交补充' : '重试'
  return props.variant === 'dialog' ? '通过' : '确认'
})
const secondaryLabel = computed(() => {
  if (isException.value) return '跳过'
  if (props.variant === 'bar' && props.confirmation.requestType === 'DECISION') return '稍后'
  if (props.confirmation.requestType === 'APPROVAL') return '退回'
  return '拒绝'
})

watch(fields, (nextFields) => {
  if (!nextFields.length) {
    activeFieldId.value = ''
    return
  }
  if (!nextFields.some(field => field.id === activeFieldId.value)) {
    activeFieldId.value = nextFields[0].id
  }
}, { immediate: true })

function buttonBusyLabel(reply: 'once' | 'always' | 'reject') {
  if (!props.busyAction?.includes(reply)) return null
  if (reply === 'always') return '正在允许本次会话...'
  if (reply === 'reject') return '正在拒绝...'
  return '正在允许一次...'
}

function isChoiceSelected(option: InteractionOption): boolean {
  return isMultiSelect.value
    ? selectedChoiceSet.value.has(option.id)
    : effectiveSelectedOptionId.value === option.id
}

function isFieldActive(field: InteractionField): boolean {
  return fields.value.length <= 1 || activeFieldId.value === field.id
}

function selectField(fieldId: string) {
  activeFieldId.value = fieldId
}

function questionOrdinal(index: number): string {
  const labels = ['一', '二', '三', '四', '五', '六', '七', '八', '九']
  return labels[index] ?? String(index + 1)
}

function isFieldAnswered(field: InteractionField): boolean {
  return isFieldComplete(field, props.fieldValues)
}

function allowsFieldCustom(field: InteractionField): boolean {
  return field.type === 'select' && field.allowCustom === true
}

function hasPresetFieldValue(field: InteractionField, value: string): boolean {
  return (field.options ?? []).some(option => option.value === value)
}

function fieldSelectValue(field: InteractionField): string {
  const value = props.fieldValues[field.id] ?? ''
  return hasPresetFieldValue(field, value) ? value : ''
}

function isFieldOptionSelected(field: InteractionField, option: FieldSelectOption): boolean {
  return fieldSelectValue(field) === option.value
}

function fieldCustomValue(field: InteractionField): string {
  const value = props.fieldValues[field.id] ?? ''
  return value && !hasPresetFieldValue(field, value) ? value : ''
}

function fieldInputId(field: InteractionField): string {
  return props.variant === 'dialog'
    ? `confirmation-field-${field.id}`
    : `field-${field.id}`
}

function updateInputText(event: Event) {
  emit('update:inputText', (event.target as HTMLInputElement | HTMLTextAreaElement).value)
}

function updateCustomChoice(event: Event) {
  emit('update:customChoice', (event.target as HTMLInputElement).value)
}

function updateReviewComment(event: Event) {
  emit('update:reviewComment', (event.target as HTMLTextAreaElement).value)
}

function updateFieldValue(fieldId: string, event: Event) {
  emit('update:fieldValue', fieldId, (event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement).value)
}

function updateFieldSelectValue(fieldId: string, value: string) {
  emit('update:fieldValue', fieldId, value)
}

function updateFieldCustomValue(fieldId: string, event: Event) {
  emit('update:fieldValue', fieldId, (event.target as HTMLInputElement).value)
}

function updateCheckboxField(fieldId: string, event: Event) {
  emit('update:fieldValue', fieldId, (event.target as HTMLInputElement).checked ? 'true' : 'false')
}

function submit(submission: InteractionFormSubmission | null, fallbackErrorTitle: string) {
  if (!submission || props.busyAction) return
  emit('submit', {
    ...submission,
    outcome: submission.outcome ?? (submission.actionType === 'REJECT' ? 'rejected' : 'resolved'),
    errorTitle: submission.errorTitle ?? fallbackErrorTitle,
  })
}

function submitPermission(reply: 'once' | 'always' | 'reject') {
  const actionType: ConfirmationActionType = reply === 'reject' ? 'REJECT' : 'APPROVE'
  submit({
    actionType,
    payload: { reply },
    comment: reply,
    outcome: reply === 'reject' ? 'rejected' : 'resolved',
    busyKey: `PERMISSION:${reply}`,
    errorTitle: '提交失败',
  }, '提交失败')
}

function submitChoice() {
  const custom = allowCustomChoice.value ? customChoiceText.value : ''
  if (isMultiSelect.value) {
    submit(buildMultiChoiceSubmission({
      requestType: props.confirmation.requestType,
      interactionType: props.confirmation.interactionType,
      options: options.value,
      selectedIds: props.selectedChoiceIds,
      customChoice: custom,
      remark: props.reviewComment,
    }), '提交选择失败')
    return
  }
  submit(buildSingleChoiceSubmission({
    requestType: props.confirmation.requestType,
    interactionType: props.confirmation.interactionType,
    options: options.value,
    selectedId: effectiveSelectedOptionId.value === '__custom__' ? null : effectiveSelectedOptionId.value,
    customChoice: custom,
    fallbackChoice: options.value.length > 0 ? undefined : props.inputText,
    remark: props.reviewComment,
  }), '提交选择失败')
}

function submitInput() {
  submit(buildInputSubmission(fields.value, props.fieldValues, props.inputText), '提交补充失败')
}

function submitPrimary() {
  if (isPermission.value) {
    submitPermission('once')
    return
  }
  if (isInputRequired.value) {
    submitInput()
    return
  }
  if (usesOptionControl.value) {
    submitChoice()
    return
  }
  if (isException.value) {
    const input = props.inputText.trim()
    submit(input
      ? { actionType: 'SUPPLEMENT', payload: { input }, comment: input, busyKey: 'SUPPLEMENT' }
      : { actionType: 'RETRY', busyKey: 'RETRY', errorTitle: '重试失败' }, input ? '提交补充失败' : '重试失败')
    return
  }
  submit({ actionType: 'APPROVE', busyKey: 'APPROVE', errorTitle: '通过失败' }, '通过失败')
}

function submitSecondary() {
  if (isException.value) {
    submit({ actionType: 'SKIP', busyKey: 'SKIP', errorTitle: '跳过失败' }, '跳过失败')
    return
  }
  submit({ actionType: 'REJECT', outcome: 'rejected', busyKey: 'REJECT', errorTitle: '拒绝失败' }, '拒绝失败')
}

function submitReject() {
  submit({ actionType: 'REJECT', outcome: 'rejected', busyKey: 'REJECT', errorTitle: '拒绝失败' }, '拒绝失败')
}
</script>

<template>
  <template v-if="variant === 'bar'">
    <div v-if="recovering" class="interaction-bar__recovering">
      <span class="interaction-bar__recovering-dot"></span>
      <strong>已提交，正在恢复并同步状态</strong>
    </div>
    <div v-else class="interaction-bar__main">
      <div class="interaction-bar__copy">
        <span class="interaction-bar__type">{{ typeLabel }}</span>
        <strong>{{ barQuestion }}</strong>
      </div>

      <div v-if="usesOptionControl" class="interaction-bar__control interaction-bar__control--options">
        <template v-if="options.length">
          <button
            v-for="(option, optionIndex) in options"
            :key="option.id"
            type="button"
            class="interaction-bar__option"
            :class="{ 'interaction-bar__option--selected': isChoiceSelected(option) }"
            @click="isMultiSelect ? emit('toggleChoice', option.id) : emit('update:selectedOptionId', option.id)"
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
            :value="customChoice"
            class="interaction-bar__input"
            type="text"
            placeholder="自定义输入..."
            @input="updateCustomChoice"
          >
        </label>
        <input
          v-if="!options.length && !allowCustomChoice"
          :value="inputText"
          class="interaction-bar__input"
          type="text"
          placeholder="输入你的选择..."
          @input="updateInputText"
        >
        <textarea
          v-if="isArtifactReview"
          :value="reviewComment"
          class="interaction-bar__textarea interaction-bar__review-note"
          placeholder="补充审阅备注..."
          rows="2"
          @input="updateReviewComment"
        ></textarea>
      </div>

      <div
        v-else-if="isInputRequired"
        class="interaction-bar__control"
        :class="{ 'interaction-bar__control--fields': fields.length > 0 }"
      >
        <template v-if="fields.length > 0">
          <div v-if="fields.length > 1" class="interaction-bar__field-tabs" role="tablist" aria-label="待回答问题">
            <button
              v-for="(field, fieldIndex) in fields"
              :key="field.id"
              type="button"
              class="interaction-bar__field-tab"
              :class="{
                'interaction-bar__field-tab--active': isFieldActive(field),
                'interaction-bar__field-tab--answered': isFieldAnswered(field),
              }"
              role="tab"
              :aria-selected="isFieldActive(field)"
              @click="selectField(field.id)"
            >
              <span class="interaction-bar__field-tab-index">{{ fieldIndex + 1 }}</span>
              <span class="interaction-bar__field-tab-copy">
                <strong>问题{{ questionOrdinal(fieldIndex) }}</strong>
                <span>{{ field.label }}</span>
              </span>
            </button>
          </div>
          <div class="interaction-bar__fields">
            <div v-for="field in visibleFields" :key="field.id" class="interaction-bar__field" role="tabpanel">
              <label :for="fieldInputId(field)" class="interaction-bar__field-label">
                {{ field.label }}
                <span v-if="field.required" class="interaction-bar__field-required">*</span>
              </label>
              <p v-if="fieldQuestion(field)" class="interaction-bar__field-question">
                {{ fieldQuestion(field) }}
              </p>
              <textarea
                v-if="field.type === 'textarea'"
                :id="fieldInputId(field)"
                :value="fieldValues[field.id] ?? ''"
                class="interaction-bar__textarea interaction-bar__field-input"
                :placeholder="fieldPlaceholder(field)"
                rows="2"
                @input="(event: Event) => updateFieldValue(field.id, event)"
              ></textarea>
              <template v-else-if="field.type === 'select'">
                <div
                  :id="fieldInputId(field)"
                  class="interaction-bar__field-menu"
                  role="radiogroup"
                  :aria-label="field.label"
                >
                  <button
                    v-for="option in field.options ?? []"
                    :key="option.value"
                    type="button"
                    class="interaction-bar__field-menu-option"
                    :class="{ 'interaction-bar__field-menu-option--selected': isFieldOptionSelected(field, option) }"
                    role="radio"
                    :aria-checked="isFieldOptionSelected(field, option)"
                    @click="updateFieldSelectValue(field.id, option.value)"
                  >
                    <span class="interaction-bar__field-menu-dot"></span>
                    <span class="interaction-bar__field-menu-copy">
                      <strong>{{ option.label }}</strong>
                    </span>
                  </button>
                </div>
                <label v-if="allowsFieldCustom(field)" class="interaction-bar__field-custom">
                  <span>自定义选择</span>
                  <input
                    :value="fieldCustomValue(field)"
                    class="interaction-bar__input interaction-bar__field-input"
                    type="text"
                    placeholder="输入自己的选择..."
                    @input="(event: Event) => updateFieldCustomValue(field.id, event)"
                  >
                </label>
              </template>
              <label v-else-if="field.type === 'checkbox'" :for="fieldInputId(field)" class="interaction-bar__checkbox">
                <input
                  :id="fieldInputId(field)"
                  type="checkbox"
                  :checked="fieldValues[field.id] === 'true'"
                  @change="(event: Event) => updateCheckboxField(field.id, event)"
                >
                <span>已确认</span>
              </label>
              <input
                v-else
                :id="fieldInputId(field)"
                :value="fieldValues[field.id] ?? ''"
                class="interaction-bar__input interaction-bar__field-input"
                :type="field.type === 'number' ? 'number' : 'text'"
                :placeholder="fieldPlaceholder(field)"
                @input="(event: Event) => updateFieldValue(field.id, event)"
              >
            </div>
          </div>
        </template>
        <textarea
          v-else
          :value="inputText"
          class="interaction-bar__textarea"
          placeholder="输入你的补充要求..."
          rows="3"
          @input="updateInputText"
        ></textarea>
      </div>

      <div v-else-if="isPermission" class="interaction-bar__hint interaction-bar__permission-hint">
        <span>这是 OpenCode 原生工具授权，处理后才会继续执行当前工具。</span>
        <span v-if="permissionScopeText">同类请求范围：{{ permissionScopeText }}</span>
      </div>

      <div v-else-if="confirmation.requestType === 'APPROVAL' || confirmation.requestType === 'CONFIRM'" class="interaction-bar__hint">
        审批当前节点产物或结论。
      </div>

      <div v-else-if="isException" class="interaction-bar__control">
        <textarea
          :value="inputText"
          class="interaction-bar__textarea"
          placeholder="补充异常处理信息后继续当前节点..."
          rows="3"
          @input="updateInputText"
        ></textarea>
      </div>

      <div v-else class="interaction-bar__hint">
        处理后 Agent 会继续推进当前节点。
      </div>

      <div class="interaction-bar__actions" :class="{ 'interaction-bar__actions--permission': isPermission }">
        <template v-if="isPermission">
          <button type="button" class="interaction-bar__primary" :disabled="!!busyAction" @click="submitPermission('once')">
            {{ buttonBusyLabel('once') ?? '允许一次' }}
          </button>
          <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="submitPermission('always')">
            {{ buttonBusyLabel('always') ?? '本次会话允许同类请求' }}
          </button>
          <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="submitPermission('reject')">
            {{ buttonBusyLabel('reject') ?? '拒绝' }}
          </button>
          <button v-if="showDetailAction" type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="emit('openDetail')">
            详情
          </button>
        </template>
        <template v-else>
          <button v-if="showDetailAction" type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="emit('openDetail')">
            详情
          </button>
          <button v-if="isException" type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="submitReject">
            拒绝
          </button>
          <button type="button" class="interaction-bar__ghost" :disabled="!!busyAction" @click="submitSecondary">
            {{ secondaryLabel }}
          </button>
          <button
            type="button"
            class="interaction-bar__primary"
            :disabled="!!busyAction || (isInputRequired && !canSubmitInput) || (usesOptionControl && !canSubmitChoice)"
            @click="submitPrimary"
          >
            {{ busyAction ? '提交中...' : primaryLabel }}
          </button>
        </template>
      </div>
    </div>
  </template>

  <template v-else>
    <section v-if="recovering" class="confirmation-dialog__interaction">
      <div class="confirmation-dialog__recovering">
        <span class="confirmation-dialog__recovering-dot"></span>
        <strong>已提交，正在恢复并同步状态</strong>
      </div>
    </section>

    <template v-else>
      <section v-if="isPermission" class="confirmation-dialog__interaction">
        <div class="confirmation-dialog__interaction-title">权限授权</div>
        <p v-if="dialogQuestion" class="confirmation-dialog__question">{{ dialogQuestion }}</p>
        <div class="confirmation-dialog__permission-actions">
          <button class="confirmation-card__action confirmation-card__action--approve" :disabled="!!busyAction" @click="submitPermission('once')">
            {{ busyAction?.includes('once') ? '允许中...' : '允许一次' }}
          </button>
          <button class="confirmation-card__action confirmation-card__action--approve" :disabled="!!busyAction" @click="submitPermission('always')">
            {{ busyAction?.includes('always') ? '允许中...' : '本次会话允许同类请求' }}
          </button>
          <button class="confirmation-card__action confirmation-card__action--reject" :disabled="!!busyAction" @click="submitPermission('reject')">
            {{ busyAction?.includes('reject') ? '处理中...' : '拒绝授权' }}
          </button>
        </div>
      </section>

      <section v-else-if="usesOptionControl" class="confirmation-dialog__interaction">
        <div class="confirmation-dialog__interaction-title">{{ choiceTitle }}</div>
        <p v-if="dialogQuestion" class="confirmation-dialog__question">{{ dialogQuestion }}</p>
        <div v-if="options.length" class="confirmation-dialog__options" :role="isMultiSelect ? 'group' : 'radiogroup'" aria-label="选择处理路径">
          <label
            v-for="option in options"
            :key="option.id"
            class="confirmation-dialog__option"
            :class="{ 'confirmation-dialog__option--selected': isChoiceSelected(option) }"
          >
            <input
              :type="isMultiSelect ? 'checkbox' : 'radio'"
              name="confirmation-option"
              :value="option.id"
              :checked="isChoiceSelected(option)"
              @change="isMultiSelect ? emit('toggleChoice', option.id) : emit('update:selectedOptionId', option.id)"
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
            :value="customChoice"
            class="confirmation-dialog__input"
            type="text"
            placeholder="输入自定义处理方式..."
            @input="updateCustomChoice"
          >
        </label>
        <textarea
          v-if="!options.length && !allowCustomChoice"
          :value="inputText"
          class="confirmation-dialog__textarea"
          rows="4"
          placeholder="输入你希望 Agent 采用的处理方式..."
          @input="updateInputText"
        />
        <textarea
          v-if="isArtifactReview"
          :value="reviewComment"
          class="confirmation-dialog__textarea confirmation-dialog__review-note"
          rows="3"
          placeholder="补充审阅备注..."
          @input="updateReviewComment"
        />
      </section>

      <section v-else-if="isException" class="confirmation-dialog__interaction">
        <div class="confirmation-dialog__interaction-title">异常处理</div>
        <p v-if="dialogQuestion" class="confirmation-dialog__question">{{ dialogQuestion }}</p>
        <textarea
          :value="inputText"
          class="confirmation-dialog__textarea"
          rows="3"
          placeholder="补充异常处理信息后继续当前节点..."
          @input="updateInputText"
        />
      </section>

      <section v-else-if="isInputRequired" class="confirmation-dialog__interaction">
        <div class="confirmation-dialog__interaction-title">{{ inputTitle }}</div>
        <p v-if="showAggregateFieldQuestion" class="confirmation-dialog__question">
          {{ dialogQuestion }}
        </p>
        <div v-if="fields.length > 1" class="confirmation-dialog__field-tabs" role="tablist" aria-label="待回答问题">
          <button
            v-for="(field, fieldIndex) in fields"
            :key="field.id"
            type="button"
            class="confirmation-dialog__field-tab"
            :class="{
              'confirmation-dialog__field-tab--active': isFieldActive(field),
              'confirmation-dialog__field-tab--answered': isFieldAnswered(field),
            }"
            role="tab"
            :aria-selected="isFieldActive(field)"
            @click="selectField(field.id)"
          >
            <span class="confirmation-dialog__field-tab-index">{{ fieldIndex + 1 }}</span>
            <span class="confirmation-dialog__field-tab-copy">
              <strong>问题{{ questionOrdinal(fieldIndex) }}</strong>
              <span>{{ field.label }}</span>
            </span>
          </button>
        </div>
        <div v-if="fields.length" class="confirmation-dialog__fields">
          <div v-for="field in visibleFields" :key="field.id" class="confirmation-dialog__field" role="tabpanel">
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
              @input="(event: Event) => updateFieldValue(field.id, event)"
            />
            <template v-else-if="field.type === 'select'">
              <div
                :id="fieldInputId(field)"
                class="confirmation-dialog__field-menu"
                role="radiogroup"
                :aria-label="field.label"
              >
                <button
                  v-for="option in field.options ?? []"
                  :key="option.value"
                  type="button"
                  class="confirmation-dialog__field-menu-option"
                  :class="{ 'confirmation-dialog__field-menu-option--selected': isFieldOptionSelected(field, option) }"
                  role="radio"
                  :aria-checked="isFieldOptionSelected(field, option)"
                  @click="updateFieldSelectValue(field.id, option.value)"
                >
                  <span class="confirmation-dialog__field-menu-dot"></span>
                  <span class="confirmation-dialog__field-menu-copy">
                    <strong>{{ option.label }}</strong>
                  </span>
                </button>
              </div>
              <label v-if="allowsFieldCustom(field)" class="confirmation-dialog__field-custom">
                <span>自定义选择</span>
                <input
                  :value="fieldCustomValue(field)"
                  class="confirmation-dialog__input confirmation-dialog__field-input"
                  type="text"
                  placeholder="输入自己的选择..."
                  @input="(event: Event) => updateFieldCustomValue(field.id, event)"
                >
              </label>
            </template>
            <label v-else-if="field.type === 'checkbox'" :for="fieldInputId(field)" class="confirmation-dialog__checkbox">
              <input
                :id="fieldInputId(field)"
                type="checkbox"
                :checked="fieldValues[field.id] === 'true'"
                @change="(event: Event) => updateCheckboxField(field.id, event)"
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
              @input="(event: Event) => updateFieldValue(field.id, event)"
            >
          </div>
        </div>
        <textarea
          v-else
          :value="inputText"
          class="confirmation-dialog__textarea"
          rows="5"
          placeholder="补充 Agent 继续执行所需的信息..."
          @input="updateInputText"
        />
      </section>

      <section v-else class="confirmation-dialog__interaction">
        <div class="confirmation-dialog__interaction-title">确认处理</div>
        <p v-if="dialogQuestion" class="confirmation-dialog__question">{{ dialogQuestion }}</p>
      </section>

      <footer class="confirmation-dialog__actions">
        <template v-if="isActionable && !isPermission">
          <button
            class="confirmation-card__action confirmation-card__action--approve"
            :disabled="!!busyAction || (isInputRequired && !canSubmitInput) || (usesOptionControl && !canSubmitChoice)"
            @click="submitPrimary"
          >
            {{ busyAction ? '提交中...' : primaryLabel }}
          </button>
          <button
            v-if="isException"
            class="confirmation-card__action confirmation-card__action--reject"
            :disabled="!!busyAction"
            @click="submitSecondary"
          >
            {{ busyAction?.includes('SKIP') ? '处理中...' : '跳过' }}
          </button>
          <button
            class="confirmation-card__action confirmation-card__action--reject"
            :disabled="!!busyAction"
            @click="submitReject"
          >
            {{ busyAction?.includes('REJECT') ? '处理中...' : (isException ? '拒绝' : secondaryLabel) }}
          </button>
        </template>
        <button v-if="showEnterSessionAction" class="confirmation-card__action" :disabled="!!busyAction" @click="emit('enterSession')">
          {{ confirmation.status === 'IN_CONVERSATION' ? '已在会话中' : '进入会话' }}
        </button>
      </footer>
    </template>
  </template>
</template>

<style scoped>
.interaction-bar__recovering,
.confirmation-dialog__recovering {
  display: flex;
  align-items: center;
  gap: 8px;
}

.interaction-bar__recovering {
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--accent-blue) 30%, var(--border-color));
  border-radius: 7px;
  background: color-mix(in srgb, var(--accent-blue) 6%, var(--bg-card));
  color: var(--accent-blue);
  font-size: 12px;
}

.interaction-bar__recovering strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
}

.interaction-bar__recovering-dot,
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

.interaction-bar__copy strong {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 900;
  line-height: 1.45;
}

.interaction-bar__hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.interaction-bar__permission-hint {
  display: grid;
  gap: 4px;
}

.interaction-bar__control {
  display: flex;
  gap: 8px;
}

.interaction-bar__control--fields {
  display: grid;
}

.interaction-bar__control--options,
.interaction-bar__fields {
  display: grid;
  gap: 8px;
}

.interaction-bar__field-tabs,
.confirmation-dialog__field-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.interaction-bar__field-tab,
.confirmation-dialog__field-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  max-width: 190px;
  padding: 5px 9px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.interaction-bar__field-tab-copy,
.confirmation-dialog__field-tab-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
  line-height: 1.2;
}

.interaction-bar__field-tab-copy strong,
.confirmation-dialog__field-tab-copy strong,
.interaction-bar__field-tab-copy span,
.confirmation-dialog__field-tab-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interaction-bar__field-tab-copy strong,
.confirmation-dialog__field-tab-copy strong {
  color: var(--text-primary);
  font-size: 11px;
  font-weight: 900;
}

.interaction-bar__field-tab-copy span,
.confirmation-dialog__field-tab-copy span {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 750;
}

.interaction-bar__field-tab-index,
.confirmation-dialog__field-tab-index {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: var(--bg-primary);
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 900;
}

.interaction-bar__field-tab--active,
.confirmation-dialog__field-tab--active {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
  color: var(--text-primary);
}

.interaction-bar__field-tab--answered .interaction-bar__field-tab-index,
.confirmation-dialog__field-tab--answered .confirmation-dialog__field-tab-index {
  background: var(--success);
  color: var(--on-success);
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

.interaction-bar__custom-choice,
.interaction-bar__field,
.interaction-bar__field-custom {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.interaction-bar__field-menu,
.confirmation-dialog__field-menu {
  display: grid;
  gap: 6px;
}

.interaction-bar__field-menu-option,
.confirmation-dialog__field-menu-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.interaction-bar__field-menu-option {
  min-height: 38px;
  padding: 8px 10px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 850;
}

.confirmation-dialog__field-menu-option {
  min-height: 38px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 800;
}

.interaction-bar__field-menu-option:hover,
.confirmation-dialog__field-menu-option:hover {
  border-color: var(--brand-border);
  background: var(--surface-hover);
}

.interaction-bar__field-menu-option--selected,
.confirmation-dialog__field-menu-option--selected {
  border-color: var(--accent-blue);
  background: var(--brand-soft);
  color: var(--text-primary);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent-blue) 30%, transparent);
}

.interaction-bar__field-menu-dot,
.confirmation-dialog__field-menu-dot {
  display: inline-block;
  flex: 0 0 auto;
  margin-top: 3px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--border-strong);
  border-radius: 999px;
  background: var(--bg-card);
}

.interaction-bar__field-menu-option--selected .interaction-bar__field-menu-dot,
.confirmation-dialog__field-menu-option--selected .confirmation-dialog__field-menu-dot {
  border-color: var(--accent-blue);
  background: var(--accent-blue);
  box-shadow: inset 0 0 0 2px var(--bg-card);
}

.interaction-bar__field-menu-copy,
.confirmation-dialog__field-menu-copy {
  min-width: 0;
}

.interaction-bar__field-menu-copy strong,
.confirmation-dialog__field-menu-copy strong {
  display: block;
  overflow-wrap: anywhere;
  line-height: 1.35;
  white-space: normal;
}

.interaction-bar__field-label,
.confirmation-dialog__field-label,
.interaction-bar__field-custom,
.confirmation-dialog__field-custom,
.confirmation-dialog__custom-choice {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
}

.interaction-bar__field-required,
.confirmation-dialog__field-required {
  color: var(--error);
  margin-left: 2px;
}

.interaction-bar__field-question,
.confirmation-dialog__field-question {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.interaction-bar__input,
.interaction-bar__textarea {
  width: 100%;
  border: 1px solid var(--border-color);
  outline: none;
  background: var(--bg-primary);
  color: var(--text-primary);
  font: inherit;
}

.interaction-bar__input {
  padding: 0 10px;
}

.interaction-bar__textarea {
  min-height: 76px;
  padding: 8px 10px;
  resize: vertical;
  border-radius: 7px;
  line-height: 1.55;
}

.interaction-bar__field-input {
  min-height: 34px;
}

.interaction-bar__input:focus,
.interaction-bar__textarea:focus {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-blue) 16%, transparent);
}

.interaction-bar__checkbox,
.confirmation-dialog__checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.interaction-bar__checkbox input,
.confirmation-dialog__checkbox input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-blue);
}

.interaction-bar__actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.interaction-bar__actions--permission {
  flex-wrap: wrap;
}

.interaction-bar__ghost,
.interaction-bar__primary {
  padding: 0 12px;
  border: 1px solid var(--border-color);
  cursor: pointer;
}

.interaction-bar__ghost {
  background: var(--surface-overlay);
  color: var(--text-secondary);
}

.interaction-bar__primary {
  border-color: var(--accent-blue);
  background: var(--accent-blue);
  color: var(--on-brand);
}

.interaction-bar__ghost:disabled,
.interaction-bar__primary:disabled,
.confirmation-card__action:disabled {
  cursor: wait;
  opacity: 0.65;
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

.confirmation-dialog__options,
.confirmation-dialog__fields,
.confirmation-dialog__field,
.confirmation-dialog__field-custom,
.confirmation-dialog__custom-choice,
.confirmation-dialog__permission-actions {
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

@media (max-width: 720px) {
  .interaction-bar__actions,
  .confirmation-dialog__actions {
    flex-wrap: wrap;
  }
}
</style>
