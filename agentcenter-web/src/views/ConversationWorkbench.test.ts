import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConversationWorkbench from './ConversationWorkbench.vue'

const mocks = vi.hoisted(() => {
  const failedWorkItem = {
    id: 'work-1',
    code: 'FE0003',
    type: 'FE',
    title: '社区共享工具柜借还流程',
    description: null,
    status: 'BACKLOG',
    priority: 'MEDIUM',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-1',
    workflowSummary: {
      instanceId: 'wf-1',
      status: 'RUNNING',
      currentNodeInstanceId: 'node-1',
      nodes: [
        {
          id: 'node-1',
          definitionName: '详细设计 (LLD)',
          skillName: 'lld-design',
          status: 'FAILED',
          errorMessage: 'Agent Runtime 超时',
        },
      ],
      stages: [
        {
          id: 'node-1',
          stageKey: 'detail_design',
          name: '详细设计 (LLD)',
          skillName: 'lld-design',
          status: 'FAILED',
          dynamicNodeCount: 0,
          recoveryCount: 0,
          pendingConfirmationCount: 0,
          latestSummary: '详细设计 (LLD)',
          errorMessage: 'Agent Runtime 超时',
        },
      ],
    },
    createdAt: '2026-05-08T10:00:00Z',
    updatedAt: '2026-05-08T10:00:00Z',
  }

  const failedSession = {
    id: 'session-1',
    sessionType: 'WORK_ITEM',
    title: 'FE0003 · 社区共享工具柜借还流程',
    workItemId: 'work-1',
    workflowInstanceId: 'wf-1',
    runtimeType: 'OPENCODE',
    status: 'ACTIVE',
    createdAt: '2026-05-08T10:00:00Z',
  }

  const failedWorkflow = {
    id: 'wf-1',
    workItemId: 'work-1',
    workflowDefinitionId: 'wf-def-1',
    status: 'RUNNING',
    currentNodeInstanceId: 'node-1',
    nodes: [
      {
        id: 'node-1',
        nodeDefinitionId: 'node-def-1',
        status: 'FAILED',
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: 'session-1',
        startedAt: null,
        completedAt: null,
        errorMessage: 'Agent Runtime 超时',
      },
    ],
    startedAt: null,
    completedAt: null,
  }

  const workflowDefinition = {
    id: 'wf-def-1',
    workItemType: 'FE',
    name: 'FE 标准工作流',
    versionNo: 1,
    status: 'ENABLED',
    isDefault: true,
    nodes: [
      {
        id: 'node-def-1',
        nodeKey: 'lld',
        name: '详细设计 (LLD)',
        orderNo: 1,
        skillName: 'lld-design',
        inputPolicy: 'WORK_ITEM_ONLY',
        outputArtifactType: 'MARKDOWN',
        requiredConfirmation: false,
      },
    ],
  }

  return { failedWorkItem, failedSession, failedWorkflow, workflowDefinition }
})

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([mocks.failedWorkItem]),
    getById: vi.fn().mockResolvedValue(mocks.failedWorkItem),
  },
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([mocks.workflowDefinition]),
    getInstance: vi.fn().mockResolvedValue(mocks.failedWorkflow),
  },
}))

vi.mock('../api/sessions', () => ({
  sessionApi: {
    list: vi.fn().mockResolvedValue([mocks.failedSession]),
    getById: vi.fn().mockResolvedValue(mocks.failedSession),
    getMessages: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
  },
}))

vi.mock('../api/events', () => ({
  eventApi: {
    streamSessionEvents: vi.fn().mockReturnValue({ close: vi.fn() }),
  },
}))

vi.mock('../api/runtimeResources', () => ({
  runtimeResourceApi: {
    refreshSkills: vi.fn(),
  },
}))

vi.mock('../api/artifacts', () => ({
  artifactApi: {
    listByWorkItem: vi.fn().mockResolvedValue([]),
  },
}))

describe('ConversationWorkbench.vue', () => {
  it('shows a workflow failure notice above the conversation', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    expect(wrapper.find('.node-state-area--failed').text()).toContain('Agent Runtime 超时')
  })
})
