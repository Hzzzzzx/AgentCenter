import { get, post, put } from './client'
import type { WorkflowDefinitionDto, WorkflowInstanceDto, StartWorkflowResponse, UpdateWorkflowDefinitionRequest } from './types'

export const workflowApi = {
  listDefinitions: () => get<WorkflowDefinitionDto[]>('/workflow-definitions'),
  updateDefinition: (id: string, request: UpdateWorkflowDefinitionRequest) =>
    put<WorkflowDefinitionDto>(`/workflow-definitions/${id}`, request),
  getInstance: (id: string) => get<WorkflowInstanceDto>(`/workflow-instances/${id}`),
  continueWorkflow: (id: string) => post<StartWorkflowResponse>(`/workflow-instances/${id}/continue`),
  retryNode: (nodeInstanceId: string) => post<StartWorkflowResponse>(`/workflow-node-instances/${nodeInstanceId}/retry`),
  skipNode: (nodeInstanceId: string) => post<StartWorkflowResponse>(`/workflow-node-instances/${nodeInstanceId}/skip`),
}
