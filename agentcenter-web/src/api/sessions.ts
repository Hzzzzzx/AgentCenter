import { get, post } from './client'
import type { AgentSessionDto, AgentMessageDto, SendMessageRequest } from './types'

export const sessionApi = {
  list: () => get<AgentSessionDto[]>('/agent-sessions'),
  create: (data: { sessionType: string; title?: string; workItemId?: string; workflowInstanceId?: string; runtimeType?: string }) =>
    post<AgentSessionDto>('/agent-sessions', data),
  getById: (id: string) => get<AgentSessionDto>(`/agent-sessions/${id}`),
  getMessages: (id: string) => get<AgentMessageDto[]>(`/agent-sessions/${id}/messages`),
  sendMessage: (id: string, data: SendMessageRequest) => post<AgentMessageDto>(`/agent-sessions/${id}/messages`, data),
}
