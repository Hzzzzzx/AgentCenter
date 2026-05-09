<script setup lang="ts">
import type { ProjectContextOptions, ProjectContextSelection } from '../types/projectContext'

const props = defineProps<{
  modelValue: ProjectContextSelection
  contexts: ProjectContextSelection[]
  activeContextId: string
  options: ProjectContextOptions
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ProjectContextSelection]
  'update:contexts': [value: ProjectContextSelection[]]
  'update:activeContextId': [value: string]
  'sync-data': []
}>()

type ProjectContextField = keyof Omit<ProjectContextSelection, 'id'>

function updateSelection(field: ProjectContextField, event: Event) {
  updateContext({
    ...props.modelValue,
    [field]: (event.target as HTMLInputElement | HTMLSelectElement).value,
  })
}

function updateContext(nextContext: ProjectContextSelection) {
  const nextContexts = props.contexts.map((item) =>
    item.id === nextContext.id ? nextContext : item
  )
  emit('update:contexts', nextContexts)
  emit('update:modelValue', nextContext)
}

function selectContext(context: ProjectContextSelection) {
  emit('update:activeContextId', context.id)
  emit('update:modelValue', context)
}

function addContext() {
  const nextContext: ProjectContextSelection = {
    id: `ctx-${Date.now()}`,
    project: '新项目',
    cloudeReqProject: props.options.cloudeReqProjects[0] ?? '',
    space: props.options.spaces[0] ?? '',
    iteration: props.options.iterations[0] ?? '',
  }
  emit('update:contexts', [...props.contexts, nextContext])
  emit('update:activeContextId', nextContext.id)
  emit('update:modelValue', nextContext)
}

function deleteContext(context: ProjectContextSelection) {
  if (props.contexts.length <= 1) return
  const nextContexts = props.contexts.filter((item) => item.id !== context.id)
  emit('update:contexts', nextContexts)
  if (context.id === props.activeContextId) {
    const nextActive = nextContexts[0]
    emit('update:activeContextId', nextActive.id)
    emit('update:modelValue', nextActive)
  }
}
</script>

<template>
  <section class="project-context">
    <header class="project-context__header">
      <div>
        <h1 class="project-context__title">项目管理</h1>
        <p class="project-context__subtitle">维护当前工作台的项目、企业数据源和生效上下文。</p>
      </div>
      <button class="project-context__sync" type="button" @click="emit('sync-data')">
        同步数据
      </button>
    </header>

    <div class="project-context__layout">
      <aside class="project-context__list" aria-label="项目配置列表">
        <div class="project-context__list-head">
          <div>
            <strong>项目配置</strong>
            <span>{{ props.contexts.length }} 个配置</span>
          </div>
          <button class="project-context__add" type="button" @click="addContext">新增</button>
        </div>
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
              :disabled="props.contexts.length <= 1"
              @click="deleteContext(context)"
            >
              删除
            </button>
          </span>
        </article>
      </aside>

      <div class="project-context__panel">
        <div class="project-context__panel-head">
          <div>
            <strong>当前配置</strong>
            <span>保存后作为当前工作台上下文生效</span>
          </div>
        </div>

        <div class="project-context__fields">
          <label class="project-context__field project-context__field--wide">
            <span>项目</span>
            <input
              aria-label="填写项目名称"
              :value="props.modelValue.project"
              placeholder="输入自定义项目名称"
              @input="updateSelection('project', $event)"
            />
          </label>

          <label class="project-context__field">
            <span>CloudeReq 项目</span>
            <select
              aria-label="选择 CloudeReq 项目"
              :value="props.modelValue.cloudeReqProject"
              @change="updateSelection('cloudeReqProject', $event)"
            >
              <option v-for="project in props.options.cloudeReqProjects" :key="project" :value="project">
                {{ project }}
              </option>
            </select>
          </label>

          <label class="project-context__field">
            <span>空间</span>
            <select aria-label="选择空间" :value="props.modelValue.space" @change="updateSelection('space', $event)">
              <option v-for="space in props.options.spaces" :key="space" :value="space">
                {{ space }}
              </option>
            </select>
          </label>

          <label class="project-context__field">
            <span>迭代</span>
            <select aria-label="选择迭代" :value="props.modelValue.iteration" @change="updateSelection('iteration', $event)">
              <option v-for="iteration in props.options.iterations" :key="iteration" :value="iteration">
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

.project-context__sync,
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
.project-context__add:hover {
  filter: brightness(0.96);
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
