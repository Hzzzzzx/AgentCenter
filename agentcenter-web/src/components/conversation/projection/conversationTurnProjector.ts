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
  actionType?: string
  reason?: string
  errorMessage?: string
  label?: string
  output?: string
  isError?: boolean
  success?: boolean
  skillName?: string
  toolCallId?: string
  confirmationId?: string
  requestType?: string
  actionDescription?: string
  question?: string
  contextSummary?: string
  options?: string
  comment?: string
  resolutionPayload?: string
  command?: string
  input?: string
  args?: unknown
  arguments?: unknown
  result?: string
  detail?: string
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
  events: EnrichedEvent[]
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

function sortEvents(a: EnrichedEvent, b: EnrichedEvent): number {
  const seqA = a.event.seqNo ?? Number.MAX_SAFE_INTEGER
  const seqB = b.event.seqNo ?? Number.MAX_SAFE_INTEGER
  if (seqA !== seqB) return seqA - seqB

  const timeA = Date.parse(a.event.createdAt) || 0
  const timeB = Date.parse(b.event.createdAt) || 0
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

function isPromptDebug(payload: ParsedPayload): boolean {
  return payload.kind === 'prompt_debug'
}

function isRuntimeStatusHeartbeat(eventType: string, payload: ParsedPayload): boolean {
  return eventType === 'STATUS'
    && payload.type === 'status'
    && ['running', 'idle', 'waiting_user'].includes(payload.label || '')
}

function isConfirmationEvent(eventType: string): boolean {
  return eventType === 'CONFIRMATION_CREATED' || eventType === 'CONFIRMATION_RESOLVED' || eventType === 'PERMISSION_REQUIRED'
}

function isInactiveConfirmationCreated(
  ee: EnrichedEvent,
  confirmationMap: Map<string, ProjectorConfirmation>,
): boolean {
  if (ee.event.eventType !== 'CONFIRMATION_CREATED') return false
  const confId = ee.payload.confirmationId || ee.payload.id
  const confirmation = confId ? confirmationMap.get(confId) : undefined
  return !confirmation || (confirmation.status !== 'PENDING' && confirmation.status !== 'IN_CONVERSATION')
}

function toolNameFromPayload(payload: ParsedPayload): string {
  return payload.toolName || payload.skillName || payload.label || payload.command || 'tool'
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
    .map(e => e.payload.output || e.payload.result)
    .find(v => v !== undefined && v !== null && v !== '') ?? undefined
  const inputSummary = sorted
    .map(e => e.payload.input || e.payload.command)
    .find(v => v !== undefined && v !== null && v !== '')
    ?? (firstPayload.input || firstPayload.command)

  const defaultExpanded = status === 'failed'
    || (outputSummary !== undefined && outputSummary.length < 200)
    || false

  return {
    type: 'tool',
    toolCallId: lifecycle.toolCallId || `legacy-${sorted[0].event.id}`,
    rawName: lifecycle.toolName,
    displayName: readableToolName(lifecycle.toolName, outputSummary),
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

function readableToolName(toolName: string, output?: string): string {
  const skillName = extractLoadedSkillName(output)
  if (skillName) return `读取 Skill：${skillName}`
  return toolName
}

function extractLoadedSkillName(output?: string): string | undefined {
  if (!output) return undefined
  const match = output.match(/^## Skill:\s*([^\n]+)/)
  return match?.[1]?.trim() || undefined
}

function isInternalSkillLoaderLifecycle(lifecycle: ToolLifecycle): boolean {
  const sorted = [...lifecycle.events].sort(sortEvents)
  const output = sorted
    .map(e => e.payload.output || e.payload.result)
    .find(v => v !== undefined && v !== null && v !== '')
  const name = lifecycle.toolName.toLowerCase()
  return name === 'skill' && Boolean(extractLoadedSkillName(output))
}

function buildArtifactPart(ee: EnrichedEvent): ArtifactEvidencePart {
  const p = ee.payload
  return {
    type: 'artifact',
    artifactId: p.artifactId,
    filePath: p.filePath,
    title: p.title || 'Artifact',
    summary: p.summary,
    diffAvailable: p.diffAvailable,
    rawEventRef: {
      eventId: ee.event.id,
      eventType: ee.event.eventType,
      seqNo: ee.event.seqNo,
    },
  }
}

function buildDecisionPart(ee: EnrichedEvent, confirmation?: ProjectorConfirmation): DecisionGatePart {
  const p = ee.payload
  const confirmationId = p.confirmationId || p.id || ee.event.id
  let options: Array<{ value: string; label: string; description?: string }> = []
  if (p.options) {
    try {
      const parsed: unknown = JSON.parse(p.options)
      if (Array.isArray(parsed)) {
        options = parsed.map((item: unknown) => {
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
    } catch { /* use empty options */ }
  } else if (confirmation?.optionsJson) {
    try {
      const parsed: unknown = JSON.parse(confirmation.optionsJson)
      if (Array.isArray(parsed)) {
        options = parsed.map((item: unknown) => {
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
    } catch { /* empty */ }
  }

  const activeConfirmation = confirmation?.status === 'PENDING' || confirmation?.status === 'IN_CONVERSATION'
  const status = ee.event.eventType === 'CONFIRMATION_RESOLVED' || !activeConfirmation
    ? 'resolved'
    : 'waiting'
  const selectedValue = status === 'resolved' ? selectedDecisionValueFromAction(p, options) : undefined
  if (selectedValue) {
    options = options.filter(option => option.value === selectedValue)
  }

  return {
    type: 'decision',
    confirmationId,
    question: p.question || confirmation?.title || 'Confirmation required',
    prompt: p.contextSummary || undefined,
    options,
    recommended: selectedValue || p.recommended || undefined,
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
    payload.resolutionPayload,
    parseResolutionPayloadValue(payload.resolutionPayload),
    payload.actionDescription,
  ].filter((value): value is string => Boolean(value && value.trim()))

  return candidates
    .map(value => value.trim())
    .flatMap(value => [value, value.toUpperCase()])
    .find(value => options.some(option => option.value === value))
}

function parseResolutionPayloadValue(value: string | undefined): string | undefined {
  if (!value) return undefined
  try {
    const parsed: unknown = JSON.parse(value)
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

function determineStepKind(ee: EnrichedEvent): StepKind {
  const { event, payload } = ee
  if (isToolLifecycleEvent(event.eventType, payload)) return 'tool'
  if (payload.kind === 'artifact') return 'artifact'
  if (payload.kind === 'reasoning_summary') return 'reasoning'
  if (payload.kind === 'error' || event.eventType === 'ERROR') return 'error'
  if (isConfirmationEvent(event.eventType)) return 'decision'
  if (payload.kind === 'node_status') return 'status'
  return 'context'
}

function buildStepFromSingleEvent(ee: EnrichedEvent, order: number, confirmation?: ProjectorConfirmation): ExecutionStep {
  const { event, payload } = ee
  const kind = determineStepKind(ee)
  const parts: StepPart[] = []

  if (kind === 'artifact') {
    parts.push(buildArtifactPart(ee))
  } else if (kind === 'decision') {
    parts.push(buildDecisionPart(ee, confirmation))
  } else if (kind === 'error') {
    parts.push({
      type: 'error',
      message: payload.errorMessage || payload.detail || payload.reason || 'Error occurred',
      detail: payload.output || payload.result || undefined,
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
      label: payload.title || payload.label || 'Status',
      detail: payload.summary || payload.status || undefined,
      rawEventRef: {
        eventId: event.id,
        eventType: event.eventType,
        seqNo: event.seqNo,
      },
    })
  } else if (kind === 'reasoning') {
    parts.push({
      type: 'reasoning',
      summary: payload.summary || payload.title || 'Reasoning',
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

  const stepStatus: 'running' | 'completed' | 'failed' = kind === 'error' ? 'failed'
    : event.eventType === 'SKILL_STARTED' ? 'running'
    : 'completed'

  return {
    id: event.id,
    order,
    kind,
    title: keyProgressTitle(payload, event.eventType) || stepTitle(payload, event.eventType, kind),
    summary: kind === 'context' ? undefined : payload.summary || undefined,
    status: stepStatus,
    startedAt: event.createdAt,
    completedAt: kind === 'error' || stepStatus === 'completed' ? event.createdAt : undefined,
    parts,
    rawEventRefs: [{
      eventId: event.id,
      eventType: event.eventType,
      seqNo: event.seqNo,
    }],
  }
}

function renderableContextText(payload: ParsedPayload): string | undefined {
  const text = payload.output || payload.result || payload.summary || payload.title
  if (!text) return undefined
  const normalized = normalizeMarkdownText(text)
  if (!isMarkdownLike(normalized)) return undefined
  return normalized
}

function normalizeMarkdownText(text: string): string {
  return text
    .replace(/\\n/g, '\n')
    .replace(/：\s*---\s*#/g, '：\n\n---\n\n#')
    .replace(/:\s*---\s*#/g, ':\n\n---\n\n#')
    .replace(/\s+---\s+#/g, '\n\n---\n\n#')
    .trim()
}

function isMarkdownLike(text: string): boolean {
  return /(^|\n)#{1,6}[ \t]+\S/.test(text)
    || /\*\*[^*]+\*\*/.test(text)
    || /(^|\n)\s*[-*]\s+\S/.test(text)
    || text.includes('\n\n')
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
  return payload.title || payload.label || toolNameFromPayload(payload) || eventType
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

function keyProgressTitle(payload: ParsedPayload, eventType: string): string | undefined {
  if (eventType === 'SKILL_STARTED') {
    const skill = payload.skillName || payload.toolName || payload.label
    return skill ? `开始执行 ${skill}` : '开始执行 Skill'
  }
  if (eventType === 'SKILL_COMPLETED') {
    if (payload.nodeState === 'NEEDS_USER_INPUT') return userFacingNodeReason(payload.nodeStateReason || payload.reason)
    if (payload.nodeState === 'READY_TO_ADVANCE') return `${payload.skillName || 'Skill'} 已生成产物`
    return `${payload.skillName || 'Skill'} 执行完成`
  }
  if (eventType === 'CONFIRMATION_RESOLVED') {
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

function isActiveInteractionEvent(
  ee: EnrichedEvent,
  confirmationMap: Map<string, ProjectorConfirmation>,
): boolean {
  if (ee.event.eventType !== 'CONFIRMATION_CREATED' && ee.event.eventType !== 'PERMISSION_REQUIRED') {
    return false
  }

  const confId = ee.payload.confirmationId || ee.payload.id
  const confirmation = confId ? confirmationMap.get(confId) : undefined
  if (!confirmation) {
    return ee.event.eventType === 'PERMISSION_REQUIRED'
  }

  return confirmation.status === 'PENDING' || confirmation.status === 'IN_CONVERSATION'
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
  const nonToolEvents: EnrichedEvent[] = []
  const debugRefs: RawEventRef[] = []

  for (const ee of sortedEvents) {
    if (isPromptDebug(ee.payload)) {
      debugRefs.push({
        eventId: ee.event.id,
        eventType: ee.event.eventType,
        seqNo: ee.event.seqNo,
      })
      continue
    }

    if (isRuntimeStatusHeartbeat(ee.event.eventType, ee.payload)) {
      continue
    }

    if (isActiveInteractionEvent(ee, confirmationMap)) {
      continue
    }

    if (isInactiveConfirmationCreated(ee, confirmationMap)) {
      continue
    }

    if (ee.payload.kind === 'node_status') {
      continue
    }

    if (isToolLifecycleEvent(ee.event.eventType, ee.payload)) {
      const callId = ee.payload.toolCallId || null
      const name = toolNameFromPayload(ee.payload)

      let matched = false

      if (callId && toolLifecycleIndex.has(callId)) {
        const idx = toolLifecycleIndex.get(callId)!
        toolLifecycles[idx].events.push(ee)
        matched = true
      }

      if (!matched && !callId) {
        const lastLc = toolLifecycles[toolLifecycles.length - 1]
        if (lastLc && !lastLc.toolCallId && lastLc.toolName === name) {
          const hasCompletion = lastLc.events.some(
            e => e.event.eventType === 'SKILL_COMPLETED'
          )
          if (!hasCompletion) {
            lastLc.events.push(ee)
            matched = true
          }
        }
      }

      if (!matched) {
        const newLifecycle: ToolLifecycle = {
          toolCallId: callId,
          toolName: name,
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

  const allOrderedEvents: Array<
    | { type: 'lifecycle'; lifecycle: ToolLifecycle; sortKey: EnrichedEvent }
    | { type: 'single'; event: EnrichedEvent }
  > = []

  for (const lc of toolLifecycles) {
    if (isInternalSkillLoaderLifecycle(lc)) {
      continue
    }
    const sorted = [...lc.events].sort(sortEvents)
    allOrderedEvents.push({ type: 'lifecycle', lifecycle: lc, sortKey: sorted[0] })
  }
  for (const ee of nonToolEvents) {
    allOrderedEvents.push({ type: 'single', event: ee })
  }

  allOrderedEvents.sort((a, b) => {
    const keyA = a.type === 'lifecycle' ? a.sortKey : a.event
    const keyB = b.type === 'lifecycle' ? b.sortKey : b.event
    return sortEvents(keyA, keyB)
  })

  const steps: ExecutionStep[] = allOrderedEvents.map((item, index) => {
    if (item.type === 'lifecycle') {
      return buildStepFromToolLifecycle(item.lifecycle, index + 1)
    }
    const confId = item.event.payload.confirmationId
    const confirmation = confId ? confirmationMap.get(confId) : undefined
    return buildStepFromSingleEvent(item.event, index + 1, confirmation)
  })

  const answerText = extractAnswerText(messages)
  const hasStreamingText = streamingText.trim().length > 0

  let turnStatus: TurnStatus = running ? 'running' : 'completed'

  const pendingInteraction = findPendingInteraction(
    sortedEvents,
    confirmationMap,
  )

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
    answerText,
    hasStreamingText ? streamingText : '',
  )

  return turns
}

function extractAnswerText(messages: ProjectorMessage[]): string {
  return messages
    .filter(m => m.role === 'ASSISTANT' && m.content)
    .map(m => m.content!)
    .join('\n')
}

function parseOptionsFromJson(json: string): Array<{ value: string; label: string; description?: string }> {
  try {
    const parsed: unknown = JSON.parse(json)
    if (Array.isArray(parsed)) {
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
  } catch { /* empty */ }
  return []
}

function findPendingInteraction(
  events: EnrichedEvent[],
  confirmationMap: Map<string, ProjectorConfirmation>,
): InteractionProjection | null {
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
          confirmationId: confId || ee.event.id,
          question: ee.payload.question || confirmation.title || 'Confirmation required',
          prompt: ee.payload.contextSummary || undefined,
          options,
          status: 'waiting',
        }
      }

      // PERMISSION_REQUIRED with no confirmation record — synthesize from event payload
      if (ee.event.eventType === 'PERMISSION_REQUIRED' && !confirmation) {
        const options = ee.payload.options
          ? parseOptionsFromJson(ee.payload.options)
          : []

        return {
          confirmationId: confId || ee.event.id,
          question: ee.payload.question || ee.payload.title || 'Permission required',
          prompt: ee.payload.contextSummary || undefined,
          options,
          status: 'waiting',
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
  answerText: string,
  streamingText: string,
): ConversationTurnProjection[] {
  if (messages.length === 0) {
    if (steps.length === 0 && !streamingText.trim() && !pendingInteraction) return []

    const hasStreamingText = streamingText.trim().length > 0
    const answer: AnswerProjection = {
      role: 'assistant',
      text: hasStreamingText ? streamingText : answerText,
      generatedByStepIds: steps.map(s => s.id),
      streaming: hasStreamingText ? true : undefined,
    }

    return [{
      turnId: `turn-${activeSessionId || 'default'}`,
      sessionId: activeSessionId || '',
      status: overallStatus,
      answer,
      steps,
      pendingInteraction,
      debugRefs,
    }]
  }

  const sortedMessages = [...messages].sort((a, b) => {
    if (a.seqNo !== b.seqNo) return a.seqNo - b.seqNo
    const timeA = Date.parse(a.createdAt) || 0
    const timeB = Date.parse(b.createdAt) || 0
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
      g.userMsg ? Date.parse(g.userMsg.createdAt) || 0 : 0,
      ...g.assistantMsgs.map(m => Date.parse(m.createdAt) || 0),
    ]
    return times.length > 0 ? Math.min(...times) : 0
  })

  function assignGroupIndex(stepStartTime: number): number {
    for (let i = groups.length - 1; i >= 0; i--) {
      if (stepStartTime >= groupBoundaryTimes[i]) return i
    }
    return 0
  }

  const stepsByGroup: ExecutionStep[][] = groups.map(() => [])
  const debugRefsByGroup: RawEventRef[][] = groups.map(() => [])
  for (const step of steps) {
    const stepTime = Date.parse(step.startedAt ?? '') || 0
    const gi = assignGroupIndex(stepTime)
    stepsByGroup[gi].push(step)
  }
  for (const ref of debugRefs) {
    const refEvent = enrichedEvents.find(e => e.event.id === ref.eventId)
    const refTime = refEvent ? (Date.parse(refEvent.event.createdAt) || 0) : 0
    const gi = assignGroupIndex(refTime)
    debugRefsByGroup[gi].push(ref)
  }

  const turns: ConversationTurnProjection[] = []
  for (let gi = 0; gi < groups.length; gi++) {
    const group = groups[gi]
    const isLast = gi === groups.length - 1

    const turnAnswerText = group.assistantMsgs
      .filter(m => m.content)
      .map(m => m.content!)
      .join('\n')

    const hasStreamingText = isLast && streamingText.trim().length > 0
    const groupSteps = stepsByGroup[gi]
    const answer: AnswerProjection = {
      role: 'assistant',
      text: hasStreamingText ? streamingText : turnAnswerText,
      generatedByStepIds: groupSteps.map(s => s.id),
      streaming: hasStreamingText ? true : undefined,
    }

    turns.push({
      turnId: `turn-${gi}-${activeSessionId || 'default'}`,
      sessionId: activeSessionId || group.userMsg?.sessionId || group.assistantMsgs[0]?.sessionId || '',
      status: isLast ? overallStatus : 'completed',
      answer,
      steps: groupSteps,
      pendingInteraction: isLast ? pendingInteraction : undefined,
      debugRefs: debugRefsByGroup[gi],
    })
  }

  return turns
}
