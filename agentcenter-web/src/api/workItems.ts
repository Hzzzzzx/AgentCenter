import { get, post, put } from './client'
import type { WorkItemDto, CreateWorkItemRequest, StartWorkflowRequest, StartWorkflowResponse } from './types'

export const workItemApi = {
  list: () => get<WorkItemDto[]>('/work-items'),
  getById: (id: string) => get<WorkItemDto>(`/work-items/${id}`),
  create: (data: CreateWorkItemRequest) => post<WorkItemDto>('/work-items', data),
  update: (id: string, data: Partial<CreateWorkItemRequest>) => put<WorkItemDto>(`/work-items/${id}`, data),
  startWorkflow: (id: string, data?: StartWorkflowRequest) => post<StartWorkflowResponse>(`/work-items/${id}/start-workflow`, data),
}
