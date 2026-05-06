<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import type { RuntimeSkillRefreshResponse } from '../api/types'

const snapshot = ref<RuntimeSkillRefreshResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const shortChecksum = computed(() => (checksum: string) => checksum.slice(0, 10))

onMounted(() => {
  loadSkills()
})

async function loadSkills() {
  loading.value = true
  errorMessage.value = ''
  try {
    snapshot.value = await runtimeResourceApi.listSkills()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取 Skill 失败'
  } finally {
    loading.value = false
  }
}

async function refreshSkills() {
  loading.value = true
  errorMessage.value = ''
  try {
    snapshot.value = await runtimeResourceApi.refreshSkills()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '刷新 Skill 失败'
  } finally {
    loading.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '未刷新'
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div class="runtime-resources">
    <section class="runtime-resources__header">
      <div>
        <h3 class="runtime-resources__title">运行资源</h3>
        <p class="runtime-resources__subtitle">
          本地修改 .opencode/skills 后点击刷新，下一条对话和下一个工作流节点使用最新 Skill。
        </p>
      </div>
      <button
        class="runtime-resources__refresh"
        :disabled="loading"
        @click="refreshSkills"
      >
        {{ loading ? '刷新中...' : '刷新 Skill' }}
      </button>
    </section>

    <section class="runtime-resources__summary">
      <div class="runtime-resources__metric">
        <span class="runtime-resources__metric-label">可用 Skill</span>
        <strong>{{ snapshot?.skillCount ?? 0 }}</strong>
      </div>
      <div class="runtime-resources__metric">
        <span class="runtime-resources__metric-label">最近刷新</span>
        <strong>{{ formatTime(snapshot?.refreshedAt) }}</strong>
      </div>
      <div class="runtime-resources__path">
        <span>扫描目录</span>
        <code>{{ snapshot?.skillsPath ?? '.opencode/skills' }}</code>
      </div>
    </section>

    <div v-if="errorMessage" class="runtime-resources__error">{{ errorMessage }}</div>

    <section class="runtime-resources__panel">
      <div class="runtime-resources__panel-header">
        <h4>项目级 Skill</h4>
        <span>OpenCode 项目目录</span>
      </div>

      <div v-if="loading && !snapshot" class="runtime-resources__empty">正在读取...</div>
      <div v-else-if="!snapshot?.skills.length" class="runtime-resources__empty">
        暂未发现 Skill。请在扫描目录下创建子目录，并放入 SKILL.md。
      </div>
      <div v-else class="runtime-resources__list">
        <article
          v-for="skill in snapshot.skills"
          :key="skill.name"
          class="runtime-skill"
        >
          <div class="runtime-skill__main">
            <div class="runtime-skill__title-row">
              <h5>{{ skill.name }}</h5>
              <span class="runtime-skill__badge">已加载</span>
            </div>
            <p>{{ skill.description || '未填写描述' }}</p>
            <code>{{ skill.relativePath }}</code>
          </div>
          <div class="runtime-skill__meta">
            <span>{{ formatTime(skill.updatedAt) }}</span>
            <span>{{ shortChecksum(skill.checksum) }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.runtime-resources {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 20px;
  overflow-y: auto;
}

.runtime-resources__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.runtime-resources__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.runtime-resources__subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.runtime-resources__refresh {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 6px;
  background: var(--accent-blue);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.runtime-resources__refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.runtime-resources__summary {
  display: grid;
  grid-template-columns: 140px 220px minmax(0, 1fr);
  gap: 12px;
}

.runtime-resources__metric,
.runtime-resources__path {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.runtime-resources__metric-label,
.runtime-resources__path span {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
}

.runtime-resources__metric strong {
  font-size: 14px;
  color: var(--text-primary);
}

.runtime-resources code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-secondary);
}

.runtime-resources__error {
  padding: 10px 12px;
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.08);
  color: var(--error);
  font-size: 13px;
}

.runtime-resources__panel {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  overflow: hidden;
}

.runtime-resources__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.runtime-resources__panel-header h4 {
  font-size: 14px;
  font-weight: 600;
}

.runtime-resources__panel-header span {
  font-size: 12px;
  color: var(--text-secondary);
}

.runtime-resources__empty {
  padding: 24px;
  color: var(--text-secondary);
  font-size: 13px;
}

.runtime-resources__list {
  display: flex;
  flex-direction: column;
}

.runtime-skill {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
}

.runtime-skill:last-child {
  border-bottom: none;
}

.runtime-skill__main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.runtime-skill__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.runtime-skill h5 {
  font-size: 14px;
  font-weight: 600;
}

.runtime-skill p {
  font-size: 13px;
  color: var(--text-secondary);
}

.runtime-skill__badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  color: var(--success);
  font-size: 11px;
  font-weight: 600;
}

.runtime-skill__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .runtime-resources__header,
  .runtime-skill {
    align-items: stretch;
    flex-direction: column;
  }

  .runtime-resources__summary {
    grid-template-columns: 1fr;
  }

  .runtime-skill__meta {
    align-items: flex-start;
  }
}
</style>
