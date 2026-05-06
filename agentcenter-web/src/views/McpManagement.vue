<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { mcpApi } from '../api/runtimeResources'
import type { ProjectMcpServerDto } from '../api/types'

interface Props {
  projectId?: string
}

const props = withDefaults(defineProps<Props>(), {
  projectId: '01DEFAULTPROJECT0000000000001',
})

const mcps = ref<ProjectMcpServerDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const searchQuery = ref('')

const filteredMcps = computed(() => {
  if (!searchQuery.value) return mcps.value
  const q = searchQuery.value.toLowerCase()
  return mcps.value.filter(m =>
    m.name.toLowerCase().includes(q) ||
    (m.lastHealthMessage || '').toLowerCase().includes(q)
  )
})

const serverCount = computed(() => mcps.value.length)
const enabledCount = computed(() => mcps.value.filter(m => m.status === 'ENABLED').length)
const totalTools = computed(() => mcps.value.reduce((sum, m) => sum + (m.toolCount || 0), 0))
const errorCount = computed(() => mcps.value.filter(m => m.status === 'FAILED').length)

const statusColorMap: Record<string, string> = {
  ENABLED: 'var(--success)',
  DISABLED: 'var(--text-muted)',
  FAILED: 'var(--error)',
}

const healthColorMap: Record<string, string> = {
  OK: 'var(--success)',
  FAILED: 'var(--error)',
  UNKNOWN: 'var(--text-muted)',
}

async function loadMcps() {
  loading.value = true
  error.value = null
  try {
    mcps.value = await mcpApi.list(props.projectId)
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleImport() {
  try {
    await mcpApi.importConfig(props.projectId)
    await loadMcps()
  } catch (e: any) {
    error.value = e.message || '导入失败'
  }
}

async function handleToggle(mcp: ProjectMcpServerDto) {
  try {
    if (mcp.status === 'ENABLED') {
      await mcpApi.disable(props.projectId, mcp.id)
    } else {
      await mcpApi.enable(props.projectId, mcp.id)
    }
    await loadMcps()
  } catch (e: any) {
    error.value = e.message || '操作失败'
  }
}

async function handleTest(mcp: ProjectMcpServerDto) {
  try {
    await mcpApi.test(props.projectId, mcp.id)
    await loadMcps()
  } catch (e: any) {
    error.value = e.message || '测试失败'
  }
}

async function handleRefreshAll() {
  try {
    await mcpApi.refresh(props.projectId)
    await loadMcps()
  } catch (e: any) {
    error.value = e.message || '刷新失败'
  }
}

async function handleRefreshTools(mcp: ProjectMcpServerDto) {
  try {
    await mcpApi.refreshTools(props.projectId, mcp.id)
    await loadMcps()
  } catch (e: any) {
    error.value = e.message || '刷新工具失败'
  }
}

function formatTime(value: string | null | undefined) {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadMcps)
</script>

<template>
  <div class="mcp-management">
    <div class="mcp-management__header">
      <div>
        <h1 class="mcp-management__title">MCP 管理 · 项目工具连接</h1>
        <p class="mcp-management__subtitle">管理当前项目启用的 MCP Server 与工具能力</p>
      </div>
    </div>

    <div class="mcp-management__metrics">
      <div class="mcp-management__metric">
        <span class="mcp-management__metric-value">{{ serverCount }}</span>
        <span class="mcp-management__metric-label">项目 MCP</span>
      </div>
      <div class="mcp-management__metric">
        <span class="mcp-management__metric-value">{{ enabledCount }}</span>
        <span class="mcp-management__metric-label">已启用</span>
      </div>
      <div class="mcp-management__metric">
        <span class="mcp-management__metric-value">{{ totalTools }}</span>
        <span class="mcp-management__metric-label">可用工具</span>
      </div>
      <div class="mcp-management__metric">
        <span class="mcp-management__metric-value" :style="{ color: errorCount > 0 ? 'var(--error)' : undefined }">{{ errorCount }}</span>
        <span class="mcp-management__metric-label">异常连接</span>
      </div>
    </div>

    <div class="mcp-management__actions">
      <button class="mcp-management__action-btn" @click="handleRefreshAll" :disabled="loading">刷新 MCP</button>
      <button class="mcp-management__action-btn" @click="handleImport">导入配置</button>
      <input
        type="text"
        class="mcp-management__search"
        v-model="searchQuery"
        placeholder="搜索 MCP 名称、工具名..."
      />
    </div>

    <div v-if="loading && mcps.length === 0" class="mcp-management__loading">加载中...</div>
    <div v-else-if="error" class="mcp-management__error">{{ error }}</div>
    <div v-else-if="filteredMcps.length === 0" class="mcp-management__empty">
      {{ searchQuery ? '无匹配 MCP' : '当前项目暂无 MCP 配置\n可以从项目配置文件导入，或由管理员创建项目级 MCP Server。' }}
    </div>

    <div v-else class="mcp-management__list">
      <div
        v-for="mcp in filteredMcps"
        :key="mcp.id"
        class="mcp-management__item"
      >
        <div class="mcp-management__item-header">
          <span class="mcp-management__item-name">{{ mcp.name }}</span>
          <div class="mcp-management__item-badges">
            <span class="mcp-management__item-type">{{ mcp.serverType }}</span>
            <span
              class="mcp-management__item-status"
              :style="{ color: statusColorMap[mcp.status] || 'var(--text-muted)' }"
            >{{ mcp.status }}</span>
          </div>
        </div>
        <div class="mcp-management__item-meta">
          <span>工具: {{ mcp.toolCount || 0 }}</span>
          <span :style="{ color: healthColorMap[mcp.lastHealthStatus] || 'var(--text-muted)' }">
            健康: {{ mcp.lastHealthStatus }}
          </span>
          <span>检查: {{ formatTime(mcp.lastCheckedAt) }}</span>
        </div>
        <div class="mcp-management__item-actions">
          <button class="mcp-management__item-action" @click="handleToggle(mcp)">
            {{ mcp.status === 'ENABLED' ? '停用' : '启用' }}
          </button>
          <button class="mcp-management__item-action" @click="handleTest(mcp)" :disabled="mcp.status === 'DISABLED'">测试</button>
          <button class="mcp-management__item-action" @click="handleRefreshTools(mcp)" :disabled="mcp.status === 'DISABLED'">刷新工具</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mcp-management {
  padding: 24px 32px;
  height: 100%;
  overflow-y: auto;
}

.mcp-management__header {
  margin-bottom: 20px;
}

.mcp-management__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.mcp-management__subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.mcp-management__metrics {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-primary);
  border-radius: 8px;
}

.mcp-management__metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.mcp-management__metric-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.mcp-management__metric-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.mcp-management__actions {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  align-items: center;
}

.mcp-management__action-btn {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.mcp-management__action-btn:hover {
  border-color: var(--border-color-hover);
}

.mcp-management__search {
  flex: 1;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-primary);
  background: var(--bg-card);
}

.mcp-management__search:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.mcp-management__loading,
.mcp-management__empty,
.mcp-management__error {
  padding: 32px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  white-space: pre-line;
}

.mcp-management__error {
  color: var(--error);
}

.mcp-management__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mcp-management__item {
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.mcp-management__item:hover {
  border-color: var(--border-color-hover);
}

.mcp-management__item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.mcp-management__item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.mcp-management__item-badges {
  display: flex;
  gap: 8px;
  align-items: center;
}

.mcp-management__item-type {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 600;
}

.mcp-management__item-status {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.mcp-management__item-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.mcp-management__item-actions {
  display: flex;
  gap: 8px;
}

.mcp-management__item-action {
  padding: 3px 10px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.mcp-management__item-action:hover {
  background: var(--bg-card-hover);
}

.mcp-management__item-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
