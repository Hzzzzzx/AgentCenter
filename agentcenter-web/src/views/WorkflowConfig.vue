<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { skillApi } from '../api/runtimeResources'
import { useWorkflowStore } from '../stores/workflows'
import type {
  ArtifactType,
  RuntimeSkillDetailDto,
  UpdateWorkflowDefinitionRequest,
  WorkflowDefinitionDto,
  WorkflowNodeDefinitionDto,
} from '../api/types'

const DEFAULT_PROJECT_ID = '01DEFAULTPROJECT0000000000001'

type NodeDraft = {
  nodeKey: string
  name: string
  skillName: string
  inputPolicy: string
  outputArtifactType: ArtifactType
  requiredConfirmation: boolean
  stageKey: string
  allowDynamicActions: boolean
  confirmationPolicy: string
}

type DefinitionDraft = {
  id: string
  name: string
  isDefault: boolean
  nodes: NodeDraft[]
}

const workflowStore = useWorkflowStore()
const skills = ref<RuntimeSkillDetailDto[]>([])
const skillLoading = ref(false)
const editingDefinitionId = ref<string | null>(null)
const draft = ref<DefinitionDraft | null>(null)
const saving = ref(false)
const error = ref<string | null>(null)
const savedMessage = ref<string | null>(null)

const inputPolicies = [
  { value: 'WORK_ITEM_ONLY', label: '工作项' },
  { value: 'PREVIOUS_ARTIFACT', label: '上游产物' },
  { value: 'MERGED_CONTEXT', label: '合并上下文' },
]

const outputTypes: ArtifactType[] = ['MARKDOWN', 'JSON', 'PATCH', 'LOG', 'REPORT']
const confirmationTooltip = '开启后，阶段产物会进入右侧待确认，用户通过后才继续下一阶段。适合 PRD、HLD 等需要人工审阅的节点。'
const dynamicActionTooltip = '开启后，Agent 执行该阶段时可以按实际情况临时追加修复、补充分析、重试等动作，并统一挂在这个阶段下。'

const enabledDefinitions = computed(() =>
  workflowStore.definitions.filter((definition) => definition.status === 'ENABLED')
)

const availableSkillNames = computed(() => {
  const names = new Set<string>()
  for (const skill of skills.value) {
    if (skill.status === 'ENABLED') names.add(skill.name)
  }
  for (const definition of workflowStore.definitions) {
    for (const node of definition.nodes) {
      if (node.skillName) names.add(node.skillName)
      for (const name of recommendedSkillNames(node)) names.add(name)
    }
  }
  return [...names].sort((a, b) => a.localeCompare(b))
})

onMounted(() => {
  workflowStore.loadDefinitions()
  loadSkills()
})

async function loadSkills() {
  skillLoading.value = true
  try {
    skills.value = await skillApi.list(DEFAULT_PROJECT_ID)
  } catch {
    skills.value = []
  } finally {
    skillLoading.value = false
  }
}

function recommendedSkillNames(node: WorkflowNodeDefinitionDto): string[] {
  if (node.recommendedSkillNamesJson) {
    try {
      const parsed = JSON.parse(node.recommendedSkillNamesJson)
      if (Array.isArray(parsed)) {
        return parsed.filter((name): name is string => typeof name === 'string' && name.length > 0)
      }
    } catch {
      return node.skillName ? [node.skillName] : []
    }
  }
  return node.skillName ? [node.skillName] : []
}

function recommendedSkills(node: WorkflowNodeDefinitionDto): string {
  const names = recommendedSkillNames(node)
  return names.length > 0 ? names.join('、') : node.skillName
}

function inputPolicyLabel(policy: string): string {
  return inputPolicies.find((item) => item.value === policy)?.label ?? policy
}

function beginEdit(definition: WorkflowDefinitionDto) {
  error.value = null
  savedMessage.value = null
  editingDefinitionId.value = definition.id
  draft.value = {
    id: definition.id,
    name: definition.name,
    isDefault: definition.isDefault,
    nodes: definition.nodes.map((node) => ({
      nodeKey: node.nodeKey,
      name: node.name,
      skillName: node.skillName,
      inputPolicy: node.inputPolicy,
      outputArtifactType: (node.outputArtifactType ?? 'MARKDOWN') as ArtifactType,
      requiredConfirmation: node.requiredConfirmation,
      stageKey: node.stageKey ?? node.nodeKey,
      allowDynamicActions: node.allowDynamicActions !== false,
      confirmationPolicy: node.confirmationPolicy ?? (node.requiredConfirmation ? 'REQUIRED' : 'AUTO'),
    })),
  }
}

function cancelEdit() {
  editingDefinitionId.value = null
  draft.value = null
  error.value = null
}

function addNode() {
  if (!draft.value) return
  const order = draft.value.nodes.length + 1
  draft.value.nodes.push({
    nodeKey: `stage_${order}`,
    name: `阶段 ${order}`,
    skillName: availableSkillNames.value[0] ?? '',
    inputPolicy: order === 1 ? 'WORK_ITEM_ONLY' : 'PREVIOUS_ARTIFACT',
    outputArtifactType: 'MARKDOWN',
    requiredConfirmation: false,
    stageKey: `stage_${order}`,
    allowDynamicActions: true,
    confirmationPolicy: 'AUTO',
  })
}

function removeNode(index: number) {
  if (!draft.value || draft.value.nodes.length <= 1) return
  draft.value.nodes.splice(index, 1)
}

function moveNode(index: number, direction: -1 | 1) {
  if (!draft.value) return
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= draft.value.nodes.length) return
  const nodes = draft.value.nodes
  const current = nodes[index]
  nodes[index] = nodes[nextIndex]
  nodes[nextIndex] = current
}

async function saveDraft() {
  if (!draft.value) return
  error.value = null
  savedMessage.value = null
  const invalidNode = draft.value.nodes.find((node) => !node.name.trim() || !node.skillName.trim())
  if (!draft.value.name.trim()) {
    error.value = '策略名称不能为空'
    return
  }
  if (invalidNode) {
    error.value = '每个阶段都需要阶段名称和 Skill'
    return
  }

  const request: UpdateWorkflowDefinitionRequest = {
    name: draft.value.name.trim(),
    isDefault: draft.value.isDefault,
    nodes: draft.value.nodes.map((node) => ({
      nodeKey: node.nodeKey.trim() || null,
      name: node.name.trim(),
      skillName: node.skillName.trim(),
      inputPolicy: node.inputPolicy,
      outputArtifactType: node.outputArtifactType,
      requiredConfirmation: node.requiredConfirmation,
      stageKey: node.stageKey.trim() || null,
      stageGoal: node.name.trim(),
      recommendedSkillNames: [node.skillName.trim()],
      allowDynamicActions: node.allowDynamicActions,
      confirmationPolicy: node.requiredConfirmation ? 'REQUIRED' : 'AUTO',
    })),
  }

  saving.value = true
  try {
    const updated = await workflowStore.saveDefinition(draft.value.id, request)
    savedMessage.value = `已保存为 v${updated.versionNo}，后续新工作项将使用这版编排`
    editingDefinitionId.value = null
    draft.value = null
    await workflowStore.loadDefinitions()
  } catch (saveError) {
    error.value = saveError instanceof Error ? saveError.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="workflow-config">
    <div class="workflow-config__header">
      <div>
        <h3 class="workflow-config__title">任务编排</h3>
        <p class="workflow-config__subtitle">按任务类型维护工作流阶段，选择 Skill 并编排执行顺序</p>
      </div>
      <div class="workflow-config__skill-summary">
        <span>{{ skillLoading ? 'Skill 加载中' : `可选 Skill ${availableSkillNames.length}` }}</span>
      </div>
    </div>

    <div v-if="error" class="workflow-config__notice workflow-config__notice--error">{{ error }}</div>
    <div v-if="savedMessage" class="workflow-config__notice workflow-config__notice--success">{{ savedMessage }}</div>

    <div v-if="workflowStore.loading" class="workflow-config__loading">加载中...</div>
    <div v-else-if="enabledDefinitions.length === 0" class="workflow-config__empty">
      暂无任务编排
    </div>
    <div v-else class="workflow-config__list">
      <section
        v-for="def in enabledDefinitions"
        :key="def.id"
        class="workflow-definition"
      >
        <div class="workflow-definition__header">
          <div class="workflow-definition__identity">
            <span class="workflow-definition__name">{{ def.name }}</span>
            <span class="workflow-definition__type">{{ def.workItemType }}</span>
            <span v-if="def.isDefault" class="workflow-definition__default">默认</span>
            <span class="workflow-definition__version">v{{ def.versionNo }}</span>
          </div>
          <div class="workflow-definition__actions">
            <button
              v-if="editingDefinitionId !== def.id"
              class="workflow-config__button workflow-config__button--primary"
              type="button"
              @click="beginEdit(def)"
            >
              编辑
            </button>
            <template v-else>
              <button
                class="workflow-config__button"
                type="button"
                :disabled="saving"
                @click="cancelEdit"
              >
                取消
              </button>
              <button
                class="workflow-config__button workflow-config__button--primary"
                type="button"
                :disabled="saving"
                @click="saveDraft"
              >
                {{ saving ? '保存中' : '保存新版' }}
              </button>
            </template>
          </div>
        </div>

        <div v-if="editingDefinitionId === def.id && draft" class="workflow-editor">
          <div class="workflow-editor__top">
            <label>
              <span>策略名称</span>
              <input v-model="draft.name" type="text" />
            </label>
            <label class="workflow-editor__check">
              <input v-model="draft.isDefault" type="checkbox" />
              <span>作为 {{ def.workItemType }} 默认编排</span>
            </label>
          </div>

          <div class="workflow-editor__table" role="table" aria-label="编排阶段编辑">
            <div class="workflow-editor__row workflow-editor__row--head" role="row">
              <span>顺序</span>
              <span>阶段</span>
              <span>Skill</span>
              <span>输入</span>
              <span>输出</span>
              <span class="workflow-editor__head-label">
                门禁
                <button
                  class="workflow-help"
                  type="button"
                  :title="`${confirmationTooltip}\n${dynamicActionTooltip}`"
                  aria-label="查看门禁设置说明"
                >
                  ?
                </button>
              </span>
              <span>操作</span>
            </div>
            <div
              v-for="(node, index) in draft.nodes"
              :key="`${node.nodeKey}-${index}`"
              class="workflow-editor__row"
              role="row"
            >
              <div class="workflow-editor__order">
                <strong>{{ index + 1 }}</strong>
                <button type="button" :disabled="index === 0" @click="moveNode(index, -1)">上移</button>
                <button type="button" :disabled="index === draft.nodes.length - 1" @click="moveNode(index, 1)">下移</button>
              </div>
              <div class="workflow-editor__field-stack">
                <input v-model="node.name" type="text" aria-label="阶段名称" placeholder="阶段名称" />
              </div>
              <select v-model="node.skillName" aria-label="选择 Skill">
                <option value="" disabled>选择 Skill</option>
                <option
                  v-for="skillName in availableSkillNames"
                  :key="skillName"
                  :value="skillName"
                >
                  {{ skillName }}
                </option>
              </select>
              <select v-model="node.inputPolicy" aria-label="输入策略">
                <option
                  v-for="policy in inputPolicies"
                  :key="policy.value"
                  :value="policy.value"
                >
                  {{ policy.label }}
                </option>
              </select>
              <select v-model="node.outputArtifactType" aria-label="输出类型">
                <option v-for="type in outputTypes" :key="type" :value="type">{{ type }}</option>
              </select>
              <div class="workflow-editor__toggles">
                <div class="workflow-editor__toggle-row">
                  <label>
                    <input v-model="node.requiredConfirmation" type="checkbox" />
                    <span>需确认</span>
                  </label>
                  <button
                    class="workflow-help"
                    type="button"
                    :title="confirmationTooltip"
                    aria-label="查看需确认说明"
                  >
                    ?
                  </button>
                </div>
                <div class="workflow-editor__toggle-row">
                  <label>
                    <input v-model="node.allowDynamicActions" type="checkbox" />
                    <span>动态动作</span>
                  </label>
                  <button
                    class="workflow-help"
                    type="button"
                    :title="dynamicActionTooltip"
                    aria-label="查看动态动作说明"
                  >
                    ?
                  </button>
                </div>
              </div>
              <button
                class="workflow-config__button workflow-config__button--danger"
                type="button"
                :disabled="draft.nodes.length <= 1"
                @click="removeNode(index)"
              >
                删除
              </button>
            </div>
          </div>

          <button class="workflow-config__button" type="button" @click="addNode">
            新增阶段
          </button>
        </div>

        <div v-else class="workflow-definition__stages">
          <div
            v-for="node in def.nodes"
            :key="node.id"
            class="workflow-stage"
          >
            <div class="workflow-stage__order">{{ node.orderNo }}</div>
            <div class="workflow-stage__main">
              <div class="workflow-stage__topline">
                <strong>{{ node.name }}</strong>
                <span>{{ node.stageKey || node.nodeKey }}</span>
              </div>
              <p>{{ node.stageGoal || node.name }}</p>
              <div class="workflow-stage__meta">
                <span>Skill：{{ recommendedSkills(node) }}</span>
                <span>输入：{{ inputPolicyLabel(node.inputPolicy) }}</span>
                <span>输出：{{ node.outputArtifactType }}</span>
                <span>
                  {{ node.requiredConfirmation ? '需确认' : '自动推进' }}
                  <button
                    class="workflow-help workflow-help--inline"
                    type="button"
                    :title="confirmationTooltip"
                    aria-label="查看需确认说明"
                  >
                    ?
                  </button>
                </span>
                <span>
                  {{ node.allowDynamicActions !== false ? '允许动态动作' : '固定阶段' }}
                  <button
                    class="workflow-help workflow-help--inline"
                    type="button"
                    :title="dynamicActionTooltip"
                    aria-label="查看动态动作说明"
                  >
                    ?
                  </button>
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.workflow-config {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px;
  overflow-y: auto;
}

.workflow-config__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.workflow-config__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.workflow-config__subtitle {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.workflow-config__skill-summary {
  flex: 0 0 auto;
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.workflow-config__notice {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}

.workflow-config__notice--error {
  border: 1px solid rgba(239, 68, 68, 0.2);
  background-color: rgba(239, 68, 68, 0.08);
  color: var(--error);
}

.workflow-config__notice--success {
  border: 1px solid rgba(16, 185, 129, 0.2);
  background-color: rgba(16, 185, 129, 0.08);
  color: var(--success);
}

.workflow-config__loading,
.workflow-config__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 14px;
}

.workflow-config__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflow-config__button {
  min-height: 30px;
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.workflow-config__button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.workflow-config__button--primary {
  border-color: rgba(59, 130, 246, 0.4);
  background-color: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
}

.workflow-config__button--danger {
  border-color: rgba(239, 68, 68, 0.25);
  color: var(--error);
}

.workflow-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: 1px solid var(--border-color);
  border-radius: 50%;
  background-color: var(--bg-primary);
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
  cursor: help;
}

.workflow-help:hover,
.workflow-help:focus-visible {
  border-color: rgba(59, 130, 246, 0.45);
  color: var(--accent-blue);
  outline: none;
}

.workflow-help--inline {
  margin-left: 4px;
}

.workflow-definition {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.workflow-definition__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.workflow-definition__identity,
.workflow-definition__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-definition__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.workflow-definition__type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
}

.workflow-definition__default {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: rgba(16, 185, 129, 0.1);
  color: var(--success);
}

.workflow-definition__version {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.workflow-definition__stages {
  display: flex;
  flex-direction: column;
}

.workflow-stage {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.workflow-stage:last-child {
  border-bottom: none;
}

.workflow-stage__order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.workflow-stage__main {
  min-width: 0;
}

.workflow-stage__topline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-stage__topline strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 800;
}

.workflow-stage__topline span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.workflow-stage__main p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.workflow-stage__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.workflow-stage__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.workflow-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
}

.workflow-editor__top {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.workflow-editor label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.workflow-editor__check {
  flex-direction: row !important;
  align-items: center;
  min-height: 32px;
}

.workflow-editor input,
.workflow-editor select {
  min-width: 0;
  min-height: 32px;
  padding: 5px 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
}

.workflow-editor input[type='checkbox'] {
  min-height: auto;
}

.workflow-editor__table {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.workflow-editor__row {
  display: grid;
  grid-template-columns: 92px minmax(180px, 1.2fr) minmax(140px, 1fr) 116px 112px 132px 72px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}

.workflow-editor__row:last-child {
  border-bottom: none;
}

.workflow-editor__row--head {
  min-height: 34px;
  background-color: var(--bg-primary);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
}

.workflow-editor__head-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.workflow-editor__order {
  display: grid;
  grid-template-columns: 28px 1fr 1fr;
  gap: 4px;
  align-items: center;
}

.workflow-editor__order strong {
  color: var(--text-primary);
  font-size: 13px;
}

.workflow-editor__order button {
  min-height: 26px;
  padding: 3px 5px;
  border: 1px solid var(--border-color);
  border-radius: 5px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.workflow-editor__order button:disabled {
  opacity: 0.4;
}

.workflow-editor__field-stack,
.workflow-editor__toggles {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.workflow-editor__toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.workflow-editor__toggle-row label {
  flex-direction: row;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1120px) {
  .workflow-editor__table {
    overflow-x: auto;
  }

  .workflow-editor__row {
    min-width: 900px;
  }
}
</style>
