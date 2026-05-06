import { defineStore } from 'pinia'
import { ref } from 'vue'
import { confirmationApi } from '../api/confirmations'
import type { ConfirmationRequestDto, ResolveConfirmationRequest } from '../api/types'

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
    currentConfirmation.value = await confirmationApi.resolve(id, data)
    pendingConfirmations.value = pendingConfirmations.value.filter((c) => c.id !== id)
  }

  async function rejectConfirmation(id: string, comment?: string) {
    currentConfirmation.value = await confirmationApi.reject(id, { comment })
    pendingConfirmations.value = pendingConfirmations.value.filter((c) => c.id !== id)
  }

  return { pendingConfirmations, currentConfirmation, loading, loadPending, selectConfirmation, resolveConfirmation, rejectConfirmation }
})
