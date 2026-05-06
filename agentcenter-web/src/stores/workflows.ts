import { defineStore } from 'pinia'
import { ref } from 'vue'
import { workflowApi } from '../api/workflows'
import type { WorkflowDefinitionDto, WorkflowInstanceDto } from '../api/types'

export const useWorkflowStore = defineStore('workflows', () => {
  const definitions = ref<WorkflowDefinitionDto[]>([])
  const activeWorkflowInstance = ref<WorkflowInstanceDto | null>(null)
  const loading = ref(false)

  async function loadDefinitions() {
    loading.value = true
    try {
      definitions.value = await workflowApi.listDefinitions()
    } finally {
      loading.value = false
    }
  }

  async function loadInstance(id: string) {
    activeWorkflowInstance.value = await workflowApi.getInstance(id)
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

  function setActiveInstance(instance: WorkflowInstanceDto | null) {
    activeWorkflowInstance.value = instance
  }

  return { definitions, activeWorkflowInstance, loading, loadDefinitions, loadInstance, continueWorkflow, retryNode, skipNode, setActiveInstance }
})
