import type {
  ProjectorInput,
  ProjectorRuntimeEvent,
  ProjectorMessage,
  ProjectorConfirmation,
  ConversationTurnProjection,
  ExecutionStep,
  StepPart,
  ToolInvocationPart,
  ArtifactEvidencePart,
  DecisionGatePart,
  InteractionProjection,
  RawEventRef,
  AnswerProjection,
  CurrentActionProjection,
  ConversationDisplayItem,
  TurnStatus,
  StepKind,
} from './types'

interface ParsedPayload {
  id?: string
  kind?: string
  status?: string
  type?: string
  title?: string
  summary?: string
  toolName?: string | null
  rawToolName?: string | null
  actionType?: string
  reason?: string
  errorMessage?: string
  label?: string
  output?: unknown
  isError?: boolean
  success?: boolean
  skillName?: string
  toolCallId?: string
  confirmationId?: string
  requestType?: string
  actionDescription?: string
  question?: string
  contextSummary?: string
  options?: unknown
  comment?: string
  resolutionPayload?: unknown
  command?: string
  input?: unknown
  args?: unknown
  arguments?: unknown
  result?: unknown
  detail?: unknown
  error?: unknown
  rawPart?: unknown
  rawEventType?: string
  rawPartType?: string
  nodeName?: string
  visibility?: string
  filePath?: string
  artifactId?: string
  diffAvailable?: boolean
  recommended?: string
  interactionType?: string
  nodeState?: string
  nodeStateReason?: string
}

interface EnrichedEvent {
  event: ProjectorRuntimeEvent
  payload: ParsedPayload
  arrivalIndex: number
}

interface ToolLifecycle {
  toolCallId: string | null
  toolName: string
  rawToolName: string
  events: EnrichedEvent[]
}

interface ReasoningLifecycle {
  key: string
  events: EnrichedEvent[]
}

type OrderedRuntimeEvent =
  | { type: 'lifecycle'; lifecycle: ToolLifecycle; sortKey: EnrichedEvent }
  | { type: 'reasoning'; lifecycle: ReasoningLifecycle; sortKey: EnrichedEvent }
  | { type: 'single'; event: EnrichedEvent }

interface PendingInteractionCandidate {
  interaction: InteractionProjection
  createdAt?: string
}

interface GeneratedAnswerPart {
  text: string
  createdAt?: string
}

function parsePayload(payloadJson: string | null): ParsedPayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as ParsedPayload
      : {}
  } catch {
    return {}
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringifyPayloadValue(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed || undefined
  }
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try {
    const serialized = JSON.stringify(value, null, 2)
    return serialized && serialized !== '{}' && serialized !== '[]' ? serialized : undefined
  } catch {
    return String(value)
  }
}

function payloadText(payload: ParsedPayload, keys: Array<keyof ParsedPayload>): string | undefined {
  for (const key of keys) {
    const value = stringifyPayloadValue(payload[key])
    if (value) return value
  }
  return undefined
}

function nestedRecord(value: unknown, key: string): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined
  const nested = value[key]
  return isRecord(nested) ? nested : undefined
}

function rawPartText(payload: ParsedPayload, keys: string[], stateKeys: string[] = keys): string | undefined {
  const raw = isRecord(payload.rawPart) ? payload.rawPart : undefined
  if (!raw) return undefined
  const state = nestedRecord(raw, 'state')
  for (const key of stateKeys) {
    const value = state ? stringifyPayloadValue(state[key]) : undefined
    if (value) return value
  }
  for (const key of keys) {
    const value = stringifyPayloadValue(raw[key])
    if (value) return value
  }
  return undefined
}

function rawPartIdentity(payload: ParsedPayload): string | undefined {
  const raw = isRecord(payload.rawPart) ? payload.rawPart : undefined
  if (!raw) return undefined
  const candidates = [
    raw.id,
    raw.partID,
    raw.partId,
    raw.messageID && raw.partID ? `${raw.messageID}:${raw.partID}` : undefined,
    raw.messageId && raw.partId ? `${raw.messageId}:${raw.partId}` : undefined,
  ]
  return candidates
    .map(value => stringifyPayloadValue(value))
    .find(Boolean)
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const trimmed = value.trim()
  if (!trimmed) return 0

  const hasExplicitTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(trimmed)
  const normalized = !hasExplicitTimezone && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(trimmed)
    ? `${trimmed.replace(' ', 'T')}Z`
    : trimmed
  const parsed = Date.parse(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

function sortEvents(a: EnrichedEvent, b: EnrichedEvent): number {
  const seqA = a.event.seqNo ?? Number.MAX_SAFE_INTEGER
  const seqB = b.event.seqNo ?? Number.MAX_SAFE_INTEGER
  if (seqA !== seqB) return seqA - seqB

  const timeA = timestamp(a.event.createdAt)
  const timeB = timestamp(b.event.createdAt)
  if (timeA !== timeB) return timeA - timeB

  if (a.arrivalIndex !== b.arrivalIndex) return a.arrivalIndex - b.arrivalIndex

  return a.event.id.localeCompare(b.event.id)
}

function isToolLifecycleEvent(eventType: string, payload: ParsedPayload): boolean {
  return eventType === 'SKILL_STARTED'
    || eventType === 'SKILL_COMPLETED'
    || eventType === 'MCP_CALL'
    || payload.kind === 'tool_call'
}

function isNoisyToolHeartbeat(payload: ParsedPayload): boolean {
  if (payload.kind !== 'tool_call') return false
  const hasToolIdentity = Boolean(payload.toolCallId || payload.toolName || payload.skillName || payload.command)
  if (hasToolIdentity) return false
  const label = (payloadText(payload, ['label', 'title', 'status']) || '').trim().toLowerCase()
  return ['running', 'completed', 'done', 'idle'].includes(label) && !payload.summary
}

const INTERNAL_RUNTIME_TOKENS = new Set([
  'running',
  'completed',
  'done',
  'idle',
  'stop',
  'stopped',
  'waiting_user',
  'tool_calls',
  'toolcalls',
  'tool',
  'status',
  'session_status',
  '状态',
  '步骤开始',
])

function internalRuntimeToken(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[_\s-]+/g, '_')
    .replace(/[^a-z0-9_一-龥]/g, '')
}

function isInternalRuntimeText(value: string | undefined): boolean {
  if (!value) return true
  const trimmed = value.trim()
  if (!trimmed) return true
  const token = internalRuntimeToken(trimmed)
  if (INTERNAL_RUNTIME_TOKENS.has(token)) return true
  return /^(agent\s*)?(正在处理当前请求|本轮处理已完成)$/i.test(trimmed)
    || /^agent\s+(is\s+)?(running|idle|completed|stopped)$/i.test(trimmed)
}

function isNoisyRuntimeHeartbeat(ee: EnrichedEvent): boolean {
  const { event, payload } = ee
  if (isNoisyToolHeartbeat(payload)) return true

  const kind = payload.kind?.toLowerCase()
  const type = payload.type?.toLowerCase()
  const isStatusLike = event.eventType === 'STATUS'
    || kind === 'node_status'
    || kind === 'runtime_connection'
    || type === 'status'
    || payload.rawEventType === 'session.status'

  if (isStatusLike) {
    const label = payloadText(payload, ['title', 'label', 'status', 'type', 'kind'])
    const detail = payloadText(payload, ['summary', 'detail'])
    return isInternalRuntimeText(label) && isInternalRuntimeText(detail)
  }

  const isContextLikeTrace = event.eventType === 'PROCESS_TRACE'
    && (!kind || ['text', 'assistant_text', 'answer', 'agent', 'context'].includes(kind))
  if (isContextLikeTrace) {
    const text = renderableContextText(payload)
    if (text && isInternalRuntimeText(text)) return true
  }

  return false
}

function isPromptDebug(payload: ParsedPayload): boolean {
  return payload.kind === 'prompt_debug'
}

function isConfirmationTraceEvent(ee: EnrichedEvent): boolean {
  return ee.event.eventType === 'PROCESS_TRACE' && ee.payload.kind === 'confirmation'
}

function isAssistantStreamEvent(eventType: string): boolean {
  return eventType === 'ASSISTANT_DELTA' || eventType === 'ASSISTANT_COMPLETED'
}

function shouldPromoteContextToAnswer(ee: EnrichedEvent): boolean {
  if (ee.event.eventType !== 'PROCESS_TRACE') return false
  if (ee.payload.kind && !['text', 'assistant_text', 'answer'].includes(ee.payload.kind)) return false
  const text = renderableContextText(ee.payload)
  return Boolean(text)
}

function isConfirmationEvent(eventType: string): boolean {
  return eventType === 'CONFIRMATION_CREATED' || eventType === 'CONFIRMATION_RESOLVED' || eventType === 'PERMISSION_REQUIRED'
}

function toolNameFromPayload(payload: ParsedPayload): string {
  return payloadText(payload, ['skillName', 'toolName', 'label', 'command']) || rawPartText(payload, ['tool']) || 'tool'
}

function rawToolNameFromPayload(payload: ParsedPayload): string {
  return payloadText(payload, ['toolName', 'rawToolName', 'skillName', 'label', 'command']) || rawPartText(payload, ['tool']) || 'tool'
}

function isWorkflowSkillPayload(payload: ParsedPayload, event: ProjectorRuntimeEvent): boolean {
  return Boolean(payload.skillName || payload.nodeName || payload.nodeState || event.workflowNodeInstanceId)
}

function toolStatusFromPayload(eventType: string, payload: ParsedPayload): 'running' | 'completed' | 'failed' {
  if (eventType === 'SKILL_COMPLETED') {
    return payload.isError === true || payload.success === false ? 'failed' : 'completed'
  }
  if (eventType === 'SKILL_STARTED' || eventType === 'MCP_CALL') {
    return 'running'
  }
  return payload.status === 'failed' ? 'failed' : 'completed'
}

function buildToolPart(lifecycle: ToolLifecycle): ToolInvocationPart {
  const sorted = [...lifecycle.events].sort(sortEvents)
  const lastEvent = sorted[sorted.length - 1]
  const firstPayload = sorted[0].payload
  const lastPayload = lastEvent.payload
  const status = toolStatusFromPayload(lastEvent.event.eventType, lastPayload)

  // Scan ALL lifecycle events for output — don't rely on last payload only,
  // because a trailing PROCESS_TRACE (without output) would overwrite the real tool output.
  const outputSummary = sorted
    .map(e => payloadText(e.payload, ['output', 'result', 'error', 'errorMessage', 'reason', 'detail'])
      || rawPartText(e.payload, ['output', 'result', 'error']))
    .find(v => v !== undefined && v !== null && v !== '') ?? undefined
  const inputSummary = sorted
    .map(e => payloadText(e.payload, ['input', 'command', 'args', 'arguments'])
      || rawPartText(e.payload, ['input', 'command', 'args', 'arguments']))
    .find(v => v !== undefined && v !== null && v !== '')
    ?? payloadText(firstPayload, ['input', 'command', 'args', 'arguments'])
    ?? rawPartText(firstPayload, ['input', 'command', 'args', 'arguments'])

  const defaultExpanded = status === 'failed' || status === 'running'

  return {
    type: 'tool',
    toolCallId: lifecycle.toolCallId || `legacy-${sorted[0].event.id}`,
    rawName: lifecycle.rawToolName,
    displayName: readableToolName(lifecycle.toolName, inputSummary, outputSummary),
    category: toolCategory(lifecycle.toolName),
    status,
    inputSummary: inputSummary ?? undefined,
    outputSummary: outputSummary ?? undefined,
    rawPayloadRef: {
      eventId: sorted[0].event.id,
      eventType: sorted[0].event.eventType,
      seqNo: sorted[0].event.seqNo,
    },
    defaultExpanded,
  }
}

function readableToolName(toolName: string, input?: string, output?: string): string {
  const category = toolCategory(toolName)
  const target = conciseToolTarget(input || output)
  switch (category) {
    case 'read':
      return target ? `读取文件 ${target}` : '读取文件'
    case 'search':
      return target ? `搜索代码 ${target}` : '搜索代码'
    case 'list':
      return target ? `查看目录 ${target}` : '查看目录'
    case 'command':
      return target ? `执行命令 ${target}` : '执行命令'
    case 'skill': {
      const skillName = extractLoadedSkillName(output)
      if (skillName) return `读取 Skill：${skillName}`
      return `调用 ${toolName}`
    }
    default:
      return toolName
  }
}

function toolCategory(toolName: string): ToolInvocationPart['category'] {
  const normalized = toolName.toLowerCase().replace(/[\s-]+/g, '_')
  if (/^(read|read_file|view|cat|open)$/.test(normalized)) return 'read'
  if (/^(grep|rg|search|code_search|find)$/.test(normalized)) return 'search'
  if (/^(ls|list|list_dir|glob)$/.test(normalized)) return 'list'
  if (/^(bash|shell|exec|exec_command|run|run_command|terminal)$/.test(normalized)) return 'command'
  if (normalized.includes('skill') || normalized.endsWith('_design') || normalized.endsWith('_desingn')) return 'skill'
  return 'tool'
}

function conciseToolTarget(value?: string): string | undefined {
  if (!value) return undefined
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const pathMatch = trimmed.match(/(?:^|\s)((?:\/Users\/|\.{0,2}\/)?[\w@./-]+\.[A-Za-z0-9]+)(?:\s|$)/)
  if (pathMatch) return pathMatch[1].split('/').slice(-3).join('/')
  const quoted = trimmed.match(/["'`](.{1,80}?)["'`]/)
  if (quoted) return quoted[1]
  const firstLine = trimmed.split('\n')[0].trim()
  return firstLine.length > 80 ? `${firstLine.slice(0, 77)}...` : firstLine
}

function extractLoadedSkillName(output?: string): string | undefined {
  if (!output) return undefined
  const match = output.match(/^## Skill:\s*([^\n]+)/)
  return match?.[1]?.trim() || undefined
}

function isInternalSkillLoaderLifecycle(lifecycle: ToolLifecycle): boolean {
  const sorted = [...lifecycle.events].sort(sortEvents)
  const output = sorted
    .map(e => payloadText(e.payload, ['output', 'result']) || rawPartText(e.payload, ['output', 'result']))
    .find(v => v !== undefined && v !== null && v !== '')
  const name = lifecycle.rawToolName.toLowerCase()
  const onlyInternalSkillToolEvents = name === 'skill'
    && sorted.every(e => e.event.eventType === 'PROCESS_TRACE' && e.payload.kind === 'tool_call')
  return name === 'skill' && (Boolean(extractLoadedSkillName(output)) || onlyInternalSkillToolEvents)
}

function buildArtifactPart(ee: EnrichedEvent): ArtifactEvidencePart {
  const p = ee.payload
  return {
    type: 'artifact',
    artifactId: p.artifactId,
    filePath: p.filePath,
    title: artifactTitleFromPayload(p),
    summary: p.summary,
    diffAvailable: p.diffAvailable,
    rawEventRef: {
      eventId: ee.event.id,
      eventType: ee.event.eventType,
      seqNo: ee.event.seqNo,
    },
  }
}

function artifactTitleFromPayload(payload: ParsedPayload): string {
  if (payload.title && payload.title !== '产物变更') return payload.title
  if (payload.filePath) {
    const segments = payload.filePath.split(/[\\/]/).filter(Boolean)
    return segments.at(-1) || payload.filePath
  }
  return payload.summary || 'Artifact'
}

function parseDecisionOptions(value: unknown): Array<{ value: string; label: string; description?: string }> {
  let parsed: unknown = value
  if (typeof value === 'string') {
    try {
      parsed = JSON.parse(value)
    } catch {
      return []
    }
  }
  if (!Array.isArray(parsed)) return []
  return parsed.map((item: unknown) => {
    if (typeof item === 'object' && item !== null) {
      const rec = item as Record<string, unknown>
      return {
        value: String(rec.value ?? rec.key ?? rec.id ?? ''),
        label: String(rec.label ?? rec.name ?? rec.title ?? rec.value ?? ''),
        description: rec.description ? String(rec.description) : undefined,
      }
    }
    return { value: String(item), label: String(item) }
  })
}

function buildDecisionPart(ee: EnrichedEvent, confirmation?: ProjectorConfirmation): DecisionGatePart {
  const p = ee.payload
  const confirmationId = p.confirmationId || p.id || ee.event.id
  const payloadOptions = parseDecisionOptions(p.options)
  const options = payloadOptions.length > 0
    ? payloadOptions
    : parseDecisionOptions(confirmation?.optionsJson)

  const activeConfirmation = confirmation?.status === 'PENDING' || confirmation?.status === 'IN_CONVERSATION'
  const status = ee.event.eventType === 'CONFIRMATION_RESOLVED' || !activeConfirmation
    ? 'resolved'
    : 'waiting'
  const selectedValue = status === 'resolved' ? selectedDecisionValueFromAction(p, options) : undefined
  const permissionFallbackValue = status === 'resolved' && isPermissionDecision(p, confirmation)
    ? isRejectedDecision(p) ? 'REJECT' : 'APPROVE'
    : undefined

  return {
    type: 'decision',
    confirmationId,
    question: p.question || confirmation?.title || '需要你确认',
    prompt: p.contextSummary || undefined,
    options,
    recommended: selectedValue || permissionFallbackValue || p.recommended || undefined,
    status,
    requestType: confirmation?.requestType || p.requestType || undefined,
    interactionType: confirmation?.interactionType || p.interactionType || undefined,
    defaultExpanded: true,
    rawEventRef: {
      eventId: ee.event.id,
      eventType: ee.event.eventType,
      seqNo: ee.event.seqNo,
    },
  }
}

function resolveSelectedDecisionValue(
  payload: ParsedPayload,
  options: Array<{ value: string; label: string }>,
): string | undefined {
  const candidates = [
    payload.actionType,
    stringifyPayloadValue(payload.resolutionPayload),
    parseResolutionPayloadValue(payload.resolutionPayload),
    payload.actionDescription,
  ].filter((value): value is string => Boolean(value && value.trim()))

  return candidates
    .map(value => value.trim())
    .flatMap(value => [value, value.toUpperCase()])
    .find(value => options.some(option => option.value === value))
}

function parseResolutionPayloadValue(value: unknown): string | undefined {
  if (!value) return undefined
  try {
    const parsed: unknown = typeof value === 'string' ? JSON.parse(value) : value
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      const rec = parsed as Record<string, unknown>
      return String(rec.value ?? rec.actionType ?? rec.action ?? rec.choice ?? '')
    }
  } catch { /* ignore non-JSON payloads */ }
  return undefined
}

function selectedDecisionValueFromAction(
  payload: ParsedPayload,
  options: Array<{ value: string; label: string }>,
): string | undefined {
  const direct = resolveSelectedDecisionValue(payload, options)
  if (direct) return direct
  if (payload.actionType === 'APPROVE') {
    return options.find(option => ['PASS', 'APPROVE', 'APPROVED', 'YES'].includes(option.value.toUpperCase()))?.value
  }
  return undefined
}

function isPermissionDecision(payload: ParsedPayload, confirmation?: ProjectorConfirmation): boolean {
  const text = [
    payload.requestType,
    payload.interactionType,
    payload.title,
    payload.question,
    payload.summary,
    confirmation?.requestType,
    confirmation?.title,
    confirmation?.content,
  ].filter(Boolean).join(' ').toLowerCase()
  return text.includes('permission') || text.includes('opencode') || text.includes('权限')
}

function isRejectedDecision(payload: ParsedPayload): boolean {
  const text = [
    payload.actionType,
    payload.actionDescription,
    payload.comment,
    stringifyPayloadValue(payload.resolutionPayload),
    parseResolutionPayloadValue(payload.resolutionPayload),
  ].filter(Boolean).join(' ')
  return /reject|deny|refuse|拒绝/i.test(text)
}

function determineStepKind(ee: EnrichedEvent): StepKind {
  const { event, payload } = ee
  if (isToolLifecycleEvent(event.eventType, payload)) return 'tool'
  if (payload.kind === 'artifact') return 'artifact'
  if (payload.kind === 'reasoning' || payload.kind === 'reasoning_summary' || payload.rawPartType === 'reasoning') return 'reasoning'
  if (payload.kind === 'error' || event.eventType === 'ERROR') return 'error'
  if (isConfirmationEvent(event.eventType)) return 'decision'
  if (payload.kind === 'node_status' || event.eventType === 'STATUS' || payload.kind === 'runtime_connection') return 'status'
  if (payload.kind === 'subtask' || payload.kind === 'agent') return 'subtask'
  return 'context'
}

function isReasoningEvent(ee: EnrichedEvent): boolean {
  return determineStepKind(ee) === 'reasoning'
}

function reasoningLifecycleKey(ee: EnrichedEvent): string {
  const rawIdentity = rawPartIdentity(ee.payload)
  if (rawIdentity) return `raw:${rawIdentity}`
  const node = ee.event.workflowNodeInstanceId ?? 'session'
  return `${ee.event.sessionId}:${node}:reasoning`
}

function buildStepFromReasoningLifecycle(lifecycle: ReasoningLifecycle, order: number): ExecutionStep {
  const events = [...lifecycle.events].sort(sortEvents)
  const first = events[0]
  const last = events.at(-1) ?? first
  const summaries = events
    .map(ee => payloadText(ee.payload, ['summary', 'label', 'output', 'result', 'detail']) || rawPartText(ee.payload, ['text', 'summary']))
    .filter((value): value is string => Boolean(value))
  const summary = mergeAnswerText('', summaries) || 'Reasoning'
  const completed = events.some(ee => ee.payload.status === 'completed') || last.payload.status === 'completed'

  return {
    id: `reasoning-${lifecycle.key}`,
    order,
    kind: 'reasoning',
    title: payloadText(first.payload, ['title', 'label']) || '思考',
    summary,
    status: completed ? 'completed' : 'running',
    startedAt: first.event.createdAt,
    completedAt: completed ? last.event.createdAt : undefined,
    parts: [{
      type: 'reasoning',
      summary,
      messageId: undefined,
      partId: rawPartIdentity(first.payload),
      defaultExpanded: false,
      rawEventRef: {
        eventId: first.event.id,
        eventType: first.event.eventType,
        seqNo: first.event.seqNo,
      },
    }],
    rawEventRefs: events.map(ee => ({
      eventId: ee.event.id,
      eventType: ee.event.eventType,
      seqNo: ee.event.seqNo,
    })),
  }
}

function orderedRuntimeSortKey(item: OrderedRuntimeEvent): EnrichedEvent {
  return item.type === 'single' ? item.event : item.sortKey
}

function buildStepFromSingleEvent(ee: EnrichedEvent, order: number, confirmation?: ProjectorConfirmation): ExecutionStep {
  const { event, payload } = ee
  const kind = determineStepKind(ee)
  const parts: StepPart[] = []
  let decisionStatus: 'waiting' | 'submitted' | 'resolved' | undefined

  if (kind === 'artifact') {
    parts.push(buildArtifactPart(ee))
  } else if (kind === 'decision') {
    const decisionPart = buildDecisionPart(ee, confirmation)
    decisionStatus = decisionPart.status
    parts.push(decisionPart)
  } else if (kind === 'error') {
    parts.push({
      type: 'error',
      message: payloadText(payload, ['errorMessage', 'detail', 'reason', 'summary', 'label']) || rawPartText(payload, ['error']) || 'Error occurred',
      detail: payloadText(payload, ['output', 'result', 'error']) || rawPartText(payload, ['output', 'result', 'error']),
      defaultExpanded: true,
      rawEventRef: {
        eventId: event.id,
        eventType: event.eventType,
        seqNo: event.seqNo,
      },
    })
  } else if (kind === 'status') {
    parts.push({
      type: 'status',
      label: payloadText(payload, ['title', 'label', 'kind']) || 'Status',
      detail: payloadText(payload, ['summary', 'status', 'detail']),
      rawEventRef: {
        eventId: event.id,
        eventType: event.eventType,
        seqNo: event.seqNo,
      },
    })
  } else if (kind === 'reasoning') {
    parts.push({
      type: 'reasoning',
      summary: payloadText(payload, ['summary', 'label', 'output', 'result', 'detail']) || rawPartText(payload, ['text', 'summary']) || 'Reasoning',
      messageId: undefined,
      partId: undefined,
      defaultExpanded: false,
      rawEventRef: {
        eventId: event.id,
        eventType: event.eventType,
        seqNo: event.seqNo,
      },
    })
  } else {
    const text = renderableContextText(payload)
    if (text) {
      parts.push({
        type: 'text',
        text,
        rawEventRef: {
          eventId: event.id,
          eventType: event.eventType,
          seqNo: event.seqNo,
        },
      })
    } else {
      parts.push({
        type: 'raw',
        eventType: event.eventType,
        title: payload.title || payload.label || event.eventType,
        payload: payload as Record<string, unknown>,
        rawEventRef: {
          eventId: event.id,
          eventType: event.eventType,
          seqNo: event.seqNo,
        },
      })
    }
  }

  const stepStatus: 'running' | 'completed' | 'failed' | 'waiting_input' = kind === 'error' || payload.status === 'failed'
    ? 'failed'
    : kind === 'decision'
      ? decisionStatus === 'waiting' || decisionStatus === 'submitted' ? 'waiting_input' : 'completed'
      : event.eventType === 'SKILL_STARTED' || payload.status === 'running'
        ? 'running'
        : 'completed'

  return {
    id: event.id,
    order,
    kind,
    title: keyProgressTitle(payload, event.eventType, confirmation) || stepTitle(payload, event.eventType, kind),
    summary: kind === 'context' ? undefined : payloadText(payload, ['summary']) || undefined,
    status: stepStatus,
    startedAt: event.createdAt,
    completedAt: kind === 'error' || stepStatus === 'completed' || stepStatus === 'failed' ? event.createdAt : undefined,
    parts,
    rawEventRefs: [{
      eventId: event.id,
      eventType: event.eventType,
      seqNo: event.seqNo,
    }],
  }
}

function renderableContextText(payload: ParsedPayload): string | undefined {
  const text = payloadText(payload, ['output', 'result', 'summary', 'title', 'label', 'detail'])
    || rawPartText(payload, ['text', 'summary', 'reason'])
  if (!text) return undefined
  return normalizeMarkdownText(text)
}

function normalizeMarkdownText(text: string): string {
  return text
    .replace(/\\n/g, '\n')
    .replace(/：\s*---\s*#/g, '：\n\n---\n\n#')
    .replace(/:\s*---\s*#/g, ':\n\n---\n\n#')
    .replace(/\s+---\s+#/g, '\n\n---\n\n#')
    .trim()
}

function stepTitle(payload: ParsedPayload, eventType: string, kind: StepKind): string {
  if (kind === 'context') {
    const text = renderableContextText(payload)
    if (text) {
      const heading = text.match(/(^|\n)#{1,6}[ \t]+([^\n]+)/)?.[2]?.trim()
      if (heading) return heading.length > 64 ? `${heading.slice(0, 61)}...` : heading
      return 'Agent 输出'
    }
  }
  return payloadText(payload, ['title', 'label']) || toolNameFromPayload(payload) || eventType
}

function userFacingNodeReason(reason?: string): string {
  if (!reason) return '需要用户补充信息'
  if (reason.includes('implementation constraint')) return '需要补充实现约束'
  if (reason.includes('implementation route')) return '需要选择实现路线'
  if (reason.includes('draft review')) return '需要审阅 LLD 草稿'
  if (reason.includes('HLD path decision')) return '需要选择 HLD 方案'
  if (reason.includes('smoke artifact generated')) return '已生成节点产物'
  return reason
}

function keyProgressTitle(payload: ParsedPayload, eventType: string, confirmation?: ProjectorConfirmation): string | undefined {
  if (eventType === 'SKILL_STARTED') {
    const skill = payloadText(payload, ['skillName', 'toolName', 'label'])
    return skill ? `开始执行 ${skill}` : '开始执行 Skill'
  }
  if (eventType === 'SKILL_COMPLETED') {
    if (payload.nodeState === 'NEEDS_USER_INPUT') return userFacingNodeReason(payload.nodeStateReason || payload.reason)
    if (payload.nodeState === 'READY_TO_ADVANCE') return `${payloadText(payload, ['skillName']) || 'Skill'} 已生成产物`
    return `${payloadText(payload, ['skillName']) || 'Skill'} 执行完成`
  }
  if (eventType === 'CONFIRMATION_RESOLVED') {
    if (isPermissionDecision(payload, confirmation)) {
      return isRejectedDecision(payload) ? '权限已拒绝' : '权限已允许'
    }
    return payload.actionDescription || payload.comment || payload.title || '已处理用户确认'
  }
  return undefined
}

function buildStepFromToolLifecycle(lifecycle: ToolLifecycle, order: number): ExecutionStep {
  const toolPart = buildToolPart(lifecycle)
  const sorted = [...lifecycle.events].sort(sortEvents)
  const first = sorted[0]
  const last = sorted[sorted.length - 1]

  const parts: StepPart[] = [toolPart]

  for (const ee of sorted) {
    if (ee.payload.kind === 'artifact') {
      parts.push(buildArtifactPart(ee))
    }
  }

  return {
    id: `step-${lifecycle.toolCallId || lifecycle.events[0].event.id}`,
    order,
    kind: 'tool',
    title: toolPart.displayName,
    summary: summarizeToolStep(toolPart),
    status: toolPart.status === 'running' ? 'running' : toolPart.status === 'failed' ? 'failed' : 'completed',
    startedAt: first.event.createdAt,
    completedAt: toolPart.status !== 'running' ? last.event.createdAt : undefined,
    parts,
    rawEventRefs: sorted.map(ee => ({
      eventId: ee.event.id,
      eventType: ee.event.eventType,
      seqNo: ee.event.seqNo,
    })),
  }
}

function isVisibleExecutionStep(step: ExecutionStep): boolean {
  if (step.kind === 'context' || step.kind === 'status') return false
  if (step.parts.length === 0) return false
  if (step.kind === 'decision') {
    const decision = step.parts.find(part => part.type === 'decision')
    if (
      decision?.type === 'decision'
      && decision.status === 'resolved'
      && decision.options.length === 0
      && isGenericInteractionQuestion(decision.question)
    ) {
      return false
    }
  }
  return step.parts.some(part => part.type !== 'raw')
}

function isGenericInteractionQuestion(question: string | undefined): boolean {
  if (!question) return true
  return ['需要你确认', 'confirmation required', 'permission required', 'tool'].includes(question.trim().toLowerCase())
}

function completeDanglingReasoningSteps(steps: ExecutionStep[], turnStatus: TurnStatus): ExecutionStep[] {
  return steps.map((step, index) => {
    if (step.kind !== 'reasoning' || step.status !== 'running') return step

    const hasLaterVisibleWork = steps.slice(index + 1).some(laterStep =>
      laterStep.kind !== 'context' && laterStep.kind !== 'status'
    )
    if (turnStatus === 'running' && !hasLaterVisibleWork) return step

    return {
      ...step,
      status: 'completed',
      completedAt: step.completedAt ?? step.startedAt,
    }
  })
}

function decisionPartKey(step: ExecutionStep): string | undefined {
  const decision = step.parts.find(part => part.type === 'decision')
  if (!decision || decision.type !== 'decision' || decision.status !== 'resolved') return undefined
  return `decision:${decision.confirmationId}:${decision.recommended || ''}`
}

function errorStepKey(step: ExecutionStep): string | undefined {
  const error = step.parts.find(part => part.type === 'error')
  if (!error || error.type !== 'error') return undefined
  return `error:${step.title}:${error.message}:${error.detail || ''}`
}

function dedupeVisibleExecutionSteps(steps: ExecutionStep[]): ExecutionStep[] {
  const seen = new Set<string>()
  return steps.filter(step => {
    const key = errorStepKey(step) || decisionPartKey(step)
    if (!key) return true
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function summarizeToolStep(toolPart: ToolInvocationPart): string | undefined {
  const source = toolPart.status === 'failed'
    ? toolPart.outputSummary ?? toolPart.inputSummary
    : toolPart.outputSummary ?? toolPart.inputSummary
  if (!source) return undefined

  const text = source.trim()
  if (!text) return undefined

  const foundMatch = text.match(/^Found\s+(\d+)\s+match\(es\)\s+in\s+(\d+)\s+file\(s\)/)
  if (foundMatch) {
    return `找到 ${foundMatch[1]} 处匹配，涉及 ${foundMatch[2]} 个文件。`
  }

  const pathMatch = text.match(/<path>([^<]+)<\/path>/)
  if (pathMatch) {
    return `读取 ${pathMatch[1]}`
  }

  const firstLine = text.split(/\r?\n/).find(line => line.trim().length > 0)?.trim()
  if (!firstLine) return undefined
  return firstLine.length > 120 ? `${firstLine.slice(0, 117)}...` : firstLine
}

function mergeAnswerText(persistedText: string, generatedTextParts: string[]): string {
  const sections: string[] = []
  const persisted = persistedText.trim()
  if (persisted) sections.push(persisted)

  for (const part of generatedTextParts) {
    const text = part.trim()
    if (!text) continue
    if (sections.some(section => section.includes(text) || text.includes(section))) continue
    sections.push(text)
  }

  return sections.join('\n\n')
}

function currentActionForTurn(
  status: TurnStatus,
  steps: ExecutionStep[],
  hasStreamingText: boolean,
  pendingInteraction?: InteractionProjection | null,
): CurrentActionProjection | undefined {
  if (pendingInteraction) {
    return {
      label: '等待你处理当前交互',
      detail: pendingInteraction.question,
    }
  }

  if (status !== 'running') return undefined

  const runningStep = [...steps].reverse().find(step => step.status === 'running')
  if (runningStep) {
    if (runningStep.kind === 'tool') {
      return {
        label: runningStep.title.startsWith('调用') ? runningStep.title : `调用 ${runningStep.title}`,
        detail: runningStep.summary,
      }
    }
    return {
      label: runningStep.title,
      detail: runningStep.summary,
    }
  }

  if (hasStreamingText) {
    return { label: '正在生成回复', detail: '正文会持续流式更新。' }
  }

  return { label: '正在处理当前请求' }
}

function buildDisplayItems(
  turnId: string,
  status: TurnStatus,
  answer: AnswerProjection,
  steps: ExecutionStep[],
  currentAction?: CurrentActionProjection,
  pendingInteraction?: InteractionProjection,
): ConversationDisplayItem[] {
  const items: ConversationDisplayItem[] = []
  const hasActivity = steps.length > 0 || (Boolean(currentAction) && !pendingInteraction)
  const hasAnswer = answer.text.trim().length > 0
  const shouldDeferAnswer = status === 'running' && hasActivity && hasAnswer

  if (hasActivity) {
    items.push({
      type: 'agent-activity',
      id: `${turnId}:activity`,
      steps,
      status,
      currentAction,
      collapsedByDefault: Boolean(hasAnswer) && !shouldDeferAnswer,
    })
  }

  if (hasAnswer && !shouldDeferAnswer) {
    items.push({
      type: 'assistant-message',
      id: `${turnId}:assistant`,
      answer,
    })
  }

  if (pendingInteraction) {
    items.push({
      type: 'interaction-request',
      id: `${turnId}:interaction:${pendingInteraction.confirmationId}`,
      interaction: pendingInteraction,
    })
  }

  return items
}

function shouldHideInteractionRequestEvent(
  ee: EnrichedEvent,
  confirmationMap: Map<string, ProjectorConfirmation>,
): boolean {
  if (ee.event.eventType !== 'CONFIRMATION_CREATED' && ee.event.eventType !== 'PERMISSION_REQUIRED') {
    return false
  }

  const confId = ee.payload.confirmationId || ee.payload.id
  const confirmation = confId ? confirmationMap.get(confId) : undefined
  if (ee.event.eventType === 'PERMISSION_REQUIRED') return true
  return Boolean(confirmation)
}

export function projectConversationTurns(input: ProjectorInput): ConversationTurnProjection[] {
  const {
    messages,
    runtimeEvents,
    confirmations,
    streamingText,
    activeSessionId,
    running,
  } = input

  const hasNoData = messages.length === 0
    && runtimeEvents.length === 0
    && !streamingText.trim()

  if (hasNoData) return []

  const confirmationMap = new Map<string, ProjectorConfirmation>()
  for (const c of confirmations) {
    confirmationMap.set(c.id, c)
  }

  const enrichedEvents: EnrichedEvent[] = runtimeEvents
    .map((event, index) => ({
      event,
      payload: parsePayload(event.payloadJson),
      arrivalIndex: index,
    }))

  const sortedEvents = [...enrichedEvents].sort(sortEvents)

  const toolLifecycles: ToolLifecycle[] = []
  const toolLifecycleIndex = new Map<string, number>()
  const reasoningLifecycles: ReasoningLifecycle[] = []
  const reasoningLifecycleIndex = new Map<string, number>()
  const nonToolEvents: EnrichedEvent[] = []
  const debugRefs: RawEventRef[] = []
  const generatedAnswerParts: GeneratedAnswerPart[] = []

  for (const ee of sortedEvents) {
    if (isPromptDebug(ee.payload)) {
      debugRefs.push({
        eventId: ee.event.id,
        eventType: ee.event.eventType,
        seqNo: ee.event.seqNo,
      })
      continue
    }

    if (isAssistantStreamEvent(ee.event.eventType)) {
      continue
    }

    if (isConfirmationTraceEvent(ee)) {
      continue
    }

    if (isNoisyRuntimeHeartbeat(ee)) {
      continue
    }

    if (shouldPromoteContextToAnswer(ee)) {
      const text = renderableContextText(ee.payload)
      if (text) generatedAnswerParts.push({ text, createdAt: ee.event.createdAt })
      continue
    }

    if (shouldHideInteractionRequestEvent(ee, confirmationMap)) {
      continue
    }

    if (isReasoningEvent(ee)) {
      const key = reasoningLifecycleKey(ee)
      const idx = reasoningLifecycleIndex.get(key)
      if (idx === undefined) {
        reasoningLifecycleIndex.set(key, reasoningLifecycles.length)
        reasoningLifecycles.push({ key, events: [ee] })
      } else {
        reasoningLifecycles[idx].events.push(ee)
      }
    } else if (isToolLifecycleEvent(ee.event.eventType, ee.payload)) {
      const callId = ee.payload.toolCallId || null
      const name = toolNameFromPayload(ee.payload)
      const rawName = rawToolNameFromPayload(ee.payload)

      let matched = false

      if (callId && toolLifecycleIndex.has(callId)) {
        const idx = toolLifecycleIndex.get(callId)!
        toolLifecycles[idx].events.push(ee)
        matched = true
      }

      if (!matched && !callId) {
        const isTraceForExistingTool = ee.event.eventType === 'PROCESS_TRACE' && ee.payload.kind === 'tool_call'
        const openLifecycle = [...toolLifecycles].reverse().find(candidate => {
          const sameIdentity = !candidate.toolCallId
            && candidate.toolName === name
            && candidate.events.every(e => e.event.workflowNodeInstanceId === ee.event.workflowNodeInstanceId)
          if (!sameIdentity) return false
          if (isTraceForExistingTool) return true
          return !candidate.events.some(e => e.event.eventType === 'SKILL_COMPLETED')
        })
        if (openLifecycle) {
          openLifecycle.events.push(ee)
          matched = true
        }
      }

      if (!matched && callId && isWorkflowSkillPayload(ee.payload, ee.event)) {
        const openWorkflowSkill = [...toolLifecycles].reverse().find(candidate => {
          if (!candidate.toolCallId) return false
          if (!candidate.toolCallId.startsWith('workflow:')) return false
          if (candidate.events.some(e => e.event.eventType === 'SKILL_COMPLETED')) return false
          return candidate.toolName === name
            && candidate.events.every(e => e.event.workflowNodeInstanceId === ee.event.workflowNodeInstanceId)
        })
        if (openWorkflowSkill) {
          openWorkflowSkill.events.push(ee)
          matched = true
        }
      }

      if (!matched) {
        const newLifecycle: ToolLifecycle = {
          toolCallId: callId,
          toolName: name,
          rawToolName: rawName,
          events: [ee],
        }
        const idx = toolLifecycles.length
        toolLifecycles.push(newLifecycle)
        if (callId) {
          toolLifecycleIndex.set(callId, idx)
        }
      }
    } else {
      nonToolEvents.push(ee)
    }
  }

  const allOrderedEvents: OrderedRuntimeEvent[] = []

  for (const lc of toolLifecycles) {
    if (isInternalSkillLoaderLifecycle(lc)) {
      continue
    }
    const sorted = [...lc.events].sort(sortEvents)
    allOrderedEvents.push({ type: 'lifecycle', lifecycle: lc, sortKey: sorted[0] })
  }
  for (const lc of reasoningLifecycles) {
    const sorted = [...lc.events].sort(sortEvents)
    allOrderedEvents.push({ type: 'reasoning', lifecycle: lc, sortKey: sorted[0] })
  }
  for (const ee of nonToolEvents) {
    allOrderedEvents.push({ type: 'single', event: ee })
  }

  allOrderedEvents.sort((a, b) => {
    const keyA = orderedRuntimeSortKey(a)
    const keyB = orderedRuntimeSortKey(b)
    return sortEvents(keyA, keyB)
  })

  const projectedSteps: ExecutionStep[] = allOrderedEvents.map((item, index) => {
    if (item.type === 'lifecycle') {
      return buildStepFromToolLifecycle(item.lifecycle, index + 1)
    }
    if (item.type === 'reasoning') {
      return buildStepFromReasoningLifecycle(item.lifecycle, index + 1)
    }
    const confId = item.event.payload.confirmationId
    const confirmation = confId ? confirmationMap.get(confId) : undefined
    return buildStepFromSingleEvent(item.event, index + 1, confirmation)
  })
  const steps = dedupeVisibleExecutionSteps(projectedSteps.filter(isVisibleExecutionStep))

  const answerText = mergeAnswerText(extractAnswerText(messages), generatedAnswerParts.map(part => part.text))
  const hasStreamingText = streamingText.trim().length > 0

  let turnStatus: TurnStatus = running ? 'running' : 'completed'

  const pendingInteractionCandidate = findPendingInteraction(
    sortedEvents,
    confirmationMap,
  )
  const pendingInteraction = pendingInteractionCandidate?.interaction

  if (pendingInteraction) {
    turnStatus = 'waiting_input'
  }

  if (steps.some(s => s.status === 'failed') && turnStatus !== 'waiting_input') {
    turnStatus = 'failed'
  }

  const turns = buildTurnsFromMessages(
    messages,
    steps,
    debugRefs,
    enrichedEvents,
    activeSessionId,
    turnStatus,
    pendingInteraction ?? undefined,
    pendingInteractionCandidate?.createdAt,
    answerText,
    hasStreamingText ? streamingText : '',
    generatedAnswerParts,
    currentActionForTurn(turnStatus, steps, hasStreamingText, pendingInteraction),
  )

  return turns
}

function extractAnswerText(messages: ProjectorMessage[]): string {
  return messages
    .filter(m => m.role === 'ASSISTANT' && m.content)
    .map(m => m.content!)
    .join('\n')
}

function parseOptionsFromJson(value: unknown): Array<{ value: string; label: string; description?: string }> {
  return parseDecisionOptions(value)
}

function findPendingInteraction(
  events: EnrichedEvent[],
  confirmationMap: Map<string, ProjectorConfirmation>,
): PendingInteractionCandidate | null {
  for (let i = events.length - 1; i >= 0; i--) {
    const ee = events[i]
    if (ee.event.eventType === 'CONFIRMATION_CREATED' || ee.event.eventType === 'PERMISSION_REQUIRED') {
      const confId = ee.payload.confirmationId || ee.payload.id
      const confirmation = confId ? confirmationMap.get(confId) : undefined

      if (confirmation && (confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION')) {
        const options = confirmation.optionsJson
          ? parseOptionsFromJson(confirmation.optionsJson)
          : ee.payload.options
            ? parseOptionsFromJson(ee.payload.options)
            : []

        return {
          interaction: {
            confirmationId: confId || ee.event.id,
            question: ee.payload.question || confirmation.title || 'Confirmation required',
            prompt: ee.payload.contextSummary || undefined,
            options,
            status: 'waiting',
            requestType: confirmation.requestType,
            interactionType: confirmation.interactionType ?? undefined,
          },
          createdAt: ee.event.createdAt,
        }
      }

      // PERMISSION_REQUIRED with no confirmation record — synthesize from event payload
      if (ee.event.eventType === 'PERMISSION_REQUIRED' && !confirmation) {
        const options = ee.payload.options
          ? parseOptionsFromJson(ee.payload.options)
          : []

        return {
          interaction: {
            confirmationId: confId || ee.event.id,
            question: ee.payload.question || ee.payload.title || 'Permission required',
            prompt: ee.payload.contextSummary || undefined,
            options,
            status: 'waiting',
            requestType: 'PERMISSION',
          },
          createdAt: ee.event.createdAt,
        }
      }
    }
  }
  return null
}

function buildTurnsFromMessages(
  messages: ProjectorMessage[],
  steps: ExecutionStep[],
  debugRefs: RawEventRef[],
  enrichedEvents: EnrichedEvent[],
  activeSessionId: string | null,
  overallStatus: TurnStatus,
  pendingInteraction: InteractionProjection | undefined,
  pendingInteractionCreatedAt: string | undefined,
  answerText: string,
  streamingText: string,
  generatedAnswerParts: GeneratedAnswerPart[],
  currentAction: CurrentActionProjection | undefined,
): ConversationTurnProjection[] {
  if (messages.length === 0) {
    if (steps.length === 0 && !streamingText.trim() && !answerText.trim() && !pendingInteraction) return []

    const hasStreamingText = streamingText.trim().length > 0
    const finalizedSteps = completeDanglingReasoningSteps(steps, overallStatus)
    const answer: AnswerProjection = {
      role: 'assistant',
      text: hasStreamingText ? streamingText : answerText,
      generatedByStepIds: finalizedSteps.map(s => s.id),
      streaming: hasStreamingText ? true : undefined,
    }

    return [{
      turnId: `turn-${activeSessionId || 'default'}`,
      sessionId: activeSessionId || '',
      anchorCreatedAt: finalizedSteps[0]?.startedAt,
      status: overallStatus,
      answer,
      steps: finalizedSteps,
      displayItems: buildDisplayItems(
        `turn-${activeSessionId || 'default'}`,
        overallStatus,
        answer,
        finalizedSteps,
        currentAction,
        pendingInteraction,
      ),
      currentAction,
      pendingInteraction,
      debugRefs,
    }]
  }

  const sortedMessages = [...messages].sort((a, b) => {
    if (a.seqNo !== b.seqNo) return a.seqNo - b.seqNo
    const timeA = timestamp(a.createdAt)
    const timeB = timestamp(b.createdAt)
    if (timeA !== timeB) return timeA - timeB
    return a.id.localeCompare(b.id)
  })

  type MessageGroup = { userMsg?: ProjectorMessage; assistantMsgs: ProjectorMessage[] }
  const groups: MessageGroup[] = []
  let current: MessageGroup = { assistantMsgs: [] }

  for (const msg of sortedMessages) {
    if (msg.role === 'USER') {
      if (current.userMsg || current.assistantMsgs.length > 0) {
        groups.push(current)
      }
      current = { userMsg: msg, assistantMsgs: [] }
    } else {
      current.assistantMsgs.push(msg)
    }
  }
  groups.push(current)

  const groupBoundaryTimes = groups.map(g => {
    const times = [
      g.userMsg ? timestamp(g.userMsg.createdAt) : 0,
      ...g.assistantMsgs.map(m => timestamp(m.createdAt)),
    ]
    return times.length > 0 ? Math.min(...times) : 0
  })

  function assignGroupIndex(stepStartTime: number): number {
    for (let i = groups.length - 1; i >= 0; i--) {
      if (stepStartTime >= groupBoundaryTimes[i]) return i
    }
    return 0
  }

  const pendingInteractionGroupIndex = pendingInteraction
    ? assignGroupIndex(timestamp(pendingInteractionCreatedAt))
    : -1

  const stepsByGroup: ExecutionStep[][] = groups.map(() => [])
  const debugRefsByGroup: RawEventRef[][] = groups.map(() => [])
  const generatedTextByGroup: string[][] = groups.map(() => [])
  for (const step of steps) {
    const stepTime = timestamp(step.startedAt)
    const gi = assignGroupIndex(stepTime)
    stepsByGroup[gi].push(step)
  }
  for (const ref of debugRefs) {
    const refEvent = enrichedEvents.find(e => e.event.id === ref.eventId)
    const refTime = refEvent ? timestamp(refEvent.event.createdAt) : 0
    const gi = assignGroupIndex(refTime)
    debugRefsByGroup[gi].push(ref)
  }
  for (const part of generatedAnswerParts) {
    const gi = assignGroupIndex(timestamp(part.createdAt))
    generatedTextByGroup[gi].push(part.text)
  }

  const turns: ConversationTurnProjection[] = []
  for (let gi = 0; gi < groups.length; gi++) {
    const group = groups[gi]
    const isLast = gi === groups.length - 1
    const isPendingInteractionGroup = gi === pendingInteractionGroupIndex
    const anchorMessage = group.userMsg ?? group.assistantMsgs[0]

    const turnAnswerText = group.assistantMsgs
      .filter(m => m.content)
      .map(m => m.content!)
      .join('\n')

    const hasStreamingText = isLast && streamingText.trim().length > 0
    const groupSteps = stepsByGroup[gi]
    const groupStatus = isPendingInteractionGroup
      ? 'waiting_input'
      : isLast
        ? overallStatus === 'waiting_input' ? 'completed' : overallStatus
        : 'completed'
    const finalizedGroupSteps = completeDanglingReasoningSteps(groupSteps, groupStatus)
    const answer: AnswerProjection = {
      role: 'assistant',
      text: hasStreamingText ? streamingText : mergeAnswerText(turnAnswerText, generatedTextByGroup[gi]),
      generatedByStepIds: finalizedGroupSteps.map(s => s.id),
      streaming: hasStreamingText ? true : undefined,
    }

    const groupCurrentAction = isPendingInteractionGroup
      ? currentActionForTurn('waiting_input', finalizedGroupSteps, false, pendingInteraction)
      : isLast
        ? currentAction
        : undefined
    const groupPendingInteraction = isPendingInteractionGroup ? pendingInteraction : undefined

    turns.push({
      turnId: `turn-${gi}-${activeSessionId || 'default'}`,
      sessionId: activeSessionId || group.userMsg?.sessionId || group.assistantMsgs[0]?.sessionId || '',
      anchorMessageId: anchorMessage?.id,
      anchorCreatedAt: anchorMessage?.createdAt ?? finalizedGroupSteps[0]?.startedAt,
      status: groupStatus,
      answer,
      steps: finalizedGroupSteps,
      displayItems: buildDisplayItems(
        `turn-${gi}-${activeSessionId || 'default'}`,
        groupStatus,
        answer,
        finalizedGroupSteps,
        groupCurrentAction,
        groupPendingInteraction,
      ),
      currentAction: groupCurrentAction,
      pendingInteraction: groupPendingInteraction,
      debugRefs: debugRefsByGroup[gi],
    })
  }

  return turns
}
