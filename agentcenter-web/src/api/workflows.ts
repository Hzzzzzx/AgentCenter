import { get, post } from './client'
import type { WorkflowDefinitionDto, WorkflowInstanceDto, StartWorkflowResponse } from './types'

export const workflowApi = {
  listDefinitions: () => get<WorkflowDefinitionDto[]>('/workflow-definitions'),
  getInstance: (id: string) => get<WorkflowInstanceDto>(`/workflow-instances/${id}`),
  continueWorkflow: (id: string) => post<StartWorkflowResponse>(`/workflow-instances/${id}/continue`),
  retryNode: (nodeInstanceId: string) => post<StartWorkflowResponse>(`/workflow-node-instances/${nodeInstanceId}/retry`),
  skipNode: (nodeInstanceId: string) => post<StartWorkflowResponse>(`/workflow-node-instances/${nodeInstanceId}/skip`),
}
