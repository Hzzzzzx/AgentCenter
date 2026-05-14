<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ProjectContextOptions, ProjectContextSelection } from '../types/projectContext'

const props = defineProps<{
  modelValue: ProjectContextSelection
  contexts: ProjectContextSelection[]
  activeContextId: string
  options: ProjectContextOptions
  syncing?: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ProjectContextSelection]
  'update:activeContextId': [value: string]
  'save-selection': [value: ProjectContextSelection]
  'sync-data': []
}>()

type ProjectContextField = keyof Omit<ProjectContextSelection, 'id'>

const draftContextId = ref(props.modelValue.id)
const draftContext = ref<ProjectContextSelection>({ ...props.modelValue })
const projectOptions = computed(() => unique(props.contexts.map((context) => context.project)))
const cloudeReqProjectOptions = computed(() =>
  unique([
    ...contextsForProject().map((context) => context.cloudeReqProject),
    ...props.options.cloudeReqProjects,
  ])
)
const spaceOptions = computed(() =>
  unique([
    ...contextsForProject().map((context) => context.space),
    ...props.options.spaces,
  ])
)
const iterationOptions = computed(() =>
  unique([
    ...contextsForProjectAndSpace().map((context) => context.iteration),
    ...props.options.iterations,
  ])
)
const isCreatingContext = computed(() => draftContext.value.id.length === 0)
const canSaveSelection = computed(() =>
  Boolean(
    clean(draftContext.value.project)
    && clean(firstNonBlank(draftContext.value.externalProjectId, draftContext.value.cloudeReqProject))
    && clean(firstNonBlank(draftContext.value.externalSpaceId, draftContext.value.space))
    && clean(firstNonBlank(draftContext.value.externalIterationId, draftContext.value.iteration))
  )
)

function updateSelection(field: ProjectContextField, event: Event) {
  const value = (event.target as HTMLInputElement | HTMLSelectElement).value
  const matched = findContextFor(field, value)
  if (matched) {
    selectContext(matched)
    return
  }
  updateDraftField(field, value)
}

function selectContext(context: ProjectContextSelection) {
  draftContextId.value = context.id
  draftContext.value = { ...context }
}

function startNewContext() {
  draftContextId.value = ''
  draftContext.value = {
    id: '',
    project: '',
    cloudeReqProject: '',
    space: '',
    iteration: '',
  }
}

function saveSelection() {
  const context = draftContext.value
  if (!canSaveSelection.value) return
  emit('save-selection', normalizedDraft(context))
}

function findContextFor(field: ProjectContextField, value: string) {
  if (field === 'project') {
    return props.contexts.find((context) => context.project === value)
  }
  if (field === 'cloudeReqProject') {
    return contextsForProject().find((context) => context.externalProjectId === value || context.cloudeReqProject === value)
      ?? props.contexts.find((context) => context.externalProjectId === value || context.cloudeReqProject === value)
  }
  if (field === 'space') {
    return contextsForProject().find((context) => context.externalSpaceId === value || context.space === value)
      ?? props.contexts.find((context) => context.externalSpaceId === value || context.space === value)
  }
  if (field === 'iteration') {
    return contextsForProjectAndSpace().find((context) => context.externalIterationId === value || context.iteration === value)
      ?? props.contexts.find((context) => context.externalIterationId === value || context.iteration === value)
  }
  return props.contexts.find((context) => context[field] === value)
}

function contextsForProject() {
  const project = draftContext.value.project
  const matches = props.contexts.filter((context) => context.project === project)
  return matches.length > 0 ? matches : props.contexts
}

function contextsForProjectAndSpace() {
  const project = draftContext.value.project
  const space = draftContext.value.space
  const matches = props.contexts.filter((context) =>
    context.project === project && context.space === space
  )
  return matches.length > 0 ? matches : contextsForProject()
}

function unique(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((value): value is string => Boolean(value))))
}

function updateDraftField(field: ProjectContextField, value: string) {
  draftContext.value = normalizedDraft({
    ...draftContext.value,
    [field]: value,
    ...(field === 'cloudeReqProject' ? { externalProjectId: null } : {}),
    ...(field === 'space' ? { externalSpaceId: null } : {}),
    ...(field === 'iteration' ? { externalIterationId: null } : {}),
  })
  draftContextId.value = draftContext.value.id
  emit('update:modelValue', draftContext.value)
}

function normalizedDraft(context: ProjectContextSelection): ProjectContextSelection {
  return {
    ...context,
    project: clean(context.project) ?? '',
    cloudeReqProject: clean(context.cloudeReqProject) ?? clean(context.externalProjectId) ?? '',
    space: clean(context.space) ?? clean(context.externalSpaceId) ?? '',
    iteration: clean(context.iteration) ?? clean(context.externalIterationId) ?? '',
    externalProjectId: clean(context.externalProjectId) ?? clean(context.cloudeReqProject),
    externalSpaceId: clean(context.externalSpaceId) ?? clean(context.space),
    externalIterationId: clean(context.externalIterationId) ?? clean(context.iteration),
  }
}

function firstNonBlank(...values: Array<string | null | undefined>) {
  return values.find((value) => clean(value) !== null) ?? null
}

function clean(value: string | null | undefined) {
  return value == null || value.trim().length === 0 ? null : value.trim()
}

watch(
  () => props.modelValue,
  (value) => {
    draftContextId.value = value.id
    draftContext.value = { ...value }
  }
)

watch(
  () => props.contexts,
  (contexts) => {
    if (contexts.length === 0) {
      draftContextId.value = ''
      return
    }
    if (!contexts.some((context) => context.id === draftContextId.value)) {
      selectContext(contexts.find((context) => context.id === props.modelValue.id) ?? contexts[0])
    }
  }
)
</script>

<template>
  <section class="project-context">
    <header class="project-context__header">
      <div>
        <h1 class="project-context__title">项目管理</h1>
        <p class="project-context__subtitle">维护当前工作台的项目、企业数据源和生效上下文。</p>
      </div>
      <div class="project-context__actions">
        <button class="project-context__save" type="button" :disabled="props.saving || !canSaveSelection" @click="saveSelection">
          {{ props.saving ? '保存中' : '保存选择' }}
        </button>
        <button class="project-context__sync" type="button" :disabled="props.syncing" @click="emit('sync-data')">
          {{ props.syncing ? '同步中' : '同步数据' }}
        </button>
      </div>
    </header>

    <div class="project-context__layout">
      <aside class="project-context__list" aria-label="项目配置列表">
        <div class="project-context__list-head">
          <div>
            <strong>项目配置</strong>
            <span>{{ props.contexts.length }} 个配置</span>
          </div>
          <div class="project-context__list-actions">
            <em>来自同步源</em>
            <button class="project-context__add" type="button" @click="startNewContext">新增项目</button>
          </div>
        </div>
        <p v-if="props.contexts.length === 0" class="project-context__empty">暂无后端同步配置</p>
        <article
          v-for="context in props.contexts"
          :key="context.id"
          class="project-context__item"
          :class="{ 'project-context__item--active': context.id === props.activeContextId }"
        >
          <button class="project-context__item-main" type="button" @click="selectContext(context)">
            <span class="project-context__item-top">
              <strong>{{ context.project }}</strong>
              <em v-if="context.id === props.activeContextId">生效中</em>
              <em v-else-if="context.id === draftContext.id">待保存</em>
            </span>
            <span class="project-context__item-meta">
              <span>{{ context.cloudeReqProject }}</span>
              <span>{{ context.space }}</span>
              <span>{{ context.iteration }}</span>
            </span>
          </button>
          <span class="project-context__item-actions">
            <button type="button" @click="selectContext(context)">编辑</button>
            <button
              class="project-context__item-delete"
              type="button"
              disabled
              title="同步源配置暂不支持在工作台直接删除"
            >
              删除
            </button>
          </span>
        </article>
      </aside>

      <div class="project-context__panel">
        <div class="project-context__panel-head">
          <div>
            <strong>{{ isCreatingContext ? '新增项目配置' : '当前生效上下文' }}</strong>
            <span>{{ isCreatingContext ? '先选择企业同步源中的项目、空间和迭代，再保存为当前上下文' : '从企业同步源选择，保存后影响工作台筛选' }}</span>
          </div>
          <button
            v-if="isCreatingContext"
            class="project-context__panel-reset"
            type="button"
            @click="startNewContext"
          >
            清空
          </button>
        </div>

        <div class="project-context__fields">
          <label class="project-context__field project-context__field--wide">
            <span>自定义项目名称</span>
            <input
              aria-label="自定义项目名称"
              :value="draftContext.project"
              list="project-context-project-options"
              placeholder="例如 AgentCenter 企业车"
              @input="updateSelection('project', $event)"
            />
            <datalist id="project-context-project-options">
              <option v-for="project in projectOptions" :key="project" :value="project" />
            </datalist>
          </label>

          <label class="project-context__field">
            <span>CloudReq 项目</span>
            <select
              aria-label="选择 CloudeReq 项目"
              :value="draftContext.cloudeReqProject"
              @change="updateSelection('cloudeReqProject', $event)"
            >
              <option value="" disabled>请选择项目</option>
              <option v-for="project in cloudeReqProjectOptions" :key="project" :value="project">
                {{ project }}
              </option>
            </select>
          </label>

          <label class="project-context__field">
            <span>空间</span>
            <select
              aria-label="选择空间"
              :value="draftContext.space"
              @change="updateSelection('space', $event)"
            >
              <option value="" disabled>请选择空间</option>
              <option v-for="space in spaceOptions" :key="space" :value="space">
                {{ space }}
              </option>
            </select>
          </label>

          <label class="project-context__field">
            <span>迭代</span>
            <select
              aria-label="选择迭代"
              :value="draftContext.iteration"
              @change="updateSelection('iteration', $event)"
            >
              <option value="" disabled>请选择迭代</option>
              <option v-for="iteration in iterationOptions" :key="iteration" :value="iteration">
                {{ iteration }}
              </option>
            </select>
          </label>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.project-context {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 100%;
  padding: 24px;
  color: var(--text-primary);
  background: var(--bg-primary);
}

.project-context__header,
.project-context__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.project-context__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.project-context__subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.project-context__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.project-context__sync,
.project-context__save,
.project-context__add,
.project-context__panel-reset {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--brand-border);
  border-radius: 6px;
  color: #fff;
  background: var(--accent-blue);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.16);
}

.project-context__sync:hover,
.project-context__save:hover,
.project-context__add:hover,
.project-context__panel-reset:hover {
  filter: brightness(0.96);
}

.project-context__sync:disabled,
.project-context__save:disabled {
  cursor: default;
  opacity: 0.58;
  filter: grayscale(0.2);
}

.project-context__save {
  color: var(--accent-blue);
  background: var(--bg-card);
}

.project-context__panel-reset {
  color: var(--text-secondary);
  background: var(--bg-secondary);
  border-color: var(--border-color);
  box-shadow: none;
}

.project-context__layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(420px, 1.15fr);
  align-items: start;
  gap: 16px;
  width: 100%;
}

.project-context__list,
.project-context__panel {
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
}

.project-context__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
}

.project-context__list-head strong {
  display: block;
  color: var(--text-primary);
  font-size: 13px;
}

.project-context__list-head span {
  display: block;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 11px;
}

.project-context__list-head em {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 6px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.project-context__list-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.project-context__list-actions .project-context__add {
  height: 28px;
  padding: 0 10px;
  box-shadow: none;
}

.project-context__empty {
  margin: 8px 0;
  color: var(--text-muted);
  font-size: 12px;
}

.project-context__item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 11px;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--text-secondary);
  background: transparent;
}

.project-context__item:hover {
  border-color: var(--border-color);
  background: var(--bg-card-hover);
}

.project-context__item-main {
  appearance: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.project-context__item-top,
.project-context__item-meta {
  display: flex;
  align-items: center;
}

.project-context__item-top {
  justify-content: space-between;
  gap: 8px;
}

.project-context__item-top strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-size: 13px;
}

.project-context__item-top em {
  flex-shrink: 0;
  padding: 2px 6px;
  border-radius: 999px;
  background: #dcfce7;
  color: #059669;
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
}

.project-context__item-meta {
  flex-wrap: wrap;
  gap: 6px;
}

.project-context__item-meta span {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 6px;
  border-radius: 5px;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  font-size: 11px;
  line-height: 18px;
}

.project-context__item--active {
  border-color: var(--brand-border);
  background: var(--brand-soft);
}

.project-context__item-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding-top: 2px;
}

.project-context__item-actions button {
  appearance: none;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 5px;
  color: var(--accent-blue);
  background: var(--bg-secondary);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.project-context__item-actions button:hover:not(:disabled) {
  border-color: var(--brand-border);
  background: var(--brand-soft);
}

.project-context__item-actions button:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
  opacity: 0.55;
}

.project-context__item-actions .project-context__item-delete {
  color: #dc2626;
}

.project-context__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  overflow: hidden;
  padding: 18px;
}

.project-context__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.project-context__panel-head strong {
  display: block;
  color: var(--text-primary);
  font-size: 14px;
}

.project-context__panel-head span {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.project-context__fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  min-width: 0;
}

.project-context__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.project-context__field--wide {
  grid-column: 1 / -1;
}

.project-context__field input,
.project-context__field select {
  appearance: none;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  background: var(--bg-secondary);
  font-size: 13px;
  font-weight: 600;
}

.project-context__field select {
  padding-right: 30px;
  background:
    linear-gradient(45deg, transparent 50%, var(--text-muted) 50%) right 14px center / 5px 5px no-repeat,
    linear-gradient(135deg, var(--text-muted) 50%, transparent 50%) right 9px center / 5px 5px no-repeat,
    var(--bg-secondary);
}

.project-context__field input:focus,
.project-context__field select:focus {
  border-color: var(--accent-blue);
  outline: 0;
  box-shadow: 0 0 0 3px var(--glow-blue);
}

@media (max-width: 900px) {
  .project-context__layout,
  .project-context__fields {
    grid-template-columns: 1fr;
  }
}
</style>
