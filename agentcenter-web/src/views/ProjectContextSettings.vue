<script setup lang="ts">
import type { ProjectContextOptions, ProjectContextSelection } from '../types/projectContext'

interface Props {
  modelValue: ProjectContextSelection
  options: ProjectContextOptions
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: ProjectContextSelection]
}>()

type ProjectContextField = keyof ProjectContextSelection

function updateSelection(field: ProjectContextField, event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: target.value,
  })
}
</script>

<template>
  <div class="project-context">
    <div class="project-context__header">
      <div>
        <h1 class="project-context__title">项目管理</h1>
        <p class="project-context__subtitle">当前工作台上下文</p>
      </div>
    </div>

    <section class="project-context__current" aria-label="当前项目上下文">
      <span class="project-context__current-label">当前</span>
      <strong>{{ modelValue.project }}</strong>
      <span>{{ modelValue.space }}</span>
      <span>{{ modelValue.iteration }}</span>
    </section>

    <form class="project-context__form" aria-label="选择项目空间迭代">
      <label class="project-context__field">
        <span>项目</span>
        <select
          aria-label="选择项目"
          :value="modelValue.project"
          @change="updateSelection('project', $event)"
        >
          <option v-for="project in options.projects" :key="project" :value="project">
            {{ project }}
          </option>
        </select>
      </label>

      <label class="project-context__field">
        <span>空间</span>
        <select
          aria-label="选择空间"
          :value="modelValue.space"
          @change="updateSelection('space', $event)"
        >
          <option v-for="space in options.spaces" :key="space" :value="space">
            {{ space }}
          </option>
        </select>
      </label>

      <label class="project-context__field">
        <span>迭代</span>
        <select
          aria-label="选择迭代"
          :value="modelValue.iteration"
          @change="updateSelection('iteration', $event)"
        >
          <option v-for="iteration in options.iterations" :key="iteration" :value="iteration">
            {{ iteration }}
          </option>
        </select>
      </label>
    </form>
  </div>
</template>

<style scoped>
.project-context {
  height: 100%;
  padding: 24px 32px;
  overflow-y: auto;
}

.project-context__header {
  margin-bottom: 20px;
}

.project-context__title {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
}

.project-context__subtitle {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.project-context__current {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin-bottom: 20px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 13px;
}

.project-context__current-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.project-context__current strong {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 700;
}

.project-context__current span:not(.project-context__current-label) {
  min-width: 0;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-context__form {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 14px;
}

.project-context__field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.project-context__field span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.project-context__field select {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 13px;
  outline: 0;
}

.project-context__field select:focus {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 3px var(--glow-blue);
}

@media (max-width: 900px) {
  .project-context__form {
    grid-template-columns: 1fr;
  }

  .project-context__current {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
