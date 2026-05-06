import { defineStore } from 'pinia'
import { ref } from 'vue'
import { eventApi } from '../api/events'
import type { RuntimeEventDto } from '../api/types'
import { useSessionStore } from './sessions'

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<RuntimeEventDto[]>([])
  const connected = ref(false)
  const activeSessionId = ref<string | null>(null)
  const activeSse = ref<EventSource | null>(null)
  const streamingText = ref('')
  let finalSyncTimer: ReturnType<typeof setTimeout> | null = null
  let finalSyncAttempts = 0
  let lastUserSeqNo = 0

  function connectSSE(sessionId: string) {
    if (activeSessionId.value === sessionId && activeSse.value) {
      connected.value = true
      return
    }
    disconnectSSE()
    activeSessionId.value = sessionId
    connected.value = true
    finalSyncAttempts = 0
    lastUserSeqNo = latestUserSeqNo()

    activeSse.value = eventApi.streamSessionEvents(sessionId, (event: RuntimeEventDto) => {
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
    connected.value = false
    streamingText.value = ''
  }

  function clearEvents() {
    events.value = []
  }

  function applyRuntimeEvent(event: RuntimeEventDto) {
    if (event.eventType === 'ASSISTANT_DELTA') {
      try {
        const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
        const text = payload.delta || payload.text || payload.label || ''
        if (text) {
          if (!streamingText.value) {
            clearFinalSyncTimer()
            finalSyncAttempts = 0
            lastUserSeqNo = latestUserSeqNo()
          }
          streamingText.value += text
        }
      } catch { /* ignore parse errors */ }
    }

    if (event.eventType === 'STATUS') {
      try {
        const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
        const status = payload.status || payload.label || ''
        if (status === 'waiting_user' || status === 'idle') {
          scheduleFinalMessageSync()
        }
      } catch { /* ignore parse errors */ }
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
        streamingText.value = ''
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
