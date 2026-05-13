import { defineStore } from 'pinia'
import { ref } from 'vue'
import { confirmationApi } from '../api/confirmations'
import type { ConfirmationRequestDto, ResolveConfirmationRequest, RuntimeEventDto } from '../api/types'

export const useConfirmationStore = defineStore('confirmations', () => {
  const pendingConfirmations = ref<ConfirmationRequestDto[]>([])
  const currentConfirmation = ref<ConfirmationRequestDto | null>(null)
  const loading = ref(false)
  const recoveringIds = ref<Set<string>>(new Set())
  const recoverySyncTimers = new Map<string, ReturnType<typeof setTimeout>>()
  const RECOVERY_SYNC_DELAY_MS = 1200

  async function loadPending() {
    loading.value = true
    try {
      const [pending, inConversation] = await Promise.all([
        confirmationApi.list('PENDING'),
        confirmationApi.list('IN_CONVERSATION'),
      ])
      pendingConfirmations.value = [...pending, ...inConversation]
      syncRecoveringWithPending()
    } finally {
      loading.value = false
    }
  }

  async function selectConfirmation(id: string) {
    currentConfirmation.value = await confirmationApi.getById(id)
  }

  async function resolveConfirmation(id: string, data: ResolveConfirmationRequest, options: { remove?: boolean } = {}) {
    try {
      currentConfirmation.value = await confirmationApi.resolve(id, data)
      if (options.remove !== false) {
        removeById(id)
      }
      return currentConfirmation.value
    } catch (error) {
      throw error
    }
  }

  async function rejectConfirmation(id: string, comment?: string) {
    try {
      currentConfirmation.value = await confirmationApi.reject(id, { comment })
    } catch (error) {
      throw error
    }
  }

  function addFromEvent(event: RuntimeEventDto) {
    try {
      const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
      const id = payload.id || payload.confirmationId
      if (id && payload.requestType) {
        if (pendingConfirmations.value.some((c) => c.id === id)) {
          return
        }
        const confirmation: Record<string, unknown> = { ...payload, id }
        if (!isCompleteConfirmationPayload(confirmation)) {
          loadPending()
          return
        }
        pendingConfirmations.value.push(confirmation as unknown as ConfirmationRequestDto)
      } else {
        loadPending()
      }
    } catch {
      loadPending()
    }
  }

  function isCompleteConfirmationPayload(payload: Record<string, unknown>): boolean {
    return typeof payload.status === 'string'
      && typeof payload.title === 'string'
  }

  function removeFromPending(event: RuntimeEventDto) {
    try {
      const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
      const id = payload.confirmationId || payload.id
      if (id) {
        removeById(id)
      } else {
        loadPending()
      }
    } catch {
      loadPending()
    }
  }

  function removeById(id: string) {
    pendingConfirmations.value = pendingConfirmations.value.filter((c) => c.id !== id)
    if (currentConfirmation.value?.id === id) {
      currentConfirmation.value = null
    }
    clearRecovering(id)
  }

  function markRecovering(id: string) {
    recoveringIds.value = new Set([...recoveringIds.value, id])
    scheduleRecoverySync(id)
  }

  function clearRecovering(id: string) {
    const timer = recoverySyncTimers.get(id)
    if (timer) {
      clearTimeout(timer)
      recoverySyncTimers.delete(id)
    }
    const next = new Set(recoveringIds.value)
    next.delete(id)
    recoveringIds.value = next
  }

  function scheduleRecoverySync(id: string) {
    const existing = recoverySyncTimers.get(id)
    if (existing) clearTimeout(existing)
    recoverySyncTimers.set(id, setTimeout(() => {
      recoverySyncTimers.delete(id)
      void loadPending()
    }, RECOVERY_SYNC_DELAY_MS))
  }

  function syncRecoveringWithPending() {
    const activeIds = new Set(pendingConfirmations.value.map((confirmation) => confirmation.id))
    for (const id of recoveringIds.value) {
      if (!activeIds.has(id)) clearRecovering(id)
    }
  }

  return { pendingConfirmations, currentConfirmation, loading, recoveringIds, loadPending, selectConfirmation, resolveConfirmation, rejectConfirmation, addFromEvent, removeFromPending, removeById, markRecovering, clearRecovering }
})
