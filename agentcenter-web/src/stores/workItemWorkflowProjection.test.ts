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
})
