import { defineStore } from 'pinia'
import { ref } from 'vue'
import { eventApi } from '../api/events'
import type { RuntimeEventDto } from '../api/types'
import { useSessionStore } from './sessions'
import { useWorkflowStore } from './workflows'
import { useConfirmationStore } from './confirmations'

const STREAM_FRAME_DELAY_MS = 16
const STREAM_FRAME_BATCH_SIZE = 8

type RuntimePayload = Record<string, unknown>

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<RuntimeEventDto[]>([])
  const connected = ref(false)
  const activeSessionId = ref<string | null>(null)
  const activeSse = ref<EventSource | null>(null)
  const streamingText = ref('')
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
    finalSyncAttempts = 0
    lastUserSeqNo = latestUserSeqNo()

    activeSse.value = eventApi.streamSessionEvents(sessionId, (event: RuntimeEventDto) => {
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

    if (event.eventType === 'STATUS') {
      const payload = parsePayload(event.payloadJson)
      const status = textField(payload, ['status', 'label'])
      if (status === 'waiting_user' || status === 'idle') {
        scheduleFinalMessageSync()
      }
    }

    if (event.eventType === 'SKILL_COMPLETED') {
      if (event.workflowInstanceId) {
        const workflowStore = useWorkflowStore()
        workflowStore.refreshInstance(event.workflowInstanceId)
      }
    }

    if (event.eventType === 'CONFIRMATION_CREATED') {
      const confirmationStore = useConfirmationStore()
      confirmationStore.addFromEvent(event)
      if (event.workflowInstanceId) {
        const workflowStore = useWorkflowStore()
        workflowStore.refreshInstance(event.workflowInstanceId)
      }
    }
  }

  function scheduleFinalMessageSync() {
    clearFinalSyncTimer()
    finalSyncAttempts = 0
    runFinalMessageSync(900)
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

  function resetStreamingOutput() {
    if (streamFlushTimer) {
      clearTimeout(streamFlushTimer)
      streamFlushTimer = null
    }
    streamQueue.length = 0
    streamingText.value = ''
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
    connectSSE,
    disconnectSSE,
    clearEvents,
  }
})
