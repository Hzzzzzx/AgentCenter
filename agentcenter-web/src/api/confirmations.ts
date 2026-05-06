import { get, post } from './client'
import type { ConfirmationRequestDto, ResolveConfirmationRequest } from './types'

export const confirmationApi = {
  list: (status?: string) => get<ConfirmationRequestDto[]>(`/confirmations${status ? `?status=${status}` : ''}`),
  getById: (id: string) => get<ConfirmationRequestDto>(`/confirmations/${id}`),
  enterSession: (id: string) => post<ConfirmationRequestDto>(`/confirmations/${id}/enter-session`),
  resolve: (id: string, data: ResolveConfirmationRequest) => post<ConfirmationRequestDto>(`/confirmations/${id}/resolve`, data),
  reject: (id: string, data?: { comment?: string }) => post<ConfirmationRequestDto>(`/confirmations/${id}/reject`, data),
}
