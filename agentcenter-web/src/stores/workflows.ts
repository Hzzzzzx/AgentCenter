import { defineStore } from 'pinia'
import { ref } from 'vue'
import { workflowApi } from '../api/workflows'
import { workItemApi } from '../api/workItems'
import type { RestartWorkflowRequest, StartWorkflowResponse, UpdateWorkflowDefinitionRequest, WorkflowDefinitionDto, WorkflowInstanceDto, WorkflowVersionDto } from '../api/types'

export const useWorkflowStore = defineStore('workflows', () => {
  const definitions = ref<WorkflowDefinitionDto[]>([])
  const activeWorkflowInstance = ref<WorkflowInstanceDto | null>(null)
  const loading = ref(false)
  const instancesByWorkItemId = ref<Record<string, WorkflowInstanceDto>>({})
  const versionsByWorkItemId = ref<Record<string, WorkflowVersionDto[]>>({})

  async function loadDefinitions(projectId?: string | null) {
    loading.value = true
    try {
      definitions.value = await workflowApi.listDefinitions(projectId)
    } finally {
      loading.value = false
    }
  }

  async function loadInstance(id: string) {
    activeWorkflowInstance.value = await workflowApi.getInstance(id)
  }

  async function saveDefinition(id: string, request: UpdateWorkflowDefinitionRequest) {
    const updated = await workflowApi.updateDefinition(id, request)
    definitions.value = definitions.value
      .map((definition) => definition.id === id ? { ...definition, status: 'DISABLED', isDefault: false } : definition)
      .concat(updated)
      .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || b.versionNo - a.versionNo)
    return updated
  }

  async function continueWorkflow(id: string) {
    const response = await workflowApi.continueWorkflow(id)
    activeWorkflowInstance.value = response.workflowInstance
    return response
  }

  async function retryNode(nodeInstanceId: string) {
    const response = await workflowApi.retryNode(nodeInstanceId)
    activeWorkflowInstance.value = response.workflowInstance
    return response
  }

  async function skipNode(nodeInstanceId: string) {
    const response = await workflowApi.skipNode(nodeInstanceId)
    activeWorkflowInstance.value = response.workflowInstance
    return response
  }

  async function restartWorkflow(workItemId: string, request?: RestartWorkflowRequest): Promise<StartWorkflowResponse> {
    const response = await workItemApi.restartWorkflow(workItemId, request)
    activeWorkflowInstance.value = response.workflowInstance
    if (response.workflowInstance.workItemId) {
      instancesByWorkItemId.value[response.workflowInstance.workItemId] = response.workflowInstance
    }
    await loadVersions(workItemId)
    return response
  }

  async function loadVersions(workItemId: string): Promise<WorkflowVersionDto[]> {
    const versions = await workItemApi.listWorkflowVersions(workItemId)
    versionsByWorkItemId.value = {
      ...versionsByWorkItemId.value,
      [workItemId]: versions,
    }
    return versions
  }

  function setActiveInstance(instance: WorkflowInstanceDto | null) {
    activeWorkflowInstance.value = instance
  }

  function upsertInstance(instance: WorkflowInstanceDto) {
    instancesByWorkItemId.value[instance.workItemId] = instance
  }

  async function loadInstanceForWorkItem(workItemId: string, instanceId: string) {
    const instance = await workflowApi.getInstance(instanceId)
    instancesByWorkItemId.value[workItemId] = instance
  }

  async function refreshInstance(instanceId: string): Promise<WorkflowInstanceDto> {
    const instance = await workflowApi.getInstance(instanceId)
    activeWorkflowInstance.value = instance
    if (instance.workItemId) {
      instancesByWorkItemId.value[instance.workItemId] = instance
    }
    return instance
  }

  return { definitions, activeWorkflowInstance, loading, instancesByWorkItemId, versionsByWorkItemId, loadDefinitions, saveDefinition, loadInstance, continueWorkflow, retryNode, skipNode, restartWorkflow, loadVersions, setActiveInstance, upsertInstance, loadInstanceForWorkItem, refreshInstance }
})
