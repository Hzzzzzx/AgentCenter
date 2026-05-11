import { defineStore } from 'pinia'
import { ref } from 'vue'
import { confirmationApi } from '../api/confirmations'
import type { ConfirmationRequestDto, ResolveConfirmationRequest, RuntimeEventDto } from '../api/types'

export const useConfirmationStore = defineStore('confirmations', () => {
  const pendingConfirmations = ref<ConfirmationRequestDto[]>([])
  const currentConfirmation = ref<ConfirmationRequestDto | null>(null)
  const loading = ref(false)

  async function loadPending() {
    loading.value = true
    try {
      const [pending, inConversation] = await Promise.all([
        confirmationApi.list('PENDING'),
        confirmationApi.list('IN_CONVERSATION'),
      ])
      pendingConfirmations.value = [...pending, ...inConversation]
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
      if (payload.id) {
        if (!pendingConfirmations.value.some((c) => c.id === payload.id)) {
          pendingConfirmations.value.push(payload as ConfirmationRequestDto)
        }
      } else {
        loadPending()
      }
    } catch {
      loadPending()
    }
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
  }

  return { pendingConfirmations, currentConfirmation, loading, loadPending, selectConfirmation, resolveConfirmation, rejectConfirmation, addFromEvent, removeFromPending, removeById }
})
