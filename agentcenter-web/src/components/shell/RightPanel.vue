<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import ConfirmationPanel from '../confirmation/ConfirmationPanel.vue'
import NotificationBubbles from '../notifications/NotificationBubbles.vue'
import ArtifactViewer from '../conversation/ArtifactViewer.vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useWorkflowStore } from '../../stores/workflows'
import type { ArtifactDto, WorkItemDto, WorkflowDefinitionDto, WorkflowNodeStatus } from '../../api/types'

interface Props {
  collapsed?: boolean
  expanded?: boolean
  activeView?: string
  selectedWorkItem?: WorkItemDto | null
  selectedArtifact?: ArtifactDto | null
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false,
  expanded: false,
  activeView: 'home',
  selectedWorkItem: null,
  selectedArtifact: null,
})

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'update:expanded': [value: boolean]
  'handle-confirmation': [id: string]
  'start-workflow': [workItemId: string]
  'enter-conversation': [id: string]
  'confirmations-changed': [workItemId?: string | null]
  'close-artifact': []
}>()

function handleConfirmation(id: string) {
  emit('handle-confirmation', id)
}

const confirmationStore = useConfirmationStore()
const workflowStore = useWorkflowStore()
const activeTab = ref<'confirmations' | 'details' | 'artifact'>('confirmations')
const pendingConfirmationCount = computed(() => confirmationStore.pendingConfirmations.length)
const pendingConfirmationCountLabel = computed(() =>
  pendingConfirmationCount.value > 99 ? '99+' : String(pendingConfirmationCount.value)
)

type RightPanelTab = {
  id: 'confirmations' | 'details' | 'artifact'
  label: string
  closable: boolean
}

const tabs = computed(() => {
  const base: RightPanelTab[] = [
    { id: 'confirmations', label: '待确认', closable: false },
    { id: 'details', label: '详情', closable: false },
  ]
  if (props.selectedArtifact) {
    base.push({ id: 'artifact', label: '产物预览', closable: true })
  }
  return base
})

onMounted(() => {
  if (workflowStore.definitions.length === 0) {
    workflowStore.loadDefinitions()
  }
})

// Auto-switch to details tab when a work item is selected
watch(() => props.selectedWorkItem, (item) => {
  if (item && !props.selectedArtifact) {
    activeTab.value = 'details'
  }
})

// Auto-switch to confirmations tab when a confirmation is selected from the node state area
watch(() => confirmationStore.currentConfirmation, (confirmation) => {
  if (confirmation) {
    activeTab.value = 'confirmations'
  }
})

watch(() => props.selectedArtifact, (artifact) => {
  if (artifact) {
    activeTab.value = 'artifact'
    emit('update:collapsed', false)
  } else if (activeTab.value === 'artifact') {
    activeTab.value = props.selectedWorkItem ? 'details' : 'confirmations'
  }
})

type DetailWorkflowNode = {
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

function buildWorkflowWithAnchors(skillNodes: DetailWorkflowNode[]): DetailWorkflowNode[] {
  const item = props.selectedWorkItem
  const workflowStatus = item?.workflowSummary?.status
  const hasStarted = Boolean(item?.currentWorkflowInstanceId || item?.workflowSummary)
  const allSkillsCompleted = skillNodes.length > 0 && skillNodes.every((node) =>
    ['COMPLETED', 'SKIPPED'].includes(node.status)
  )
  return [
    { id: 'start', label: '开始', status: hasStarted ? 'COMPLETED' : 'PENDING', kind: 'start' },
    ...skillNodes,
    {
      id: 'end',
      label: '完成',
      status: workflowStatus === 'COMPLETED' || allSkillsCompleted ? 'COMPLETED' : 'PENDING',
      kind: 'end',
    },
  ]
}

function pickDefaultDefinition(item: WorkItemDto): WorkflowDefinitionDto | null {
  const enabledDefinitions = workflowStore.definitions.filter((definition) =>
    definition.workItemType === item.type && definition.status === 'ENABLED'
  )
  return enabledDefinitions.find((definition) => definition.isDefault)
    ?? enabledDefinitions.sort((a, b) => b.versionNo - a.versionNo)[0]
    ?? null
}

const workflowNodes = computed(() => {
  const item = props.selectedWorkItem
  if (!item) return []
  if (item.workflowSummary?.stages?.length) {
    const stages = item.workflowSummary.stages.map((stage) => ({
      id: stage.id,
      label: stage.name ?? stage.skillName ?? '阶段',
      status: stage.status,
      kind: 'skill' as const,
      dynamicNodeCount: stage.dynamicNodeCount,
      recoveryCount: stage.recoveryCount,
      pendingConfirmationCount: stage.pendingConfirmationCount,
      latestSummary: stage.latestSummary,
      errorMessage: stage.errorMessage,
    }))
    return buildWorkflowWithAnchors(stages)
  }
  if (item.workflowSummary && item.workflowSummary.nodes.length > 0) {
    const skills = item.workflowSummary.nodes.map((n) => ({
      id: n.id,
      label: n.definitionName ?? n.skillName ?? '阶段',
      status: n.status,
      kind: 'skill' as const,
      errorMessage: n.errorMessage,
    }))
    return buildWorkflowWithAnchors(skills)
  }
  const defaultDefinition = pickDefaultDefinition(item)
  if (defaultDefinition?.nodes.length) {
    const skills = [...defaultDefinition.nodes]
      .sort((a, b) => a.orderNo - b.orderNo)
      .map((node) => ({
        id: node.id,
        label: node.name ?? node.skillName ?? '阶段',
        status: 'PENDING' as WorkflowNodeStatus,
        kind: 'skill' as const,
        latestSummary: node.skillName,
      }))
    return buildWorkflowWithAnchors(skills)
  }
  return buildWorkflowWithAnchors([])
})

const hasActiveWorkflow = computed(() => {
  const item = props.selectedWorkItem
  if (!item) return false
  return !!item.currentWorkflowInstanceId || !!item.workflowSummary
})

const isSelectedWorkItemConversationOpen = computed(() =>
  props.activeView === 'conversation' && Boolean(props.selectedWorkItem)
)

const conversationButtonLabel = computed(() =>
  isSelectedWorkItemConversationOpen.value ? '会话中' : '进入会话'
)

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

function nodeMeta(node: DetailWorkflowNode): string {
  if (node.status === 'FAILED' && node.errorMessage?.trim()) {
    return `失败原因：${node.errorMessage.trim()}`
  }
  const parts: string[] = []
  if (node.dynamicNodeCount) parts.push(`${node.dynamicNodeCount} 动态步骤`)
  if (node.recoveryCount) parts.push(`${node.recoveryCount} 修复`)
  if (node.pendingConfirmationCount) parts.push(`${node.pendingConfirmationCount} 待确认`)
  return parts.join(' · ')
}

function nodeMetaText(node: DetailWorkflowNode): string {
  return nodeMeta(node) || node.latestSummary || ''
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

function handleOpenConfirmations() {
  activeTab.value = 'confirmations'
  emit('update:collapsed', false)
}

function handleCloseArtifact(event: MouseEvent) {
  event.stopPropagation()
  emit('close-artifact')
}

function handleToggleExpanded() {
  emit('update:collapsed', false)
  emit('update:expanded', !props.expanded)
}
</script>

<template>
  <aside class="right-panel" :class="{ 'right-panel--collapsed': collapsed }">
    <div class="right-panel__header">
      <button
        class="right-panel__toggle"
        :title="collapsed ? '展开面板' : '收起面板'"
        :aria-label="collapsed ? '展开面板' : '收起面板'"
        @click="emit('update:collapsed', !collapsed)"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          :class="{ 'is-collapsed': collapsed }"
        >
          <rect x="4" y="5" width="16" height="14" rx="2.5" stroke="currentColor" stroke-width="1.8"/>
          <path d="M15 5v14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M9 9l3 3-3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <div v-if="!collapsed" class="right-panel__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="right-panel__tab"
          :class="{ 'right-panel__tab--active': activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          <span>{{ tab.label }}</span>
          <button
            v-if="tab.closable"
            type="button"
            class="right-panel__tab-close"
            aria-label="关闭产物预览"
            title="关闭产物预览"
            @click="handleCloseArtifact"
          >
            ×
          </button>
          <span
            v-if="tab.id === 'confirmations' && pendingConfirmationCount > 0"
            class="right-panel__tab-badge"
          >
            {{ pendingConfirmationCountLabel }}
          </span>
        </button>
      </div>

      <NotificationBubbles v-if="!collapsed" anchor="right-panel" />

      <button
        v-if="!collapsed && selectedArtifact"
        type="button"
        class="right-panel__expand"
        :class="{ 'right-panel__expand--active': expanded }"
        :aria-label="expanded ? '还原产物预览' : '扩大产物预览'"
        :title="expanded ? '还原产物预览' : '扩大产物预览'"
        @click="handleToggleExpanded"
      >
        <svg v-if="!expanded" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M8 3H3v5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M3 3l7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M16 21h5v-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M21 21l-7-7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M10 4H4v6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M4 10l6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <path d="M14 20h6v-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M20 14l-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <div v-if="collapsed" class="right-panel__rail" aria-label="右侧快捷入口">
      <button
        class="right-panel__rail-action"
        :title="`待确认 ${pendingConfirmationCountLabel}`"
        :aria-label="`待确认 ${pendingConfirmationCountLabel}`"
        @click="handleOpenConfirmations"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
        >
          <path d="M6 4h12a2 2 0 012 2v10a2 2 0 01-2 2h-5l-4 3v-3H6a2 2 0 01-2-2V6a2 2 0 012-2z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
          <path d="M8 11l2.4 2.4L16 8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span
          v-if="pendingConfirmationCount > 0"
          class="right-panel__rail-badge"
          aria-label="待确认数量"
        >
          {{ pendingConfirmationCountLabel }}
        </span>
      </button>
    </div>

    <div v-if="!collapsed" class="right-panel__content">
      <div class="right-panel__body">
        <ConfirmationPanel
          v-if="activeTab === 'confirmations'"
          @handle="handleConfirmation"
          @changed="emit('confirmations-changed', $event)"
        />
        <template v-else-if="activeTab === 'details'">
          <div v-if="selectedWorkItem" class="right-panel__detail">
            <div class="detail__header">
              <span class="detail__type-tag">{{ selectedWorkItem.type }}</span>
              <span class="detail__code">{{ selectedWorkItem.code }}</span>
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
                  <span class="detail__node-content">
                    <span class="detail__node-label">{{ node.label }}</span>
                    <span v-if="nodeMetaText(node)" class="detail__node-meta">
                      {{ nodeMetaText(node) }}
                    </span>
                  </span>
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
                {{ conversationButtonLabel }}
              </button>
            </div>
          </div>
          <div v-else class="right-panel__placeholder">
            <span>选择一个工作项查看详情</span>
          </div>
        </template>
        <template v-else-if="activeTab === 'artifact'">
          <ArtifactViewer :artifact="selectedArtifact" />
        </template>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  overflow: hidden;
}

.right-panel--collapsed {
}

.right-panel__header {
  position: relative;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  min-height: 42px;
  padding: 6px 10px 0;
  gap: 8px;
  border-bottom: 1px solid var(--border-color);
}

.right-panel--collapsed .right-panel__header {
  justify-content: center;
  min-height: 38px;
  padding: 5px 0 0;
  border-bottom: 0;
}

.right-panel__content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.right-panel__tabs {
  display: flex;
  flex: 1;
  align-self: stretch;
  gap: 4px;
  min-width: 0;
}

.right-panel__tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

.right-panel__tab-close {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: currentColor;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.right-panel__tab-close:hover {
  background: var(--bg-tertiary);
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
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.right-panel__toggle:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.right-panel__toggle svg {
  transition: transform 0.18s ease;
}

.right-panel__toggle svg.is-collapsed {
  transform: rotate(180deg);
}

.right-panel__rail {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding-top: 8px;
}

.right-panel__rail-action {
  position: relative;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.right-panel__rail-action:hover {
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.right-panel__rail-badge,
.right-panel__tab-badge {
  display: grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent-blue);
  color: var(--on-brand);
  font-size: 10px;
  font-weight: 850;
  line-height: 1;
}

.right-panel__rail-badge {
  position: absolute;
  top: -4px;
  right: -5px;
  border: 2px solid var(--bg-secondary);
}

.right-panel__tab-badge {
  margin-left: 6px;
  background: var(--brand-soft);
  color: var(--accent-blue);
}

.right-panel__expand {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  cursor: pointer;
}

.right-panel__expand:hover,
.right-panel__expand--active {
  background: var(--text-primary);
  color: var(--bg-card);
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
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 700;
}

.detail__type-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 6px;
  background: var(--surface-muted);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
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
  min-height: 32px;
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
  border-color: var(--brand-border);
  background: var(--accent-blue);
  box-shadow: 0 0 0 4px var(--focus-ring);
  animation: detail-pulse 2s ease-in-out infinite;
}

@keyframes detail-pulse {
  0%, 100% { box-shadow: 0 0 0 3px var(--focus-ring); }
  50% { box-shadow: 0 0 0 6px var(--focus-ring); }
}

.detail__node--waiting .detail__node-dot {
  border-color: var(--warning);
  background: var(--warning);
}

.detail__node--done .detail__node-dot {
  border-color: var(--success);
  background: var(--success);
}

.detail__node--failed .detail__node-dot {
  border-color: var(--error);
  background: var(--error);
}

.detail__node--skipped .detail__node-dot {
  border-color: var(--text-muted);
  background: var(--text-muted);
  opacity: 0.5;
}

.detail__node-content {
  flex: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.detail__node-label {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.detail__node-meta {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail__node--failed .detail__node-meta {
  color: var(--error);
  overflow-wrap: anywhere;
  white-space: normal;
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
  color: var(--on-brand);
}

.detail__btn--primary:hover {
  opacity: 0.9;
}

.detail__btn--secondary {
  background: var(--bg-card);
  color: var(--accent-blue);
  border: 1px solid var(--brand-border);
}

.detail__btn--secondary:hover {
  background: var(--accent-blue);
  color: var(--on-brand);
}
</style>
