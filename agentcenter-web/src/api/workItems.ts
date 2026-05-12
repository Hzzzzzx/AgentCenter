import { get, post, put } from './client'
import type {
  WorkItemDto,
  CreateWorkItemRequest,
  StartWorkflowRequest,
  StartWorkflowResponse,
  WorkItemOverviewDto,
} from './types'

export interface WorkItemScopeQuery {
  projectId?: string | null
  spaceId?: string | null
  iterationId?: string | null
}

function scopedPath(path: string, scope?: WorkItemScopeQuery): string {
  const params = new URLSearchParams()
  if (scope?.projectId) params.set('projectId', scope.projectId)
  if (scope?.spaceId) params.set('spaceId', scope.spaceId)
  if (scope?.iterationId) params.set('iterationId', scope.iterationId)
  const query = params.toString()
  return query ? `${path}?${query}` : path
}

export const workItemApi = {
  list: (scope?: WorkItemScopeQuery) => get<WorkItemDto[]>(scopedPath('/work-items', scope)),
  overview: (scope?: WorkItemScopeQuery) => get<WorkItemOverviewDto>(scopedPath('/work-items/overview', scope)),
  getById: (id: string) => get<WorkItemDto>(`/work-items/${id}`),
  create: (data: CreateWorkItemRequest) => post<WorkItemDto>('/work-items', data),
  update: (id: string, data: Partial<CreateWorkItemRequest>) => put<WorkItemDto>(`/work-items/${id}`, data),
  startWorkflow: (id: string, data?: StartWorkflowRequest) => post<StartWorkflowResponse>(`/work-items/${id}/start-workflow`, data),
}
