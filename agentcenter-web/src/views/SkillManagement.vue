<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { skillApi } from '../api/runtimeResources'
import type { RuntimeSkillDetailDto, SkillStatus } from '../api/types'

interface Props {
  projectId?: string
}

const props = withDefaults(defineProps<Props>(), {
  projectId: '01DEFAULTPROJECT0000000000001',
})

const skills = ref<RuntimeSkillDetailDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const searchQuery = ref('')
const filterStatus = ref<SkillStatus | 'ALL'>('ALL')

const filteredSkills = computed(() => {
  let result = skills.value
  if (filterStatus.value !== 'ALL') {
    result = result.filter(s => s.status === filterStatus.value)
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(s =>
      s.name.toLowerCase().includes(q) ||
      (s.description || '').toLowerCase().includes(q) ||
      (s.displayName || '').toLowerCase().includes(q)
    )
  }
  return result
})

const skillCount = computed(() => skills.value.length)
const enabledCount = computed(() => skills.value.filter(s => s.status === 'ENABLED').length)
const errorCount = computed(() => skills.value.filter(s => s.status === 'INVALID').length)

const statusColorMap: Record<string, string> = {
  ENABLED: 'var(--success)',
  DISABLED: 'var(--text-muted)',
  INVALID: 'var(--error)',
  UPDATING: 'var(--warning)',
}

async function loadSkills() {
  loading.value = true
  error.value = null
  try {
    skills.value = await skillApi.list(props.projectId)
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await skillApi.upload(props.projectId, file)
    await loadSkills()
  } catch (e: any) {
    error.value = e.message || '上传失败'
  }
  input.value = ''
}

async function handleRefresh() {
  try {
    await skillApi.refresh(props.projectId)
    await loadSkills()
  } catch (e: any) {
    error.value = e.message || '刷新失败'
  }
}

async function handleToggleSkill(skill: RuntimeSkillDetailDto) {
  try {
    if (skill.status === 'ENABLED') {
      await skillApi.disable(props.projectId, skill.id)
    } else {
      await skillApi.enable(props.projectId, skill.id)
    }
    await loadSkills()
  } catch (e: any) {
    error.value = e.message || '操作失败'
  }
}

async function handleDeleteSkill(skill: RuntimeSkillDetailDto) {
  if (!confirm(`确定要删除 Skill "${skill.name}" 吗？`)) return
  try {
    await skillApi.delete(props.projectId, skill.id)
    await loadSkills()
  } catch (e: any) {
    error.value = e.message || '删除失败'
  }
}

function formatTime(value: string | null | undefined) {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadSkills)
</script>

<template>
  <div class="skill-management">
    <div class="skill-management__header">
      <div>
        <h1 class="skill-management__title">Skill 管理 · OpenCode 运行能力</h1>
        <p class="skill-management__subtitle">管理当前项目可用的 OpenCode Skill，上传后可被会话和工作流节点使用</p>
      </div>
    </div>

    <div class="skill-management__metrics">
      <div class="skill-management__metric">
        <span class="skill-management__metric-value">{{ skillCount }}</span>
        <span class="skill-management__metric-label">已安装</span>
      </div>
      <div class="skill-management__metric">
        <span class="skill-management__metric-value">{{ enabledCount }}</span>
        <span class="skill-management__metric-label">已启用</span>
      </div>
      <div class="skill-management__metric">
        <span class="skill-management__metric-value" :style="{ color: errorCount > 0 ? 'var(--error)' : undefined }">{{ errorCount }}</span>
        <span class="skill-management__metric-label">异常</span>
      </div>
    </div>

    <div class="skill-management__actions">
      <label class="skill-management__upload-btn">
        <input type="file" accept=".zip" @change="handleUpload" hidden />
        上传 Skill ZIP
      </label>
      <button class="skill-management__action-btn" @click="handleRefresh" :disabled="loading">刷新 Skill</button>
      <button
        class="skill-management__action-btn"
        :class="{ 'skill-management__action-btn--active': filterStatus === 'INVALID' }"
        @click="filterStatus = filterStatus === 'INVALID' ? 'ALL' : 'INVALID'"
      >
        只看异常
      </button>
      <input
        type="text"
        class="skill-management__search"
        v-model="searchQuery"
        placeholder="搜索 Skill 名称、描述..."
      />
    </div>

    <div v-if="loading && skills.length === 0" class="skill-management__loading">加载中...</div>
    <div v-else-if="error" class="skill-management__error">{{ error }}</div>
    <div v-else-if="filteredSkills.length === 0" class="skill-management__empty">
      {{ searchQuery || filterStatus !== 'ALL' ? '无匹配 Skill' : '暂无已安装的 Skill' }}
    </div>

    <div v-else class="skill-management__list">
      <div
        v-for="skill in filteredSkills"
        :key="skill.id"
        class="skill-management__item"
      >
        <div class="skill-management__item-header">
          <span class="skill-management__item-name">{{ skill.displayName || skill.name }}</span>
          <span
            class="skill-management__item-status"
            :style="{ color: statusColorMap[skill.status] || 'var(--text-muted)' }"
          >{{ skill.status }}</span>
        </div>
        <div class="skill-management__item-meta">
          <span>{{ skill.version || '0.0.0' }}</span>
          <span>{{ skill.source }}</span>
          <span>{{ skill.relativePath }}</span>
          <span>{{ formatTime(skill.updatedAt) }}</span>
        </div>
        <div v-if="skill.description" class="skill-management__item-desc">{{ skill.description }}</div>
        <div class="skill-management__item-actions">
          <label class="skill-management__item-action">
            <input type="file" accept=".zip" @change="handleUpload" hidden />
            更新
          </label>
          <button class="skill-management__item-action" @click="handleToggleSkill(skill)">
            {{ skill.status === 'ENABLED' ? '停用' : '启用' }}
          </button>
          <button class="skill-management__item-action skill-management__item-action--danger" @click="handleDeleteSkill(skill)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skill-management {
  padding: 24px 32px;
  height: 100%;
  overflow-y: auto;
}

.skill-management__header {
  margin-bottom: 20px;
}

.skill-management__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.skill-management__subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.skill-management__metrics {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-primary);
  border-radius: 8px;
}

.skill-management__metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.skill-management__metric-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.skill-management__metric-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.skill-management__actions {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  align-items: center;
}

.skill-management__upload-btn {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  background: var(--accent-blue);
  color: #fff;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.skill-management__upload-btn:hover {
  opacity: 0.9;
}

.skill-management__action-btn {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.skill-management__action-btn:hover {
  border-color: var(--border-color-hover);
}

.skill-management__action-btn--active {
  background: rgba(239, 68, 68, 0.08);
  border-color: var(--error);
  color: var(--error);
}

.skill-management__search {
  flex: 1;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-primary);
  background: var(--bg-card);
}

.skill-management__search:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.skill-management__loading,
.skill-management__empty,
.skill-management__error {
  padding: 32px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.skill-management__error {
  color: var(--error);
}

.skill-management__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-management__item {
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.skill-management__item:hover {
  border-color: var(--border-color-hover);
}

.skill-management__item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.skill-management__item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.skill-management__item-status {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.skill-management__item-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.skill-management__item-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  line-height: 1.4;
}

.skill-management__item-actions {
  display: flex;
  gap: 8px;
}

.skill-management__item-action {
  padding: 3px 10px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.skill-management__item-action:hover {
  background: var(--bg-card-hover);
}

.skill-management__item-action--danger {
  color: var(--error);
  border-color: rgba(239, 68, 68, 0.3);
}

.skill-management__item-action--danger:hover {
  background: rgba(239, 68, 68, 0.08);
}
</style>
