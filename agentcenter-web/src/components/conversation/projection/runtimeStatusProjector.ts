export type RuntimeStatusTone = 'ok' | 'running' | 'warning' | 'error' | 'blocked' | 'offline'

export interface RuntimeStatusEvent {
  id: string
  eventType: string
  payloadJson: string | null
  seqNo?: number | null
  createdAt: string
}

export interface RuntimeStatusInput {
  events: RuntimeStatusEvent[]
  connected: boolean
  running: boolean
  pendingExceptionCount: number
}

export interface RuntimeStatusProjection {
  tone: RuntimeStatusTone
  label: string
  detail?: string
  badge: string
  eventId?: string
  rawEventType?: string
  expandable: boolean
}

interface RuntimePayload {
  kind?: string
  type?: string
  status?: string
  title?: string
  label?: string
  summary?: string
  detail?: string
  reason?: string
  errorMessage?: string
  rawEventType?: string
  recoverable?: boolean
  retryAttempt?: number
  retryLimit?: number
}

const CONNECTION_RAW_EVENT_TYPES = new Set([
  'browser.sse.error',
  'event.stream.error',
  'event.stream.closed',
])

function parsePayload(payloadJson: string | null): RuntimePayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as RuntimePayload
      : {}
  } catch {
    return {}
  }
}

function payloadText(payload: RuntimePayload, keys: Array<keyof RuntimePayload>): string | undefined {
  for (const key of keys) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number') return String(value)
  }
  return undefined
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function sortEvents(a: RuntimeStatusEvent, b: RuntimeStatusEvent): number {
  const seqA = a.seqNo ?? Number.MAX_SAFE_INTEGER
  const seqB = b.seqNo ?? Number.MAX_SAFE_INTEGER
  if (seqA !== seqB) return seqA - seqB
  const timeA = timestamp(a.createdAt)
  const timeB = timestamp(b.createdAt)
  if (timeA !== timeB) return timeA - timeB
  return a.id.localeCompare(b.id)
}

export function isRuntimeConnectionDiagnostic(event: RuntimeStatusEvent): boolean {
  const payload = parsePayload(event.payloadJson)
  const kind = payload.kind?.toLowerCase()
  const type = payload.type?.toLowerCase()
  const rawEventType = payload.rawEventType ?? ''
  return kind === 'runtime_connection'
    || type === 'runtime_connection'
    || CONNECTION_RAW_EVENT_TYPES.has(rawEventType)
}

function isRuntimeRetryDiagnostic(event: RuntimeStatusEvent): boolean {
  const payload = parsePayload(event.payloadJson)
  return event.eventType === 'PROCESS_TRACE'
    && payload.kind === 'retry'
    && payload.status === 'retrying'
}

function isRuntimeTaskError(event: RuntimeStatusEvent): boolean {
  return event.eventType === 'ERROR' && !isRuntimeConnectionDiagnostic(event)
}

function latestMatching(
  events: RuntimeStatusEvent[],
  predicate: (event: RuntimeStatusEvent) => boolean,
): RuntimeStatusEvent | undefined {
  return [...events].sort(sortEvents).reverse().find(predicate)
}

function projectionFromEvent(
  event: RuntimeStatusEvent,
  tone: RuntimeStatusTone,
  fallbackLabel: string,
  badge: string,
): RuntimeStatusProjection {
  const payload = parsePayload(event.payloadJson)
  return {
    tone,
    label: payloadText(payload, ['title', 'label', 'summary']) ?? fallbackLabel,
    detail: payloadText(payload, ['summary', 'detail', 'errorMessage', 'reason']),
    badge,
    eventId: event.id,
    rawEventType: payload.rawEventType,
    expandable: true,
  }
}

export function projectRuntimeStatus(input: RuntimeStatusInput): RuntimeStatusProjection {
  if (input.pendingExceptionCount > 0) {
    return {
      tone: 'blocked',
      label: input.pendingExceptionCount > 1 ? `需要处理 ${input.pendingExceptionCount} 个异常` : '需要处理异常',
      detail: '工作流或 Runtime 恢复项正在等待你的选择。',
      badge: '待处理',
      expandable: true,
    }
  }

  const taskError = latestMatching(input.events, isRuntimeTaskError)
  if (taskError) {
    return projectionFromEvent(taskError, 'error', '当前任务执行失败', '异常')
  }

  const retry = latestMatching(input.events, isRuntimeRetryDiagnostic)
  if (retry) {
    return projectionFromEvent(retry, 'warning', '读取 Runtime 输出失败，正在自动重试', '重试中')
  }

  const connectionDiagnostic = latestMatching(input.events, isRuntimeConnectionDiagnostic)
  if (connectionDiagnostic) {
    const payload = parsePayload(connectionDiagnostic.payloadJson)
    const rawEventType = payload.rawEventType ?? ''
    if (rawEventType === 'browser.sse.error' && input.connected) {
      // Browser EventSource recovered; keep the historical event in the timeline, not in the live bar.
    } else {
      const label = rawEventType === 'event.stream.closed'
        ? 'OpenCode 事件流已断开'
        : rawEventType === 'browser.sse.error'
          ? '事件流连接异常'
          : 'OpenCode 事件流异常'
      return projectionFromEvent(connectionDiagnostic, 'warning', label, payload.recoverable === false ? '中断' : '可恢复')
    }
  }

  if (input.running) {
    return {
      tone: 'running',
      label: 'Runtime 正在处理',
      detail: '当前会话仍在生成回复或执行节点。',
      badge: '运行中',
      expandable: false,
    }
  }

  if (!input.connected) {
    return {
      tone: 'offline',
      label: '事件流未连接',
      detail: '进入会话后会自动订阅运行事件。',
      badge: '离线',
      expandable: false,
    }
  }

  return {
    tone: 'ok',
    label: 'Runtime 已连接',
    detail: '运行事件会在当前会话中实时同步。',
    badge: '正常',
    expandable: false,
  }
}
