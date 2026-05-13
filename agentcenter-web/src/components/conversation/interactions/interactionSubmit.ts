import type {
  ConfirmationActionType,
  ConfirmationRequestDto,
  ResolveConfirmationRequest,
} from '../../../api/types'
import type { InteractionField, InteractionOption, InteractionSchema } from './interactionSchema'

export type FieldValueMap = Record<string, string>

export interface InteractionSubmission {
  actionType: ConfirmationActionType
  payload?: Record<string, unknown>
  comment?: string
}

export interface InteractionFormSubmission extends InteractionSubmission {
  outcome?: 'resolved' | 'rejected'
  busyKey?: string
  errorTitle?: string
}

const VALID_ACTION_TYPES: ConfirmationActionType[] = [
  'ENTER_SESSION',
  'APPROVE',
  'REJECT',
  'SUPPLEMENT',
  'CHOOSE',
  'RETRY',
  'SKIP',
  'ADVANCE',
]

export function fieldQuestion(field: InteractionField): string | null {
  const placeholder = field.placeholder?.trim()
  if (!placeholder || placeholder === field.label.trim()) return null
  return placeholder
}

export function fieldPlaceholder(field: InteractionField): string {
  return field.type === 'textarea' ? '输入你的回答...' : '输入答案...'
}

export function fieldRawValue(field: InteractionField, values: FieldValueMap): string {
  if (field.type === 'checkbox') return values[field.id] === 'true' ? 'true' : 'false'
  return values[field.id]?.trim() ?? ''
}

export function fieldDisplayValue(field: InteractionField, value: string): string {
  if (field.type === 'checkbox') return value === 'true' ? '是' : (field.required ? '否' : '')
  if (field.type === 'select') return field.options?.find(option => option.value === value)?.label ?? value
  return value
}

export function isFieldComplete(field: InteractionField, values: FieldValueMap): boolean {
  if (!field.required) return true
  if (field.type === 'checkbox') return fieldRawValue(field, values) === 'true'
  return fieldRawValue(field, values).length > 0
}

export function collectFieldPayload(fields: InteractionField[], values: FieldValueMap) {
  const payloadFields = fields.reduce<Record<string, string>>((acc, field) => {
    acc[field.id] = fieldRawValue(field, values)
    return acc
  }, {})
  const input = fields
    .map(field => fieldDisplayValue(field, payloadFields[field.id]))
    .filter(Boolean)
    .join('\n')
  return { input, fields: payloadFields }
}

export function usesOptionControl(
  requestType: ConfirmationRequestDto['requestType'],
  schema: InteractionSchema | null,
): boolean {
  if (requestType === 'DECISION') return true
  return (requestType === 'APPROVAL' || requestType === 'CONFIRM')
    && (schema?.options?.length ?? 0) > 0
}

export function approvalActionForChoice(option?: InteractionOption, fallback = ''): ConfirmationActionType {
  if (option?.actionType) return option.actionType
  const text = `${option?.id ?? ''} ${option?.label ?? ''} ${fallback}`.toLowerCase()
  if (/(reject|revise|return|fail|no|denied|不通过|退回|驳回|调整|拒绝|否)/.test(text)) {
    return 'REJECT'
  }
  return 'APPROVE'
}

export function decisionActionForChoice(
  interactionType: string | null | undefined,
  option?: InteractionOption,
  fallbackId = '',
): ConfirmationActionType {
  if (option?.actionType) return option.actionType
  const optionId = option?.id || fallbackId
  return interactionType === 'WORKFLOW_ADVANCE' && VALID_ACTION_TYPES.includes(optionId as ConfirmationActionType)
    ? optionId as ConfirmationActionType
    : 'CHOOSE'
}

export function choiceActionType(
  requestType: ConfirmationRequestDto['requestType'],
  interactionType: string | null | undefined,
  option?: InteractionOption,
  fallback = '',
): ConfirmationActionType {
  if (requestType === 'DECISION') {
    return decisionActionForChoice(interactionType, option, fallback)
  }
  return approvalActionForChoice(option, fallback)
}

export function withRemark(payload: Record<string, unknown>, remark: string): Record<string, unknown> {
  const trimmed = remark.trim()
  return trimmed ? { ...payload, remark: trimmed } : payload
}

export function choiceComment(label: string, remark: string): string {
  const trimmed = remark.trim()
  return trimmed ? `${label}\n${trimmed}` : label
}

export function buildSingleChoiceSubmission(params: {
  requestType: ConfirmationRequestDto['requestType']
  interactionType?: string | null
  options: InteractionOption[]
  selectedId?: string | null
  customChoice?: string
  fallbackChoice?: string
  remark?: string
}): InteractionSubmission | null {
  const custom = params.customChoice?.trim() ?? ''
  const remark = params.remark?.trim() ?? ''
  if (custom) {
    return {
      actionType: choiceActionType(params.requestType, params.interactionType, undefined, custom),
      payload: withRemark({ choice: custom, customChoice: custom }, remark),
      comment: choiceComment(custom, remark),
    }
  }

  const selected = params.options.find(option =>
    option.id === params.selectedId || option.label === params.selectedId)
  if (selected) {
    return {
      actionType: choiceActionType(params.requestType, params.interactionType, selected, selected.id),
      payload: withRemark({ choice: selected.id, choiceId: selected.id, choiceLabel: selected.label }, remark),
      comment: choiceComment(selected.label, remark),
    }
  }

  const fallback = params.fallbackChoice?.trim() ?? ''
  if (!fallback) return null
  return {
    actionType: choiceActionType(params.requestType, params.interactionType, undefined, fallback),
    payload: withRemark({ choice: fallback }, remark),
    comment: choiceComment(fallback, remark),
  }
}

export function buildMultiChoiceSubmission(params: {
  requestType: ConfirmationRequestDto['requestType']
  interactionType?: string | null
  options: InteractionOption[]
  selectedIds: string[]
  customChoice?: string
  remark?: string
}): InteractionSubmission | null {
  const selected = params.options.filter(option => params.selectedIds.includes(option.id))
  const choiceIds = selected.map(option => option.id)
  const choiceLabels = selected.map(option => option.label)
  const custom = params.customChoice?.trim() ?? ''
  const choices = custom ? [...choiceLabels, custom] : choiceLabels
  if (choices.length === 0) return null

  const remark = params.remark?.trim() ?? ''
  const payload: Record<string, unknown> = {
    choiceIds,
    choiceLabels,
    choices,
  }
  if (custom) payload.customChoice = custom

  return {
    actionType: choiceActionType(params.requestType, params.interactionType, selected[0], choiceIds[0]),
    payload: withRemark(payload, remark),
    comment: choiceComment(choices.join('，'), remark),
  }
}

export function buildInputSubmission(fields: InteractionField[], values: FieldValueMap, inputText: string): InteractionSubmission | null {
  if (fields.length > 0) {
    const payload = collectFieldPayload(fields, values)
    return {
      actionType: 'SUPPLEMENT',
      payload,
      comment: payload.input || undefined,
    }
  }

  const input = inputText.trim()
  if (!input) return null
  return {
    actionType: 'SUPPLEMENT',
    payload: { input },
    comment: input,
  }
}

export function toResolveRequest(submission: InteractionSubmission): ResolveConfirmationRequest {
  const request: ResolveConfirmationRequest = { actionType: submission.actionType }
  if (submission.payload && Object.keys(submission.payload).length > 0) {
    request.payload = submission.payload
  }
  if (submission.comment) {
    request.comment = submission.comment
  }
  return request
}
