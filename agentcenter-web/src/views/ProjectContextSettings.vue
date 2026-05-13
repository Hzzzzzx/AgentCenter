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
const draftContext = computed(() =>
  props.contexts.find((context) => context.id === draftContextId.value)
  ?? props.modelValue
)
const projectOptions = computed(() => unique(props.contexts.map((context) => context.project)))
const cloudeReqProjectOptions = computed(() =>
  unique(contextsForProject().map((context) => context.cloudeReqProject))
)
const spaceOptions = computed(() =>
  unique(contextsForProject().map((context) => context.space))
)
const iterationOptions = computed(() =>
  unique(contextsForProjectAndSpace().map((context) => context.iteration))
)

function updateSelection(field: ProjectContextField, event: Event) {
  const value = (event.target as HTMLInputElement | HTMLSelectElement).value
  const matched = findContextFor(field, value)
  if (matched) {
    selectContext(matched)
  }
}

function selectContext(context: ProjectContextSelection) {
  draftContextId.value = context.id
}

function saveSelection() {
  const context = draftContext.value
  if (!context.id) return
  emit('save-selection', context)
}

function findContextFor(field: ProjectContextField, value: string) {
  if (field === 'project') {
    return props.contexts.find((context) => context.project === value)
  }
  if (field === 'cloudeReqProject') {
    return contextsForProject().find((context) => context.cloudeReqProject === value)
      ?? props.contexts.find((context) => context.cloudeReqProject === value)
  }
  if (field === 'space') {
    return contextsForProject().find((context) => context.space === value)
      ?? props.contexts.find((context) => context.space === value)
  }
  if (field === 'iteration') {
    return contextsForProjectAndSpace().find((context) => context.iteration === value)
      ?? props.contexts.find((context) => context.iteration === value)
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

watch(
  () => props.modelValue.id,
  (id) => {
    draftContextId.value = id
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
      draftContextId.value = props.modelValue.id || contexts[0].id
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
        <button class="project-context__save" type="button" :disabled="props.saving || !draftContext.id" @click="saveSelection">
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
          <em>来自同步源</em>
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
          </span>
        </article>
      </aside>

      <div class="project-context__panel">
        <div class="project-context__panel-head">
          <div>
            <strong>当前生效上下文</strong>
            <span>从企业同步源选择，保存后影响工作台筛选</span>
          </div>
        </div>

        <div class="project-context__fields">
          <label class="project-context__field project-context__field--wide">
            <span>项目</span>
            <select
              aria-label="选择项目"
              :value="draftContext.project"
              :disabled="projectOptions.length === 0"
              @change="updateSelection('project', $event)"
            >
              <option v-if="projectOptions.length === 0" value="">暂无项目</option>
              <option v-for="project in projectOptions" :key="project" :value="project">
                {{ project }}
              </option>
            </select>
          </label>

          <label class="project-context__field">
            <span>CloudeReq 项目</span>
            <select
              aria-label="选择 CloudeReq 项目"
              :value="draftContext.cloudeReqProject"
              :disabled="cloudeReqProjectOptions.length === 0"
              @change="updateSelection('cloudeReqProject', $event)"
            >
              <option v-if="cloudeReqProjectOptions.length === 0" value="">暂无项目</option>
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
              :disabled="spaceOptions.length === 0"
              @change="updateSelection('space', $event)"
            >
              <option v-if="spaceOptions.length === 0" value="">暂无空间</option>
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
              :disabled="iterationOptions.length === 0"
              @change="updateSelection('iteration', $event)"
            >
              <option v-if="iterationOptions.length === 0" value="">暂无迭代</option>
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
.project-context__add {
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
.project-context__add:hover {
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

.project-context__layout {
  display: grid;
  grid-template-columns: minmax(220px, 240px) minmax(0, 1fr);
  gap: 16px;
  width: 100%;
  max-width: 720px;
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
