import { defineStore } from 'pinia'
import { ref } from 'vue'
import { eventApi } from '../api/events'
import type { RuntimeEventDto } from '../api/types'
import { useSessionStore } from './sessions'
import { useWorkflowStore } from './workflows'
import { useConfirmationStore } from './confirmations'
import { useWorkItemWorkflowProjectionStore } from './workItemWorkflowProjection'

const STREAM_FRAME_DELAY_MS = 16
const STREAM_FRAME_BATCH_SIZE = 8

type RuntimePayload = Record<string, unknown>

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<RuntimeEventDto[]>([])
  const connected = ref(false)
  const activeSessionId = ref<string | null>(null)
  const activeSse = ref<EventSource | null>(null)
  const streamingText = ref('')
  const busy = ref(false)
  const lastNodeState = ref<string | null>(null)
  const lastNodeStateReason = ref<string | null>(null)
  const seenEventIds = new Set<string>()
  const streamQueue: string[] = []
  let finalSyncTimer: ReturnType<typeof setTimeout> | null = null
  let streamFlushTimer: ReturnType<typeof setTimeout> | null = null
  let finalSyncAttempts = 0
  let lastUserSeqNo = 0
  let connectedAtMs = 0

  function connectSSE(sessionId: string) {
    if (activeSessionId.value === sessionId && activeSse.value) {
      connected.value = true
      return
    }
    disconnectSSE()
    activeSessionId.value = sessionId
    connected.value = true
    connectedAtMs = Date.now()
    events.value = []
    seenEventIds.clear()
    resetStreamingOutput()
    markIdle()
    lastNodeState.value = null
    lastNodeStateReason.value = null
    finalSyncAttempts = 0
    lastUserSeqNo = latestUserSeqNo()

    const subscribedSessionId = sessionId
    activeSse.value = eventApi.streamSessionEvents(subscribedSessionId, (event: RuntimeEventDto) => {
      if (activeSessionId.value !== subscribedSessionId) return
      if (event.sessionId !== subscribedSessionId) return
      if (hasSeenEvent(event)) return
      events.value.push(event)
      applyRuntimeEvent(event)
    })
  }

  function disconnectSSE() {
    if (activeSse.value) {
      activeSse.value.close()
      activeSse.value = null
    }
    clearFinalSyncTimer()
    resetStreamingOutput()
    connected.value = false
  }

  function clearEvents() {
    events.value = []
    seenEventIds.clear()
  }

  function applyRuntimeEvent(event: RuntimeEventDto) {
    if (event.eventType === 'ASSISTANT_DELTA') {
      markBusy()
      const payload = parsePayload(event.payloadJson)
      const text = textField(payload, ['delta', 'text', 'label'])
      if (text && shouldApplyAssistantDelta(event)) {
        if (!streamingText.value && streamQueue.length === 0) {
          clearFinalSyncTimer()
          finalSyncAttempts = 0
          lastUserSeqNo = latestUserSeqNo()
        }
        queueStreamingText(text)
      }
    }

    if (event.eventType === 'ASSISTANT_COMPLETED') {
      flushPendingStreamingText()
      markIdle()
      scheduleFinalMessageSync(0)
    }

    if (event.eventType === 'STATUS') {
      const payload = parsePayload(event.payloadJson)
      const status = textField(payload, ['status', 'label'])
      if (status === 'waiting_user' || status === 'idle') {
        markIdle()
        scheduleFinalMessageSync()
      } else if (status) {
        markBusy()
      }
    }

    if (event.eventType === 'MCP_CALL') {
      markBusy()
    }

    if (event.eventType === 'SKILL_STARTED') {
      markBusy()
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }

    if (event.eventType === 'ERROR') {
      markIdle()
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }

    if (event.eventType === 'PERMISSION_REQUIRED') {
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }

    if (event.eventType === 'SKILL_COMPLETED') {
      flushPendingStreamingText()
      markIdle()
      const payload = parsePayload(event.payloadJson)
      const ns = textField(payload, ['nodeState'])
      lastNodeState.value = ns || null
      lastNodeStateReason.value = textField(payload, ['nodeStateReason']) || null
      scheduleFinalMessageSync(200)
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }

    if (event.eventType === 'CONFIRMATION_CREATED') {
      const confirmationStore = useConfirmationStore()
      confirmationStore.addFromEvent(event)
      flushPendingStreamingText()
      scheduleFinalMessageSync(0)
    }

    if (event.eventType === 'CONFIRMATION_RESOLVED') {
      const confirmationStore = useConfirmationStore()
      confirmationStore.removeFromPending(event)
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }

    if (event.eventType === 'PROCESS_TRACE') {
      if (event.workflowInstanceId) {
        void syncWorkflowAndWorkItem(event)
      }
    }
  }

  async function syncWorkflowAndWorkItem(event: RuntimeEventDto) {
    try {
      const workflowStore = useWorkflowStore()
      const workflowProjectionStore = useWorkItemWorkflowProjectionStore()
      const instance = event.workflowInstanceId
        ? await workflowStore.refreshInstance(event.workflowInstanceId)
        : null
      const workItemId = event.workItemId ?? instance?.workItemId
      if (workItemId) {
        await workflowProjectionStore.syncWorkItem(workItemId)
      }
    } catch (error) {
      console.error('Failed to sync workflow work item state:', error)
    }
  }

  function scheduleFinalMessageSync(delayMs = 900) {
    clearFinalSyncTimer()
    finalSyncAttempts = 0
    runFinalMessageSync(delayMs)
  }

  function runFinalMessageSync(delayMs: number) {
    finalSyncTimer = setTimeout(async () => {
      const synced = await reloadActiveMessages()
      if (!synced && finalSyncAttempts < 4) {
        finalSyncAttempts += 1
        runFinalMessageSync(800)
      }
    }, delayMs)
  }

  function clearFinalSyncTimer() {
    if (finalSyncTimer) {
      clearTimeout(finalSyncTimer)
      finalSyncTimer = null
    }
  }

  async function reloadActiveMessages(): Promise<boolean> {
    const sessionStore = useSessionStore()
    const sessionId = activeSessionId.value
    if (!sessionId || sessionStore.activeSession?.id !== sessionId) {
      return true
    }
    const streamedText = streamingText.value
    try {
      await sessionStore.selectSession(sessionId)
      lastUserSeqNo = latestUserSeqNo()
      if (!streamedText.trim() || hasFinalAssistantAfterLastUser()) {
        resetStreamingOutput()
        return true
      }
      streamingText.value = streamedText
      return false
    } catch {
      streamingText.value = streamedText
      return false
    }
  }

  function latestUserSeqNo() {
    const sessionStore = useSessionStore()
    return sessionStore.messages
      .filter((message) => message.role === 'USER')
      .reduce((max, message) => Math.max(max, message.seqNo || 0), 0)
  }

  function hasFinalAssistantAfterLastUser() {
    const sessionStore = useSessionStore()
    return sessionStore.messages.some((message) =>
      message.role === 'ASSISTANT'
      && message.status !== 'STREAMING'
      && (message.seqNo || 0) > lastUserSeqNo
      && Boolean(message.content?.trim())
    )
  }

  function hasSeenEvent(event: RuntimeEventDto): boolean {
    if (!event.id) return false
    if (seenEventIds.has(event.id)) return true
    seenEventIds.add(event.id)
    return false
  }

  function shouldApplyAssistantDelta(event: RuntimeEventDto): boolean {
    const createdAtMs = Date.parse(event.createdAt)
    const replayed = Number.isFinite(createdAtMs) && createdAtMs < connectedAtMs - 1000
    return !(replayed && hasFinalAssistantAfterLastUser())
  }

  function queueStreamingText(text: string) {
    streamQueue.push(...splitDeltas(text))
    scheduleStreamingFlush()
  }

  function scheduleStreamingFlush() {
    if (streamFlushTimer) return
    streamFlushTimer = setTimeout(() => {
      streamFlushTimer = null
      flushStreamingFrame()
    }, STREAM_FRAME_DELAY_MS)
  }

  function flushStreamingFrame() {
    const next = streamQueue.splice(0, STREAM_FRAME_BATCH_SIZE).join('')
    if (next) {
      streamingText.value += next
    }
    if (streamQueue.length > 0) {
      scheduleStreamingFlush()
    }
  }

  function flushPendingStreamingText() {
    if (streamFlushTimer) {
      clearTimeout(streamFlushTimer)
      streamFlushTimer = null
    }
    if (streamQueue.length > 0) {
      streamingText.value += streamQueue.splice(0).join('')
    }
  }

  function resetStreamingOutput() {
    if (streamFlushTimer) {
      clearTimeout(streamFlushTimer)
      streamFlushTimer = null
    }
    streamQueue.length = 0
    streamingText.value = ''
  }

  function markBusy() {
    busy.value = true
  }

  function markIdle() {
    busy.value = false
  }

  function splitDeltas(text: string): string[] {
    const out: string[] = []
    let buffer = ''
    Array.from(text).forEach((char) => {
      buffer += char
      const isCjk = /[\u3400-\u9fff]/.test(char)
      const isEdge = /[\n。！？!?，,；;：:、]/.test(char)
      const limit = isCjk ? 1 : /\s/.test(char) ? 1 : 4
      if (isEdge || buffer.length >= limit) {
        out.push(buffer)
        buffer = ''
      }
    })
    if (buffer) out.push(buffer)
    return out
  }

  function parsePayload(payloadJson: string | null): RuntimePayload {
    if (!payloadJson) return {}
    try {
      const parsed: unknown = JSON.parse(payloadJson)
      return isRecord(parsed) ? parsed : {}
    } catch {
      return {}
    }
  }

  function isRecord(value: unknown): value is RuntimePayload {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
  }

  function textField(payload: RuntimePayload, keys: string[]): string {
    for (const key of keys) {
      const value = payload[key]
      if (typeof value === 'string' && value.length > 0) {
        return value
      }
    }
    return ''
  }

  return {
    events,
    connected,
    activeSessionId,
    activeSse,
    streamingText,
    busy,
    lastNodeState,
    lastNodeStateReason,
    connectSSE,
    disconnectSSE,
    clearEvents,
    markBusy,
    markIdle,
    resetStreamingOutput,
  }
})
