import { describe, it, expect } from 'vitest'
import { parseInteractionSchema, getInteractionIcon } from './interactionSchema'
import type { ConfirmationRequestDto } from '../../../api/types'

function makeConfirmation(overrides: Partial<ConfirmationRequestDto> = {}): ConfirmationRequestDto {
  return {
    id: 'cf_001',
    requestType: 'DECISION',
    status: 'PENDING',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    agentSessionId: null,
    skillName: null,
    title: 'Test Title',
    content: 'What should we do?',
    contextSummary: null,
    optionsJson: null,
    priority: 'MEDIUM',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('parseInteractionSchema', () => {
  it('parses protocol interaction with structured interactionSchemaJson containing options', () => {
    const confirmation = makeConfirmation({
      interactionSchemaJson: JSON.stringify({
        selection: 'single',
        options: [
          { id: 'a', label: 'Option A', description: 'Desc A', actionType: 'ADVANCE' },
          { id: 'b', label: 'Option B' },
        ],
        allowCustom: true,
      }),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema).not.toBeNull()
    expect(schema!.interactionId).toBe('cf_001')
    expect(schema!.interactionType).toBe('DECISION')
    expect(schema!.selection).toBe('single')
    expect(schema!.options).toHaveLength(2)
    expect(schema!.options![0]).toEqual({ id: 'a', label: 'Option A', description: 'Desc A', actionType: 'ADVANCE' })
    expect(schema!.allowCustom).toBe(true)
    expect(schema!.title).toBe('Test Title')
    expect(schema!.question).toBe('What should we do?')
  })

  it('parses protocol interaction with fields array', () => {
    const confirmation = makeConfirmation({
      interactionSchemaJson: JSON.stringify({
        fields: [
          { id: 'name', label: 'Name', type: 'text' as const, required: true },
          { id: 'reason', label: 'Reason', type: 'textarea' as const, placeholder: 'Why?' },
          {
            id: 'scope',
            label: 'Scope',
            type: 'select',
            required: true,
            allow_custom: true,
            options: [
              { value: 'interaction', label: 'Interaction' },
              { id: 'artifact', label: 'Artifact' },
            ],
          },
        ],
      }),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.fields).toHaveLength(3)
    expect(schema!.fields![0]).toEqual({ id: 'name', label: 'Name', type: 'text', required: true })
    expect(schema!.fields![1].placeholder).toBe('Why?')
    expect(schema!.fields![2].options).toEqual([
      { value: 'interaction', label: 'Interaction' },
      { value: 'artifact', label: 'Artifact' },
    ])
    expect(schema!.fields![2].allowCustom).toBe(true)
    expect(schema!.options).toBeUndefined()
  })

  it('converts legacy optionsJson string array to InteractionOption[]', () => {
    const confirmation = makeConfirmation({
      optionsJson: JSON.stringify(['Option A', 'Option B', 'Option C']),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.options).toEqual([
      { id: 'Option A', label: 'Option A' },
      { id: 'Option B', label: 'Option B' },
      { id: 'Option C', label: 'Option C' },
    ])
  })

  it('uses legacy optionsJson object array directly', () => {
    const confirmation = makeConfirmation({
      optionsJson: JSON.stringify([
        { id: 'opt1', label: 'Option 1', description: 'First' },
        { id: 'opt2', label: 'Option 2' },
      ]),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.options).toEqual([
      { id: 'opt1', label: 'Option 1', description: 'First' },
      { id: 'opt2', label: 'Option 2' },
    ])
  })

  it('normalizes legacy value/label options into stable ids', () => {
    const confirmation = makeConfirmation({
      optionsJson: JSON.stringify([
        { value: 'FAST', label: '快速验证', description: '只保留最小测试链路' },
        { key: 'STRICT', name: '严格校验' },
      ]),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.options).toEqual([
      { id: 'FAST', label: '快速验证', description: '只保留最小测试链路' },
      { id: 'STRICT', label: '严格校验' },
    ])
  })

  it('normalizes protocol schema options with value fields', () => {
    const confirmation = makeConfirmation({
      interactionSchemaJson: JSON.stringify({
        selection: 'single',
        options: [
        { value: 'APPROVE', label: '允许', action_type: 'APPROVE' },
        { value: 'REJECT', label: '拒绝' },
        ],
      }),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.options).toEqual([
      { id: 'APPROVE', label: '允许', actionType: 'APPROVE' },
      { id: 'REJECT', label: '拒绝' },
    ])
  })

  it('parses multiselect fields without downgrading them to select', () => {
    const confirmation = makeConfirmation({
      requestType: 'INPUT_REQUIRED',
      interactionSchemaJson: JSON.stringify({
        fields: [
          {
            id: 'acceptance',
            label: '验收标准',
            type: 'multiselect',
            required: true,
            options: [
              { value: 'ui', label: 'UI 可见' },
              { value: 'e2e', label: '端到端通过' },
            ],
          },
        ],
      }),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.fields![0].type).toBe('multiselect')
  })

  it('provides default artifact review options when the backend omits them', () => {
    const confirmation = makeConfirmation({
      requestType: 'APPROVAL',
      interactionType: 'ARTIFACT_REVIEW',
      optionsJson: null,
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.options).toEqual([
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
    ])
  })

  it('normalizes snake-case custom and multiple selection metadata', () => {
    const confirmation = makeConfirmation({
      interactionSchemaJson: JSON.stringify({
        selection: 'multiple',
        allow_custom: true,
        options: [{ id: 'A', label: '方案 A' }],
      }),
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.selection).toBe('multi')
    expect(schema!.allowCustom).toBe(true)
  })

  it('returns schema without options when optionsJson is null', () => {
    const confirmation = makeConfirmation({ optionsJson: null })

    const schema = parseInteractionSchema(confirmation)

    expect(schema).not.toBeNull()
    expect(schema!.options).toBeUndefined()
  })

  it('derives interactionType from requestType when interactionType is missing', () => {
    const cases: Array<[ConfirmationRequestDto['requestType'], string]> = [
      ['DECISION', 'DECISION'],
      ['INPUT_REQUIRED', 'INPUT'],
      ['APPROVAL', 'APPROVAL'],
      ['CONFIRM', 'APPROVAL'],
      ['EXCEPTION', 'BLOCKER'],
      ['PERMISSION', 'PERMISSION'],
    ]

    for (const [requestType, expected] of cases) {
      const confirmation = makeConfirmation({ requestType })
      const schema = parseInteractionSchema(confirmation)
      expect(schema!.interactionType).toBe(expected)
    }
  })

  it('derives title from content when title is empty', () => {
    const confirmation = makeConfirmation({
      title: '',
      content: 'This is a very long content that should be truncated at fifty characters to make a reasonable title',
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.title).toHaveLength(51) // 50 chars + ellipsis
    expect(schema!.title.endsWith('…')).toBe(true)
    expect(schema!.question).toBe(confirmation.content)
  })

  it('uses interactionId from confirmation when present', () => {
    const confirmation = makeConfirmation({
      interactionId: 'int_custom_123',
    })

    const schema = parseInteractionSchema(confirmation)

    expect(schema!.interactionId).toBe('int_custom_123')
  })

  it('returns null for nullish input', () => {
    expect(parseInteractionSchema(null as unknown as ConfirmationRequestDto)).toBeNull()
  })
})

describe('getInteractionIcon', () => {
  it('returns correct icon for each known type', () => {
    const mapping: Record<string, string> = {
      DECISION: '📋',
      INPUT: '✏️',
      APPROVAL: '✅',
      ARTIFACT_REVIEW: '📄',
      PERMISSION: '🔐',
      BLOCKER: '🚫',
      ASK_USER: '❓',
    }

    for (const [type, icon] of Object.entries(mapping)) {
      expect(getInteractionIcon(type)).toBe(icon)
    }
  })

  it('returns default icon for unknown type', () => {
    expect(getInteractionIcon('UNKNOWN')).toBe('💬')
  })
})
