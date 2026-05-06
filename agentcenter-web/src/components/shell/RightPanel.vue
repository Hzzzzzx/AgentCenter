<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ConfirmationPanel from '../confirmation/ConfirmationPanel.vue'
import type { WorkItemDto, WorkflowNodeStatus } from '../../api/types'

interface Props {
  collapsed?: boolean
  selectedWorkItem?: WorkItemDto | null
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false,
  selectedWorkItem: null,
})

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'handle-confirmation': [id: string]
  'start-workflow': [workItemId: string]
  'enter-conversation': [id: string]
}>()

function handleConfirmation(id: string) {
  emit('handle-confirmation', id)
}

const activeTab = ref<'confirmations' | 'details'>('confirmations')

const tabs = [
  { id: 'confirmations' as const, label: '待确认' },
  { id: 'details' as const, label: '详情' },
]

// Auto-switch to details tab when a work item is selected
watch(() => props.selectedWorkItem, (item) => {
  if (item) {
    activeTab.value = 'details'
  }
})

type DetailWorkflowNode = {
  id: string | null
  label: string
  status: WorkflowNodeStatus
  kind: 'start' | 'skill' | 'end'
}

const defaultNodes: DetailWorkflowNode[] = [
  { id: null, label: 'PRD', status: 'PENDING', kind: 'skill' },
  { id: null, label: 'HLD', status: 'PENDING', kind: 'skill' },
  { id: null, label: 'LLD', status: 'PENDING', kind: 'skill' },
]

function buildWorkflowWithAnchors(skillNodes: DetailWorkflowNode[]): DetailWorkflowNode[] {
  const item = props.selectedWorkItem
  const workflowStatus = item?.workflowSummary?.status
  const hasStarted = Boolean(item?.currentWorkflowInstanceId || item?.workflowSummary)
  const allSkillsCompleted = skillNodes.length > 0 && skillNodes.every((node) =>
    ['COMPLETED', 'SKIPPED'].includes(node.status)
  )
  return [
    { id: 'start', label: '开始', status: hasStarted ? 'COMPLETED' : 'RUNNING', kind: 'start' },
    ...skillNodes,
    {
      id: 'end',
      label: '结束',
      status: workflowStatus === 'COMPLETED' || allSkillsCompleted ? 'COMPLETED' : 'PENDING',
      kind: 'end',
    },
  ]
}

const workflowNodes = computed(() => {
  const item = props.selectedWorkItem
  if (!item) return []
  if (item.workflowSummary && item.workflowSummary.nodes.length > 0) {
    const skills = item.workflowSummary.nodes.map((n) => ({
      id: n.id,
      label: n.definitionName ?? n.skillName ?? '节点',
      status: n.status,
      kind: 'skill' as const,
    }))
    return buildWorkflowWithAnchors(skills)
  }
  return buildWorkflowWithAnchors(defaultNodes)
})

const hasActiveWorkflow = computed(() => {
  const item = props.selectedWorkItem
  if (!item) return false
  return !!item.currentWorkflowInstanceId || !!item.workflowSummary
})

const statusLabels: Record<WorkflowNodeStatus, string> = {
  PENDING: '等待中',
  RUNNING: '运行中',
  WAITING_CONFIRMATION: '待确认',
  COMPLETED: '已完成',
  FAILED: '失败',
  SKIPPED: '已跳过',
}

function nodeClass(status: WorkflowNodeStatus): string {
  const map: Record<WorkflowNodeStatus, string> = {
    PENDING: '',
    RUNNING: 'detail__node--active',
    WAITING_CONFIRMATION: 'detail__node--waiting',
    COMPLETED: 'detail__node--done',
    FAILED: 'detail__node--failed',
    SKIPPED: 'detail__node--skipped',
  }
  return map[status]
}

function handleStartWorkflow() {
  if (props.selectedWorkItem) {
    emit('start-workflow', props.selectedWorkItem.id)
  }
}

function handleEnterConversation() {
  if (props.selectedWorkItem) {
    emit('enter-conversation', props.selectedWorkItem.id)
  }
}
</script>

<template>
  <aside class="right-panel" :class="{ 'right-panel--collapsed': collapsed }">
    <div v-if="!collapsed" class="right-panel__content">
      <div class="right-panel__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="right-panel__tab"
          :class="{ 'right-panel__tab--active': activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="right-panel__body">
        <ConfirmationPanel v-if="activeTab === 'confirmations'" @handle="handleConfirmation" />
        <template v-else-if="activeTab === 'details'">
          <div v-if="selectedWorkItem" class="right-panel__detail">
            <div class="detail__header">
              <span class="detail__code">{{ selectedWorkItem.code }}</span>
              <span class="detail__type">{{ selectedWorkItem.type }}</span>
            </div>
            <h3 class="detail__title">{{ selectedWorkItem.title }}</h3>
            <p class="detail__desc">{{ selectedWorkItem.description || '暂无描述' }}</p>

            <div class="detail__workflow">
              <h4>工作流进展</h4>
              <div class="detail__nodes">
                <div
                  v-for="node in workflowNodes"
                  :key="node.id ?? node.label"
                  class="detail__node"
                  :class="nodeClass(node.status)"
                >
                  <span class="detail__node-dot"></span>
                  <span class="detail__node-label">{{ node.label }}</span>
                  <span class="detail__node-status">{{ statusLabels[node.status] }}</span>
                </div>
              </div>
            </div>

            <div class="detail__actions">
              <button
                v-if="!hasActiveWorkflow"
                class="detail__btn detail__btn--primary"
                @click="handleStartWorkflow"
              >
                开始处理
              </button>
              <button
                v-if="hasActiveWorkflow"
                class="detail__btn detail__btn--secondary"
                @click="handleEnterConversation"
              >
                进入会话
              </button>
            </div>
          </div>
          <div v-else class="right-panel__placeholder">
            <span>选择一个工作项查看详情</span>
          </div>
        </template>
      </div>
    </div>

    <button
      class="right-panel__toggle"
      :title="collapsed ? '展开面板' : '收起面板'"
      @click="emit('update:collapsed', !collapsed)"
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        :style="{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }"
      >
        <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>
  </aside>
</template>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: row-reverse;
  height: 100%;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  overflow: hidden;
}

.right-panel--collapsed {
}

.right-panel__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.right-panel__tabs {
  display: flex;
  padding: 12px 16px 0;
  gap: 4px;
  border-bottom: 1px solid var(--border-color);
}

.right-panel__tab {
  position: relative;
  height: auto;
  padding: 6px 12px;
  border: none;
  border-radius: 6px 6px 0 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s;
}

.right-panel__tab:hover {
  color: var(--text-primary);
}

.right-panel__tab--active {
  color: var(--accent-blue);
  background: var(--bg-primary);
}

.right-panel__tab--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background-color: var(--accent-blue);
}

.right-panel__body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.right-panel__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: 13px;
}

.right-panel__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-muted);
  cursor: pointer;
  flex-shrink: 0;
  align-self: flex-start;
}

.right-panel__toggle:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

/* Work item detail */
.right-panel__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail__header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail__code {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 700;
}

.detail__type {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.detail__title {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 800;
  line-height: 1.4;
}

.detail__desc {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.detail__workflow {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.detail__workflow h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 700;
}

.detail__nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail__node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}

.detail__node-dot {
  width: 10px;
  height: 10px;
  border: 2px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-card);
  flex-shrink: 0;
}

.detail__node--active .detail__node-dot {
  border-color: rgba(59, 130, 246, 0.3);
  background: var(--accent-blue);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.15);
  animation: detail-pulse 2s ease-in-out infinite;
}

@keyframes detail-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }
  50% { box-shadow: 0 0 0 6px rgba(59, 130, 246, 0.2); }
}

.detail__node--waiting .detail__node-dot {
  border-color: #f59e0b;
  background: #f59e0b;
}

.detail__node--done .detail__node-dot {
  border-color: var(--success);
  background: var(--success);
}

.detail__node--failed .detail__node-dot {
  border-color: #ef4444;
  background: #ef4444;
}

.detail__node--skipped .detail__node-dot {
  border-color: #9ca3af;
  background: #9ca3af;
  opacity: 0.5;
}

.detail__node-label {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.detail__node-status {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.detail__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.detail__btn {
  width: 100%;
  height: 36px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.detail__btn--primary {
  background: var(--accent-blue);
  color: #fff;
}

.detail__btn--primary:hover {
  opacity: 0.9;
}

.detail__btn--secondary {
  background: var(--bg-card);
  color: var(--accent-blue);
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.detail__btn--secondary:hover {
  background: var(--accent-blue);
  color: #fff;
}
</style>
