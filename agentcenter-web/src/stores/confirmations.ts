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
      pendingConfirmations.value = await confirmationApi.list('PENDING')
    } finally {
      loading.value = false
    }
  }

  async function selectConfirmation(id: string) {
    currentConfirmation.value = await confirmationApi.getById(id)
  }

  async function resolveConfirmation(id: string, data: ResolveConfirmationRequest) {
    try {
      currentConfirmation.value = await confirmationApi.resolve(id, data)
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

  return { pendingConfirmations, currentConfirmation, loading, loadPending, selectConfirmation, resolveConfirmation, rejectConfirmation, addFromEvent }
})
