import type { ConfirmationRequestDto } from '../../../api/types'

export interface InteractionOption {
  id: string
  label: string
  description?: string
}

export interface InteractionField {
  id: string
  label: string
  type: 'text' | 'textarea' | 'number' | 'select' | 'checkbox'
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

function optionText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : String(value ?? '').trim()
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
  return option
}

function normalizeOptions(raw: unknown): InteractionOption[] | undefined {
  if (!Array.isArray(raw) || raw.length === 0) return undefined
  const options = raw
    .map(normalizeOption)
    .filter((option): option is InteractionOption => !!option)
  return options.length ? options : undefined
}

function parseLegacyOptions(optionsJson: string | null): InteractionOption[] | undefined {
  if (!optionsJson) return undefined

  try {
    return normalizeOptions(JSON.parse(optionsJson))
  } catch {
    return undefined
  }
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
      return {
        interactionId,
        interactionType,
        title,
        question: schemaQuestion,
        selection: schema.selection,
        options: normalizeOptions(schema.options),
        allowCustom: schema.allowCustom,
        fields: schema.fields,
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
    options,
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
