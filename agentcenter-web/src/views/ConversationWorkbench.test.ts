import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConversationWorkbench from './ConversationWorkbench.vue'

const mocks = vi.hoisted(() => {
  const runningWorkItem = {
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
          status: 'RUNNING',
          errorMessage: null,
        },
      ],
      stages: [
        {
          id: 'node-1',
          stageKey: 'detail_design',
          name: '详细设计 (LLD)',
          skillName: 'lld-design',
          status: 'RUNNING',
          dynamicNodeCount: 0,
          recoveryCount: 0,
          pendingConfirmationCount: 0,
          latestSummary: '详细设计 (LLD)',
          errorMessage: null,
        },
      ],
    },
    createdAt: '2026-05-08T10:00:00Z',
    updatedAt: '2026-05-08T10:00:00Z',
  }

  const runningSession = {
    id: 'session-1',
    sessionType: 'WORK_ITEM',
    title: 'FE0003 · 社区共享工具柜借还流程',
    workItemId: 'work-1',
    workflowInstanceId: 'wf-1',
    runtimeType: 'OPENCODE',
    status: 'ACTIVE',
    createdAt: '2026-05-08T10:00:00Z',
  }

  const runningWorkflow = {
    id: 'wf-1',
    workItemId: 'work-1',
    workflowDefinitionId: 'wf-def-1',
    status: 'RUNNING',
    currentNodeInstanceId: 'node-1',
    nodes: [
      {
        id: 'node-1',
        nodeDefinitionId: 'node-def-1',
        status: 'RUNNING',
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: 'session-1',
        startedAt: null,
        completedAt: null,
        errorMessage: null,
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

  return { runningWorkItem, runningSession, runningWorkflow, workflowDefinition }
})

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([mocks.runningWorkItem]),
    getById: vi.fn().mockResolvedValue(mocks.runningWorkItem),
  },
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([mocks.workflowDefinition]),
    getInstance: vi.fn().mockResolvedValue(mocks.runningWorkflow),
  },
}))

vi.mock('../api/sessions', () => ({
  sessionApi: {
    list: vi.fn().mockResolvedValue([mocks.runningSession]),
    getById: vi.fn().mockResolvedValue(mocks.runningSession),
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
  it('shows pause action while the workflow node is running', async () => {
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

    const sendButton = wrapper.find('.conversation-workbench__send')
    expect(sendButton.classes()).toContain('conversation-workbench__send--pause')
    expect(sendButton.attributes('aria-label')).toBe('暂停当前回复')
    expect(wrapper.find('.node-state-area--running').exists()).toBe(false)
  })
})
