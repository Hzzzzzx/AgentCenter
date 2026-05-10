<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { skillApi } from '../api/runtimeResources'
import { useWorkflowStore } from '../stores/workflows'
import MarkdownContent from '../components/conversation/MarkdownContent.vue'
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
const initialDraftSnapshot = ref<string | null>(null)
const saving = ref(false)
const error = ref<string | null>(null)
const savedMessage = ref<string | null>(null)
const orchestrationIntent = ref('选择可用 Skill 后，由 Agent 先生成可解释的大阶段路线；真正执行时根据工作项信息、上游产物和运行时事件动态决定是否提问、让用户选择方案或进入下一阶段。')
const agentFlowMermaid = ref('')

const inputPolicies = [
  { value: 'WORK_ITEM_ONLY', label: '工作项' },
  { value: 'PREVIOUS_ARTIFACT', label: '上游产物' },
  { value: 'MERGED_CONTEXT', label: '合并上下文' },
]

const outputTypes: ArtifactType[] = ['MARKDOWN', 'JSON', 'PATCH', 'LOG', 'REPORT']
const interactionTypes = [
  { type: 'ASK_USER', label: '信息缺口', desc: '需求、约束或验收标准缺失时再向用户提问' },
  { type: 'DECISION_REQUIRED', label: '方案选择', desc: '存在多个可行路线且取舍会影响结果时让用户选择' },
  { type: 'ARTIFACT_REVIEW_REQUESTED', label: '产物审阅', desc: 'PRD/HLD/LLD 风险较高时请求用户确认或退回修改' },
  { type: 'PERMISSION_REQUIRED', label: '授权动作', desc: '涉及外部系统、写入、生产数据或敏感操作时请求授权' },
]

const runtimePrinciples = [
  '编排页只定义大阶段蓝图，不把每一步工具调用写死。',
  '阶段之间默认通过 Markdown 产物和工作项上下文传递。',
  '待确认不再由阶段勾选强制产生，而由 Agent 输出的交互事件触发。',
  '执行中允许 Agent 增加临时修复、澄清、重试和风险检查动作。',
]

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

const isDraftDirty = computed(() => {
  if (!draft.value || initialDraftSnapshot.value === null) return false
  return serializeDraft(draft.value) !== initialDraftSnapshot.value
})

const agentFlowBranches = computed(() => {
  if (!draft.value) return []
  return draft.value.nodes.map((node, index) => ({
    id: `${index + 1}`,
    stage: node.name || `阶段 ${index + 1}`,
    skillName: node.skillName || '未选择 Skill',
    input: inputPolicyLabel(node.inputPolicy),
    review: '产物审阅由 Skill/Agent 判断',
    dynamic: 'Agent 可连续使用 Skill 补动作',
  }))
})

const agentFlowMermaidMarkdown = computed(() =>
  agentFlowMermaid.value.trim()
    ? `\`\`\`mermaid\n${agentFlowMermaid.value}\n\`\`\``
    : ''
)

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

function blueprintSummary(definition: WorkflowDefinitionDto): string {
  return `${definition.nodes.length} 个大阶段 · Skill 驱动执行 · 交互由 Agent 运行时事件触发`
}

function stageGoal(node: WorkflowNodeDefinitionDto | NodeDraft): string {
  if ('stageGoal' in node && node.stageGoal?.trim()) return node.stageGoal
  if (node.inputPolicy === 'WORK_ITEM_ONLY') return '基于工作项信息生成本阶段产物'
  if (node.inputPolicy === 'PREVIOUS_ARTIFACT') return '消费上游 Markdown 产物并补齐本阶段输出'
  return '合并工作项、历史产物和运行时上下文后继续推进'
}

function interactionHint(node: WorkflowNodeDefinitionDto | NodeDraft): string {
  const artifactType = node.outputArtifactType ?? 'MARKDOWN'
  return `运行时按 ${node.skillName || '所选 Skill'} 内容决定是否提问、审阅 ${artifactType} 产物或追加临时动作`
}

function inputPolicyLabel(policy: string): string {
  return inputPolicies.find((item) => item.value === policy)?.label ?? policy
}

function beginEdit(definition: WorkflowDefinitionDto) {
  error.value = null
  savedMessage.value = null
  editingDefinitionId.value = definition.id
  const nextDraft: DefinitionDraft = {
    id: definition.id,
    name: definition.name,
    isDefault: definition.isDefault,
    nodes: definition.nodes.map((node) => ({
      nodeKey: node.nodeKey,
      name: node.name,
      skillName: node.skillName,
      inputPolicy: node.inputPolicy,
      outputArtifactType: (node.outputArtifactType ?? 'MARKDOWN') as ArtifactType,
      requiredConfirmation: false,
      stageKey: node.stageKey ?? node.nodeKey,
      allowDynamicActions: true,
      confirmationPolicy: 'EVENT_DRIVEN',
    })),
  }
  draft.value = nextDraft
  agentFlowMermaid.value = buildAgentFlowMermaid(nextDraft)
  initialDraftSnapshot.value = serializeDraft(nextDraft)
}

function cancelEdit() {
  editingDefinitionId.value = null
  draft.value = null
  initialDraftSnapshot.value = null
  agentFlowMermaid.value = ''
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
    confirmationPolicy: 'EVENT_DRIVEN',
  })
  agentFlowMermaid.value = buildAgentFlowMermaid(draft.value)
}

function appendSkillAsStage(skillName: string) {
  if (!draft.value) return
  const order = draft.value.nodes.length + 1
  draft.value.nodes.push({
    nodeKey: stageKeyForSkill(skillName, order),
    name: stageNameForSkill(skillName, order),
    skillName,
    inputPolicy: order === 1 ? 'WORK_ITEM_ONLY' : 'PREVIOUS_ARTIFACT',
    outputArtifactType: 'MARKDOWN',
    requiredConfirmation: false,
    stageKey: stageKeyForSkill(skillName, order),
    allowDynamicActions: true,
    confirmationPolicy: 'EVENT_DRIVEN',
  })
  agentFlowMermaid.value = buildAgentFlowMermaid(draft.value)
}

function removeNode(index: number) {
  if (!draft.value || draft.value.nodes.length <= 1) return
  draft.value.nodes.splice(index, 1)
  agentFlowMermaid.value = buildAgentFlowMermaid(draft.value)
}

function moveNode(index: number, direction: -1 | 1) {
  if (!draft.value) return
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= draft.value.nodes.length) return
  const nodes = draft.value.nodes
  const current = nodes[index]
  nodes[index] = nodes[nextIndex]
  nodes[nextIndex] = current
  agentFlowMermaid.value = buildAgentFlowMermaid(draft.value)
}

function generateDraftPlan() {
  if (!draft.value) return
  draft.value.nodes = [...draft.value.nodes]
    .sort((left, right) => skillSortWeight(left.skillName) - skillSortWeight(right.skillName))
    .map((node, index) => ({
      ...node,
      name: node.name.trim() || stageNameForSkill(node.skillName, index + 1),
      inputPolicy: index === 0 ? 'WORK_ITEM_ONLY' : 'PREVIOUS_ARTIFACT',
      outputArtifactType: 'MARKDOWN',
      requiredConfirmation: false,
      stageKey: node.stageKey.trim() || stageKeyForSkill(node.skillName, index + 1),
      allowDynamicActions: true,
      confirmationPolicy: 'EVENT_DRIVEN',
    }))
  agentFlowMermaid.value = buildAgentFlowMermaid(draft.value)
  savedMessage.value = '已根据当前 Skill 和编排意图生成阶段草案，保存后会成为新版编排'
}

function skillSortWeight(skillName: string): number {
  const normalized = skillName.toLowerCase()
  if (normalized.includes('prd') || normalized.includes('requirement')) return 10
  if (normalized.includes('hld') || normalized.includes('solution')) return 20
  if (normalized.includes('lld') || normalized.includes('implementation')) return 30
  if (normalized.includes('review') || normalized.includes('verification')) return 40
  if (normalized.includes('archive') || normalized.includes('finalize')) return 50
  return 100
}

function stageNameForSkill(skillName: string, order: number): string {
  const normalized = skillName.toLowerCase()
  if (normalized.includes('prd') || normalized.includes('requirement')) return '需求整理 (PRD)'
  if (normalized.includes('hld') || normalized.includes('solution')) return '方案设计 (HLD)'
  if (normalized.includes('lld') || normalized.includes('implementation')) return '详细设计 (LLD)'
  if (normalized.includes('review') || normalized.includes('verification')) return '验证与评审'
  if (normalized.includes('archive') || normalized.includes('finalize')) return '归档完成'
  return `阶段 ${order}`
}

function stageKeyForSkill(skillName: string, order: number): string {
  const normalized = skillName.toLowerCase()
  if (normalized.includes('prd') || normalized.includes('requirement')) return 'requirement_refine'
  if (normalized.includes('hld') || normalized.includes('solution')) return 'solution_design'
  if (normalized.includes('lld') || normalized.includes('implementation')) return 'implementation_plan'
  if (normalized.includes('review') || normalized.includes('verification')) return 'verification_review'
  if (normalized.includes('archive') || normalized.includes('finalize')) return 'finalize_archive'
  return `stage_${order}`
}

function serializeDraft(value: DefinitionDraft): string {
  return JSON.stringify({
    name: value.name.trim(),
    isDefault: value.isDefault,
    nodes: value.nodes.map((node) => ({
      nodeKey: node.nodeKey.trim(),
      name: node.name.trim(),
      skillName: node.skillName.trim(),
      inputPolicy: node.inputPolicy,
      outputArtifactType: node.outputArtifactType,
      requiredConfirmation: false,
      stageKey: node.stageKey.trim(),
      allowDynamicActions: true,
      confirmationPolicy: 'EVENT_DRIVEN',
    })),
  })
}

function buildAgentFlowMermaid(value: DefinitionDraft): string {
  const lines = ['flowchart TD']
  lines.push('  START["读取工作项与用户编排意图"]')
  value.nodes.forEach((node, index) => {
    const id = `S${index + 1}`
    const nextId = index === value.nodes.length - 1 ? 'DONE' : `S${index + 2}`
    const stageLabel = `${node.name || `阶段 ${index + 1}`}\\nSkill: ${node.skillName || '未选择'}`
    if (index === 0) {
      lines.push(`  START --> ${id}["${escapeMermaidLabel(stageLabel)}"]`)
    } else {
      lines.push(`  ${id}["${escapeMermaidLabel(stageLabel)}"]`)
    }
    lines.push(`  ${id} --> Q${index + 1}{"Skill 判断信息是否足够？"}`)
    lines.push(`  Q${index + 1} -->|不足 / ASK_USER| U${index + 1}["向用户澄清输入"]`)
    lines.push(`  U${index + 1} --> ${id}`)
    lines.push(`  Q${index + 1} -->|足够| D${index + 1}{"是否存在路线取舍？"}`)
    lines.push(`  D${index + 1} -->|需要选择 / DECISION_REQUIRED| C${index + 1}["让用户选择方案"]`)
    lines.push(`  C${index + 1} --> A${index + 1}["生成阶段产物"]`)
    lines.push(`  D${index + 1} -->|无阻塞| A${index + 1}`)
    lines.push(`  A${index + 1} --> R${index + 1}{"Skill/Agent 是否请求审阅？"}`)
    lines.push(`  R${index + 1} -->|是 / ARTIFACT_REVIEW_REQUESTED| V${index + 1}["等待用户审阅"]`)
    lines.push(`  V${index + 1} --> ${nextId}`)
    lines.push(`  R${index + 1} -->|否| ${nextId}`)
    lines.push(`  A${index + 1} -. 异常/缺口 .-> F${index + 1}["临时修复或补充分析"]`)
    lines.push(`  F${index + 1} -. 回到阶段 .-> ${id}`)
  })
  lines.push('  DONE["完成并归档产物"]')
  return lines.join('\n')
}

function escapeMermaidLabel(value: string): string {
  return value.replace(/"/g, "'")
}

async function saveDraft() {
  if (!draft.value) return
  error.value = null
  savedMessage.value = null
  if (!isDraftDirty.value) {
    savedMessage.value = '没有检测到改动，未创建新版本'
    return
  }
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
      requiredConfirmation: false,
      stageKey: node.stageKey.trim() || null,
      stageGoal: node.name.trim(),
      recommendedSkillNames: [node.skillName.trim()],
      allowDynamicActions: true,
      confirmationPolicy: 'EVENT_DRIVEN',
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
        <h3 class="workflow-config__title">Agent-first 任务编排</h3>
        <p class="workflow-config__subtitle">选择 Skill 和编排意图，形成可解释的大阶段蓝图；执行细节交给 Agent 根据上下文动态推进</p>
      </div>
      <div class="workflow-config__skill-summary">
        <span>{{ skillLoading ? 'Skill 加载中' : `可选 Skill ${availableSkillNames.length}` }}</span>
      </div>
    </div>

    <section class="workflow-config__model" aria-label="Agent-first 编排模型">
      <div class="workflow-config__model-step">
        <strong>1. Skill 池</strong>
        <span>用户选择已上传的 Skill，作为 Agent 可调用能力</span>
      </div>
      <div class="workflow-config__model-step">
        <strong>2. 阶段蓝图</strong>
        <span>系统生成 PRD、HLD、LLD 等大阶段，不预先写死所有分支</span>
      </div>
      <div class="workflow-config__model-step">
        <strong>3. 运行时决策</strong>
        <span>Agent 根据产物、缺口、异常和用户交互事件实时补动作</span>
      </div>
    </section>

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
          <span class="workflow-definition__summary">{{ blueprintSummary(def) }}</span>
          <div class="workflow-definition__actions">
            <button
              v-if="editingDefinitionId !== def.id"
              class="workflow-config__button workflow-config__button--primary"
              type="button"
              @click="beginEdit(def)"
            >
              编辑蓝图
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
                :disabled="saving || !isDraftDirty"
                @click="saveDraft"
              >
                {{ saving ? '保存中' : isDraftDirty ? '保存新版' : '无改动' }}
              </button>
            </template>
          </div>
        </div>

        <div v-if="editingDefinitionId === def.id && draft" class="workflow-editor">
          <div class="workflow-editor__strategy">
            <label class="workflow-editor__name">
              <span>编排名称</span>
              <input v-model="draft.name" type="text" />
            </label>
            <label class="workflow-editor__check">
              <input v-model="draft.isDefault" type="checkbox" />
              <span>作为 {{ def.workItemType }} 默认编排</span>
            </label>
            <label class="workflow-editor__intent">
              <span>自然语言编排意图</span>
              <textarea
                v-model="orchestrationIntent"
                rows="3"
                aria-label="自然语言编排意图"
              />
            </label>
            <button
              class="workflow-config__button workflow-config__button--primary"
              type="button"
              @click="generateDraftPlan"
            >
              生成阶段草案
            </button>
          </div>

          <section class="workflow-editor__agent-flow" aria-label="Agent 理解流程图">
            <div class="workflow-editor__section-title">
              <strong>Agent 理解流程图</strong>
              <span>生成阶段草案时同步刷新，用户可据此调整阶段和 Skill</span>
            </div>
            <div class="workflow-editor__flow-grid">
              <div class="workflow-editor__flow-preview">
                <div
                  v-for="branch in agentFlowBranches"
                  :key="branch.id"
                  class="workflow-editor__flow-stage"
                >
                  <span class="workflow-editor__stage-order">{{ branch.id }}</span>
                  <div>
                    <strong>{{ branch.stage }}</strong>
                    <p>{{ branch.skillName }} · {{ branch.input }}</p>
                    <div class="workflow-editor__flow-branches">
                      <span>{{ branch.dynamic }}</span>
                      <span>{{ branch.review }}</span>
                      <span>异常时回到本阶段修复</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="workflow-editor__flow-diagram">
                <div class="workflow-editor__flow-diagram-title">Agent 预估执行路线</div>
                <MarkdownContent
                  class="workflow-editor__flow-mermaid"
                  :content="agentFlowMermaidMarkdown"
                />
                <details class="workflow-editor__flow-source">
                  <summary>查看 Mermaid 源码</summary>
                  <textarea
                    v-model="agentFlowMermaid"
                    rows="10"
                    aria-label="Agent 理解 Mermaid 草图"
                  />
                </details>
              </div>
            </div>
          </section>

          <div class="workflow-editor__workspace">
            <aside class="workflow-editor__skills" aria-label="可选 Skill">
              <div class="workflow-editor__section-title">
                <strong>Skill 池</strong>
                <span>点击加入阶段草案</span>
              </div>
              <button
                v-for="skillName in availableSkillNames"
                :key="skillName"
                class="workflow-editor__skill"
                type="button"
                :aria-label="`把 ${skillName} 加入阶段`"
                @click="appendSkillAsStage(skillName)"
              >
                <span>{{ skillName }}</span>
                <small>加入</small>
              </button>
            </aside>

            <div class="workflow-editor__stages" aria-label="阶段草案">
              <div class="workflow-editor__section-title">
                <strong>大阶段草案</strong>
                <span>阶段顺序只是建议，运行中允许 Agent 动态补动作</span>
              </div>

              <article
                v-for="(node, index) in draft.nodes"
                :key="`${node.nodeKey}-${index}`"
                class="workflow-editor__stage-card"
              >
                <div class="workflow-editor__stage-head">
                  <span class="workflow-editor__stage-order">{{ index + 1 }}</span>
                  <input v-model="node.name" type="text" aria-label="阶段名称" placeholder="阶段名称" />
                  <div class="workflow-editor__stage-actions">
                    <button type="button" :disabled="index === 0" @click="moveNode(index, -1)">上移</button>
                    <button type="button" :disabled="index === draft.nodes.length - 1" @click="moveNode(index, 1)">下移</button>
                    <button type="button" :disabled="draft.nodes.length <= 1" @click="removeNode(index)">删除</button>
                  </div>
                </div>

                <div class="workflow-editor__stage-grid">
                  <label>
                    <span>Skill</span>
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
                  </label>
                  <label>
                    <span>输入上下文</span>
                    <select v-model="node.inputPolicy" aria-label="输入策略">
                      <option
                        v-for="policy in inputPolicies"
                        :key="policy.value"
                        :value="policy.value"
                      >
                        {{ policy.label }}
                      </option>
                    </select>
                  </label>
                  <label>
                    <span>产物类型</span>
                    <select v-model="node.outputArtifactType" aria-label="输出类型">
                      <option v-for="type in outputTypes" :key="type" :value="type">{{ type }}</option>
                    </select>
                  </label>
                </div>

                <div class="workflow-editor__runtime">
                  <strong>运行时交互交给 Agent</strong>
                  <p>{{ interactionHint(node) }}</p>
                </div>
              </article>

              <button class="workflow-config__button" type="button" @click="addNode">
                新增空阶段
              </button>
            </div>
          </div>
        </div>

        <div v-else class="workflow-definition__body">
          <div class="workflow-definition__blueprint">
            <div class="workflow-definition__section-title">
              <strong>大阶段蓝图</strong>
              <span>用户确认的是路线，不是固定 DAG</span>
            </div>
            <div class="workflow-route" aria-label="阶段路线">
              <div
                v-for="node in def.nodes"
                :key="node.id"
                class="workflow-route__node"
              >
                <span class="workflow-route__order">{{ node.orderNo }}</span>
                <div class="workflow-route__content">
                  <div class="workflow-route__top">
                    <strong>{{ node.name }}</strong>
                    <span>{{ node.stageKey || node.nodeKey }}</span>
                  </div>
                  <p>{{ stageGoal(node) }}</p>
                  <div class="workflow-route__meta">
                    <span>Skill：{{ recommendedSkills(node) }}</span>
                    <span>输入：{{ inputPolicyLabel(node.inputPolicy) }}</span>
                    <span>产物：{{ node.outputArtifactType }}</span>
                  </div>
                  <small>{{ interactionHint(node) }}</small>
                </div>
              </div>
            </div>
          </div>

          <aside class="workflow-definition__runtime">
            <div class="workflow-definition__section-title">
              <strong>运行时原则</strong>
              <span>Agent 自主推进</span>
            </div>
            <ul>
              <li v-for="principle in runtimePrinciples" :key="principle">{{ principle }}</li>
            </ul>
            <div class="workflow-definition__section-title workflow-definition__section-title--compact">
              <strong>可能交互事件</strong>
            </div>
            <div class="workflow-events">
              <div
                v-for="event in interactionTypes"
                :key="event.type"
                class="workflow-events__item"
              >
                <span>{{ event.type }}</span>
                <strong>{{ event.label }}</strong>
                <p>{{ event.desc }}</p>
              </div>
            </div>
          </aside>
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

.workflow-config__model {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.workflow-config__model-step {
  min-height: 76px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
}

.workflow-config__model-step strong {
  display: block;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.workflow-config__model-step span {
  display: block;
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.5;
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

.workflow-definition__summary {
  flex: 1;
  min-width: 0;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 750;
  text-align: right;
}

.workflow-definition__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  padding: 16px;
}

.workflow-definition__section-title,
.workflow-editor__section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.workflow-definition__section-title strong,
.workflow-editor__section-title strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.workflow-definition__section-title span,
.workflow-editor__section-title span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 750;
}

.workflow-definition__section-title--compact {
  margin-top: 14px;
}

.workflow-route {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.workflow-route__node {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
}

.workflow-route__order,
.workflow-editor__stage-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background-color: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 900;
}

.workflow-route__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-route__top strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.workflow-route__top span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 750;
}

.workflow-route__content p,
.workflow-route__content small {
  display: block;
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.5;
}

.workflow-route__content small {
  color: var(--text-muted);
}

.workflow-route__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.workflow-route__meta span,
.workflow-events__item span {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.workflow-definition__runtime {
  min-width: 0;
  padding-left: 16px;
  border-left: 1px solid var(--border-color);
}

.workflow-definition__runtime ul {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.6;
}

.workflow-events {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.workflow-events__item {
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
}

.workflow-events__item strong {
  display: block;
  margin-top: 6px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
}

.workflow-events__item p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.45;
}

.workflow-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.workflow-editor__strategy {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 12px;
  align-items: end;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
}

.workflow-editor__intent {
  grid-column: 1 / -1;
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
.workflow-editor select,
.workflow-editor textarea {
  min-width: 0;
  min-height: 32px;
  padding: 6px 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
}

.workflow-editor textarea {
  resize: vertical;
  line-height: 1.5;
}

.workflow-editor input[type='checkbox'] {
  min-height: auto;
}

.workflow-editor__agent-flow {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
}

.workflow-editor__flow-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.9fr);
  gap: 12px;
}

.workflow-editor__flow-preview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.workflow-editor__flow-stage {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
}

.workflow-editor__flow-stage strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.workflow-editor__flow-stage p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.workflow-editor__flow-branches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.workflow-editor__flow-branches span {
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.workflow-editor__flow-diagram {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-secondary);
}

.workflow-editor__flow-diagram-title {
  margin-bottom: 8px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
}

.workflow-editor__flow-mermaid {
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-primary);
  overflow: auto;
}

.workflow-editor__flow-mermaid :deep(.markdown-content__mermaid) {
  min-height: 240px;
}

.workflow-editor__flow-mermaid :deep(.markdown-content__mermaid svg) {
  max-width: none;
  min-width: 520px;
}

.workflow-editor__flow-source {
  display: block !important;
  margin-top: 8px;
}

.workflow-editor__flow-source summary {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
}

.workflow-editor__flow-source textarea {
  width: 100%;
  margin-top: 6px;
  min-height: 260px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.45;
}

.workflow-editor__workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 14px;
}

.workflow-editor__skills,
.workflow-editor__stages {
  min-width: 0;
}

.workflow-editor__skill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  margin-bottom: 8px;
  padding: 6px 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.workflow-editor__skill small {
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 900;
}

.workflow-editor__stage-card {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background-color: var(--bg-primary);
}

.workflow-editor__stage-card + .workflow-editor__stage-card {
  margin-top: 10px;
}

.workflow-editor__stage-head {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.workflow-editor__stage-actions {
  display: flex;
  gap: 6px;
}

.workflow-editor__stage-actions button {
  min-height: 28px;
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 5px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
}

.workflow-editor__stage-actions button:disabled {
  opacity: 0.45;
}

.workflow-editor__stage-grid {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 140px 120px;
  gap: 10px;
  margin-top: 10px;
}

.workflow-editor__runtime {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--border-color);
}

.workflow-editor__runtime strong {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
}

.workflow-editor__runtime p {
  flex-basis: 100%;
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
}

@media (max-width: 1180px) {
  .workflow-config__model,
  .workflow-definition__body,
  .workflow-editor__flow-grid,
  .workflow-editor__workspace {
    grid-template-columns: 1fr;
  }

  .workflow-definition__runtime {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid var(--border-color);
    padding-top: 14px;
  }
}

@media (max-width: 760px) {
  .workflow-definition__header,
  .workflow-editor__stage-head,
  .workflow-editor__stage-grid,
  .workflow-editor__strategy {
    grid-template-columns: 1fr;
  }

  .workflow-definition__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .workflow-definition__summary {
    text-align: left;
  }
}
</style>
