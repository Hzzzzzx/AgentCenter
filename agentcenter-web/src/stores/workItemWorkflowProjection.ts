import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { workItemApi } from '../api/workItems'
import { useRuntimeSettingsStore } from './runtimeSettings'
import { useWorkItemStore } from './workItems'
import { useWorkflowStore } from './workflows'
import type {
  BatchStartWorkflowsResponse,
  StartWorkflowResponse,
  WorkItemDto,
  WorkItemType,
  WorkflowInstanceDto,
  WorkflowNodeStatus,
  WorkflowStatus,
} from '../api/types'

export type WorkItemWorkflowCommandState =
  | 'IDLE'
  | 'STARTING'
  | 'RUNNING'
  | 'READY'
  | 'WAITING_CONFIRMATION'
  | 'FAILED'
  | 'COMPLETED'

export type ProjectedWorkflowNode = {
  id: string | null
  label: string
  status: WorkflowNodeStatus
  kind: 'start' | 'skill' | 'end'
  dynamicNodeCount?: number
  recoveryCount?: number
  pendingConfirmationCount?: number
  latestSummary?: string | null
  errorMessage?: string | null
}

export type WorkItemWorkflowProjection = {
  workItemId: string
  hasWorkflow: boolean
  commandState: WorkItemWorkflowCommandState
  actionLabel: string
  actionDisabled: boolean
  workflowInstance: WorkflowInstanceDto | null
  nodes: ProjectedWorkflowNode[]
  currentNode: ProjectedWorkflowNode | null
  flowSummaryLabel: string
}

const workflowStatusLabels: Record<WorkflowStatus, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  BLOCKED: '已阻塞',
  FAILED: '异常',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  SUPERSEDED: '已替代',
}

const defaultStageLabels: Record<WorkItemDto['type'], string[]> = {
  FE: ['需求', '方案', '实施', '验证', '归档'],
  US: ['故事', '验收', '拆分', '评审', '归档'],
  TASK: ['理解', '计划', '执行', '验证', '总结'],
  WORK: ['分析', 'Runbook', '执行', '校验', '报告'],
  BUG: ['复现', '根因', '修复', '回归', '关闭'],
  VULN: ['分级', '影响', '修复', '验证', '归档'],
}

export const useWorkItemWorkflowProjectionStore = defineStore('workItemWorkflowProjection', () => {
  const startingIds = ref<Set<string>>(new Set())
  const startErrors = ref<Record<string, string>>({})
  const startingIdList = computed(() => [...startingIds.value])
  const workItemStore = useWorkItemStore()
  const workflowStore = useWorkflowStore()
  const runtimeSettingsStore = useRuntimeSettingsStore()

  function isStarting(workItemId: string): boolean {
    return startingIds.value.has(workItemId)
  }

  function errorFor(workItemId: string): string | undefined {
    return startErrors.value[workItemId]
  }

  async function startWorkflow(workItemId: string, response?: StartWorkflowResponse): Promise<StartWorkflowResponse> {
    if (isStarting(workItemId)) {
      throw new Error('工作流正在启动中')
    }

    startingIds.value = new Set([...startingIds.value, workItemId])
    const { [workItemId]: _removed, ...restErrors } = startErrors.value
    startErrors.value = restErrors

    try {
      const result = response ?? await workItemApi.startWorkflow(workItemId, {
        mode: runtimeSettingsStore.workflowRunMode,
      })
      if (result.workflowInstance) {
        workflowStore.setActiveInstance(result.workflowInstance)
        workflowStore.upsertInstance(result.workflowInstance)
      }
      await syncWorkItem(workItemId)
      return result
    } catch (error) {
      const message = error instanceof Error ? error.message : '启动工作流失败'
      startErrors.value = { ...startErrors.value, [workItemId]: message }
      throw error
    } finally {
      const next = new Set(startingIds.value)
      next.delete(workItemId)
      startingIds.value = next
    }
  }

  async function startWorkflowsBatch(
    workItemType: WorkItemType,
    workItemIds: string[],
  ): Promise<BatchStartWorkflowsResponse> {
    const uniqueIds = [...new Set(workItemIds.filter((id) => id.trim().length > 0))]
    const pendingIds = uniqueIds.filter((id) => !isStarting(id))
    if (pendingIds.length === 0) {
      throw new Error('没有可启动的工作项')
    }

    startingIds.value = new Set([...startingIds.value, ...pendingIds])
    startErrors.value = Object.fromEntries(
      Object.entries(startErrors.value).filter(([id]) => !pendingIds.includes(id))
    )

    try {
      const result = await workItemApi.startWorkflows({
        workItemType,
        workItemIds: pendingIds,
        limit: runtimeSettingsStore.batchStartWorkflowLimit,
        mode: runtimeSettingsStore.workflowRunMode,
      })
      for (const item of result.results) {
        if (item.response?.workflowInstance) {
          workflowStore.upsertInstance(item.response.workflowInstance)
        }
        if (item.status === 'FAILED') {
          startErrors.value = {
            ...startErrors.value,
            [item.workItemId]: item.reason || '批量启动失败',
          }
        }
      }
      await Promise.all(
        result.results
          .filter((item) => item.status === 'STARTED' || item.reason === '工作项已开始或不在初始阶段')
          .map((item) => syncWorkItem(item.workItemId))
      )
      return result
    } catch (error) {
      const message = error instanceof Error ? error.message : '批量启动工作流失败'
      startErrors.value = pendingIds.reduce<Record<string, string>>((next, id) => {
        next[id] = message
        return next
      }, { ...startErrors.value })
      throw error
    } finally {
      const next = new Set(startingIds.value)
      pendingIds.forEach((id) => next.delete(id))
      startingIds.value = next
    }
  }

  async function syncWorkItem(workItemId: string): Promise<WorkItemDto> {
    const item = await workItemStore.refreshItem(workItemId)
    hydrateInstanceFromSummary(item)
    return item
  }

  function syncWorkItemsFromList(items: WorkItemDto[] = workItemStore.items): void {
    items.forEach(hydrateInstanceFromSummary)
  }

  function projectionFor(item: WorkItemDto): WorkItemWorkflowProjection {
    const workflowInstance = workflowStore.instancesByWorkItemId[item.id] ?? instanceFromSummary(item)
    const hasWorkflow = Boolean(workflowInstance || item.currentWorkflowInstanceId || item.workflowSummary)
    const nodes = buildNodes(item, workflowInstance)
    const currentNode = pickCurrentNode(item, workflowInstance, nodes)
    const commandState = commandStateFor(item, workflowInstance, nodes)
    const actionLabel = actionLabelFor(commandState, workflowInstance)

    return {
      workItemId: item.id,
      hasWorkflow,
      commandState,
      actionLabel,
      actionDisabled: commandState === 'STARTING',
      workflowInstance,
      nodes,
      currentNode,
      flowSummaryLabel: commandState === 'STARTING' ? actionLabel : currentNode?.label ?? actionLabel,
    }
  }

  function hydrateInstanceFromSummary(item: WorkItemDto): void {
    const instance = instanceFromSummary(item)
    const existing = workflowStore.instancesByWorkItemId[item.id]
    if (instance && (!existing || existing.id !== instance.id)) {
      workflowStore.upsertInstance(instance)
      return
    }
    if (instance && existing.id === instance.id) {
      workflowStore.upsertInstance(mergeSummaryIntoInstance(existing, instance))
    }
  }

  function mergeSummaryIntoInstance(
    existing: WorkflowInstanceDto,
    summary: WorkflowInstanceDto,
  ): WorkflowInstanceDto {
    const summaryNodesById = new Map(summary.nodes.map((node) => [node.id, node]))
    const existingNodeIds = new Set(existing.nodes.map((node) => node.id))
    const mergedNodes = existing.nodes.map((node) => {
      const summaryNode = summaryNodesById.get(node.id)
      if (!summaryNode) return node
      return {
        ...node,
        status: summaryNode.status,
        errorMessage: summaryNode.errorMessage ?? node.errorMessage,
        skillName: node.skillName ?? summaryNode.skillName,
      }
    })
    const appendedSummaryNodes = summary.nodes.filter((node) => !existingNodeIds.has(node.id))

    return {
      ...existing,
      status: summary.status,
      currentNodeInstanceId: summary.currentNodeInstanceId,
      nodes: [...mergedNodes, ...appendedSummaryNodes],
    }
  }

  function instanceFromSummary(item: WorkItemDto): WorkflowInstanceDto | null {
    const summary = item.workflowSummary
    if (!summary) return null
    return {
      id: summary.instanceId,
      workItemId: item.id,
      workflowDefinitionId: '',
      status: summary.status,
      currentNodeInstanceId: summary.currentNodeInstanceId,
      nodes: summary.nodes.map((node) => ({
        id: node.id,
        nodeDefinitionId: '',
        status: node.status,
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: null,
        startedAt: null,
        completedAt: null,
        errorMessage: node.errorMessage ?? null,
        skillName: node.definitionName ?? node.skillName,
      })),
      startedAt: null,
      completedAt: null,
    }
  }

  function buildNodes(item: WorkItemDto, workflowInstance: WorkflowInstanceDto | null): ProjectedWorkflowNode[] {
    const summary = item.workflowSummary
    const workflowStatus = workflowInstance?.status ?? summary?.status ?? null
    const hasWorkflow = Boolean(workflowInstance || item.currentWorkflowInstanceId || summary)

    if (summary?.stages?.length) {
      return withAnchors(
        summary.stages.map((stage) => ({
          id: stage.id,
          label: stage.name ?? stage.skillName ?? '阶段',
          status: nodeStatusFromWorkflow(workflowInstance, stage.id, stage.status),
          kind: 'skill' as const,
          dynamicNodeCount: stage.dynamicNodeCount,
          recoveryCount: stage.recoveryCount,
          pendingConfirmationCount: stage.pendingConfirmationCount,
          latestSummary: stage.latestSummary,
          errorMessage: nodeErrorFromWorkflow(workflowInstance, stage.id, stage.errorMessage),
        })),
        workflowStatus,
        hasWorkflow,
      )
    }

    if (summary?.nodes.length) {
      return withAnchors(
        summary.nodes.map((node) => ({
          id: node.id,
          label: node.definitionName ?? node.skillName ?? '阶段',
          status: nodeStatusFromWorkflow(workflowInstance, node.id, node.status),
          kind: 'skill' as const,
          errorMessage: nodeErrorFromWorkflow(workflowInstance, node.id, node.errorMessage),
        })),
        workflowStatus,
        hasWorkflow,
      )
    }

    if (workflowInstance?.nodes.length) {
      return withAnchors(
        workflowInstance.nodes.map((node) => ({
          id: node.id,
          label: node.skillName ?? '阶段',
          status: node.status,
          kind: 'skill' as const,
          latestSummary: node.summary,
          errorMessage: node.errorMessage,
        })),
        workflowStatus,
        hasWorkflow,
      )
    }

    return withAnchors(
      defaultStageNamesFor(item).map((label) => ({
        id: null,
        label,
        status: 'PENDING' as WorkflowNodeStatus,
        kind: 'skill' as const,
      })),
      workflowStatus,
      hasWorkflow,
    )
  }

  function nodeStatusFromWorkflow(
    workflowInstance: WorkflowInstanceDto | null,
    nodeId: string,
    fallback: WorkflowNodeStatus,
  ): WorkflowNodeStatus {
    return workflowInstance?.nodes.find((node) => node.id === nodeId)?.status ?? fallback
  }

  function nodeErrorFromWorkflow(
    workflowInstance: WorkflowInstanceDto | null,
    nodeId: string,
    fallback?: string | null,
  ): string | null | undefined {
    return workflowInstance?.nodes.find((node) => node.id === nodeId)?.errorMessage ?? fallback
  }

  function withAnchors(
    skillNodes: ProjectedWorkflowNode[],
    workflowStatus: WorkflowStatus | null,
    hasWorkflow: boolean,
  ): ProjectedWorkflowNode[] {
    const allSkillsCompleted = skillNodes.length > 0 && skillNodes.every((node) => isNodeComplete(node.status))
    return [
      {
        id: 'start',
        label: '开始',
        status: hasWorkflow ? 'COMPLETED' : 'RUNNING',
        kind: 'start',
      },
      ...skillNodes,
      {
        id: 'end',
        label: '完成',
        status: workflowStatus === 'COMPLETED' || allSkillsCompleted ? 'COMPLETED' : 'PENDING',
        kind: 'end',
      },
    ]
  }

  function defaultStageNamesFor(item: WorkItemDto): string[] {
    const definition = workflowStore.definitions.find(
      (candidate) => candidate.workItemType === item.type && candidate.status === 'ENABLED' && candidate.isDefault
    ) ?? workflowStore.definitions.find(
      (candidate) => candidate.workItemType === item.type && candidate.status === 'ENABLED'
    )
    if (definition?.nodes.length) {
      return [...definition.nodes]
        .sort((a, b) => a.orderNo - b.orderNo)
        .map((node) => node.name)
    }
    return defaultStageLabels[item.type]
  }

  function commandStateFor(
    item: WorkItemDto,
    workflowInstance: WorkflowInstanceDto | null,
    nodes: ProjectedWorkflowNode[],
  ): WorkItemWorkflowCommandState {
    const skillNodes = nodes.filter((node) => node.kind === 'skill')
    if (isStarting(item.id)) return 'STARTING'
    const currentNodeId = workflowInstance?.currentNodeInstanceId ?? item.workflowSummary?.currentNodeInstanceId
    const currentNode = currentNodeId ? skillNodes.find((node) => node.id === currentNodeId) : null
    if (currentNode?.status === 'WAITING_CONFIRMATION') return 'WAITING_CONFIRMATION'
    if (currentNode?.status === 'RUNNING') return 'RUNNING'
    if (currentNode?.status === 'READY') return 'READY'
    if (currentNode?.status === 'FAILED') return 'FAILED'
    if (skillNodes.some((node) => node.status === 'WAITING_CONFIRMATION')) return 'WAITING_CONFIRMATION'
    if (skillNodes.some((node) => node.status === 'RUNNING')) return 'RUNNING'
    if (skillNodes.some((node) => node.status === 'READY')) return 'READY'
    if (workflowInstance?.status === 'FAILED' || workflowInstance?.status === 'BLOCKED' || skillNodes.some((node) => node.status === 'FAILED')) {
      return 'FAILED'
    }
    if (workflowInstance?.status === 'COMPLETED' || item.workflowSummary?.status === 'COMPLETED' || item.status === 'DONE') {
      return 'COMPLETED'
    }
    return 'IDLE'
  }

  function actionLabelFor(
    commandState: WorkItemWorkflowCommandState,
    workflowInstance: WorkflowInstanceDto | null,
  ): string {
    if (commandState === 'STARTING') return '启动中'
    if (commandState === 'WAITING_CONFIRMATION') return '待确认'
    if (commandState === 'RUNNING' || commandState === 'READY') return '处理中'
    if (commandState === 'FAILED') return '查看异常'
    if (commandState === 'COMPLETED') return '查看结果'
    if (commandState === 'IDLE') return '开始处理'
    return workflowStatusLabels[workflowInstance?.status ?? 'PENDING'] ?? '开始处理'
  }

  function pickCurrentNode(
    item: WorkItemDto,
    workflowInstance: WorkflowInstanceDto | null,
    nodes: ProjectedWorkflowNode[],
  ): ProjectedWorkflowNode | null {
    const currentNodeId = workflowInstance?.currentNodeInstanceId ?? item.workflowSummary?.currentNodeInstanceId
    if (currentNodeId) {
      const currentNode = nodes.find((node) => node.id === currentNodeId)
      if (currentNode) return currentNode
    }
    return nodes.find((node) => node.kind === 'skill' && !isNodeComplete(node.status))
      ?? nodes.find((node) => node.status === 'READY' && node.kind === 'skill')
      ?? nodes.find((node) => node.status === 'FAILED')
      ?? null
  }

  function isNodeComplete(status: WorkflowNodeStatus): boolean {
    return status === 'COMPLETED' || status === 'SKIPPED'
  }

  return {
    startingIds,
    startingIdList,
    startErrors,
    isStarting,
    errorFor,
    startWorkflow,
    startWorkflowsBatch,
    syncWorkItem,
    syncWorkItemsFromList,
    projectionFor,
    hydrateInstanceFromSummary,
  }
})
