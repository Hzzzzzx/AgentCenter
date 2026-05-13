import type { ConfirmationActionType, ConfirmationRequestDto } from '../../../api/types'

export interface InteractionOption {
  id: string
  label: string
  description?: string
  actionType?: ConfirmationActionType
}

type InteractionFieldType = 'text' | 'textarea' | 'number' | 'select' | 'checkbox'

export interface InteractionField {
  id: string
  label: string
  type: InteractionFieldType
  required?: boolean
  placeholder?: string
  options?: { label: string; value: string }[]
}

export interface InteractionSchema {
  interactionId: string
  interactionType: string
  title: string
  question: string
  selection?: 'single' | 'multi'
  options?: InteractionOption[]
  allowCustom?: boolean
  fields?: InteractionField[]
  required?: boolean
}

const ARTIFACT_REVIEW_DEFAULT_OPTIONS: InteractionOption[] = [
  {
    id: 'APPROVE',
    label: '通过',
    description: '产物可接受，继续推进下一步',
    actionType: 'APPROVE',
  },
  {
    id: 'REVISE',
    label: '需要调整',
    description: '补充审阅备注，让 Agent 继续完善',
    actionType: 'REJECT',
  },
]

function optionText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : String(value ?? '').trim()
}

const VALID_ACTION_TYPES = new Set<ConfirmationActionType>([
  'ENTER_SESSION',
  'APPROVE',
  'REJECT',
  'SUPPLEMENT',
  'CHOOSE',
  'RETRY',
  'SKIP',
  'ADVANCE',
])

const VALID_FIELD_TYPES = new Set<InteractionFieldType>([
  'text',
  'textarea',
  'number',
  'select',
  'checkbox',
])

function normalizeActionType(value: unknown): ConfirmationActionType | undefined {
  const text = optionText(value).toUpperCase()
  return VALID_ACTION_TYPES.has(text as ConfirmationActionType)
    ? text as ConfirmationActionType
    : undefined
}

function normalizeOption(item: unknown): InteractionOption | null {
  if (typeof item === 'string') {
    const text = item.trim()
    return text ? { id: text, label: text } : null
  }

  if (!item || typeof item !== 'object') return null

  const record = item as Record<string, unknown>
  const id = optionText(record.id ?? record.value ?? record.key ?? record.label)
  const label = optionText(record.label ?? record.name ?? record.title ?? record.value ?? record.id ?? id)
  if (!id && !label) return null

  const option: InteractionOption = {
    id: id || label,
    label: label || id,
  }
  const description = optionText(record.description)
  if (description) option.description = description
  const actionType = normalizeActionType(record.actionType ?? record.action_type ?? record.action)
  if (actionType) option.actionType = actionType
  return option
}

function normalizeOptions(raw: unknown): InteractionOption[] | undefined {
  if (!Array.isArray(raw) || raw.length === 0) return undefined
  const options = raw
    .map(normalizeOption)
    .filter((option): option is InteractionOption => !!option)
  return options.length ? options : undefined
}

function normalizeSelection(value: unknown): InteractionSchema['selection'] {
  const selection = optionText(value).toLowerCase()
  if (selection === 'multi' || selection === 'multiple') return 'multi'
  if (selection === 'single' || selection === 'one') return 'single'
  return undefined
}

function resolveOptions(interactionType: string, options: InteractionOption[] | undefined): InteractionOption[] | undefined {
  if (options?.length) return options
  return interactionType === 'ARTIFACT_REVIEW'
    ? ARTIFACT_REVIEW_DEFAULT_OPTIONS
    : undefined
}

function parseLegacyOptions(optionsJson: string | null): InteractionOption[] | undefined {
  if (!optionsJson) return undefined

  try {
    return normalizeOptions(JSON.parse(optionsJson))
  } catch {
    return undefined
  }
}

function normalizeFieldType(value: unknown): InteractionFieldType {
  const type = optionText(value).toLowerCase()
  return VALID_FIELD_TYPES.has(type as InteractionFieldType)
    ? type as InteractionFieldType
    : 'text'
}

function normalizeFieldOption(item: unknown): { label: string; value: string } | null {
  if (typeof item === 'string') {
    const text = item.trim()
    return text ? { value: text, label: text } : null
  }

  if (!item || typeof item !== 'object') return null

  const record = item as Record<string, unknown>
  const value = optionText(record.value ?? record.id ?? record.key ?? record.label)
  const label = optionText(record.label ?? record.name ?? record.title ?? record.value ?? record.id ?? value)
  if (!value && !label) return null
  return {
    value: value || label,
    label: label || value,
  }
}

function normalizeFieldOptions(raw: unknown): { label: string; value: string }[] | undefined {
  if (!Array.isArray(raw) || raw.length === 0) return undefined
  const options = raw
    .map(normalizeFieldOption)
    .filter((option): option is { label: string; value: string } => !!option)
  return options.length ? options : undefined
}

function normalizeField(item: unknown): InteractionField | null {
  if (!item || typeof item !== 'object') return null

  const record = item as Record<string, unknown>
  const id = optionText(record.id ?? record.key ?? record.name ?? record.label)
  const label = optionText(record.label ?? record.name ?? record.title ?? id)
  if (!id || !label) return null

  const field: InteractionField = {
    id,
    label,
    type: normalizeFieldType(record.type),
  }
  if (typeof record.required === 'boolean') field.required = record.required
  const placeholder = optionText(record.placeholder)
  if (placeholder) field.placeholder = placeholder
  const options = normalizeFieldOptions(record.options)
  if (options) field.options = options
  return field
}

function normalizeFields(raw: unknown): InteractionField[] | undefined {
  if (!Array.isArray(raw) || raw.length === 0) return undefined
  const fields = raw
    .map(normalizeField)
    .filter((field): field is InteractionField => !!field)
  return fields.length ? fields : undefined
}

function deriveInteractionType(requestType: ConfirmationRequestDto['requestType']): string {
  switch (requestType) {
    case 'DECISION':
      return 'DECISION'
    case 'INPUT_REQUIRED':
      return 'INPUT'
    case 'APPROVAL':
    case 'CONFIRM':
      return 'APPROVAL'
    case 'EXCEPTION':
      return 'BLOCKER'
    case 'PERMISSION':
      return 'PERMISSION'
    default:
      return 'ASK_USER'
  }
}

export function parseInteractionSchema(confirmation: ConfirmationRequestDto): InteractionSchema | null {
  if (!confirmation) return null

  const interactionId = confirmation.interactionId ?? confirmation.id
  const interactionType = confirmation.interactionType ?? deriveInteractionType(confirmation.requestType)
  const contentFallback = confirmation.content ?? ''
  const title = confirmation.title || (contentFallback.length > 50 ? contentFallback.slice(0, 50) + '…' : contentFallback)

  if (confirmation.interactionSchemaJson) {
    try {
      const schema = JSON.parse(confirmation.interactionSchemaJson) as Partial<InteractionSchema>
      const schemaQuestion = typeof schema.question === 'string' && schema.question.trim() ? schema.question : contentFallback
      const options = normalizeOptions(schema.options)
      return {
        interactionId,
        interactionType,
        title: typeof schema.title === 'string' && schema.title.trim() ? schema.title : title,
        question: schemaQuestion,
        selection: normalizeSelection(schema.selection),
        options: resolveOptions(interactionType, options),
        allowCustom: schema.allowCustom ?? (schema as Record<string, unknown>).allow_custom as boolean | undefined,
        fields: normalizeFields(schema.fields),
        required: confirmation.interactionRequired ?? schema.required,
      }
    } catch {
      // Fall through to legacy parsing
    }
  }

  const question = contentFallback

  // Legacy: optionsJson
  const options = parseLegacyOptions(confirmation.optionsJson)

  return {
    interactionId,
    interactionType,
    title,
    question,
    options: resolveOptions(interactionType, options),
    required: confirmation.interactionRequired ?? undefined,
  }
}

export function getInteractionIcon(type: string): string {
  switch (type) {
    case 'DECISION':
      return '📋'
    case 'INPUT':
      return '✏️'
    case 'APPROVAL':
      return '✅'
    case 'ARTIFACT_REVIEW':
      return '📄'
    case 'PERMISSION':
      return '🔐'
    case 'BLOCKER':
      return '🚫'
    case 'ASK_USER':
      return '❓'
    default:
      return '💬'
  }
}
