import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkflowStore } from './workflows'
import { useWorkItemWorkflowProjectionStore } from './workItemWorkflowProjection'
import type { WorkItemDto, WorkflowInstanceDto, WorkflowNodeInstanceDto } from '../api/types'

function makeNode(overrides: Partial<WorkflowNodeInstanceDto>): WorkflowNodeInstanceDto {
  return {
    id: 'node-lld',
    nodeDefinitionId: 'def-lld',
    status: 'RUNNING',
    inputArtifactId: null,
    outputArtifactId: null,
    agentSessionId: null,
    startedAt: null,
    completedAt: null,
    errorMessage: null,
    nodeKind: 'STAGE',
    origin: 'DEFINITION',
    parentNodeInstanceId: null,
    stageKey: 'lld',
    skillName: 'lld-design',
    summary: '详细设计 (LLD)',
    reason: null,
    sequenceNo: 4,
    agentState: null,
    agentStateReason: null,
    ...overrides,
  }
}

function makeInstance(overrides: Partial<WorkflowInstanceDto>): WorkflowInstanceDto {
  return {
    id: 'wf-1',
    workItemId: 'work-1',
    workflowDefinitionId: 'definition-1',
    status: 'RUNNING',
    currentNodeInstanceId: 'node-lld',
    nodes: [makeNode({})],
    startedAt: null,
    completedAt: null,
    ...overrides,
  }
}

function makeWorkItem(overrides: Partial<WorkItemDto>): WorkItemDto {
  return {
    id: 'work-1',
    code: 'FE2002',
    type: 'FE',
    title: '仪表盘数据可视化',
    description: null,
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    providerId: null,
    externalWorkItemId: null,
    projectId: null,
    spaceId: null,
    iterationId: null,
    projectContextId: null,
    projectSpaceId: null,
    projectIterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-1',
    workflowSummary: null,
    createdAt: '2026-05-12T00:00:00Z',
    updatedAt: '2026-05-12T00:00:00Z',
    ...overrides,
  }
}

describe('useWorkItemWorkflowProjectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('merges latest workflow summary into an existing cached instance', () => {
    const workflowStore = useWorkflowStore()
    const projectionStore = useWorkItemWorkflowProjectionStore()
    workflowStore.upsertInstance(makeInstance({
      nodes: [
        makeNode({
          status: 'RUNNING',
          outputArtifactId: 'artifact-lld',
          agentSessionId: 'session-lld',
        }),
      ],
    }))

    const item = makeWorkItem({
      workflowSummary: {
        instanceId: 'wf-1',
        status: 'COMPLETED',
        currentNodeInstanceId: 'node-lld',
        nodes: [
          {
            id: 'node-lld',
            definitionName: '详细设计 (LLD)',
            skillName: 'lld-design',
            status: 'COMPLETED',
            errorMessage: null,
          },
        ],
        stages: [
          {
            id: 'node-lld',
            stageKey: 'lld',
            name: '详细设计 (LLD)',
            skillName: 'lld-design',
            status: 'COMPLETED',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '详细设计 (LLD)',
            errorMessage: null,
          },
        ],
      },
    })

    projectionStore.hydrateInstanceFromSummary(item)

    const merged = workflowStore.instancesByWorkItemId['work-1']
    const mergedNode = merged.nodes.find((node) => node.id === 'node-lld')
    expect(merged.status).toBe('COMPLETED')
    expect(mergedNode?.status).toBe('COMPLETED')
    expect(mergedNode?.outputArtifactId).toBe('artifact-lld')
    expect(mergedNode?.agentSessionId).toBe('session-lld')
    expect(projectionStore.projectionFor(item).commandState).toBe('COMPLETED')
  })

  it('uses a newer work item summary when cached workflow state is still on the previous stage', () => {
    const workflowStore = useWorkflowStore()
    const projectionStore = useWorkItemWorkflowProjectionStore()
    workflowStore.upsertInstance(makeInstance({
      currentNodeInstanceId: 'node-prd',
      nodes: [
        makeNode({
          id: 'node-prd',
          status: 'RUNNING',
          stageKey: 'requirement_refine',
          skillName: 'prd-design',
          summary: '需求整理 (PRD)',
          sequenceNo: 1,
        }),
        makeNode({
          id: 'node-hld',
          status: 'PENDING',
          stageKey: 'solution_design',
          skillName: 'hld-design',
          summary: '方案设计 (HLD)',
          sequenceNo: 2,
        }),
      ],
    }))

    const item = makeWorkItem({
      workflowSummary: {
        instanceId: 'wf-1',
        status: 'RUNNING',
        currentNodeInstanceId: 'node-hld',
        currentStageKey: 'solution_design',
        nodes: [
          {
            id: 'node-prd',
            definitionName: '需求整理 (PRD)',
            skillName: 'prd-design',
            status: 'COMPLETED',
            errorMessage: null,
          },
          {
            id: 'node-hld',
            definitionName: '方案设计 (HLD)',
            skillName: 'hld-design',
            status: 'RUNNING',
            errorMessage: null,
          },
        ],
        stages: [
          {
            id: 'node-prd',
            stageKey: 'requirement_refine',
            name: '需求整理 (PRD)',
            skillName: 'prd-design',
            status: 'COMPLETED',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '需求整理 (PRD)',
            errorMessage: null,
          },
          {
            id: 'node-hld',
            stageKey: 'solution_design',
            name: '方案设计 (HLD)',
            skillName: 'hld-design',
            status: 'RUNNING',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '方案设计 (HLD)',
            errorMessage: null,
          },
        ],
      },
    })

    const projection = projectionStore.projectionFor(item)

    expect(projection.currentNode?.id).toBe('node-hld')
    expect(projection.commandState).toBe('RUNNING')
    expect(projection.nodes.find((node) => node.id === 'node-prd')?.status).toBe('COMPLETED')
    expect(projection.nodes.find((node) => node.id === 'node-hld')?.status).toBe('RUNNING')
  })

  it('does not let a stale summary regress a cached workflow instance that already advanced', () => {
    const workflowStore = useWorkflowStore()
    const projectionStore = useWorkItemWorkflowProjectionStore()
    workflowStore.upsertInstance(makeInstance({
      currentNodeInstanceId: 'node-hld',
      nodes: [
        makeNode({
          id: 'node-prd',
          status: 'COMPLETED',
          stageKey: 'requirement_refine',
          skillName: 'prd-design',
          summary: '需求整理 (PRD)',
          sequenceNo: 1,
        }),
        makeNode({
          id: 'node-hld',
          status: 'RUNNING',
          stageKey: 'solution_design',
          skillName: 'hld-design',
          summary: '方案设计 (HLD)',
          sequenceNo: 2,
        }),
      ],
    }))

    const staleItem = makeWorkItem({
      workflowSummary: {
        instanceId: 'wf-1',
        status: 'RUNNING',
        currentNodeInstanceId: 'node-prd',
        currentStageKey: 'requirement_refine',
        nodes: [
          {
            id: 'node-prd',
            definitionName: '需求整理 (PRD)',
            skillName: 'prd-design',
            status: 'RUNNING',
            errorMessage: null,
          },
          {
            id: 'node-hld',
            definitionName: '方案设计 (HLD)',
            skillName: 'hld-design',
            status: 'PENDING',
            errorMessage: null,
          },
        ],
        stages: [
          {
            id: 'node-prd',
            stageKey: 'requirement_refine',
            name: '需求整理 (PRD)',
            skillName: 'prd-design',
            status: 'RUNNING',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '需求整理 (PRD)',
            errorMessage: null,
          },
          {
            id: 'node-hld',
            stageKey: 'solution_design',
            name: '方案设计 (HLD)',
            skillName: 'hld-design',
            status: 'PENDING',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '方案设计 (HLD)',
            errorMessage: null,
          },
        ],
      },
    })

    projectionStore.hydrateInstanceFromSummary(staleItem)

    const merged = workflowStore.instancesByWorkItemId['work-1']
    expect(merged.currentNodeInstanceId).toBe('node-hld')
    expect(merged.nodes.find((node) => node.id === 'node-prd')?.status).toBe('COMPLETED')
    expect(merged.nodes.find((node) => node.id === 'node-hld')?.status).toBe('RUNNING')

    const projection = projectionStore.projectionFor(staleItem)
    expect(projection.currentNode?.id).toBe('node-hld')
    expect(projection.commandState).toBe('RUNNING')
  })

  it('uses currentStageKey when the current node id is a dynamic child outside projected stages', () => {
    const projectionStore = useWorkItemWorkflowProjectionStore()
    const item = makeWorkItem({
      workflowSummary: {
        instanceId: 'wf-1',
        status: 'RUNNING',
        currentNodeInstanceId: 'node-hld-child',
        currentStageKey: 'solution_design',
        nodes: [
          {
            id: 'node-hld-child',
            definitionName: 'HLD 动态检查',
            skillName: 'hld-check',
            status: 'RUNNING',
            errorMessage: null,
          },
        ],
        stages: [
          {
            id: 'node-prd',
            stageKey: 'requirement_refine',
            name: '需求整理 (PRD)',
            skillName: 'prd-design',
            status: 'COMPLETED',
            dynamicNodeCount: 0,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: '需求整理 (PRD)',
            errorMessage: null,
          },
          {
            id: 'node-hld',
            stageKey: 'solution_design',
            name: '方案设计 (HLD)',
            skillName: 'hld-design',
            status: 'RUNNING',
            dynamicNodeCount: 1,
            recoveryCount: 0,
            pendingConfirmationCount: 0,
            latestSummary: 'HLD 动态检查',
            errorMessage: null,
          },
        ],
      },
    })

    const projection = projectionStore.projectionFor(item)

    expect(projection.currentNode?.id).toBe('node-hld')
    expect(projection.currentNode?.stageKey).toBe('solution_design')
    expect(projection.commandState).toBe('RUNNING')
  })
})
