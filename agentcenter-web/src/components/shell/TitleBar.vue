<script setup lang="ts">
import type { ProjectContextSelection } from '../../types/projectContext'
import ThemeSwitcher from '../theme/ThemeSwitcher.vue'

interface Props {
  searchValue?: string
  projectContext?: ProjectContextSelection
  iterationOptions?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  searchValue: '',
  projectContext: () => ({
    id: 'default',
    project: 'AgentCenter',
    cloudeReqProject: '研发中台',
    space: '研发中台',
    iteration: 'Sprint 14',
  }),
  iterationOptions: () => ['Sprint 14', 'Sprint 15', '长期演进'],
})

const emit = defineEmits<{
  'update:searchValue': [value: string]
  'update-project-context': [value: ProjectContextSelection]
}>()

function updateIteration(event: Event) {
  emit('update-project-context', {
    ...props.projectContext,
    iteration: (event.target as HTMLSelectElement).value,
  })
}
</script>

<template>
  <header class="title-bar">
    <div class="title-bar__brand">
      <div class="title-bar__logo" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
          <path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8L12 2z" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
          <path d="M18.5 14l.9 2.6L22 17.5l-2.6.9-.9 2.6-.9-2.6-2.6-.9 2.6-.9.9-2.6z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="title-bar__name">
        <span>AI DevOps</span>
        <strong>v2.0</strong>
      </div>
    </div>

    <div class="title-bar__context" aria-label="当前项目空间迭代">
      <span class="title-bar__context-label">当前项目</span>
      <strong class="title-bar__context-project">{{ props.projectContext.project }}</strong>
      <select
        class="title-bar__iteration"
        aria-label="选择当前迭代"
        :value="props.projectContext.iteration"
        @change="updateIteration"
      >
        <option v-for="iteration in props.iterationOptions" :key="iteration" :value="iteration">
          {{ iteration }}
        </option>
      </select>
    </div>

    <div class="title-bar__search">
      <svg class="title-bar__search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none">
        <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2"/>
        <path d="M20 20l-4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <input
        type="search"
        placeholder="搜索 FE、US、Task、Work、缺陷、漏洞..."
        :value="props.searchValue"
        @input="emit('update:searchValue', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="title-bar__actions">
      <ThemeSwitcher />
      <div class="title-bar__user">
        <span class="title-bar__avatar">张</span>
        <span class="title-bar__user-text">
          <strong>产品负责人 A</strong>
          <em>技术负责人</em>
        </span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.title-bar {
  display: grid;
  grid-template-columns: max-content max-content minmax(260px, 1fr) max-content;
  align-items: center;
  gap: 16px;
  height: var(--titlebar-height);
  padding: 0 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.title-bar__brand,
.title-bar__context,
.title-bar__actions,
.title-bar__user {
  display: flex;
  align-items: center;
}

.title-bar__brand {
  gap: 10px;
  min-width: 160px;
}

.title-bar__logo {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: var(--on-brand);
  background: var(--brand-gradient);
}

.title-bar__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.title-bar__name strong {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  color: var(--accent-blue);
  background: var(--bg-tertiary);
}

.title-bar__context {
  gap: 6px;
  min-width: 0;
  max-width: 360px;
  height: 30px;
  padding: 0 6px 0 9px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  font-size: 12px;
  white-space: nowrap;
}

.title-bar__context-label {
  flex-shrink: 0;
  color: var(--text-muted);
  font-weight: 600;
}

.title-bar__context-project {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-weight: 700;
}

.title-bar__search {
  position: relative;
  max-width: 480px;
  width: 100%;
  justify-self: center;
}

.title-bar__iteration {
  appearance: none;
  flex-shrink: 0;
  max-width: 128px;
  height: 22px;
  padding: 0 22px 0 8px;
  border: 0;
  border-radius: 5px;
  color: var(--accent-blue);
  background:
    linear-gradient(45deg, transparent 50%, var(--accent-blue) 50%) right 10px center / 5px 5px no-repeat,
    linear-gradient(135deg, var(--accent-blue) 50%, transparent 50%) right 6px center / 5px 5px no-repeat,
    var(--brand-soft);
  font-size: 11px;
  font-weight: 700;
  outline: 0;
}

.title-bar__iteration:hover,
.title-bar__iteration:focus {
  box-shadow: inset 0 0 0 1px var(--brand-border);
}

.title-bar__search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  pointer-events: none;
}

.title-bar__search input {
  width: 100%;
  height: 36px;
  padding: 0 14px 0 44px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 13px;
  outline: 0;
}

.title-bar__search input:focus {
  border-color: var(--accent-blue);
  background: var(--bg-secondary);
  box-shadow: 0 0 0 3px var(--glow-blue);
}

.title-bar__actions {
  gap: 8px;
}

.title-bar__user {
  gap: 8px;
  padding-left: 4px;
}

.title-bar__avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--brand-gradient);
  color: var(--on-brand);
  font-size: 12px;
  font-weight: 600;
}

.title-bar__user-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.title-bar__user-text strong {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.title-bar__user-text em {
  font-style: normal;
  font-size: 10px;
  color: var(--text-muted);
}

@media (max-width: 1180px) {
  .title-bar {
    grid-template-columns: max-content max-content minmax(220px, 1fr) max-content;
  }

  .title-bar__iteration {
    display: none;
  }
}
</style>
