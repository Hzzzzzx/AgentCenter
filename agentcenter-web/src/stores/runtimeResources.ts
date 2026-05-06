import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { skillApi, mcpApi, sessionResourceApi } from '../api/runtimeResources'
import type {
  RuntimeSkillDetailDto,
  ProjectMcpServerDto,
  RuntimeResourceAuditDto,
  SessionRuntimeResourceDto,
  ResourceRefreshStatus,
} from '../api/types'

export const useRuntimeResourceStore = defineStore('runtimeResources', () => {
  // ===== State =====
  const skills = ref<RuntimeSkillDetailDto[]>([])
  const mcps = ref<ProjectMcpServerDto[]>([])
  const selectedResourceId = ref<string | null>(null)
  const selectedResourceType = ref<'SKILL' | 'MCP' | null>(null)
  const refreshStatus = ref<ResourceRefreshStatus>('IDLE')
  const skillAudits = ref<RuntimeResourceAuditDto[]>([])
  const mcpAudits = ref<RuntimeResourceAuditDto[]>([])
  const sessionResourceStatus = ref<SessionRuntimeResourceDto | null>(null)

  const loading = ref(false)
  const error = ref<string | null>(null)

  // ===== Computed =====
  const skillCount = computed(() => skills.value.length)
  const enabledSkillCount = computed(() => skills.value.filter(s => s.status === 'ENABLED').length)
  const errorSkillCount = computed(() => skills.value.filter(s => s.status === 'INVALID').length)
  const mcpCount = computed(() => mcps.value.length)
  const enabledMcpCount = computed(() => mcps.value.filter(m => m.status === 'ENABLED').length)
  const mcpToolCount = computed(() => mcps.value.reduce((sum, m) => sum + (m.toolCount || 0), 0))

  const selectedSkill = computed(() => {
    if (selectedResourceType.value !== 'SKILL' || !selectedResourceId.value) return null
    return skills.value.find(s => s.id === selectedResourceId.value) ?? null
  })

  const selectedMcp = computed(() => {
    if (selectedResourceType.value !== 'MCP' || !selectedResourceId.value) return null
    return mcps.value.find(m => m.id === selectedResourceId.value) ?? null
  })

  // ===== Skill Actions =====
  async function loadSkills(projectId: string) {
    loading.value = true
    error.value = null
    try {
      skills.value = await skillApi.list(projectId)
    } catch (e: any) {
      error.value = e.message || '加载 Skill 失败'
    } finally {
      loading.value = false
    }
  }

  async function uploadSkill(projectId: string, file: File) {
    loading.value = true
    error.value = null
    try {
      refreshStatus.value = 'REFRESHING'
      await skillApi.upload(projectId, file)
      await loadSkills(projectId)
      refreshStatus.value = 'IDLE'
    } catch (e: any) {
      error.value = e.message || '上传失败'
      refreshStatus.value = 'FAILED'
    } finally {
      loading.value = false
    }
  }

  async function updateSkillZip(projectId: string, skillId: string, file: File) {
    loading.value = true
    error.value = null
    try {
      refreshStatus.value = 'REFRESHING'
      await skillApi.updateZip(projectId, skillId, file)
      await loadSkills(projectId)
      refreshStatus.value = 'IDLE'
    } catch (e: any) {
      error.value = e.message || '更新失败'
      refreshStatus.value = 'FAILED'
    } finally {
      loading.value = false
    }
  }

  async function enableSkill(projectId: string, skillId: string) {
    try {
      await skillApi.enable(projectId, skillId)
      await loadSkills(projectId)
    } catch (e: any) {
      error.value = e.message || '启用失败'
    }
  }

  async function disableSkill(projectId: string, skillId: string) {
    try {
      await skillApi.disable(projectId, skillId)
      await loadSkills(projectId)
    } catch (e: any) {
      error.value = e.message || '停用失败'
    }
  }

  async function deleteSkill(projectId: string, skillId: string) {
    try {
      await skillApi.delete(projectId, skillId)
      if (selectedResourceId.value === skillId) {
        selectedResourceId.value = null
        selectedResourceType.value = null
      }
      await loadSkills(projectId)
    } catch (e: any) {
      error.value = e.message || '删除失败'
    }
  }

  async function refreshSkills(projectId: string) {
    loading.value = true
    error.value = null
    try {
      refreshStatus.value = 'REFRESHING'
      await skillApi.refresh(projectId)
      await loadSkills(projectId)
      refreshStatus.value = 'IDLE'
    } catch (e: any) {
      error.value = e.message || '刷新失败'
      refreshStatus.value = 'FAILED'
    } finally {
      loading.value = false
    }
  }

  async function loadSkillAudits(projectId: string, skillId: string) {
    try {
      skillAudits.value = await skillApi.audits(projectId, skillId)
    } catch (e: any) {
      error.value = e.message || '加载审计失败'
    }
  }

  // ===== MCP Actions =====
  async function loadMcps(projectId: string) {
    loading.value = true
    error.value = null
    try {
      mcps.value = await mcpApi.list(projectId)
    } catch (e: any) {
      error.value = e.message || '加载 MCP 失败'
    } finally {
      loading.value = false
    }
  }

  async function importMcps(projectId: string) {
    loading.value = true
    error.value = null
    try {
      await mcpApi.importConfig(projectId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '导入失败'
    } finally {
      loading.value = false
    }
  }

  async function enableMcp(projectId: string, mcpId: string) {
    try {
      await mcpApi.enable(projectId, mcpId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '启用失败'
    }
  }

  async function disableMcp(projectId: string, mcpId: string) {
    try {
      await mcpApi.disable(projectId, mcpId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '停用失败'
    }
  }

  async function testMcp(projectId: string, mcpId: string) {
    loading.value = true
    error.value = null
    try {
      await mcpApi.test(projectId, mcpId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '测试失败'
    } finally {
      loading.value = false
    }
  }

  async function refreshMcpTools(projectId: string, mcpId: string) {
    loading.value = true
    error.value = null
    try {
      await mcpApi.refreshTools(projectId, mcpId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '刷新工具失败'
    } finally {
      loading.value = false
    }
  }

  async function refreshMcps(projectId: string) {
    loading.value = true
    error.value = null
    try {
      await mcpApi.refresh(projectId)
      await loadMcps(projectId)
    } catch (e: any) {
      error.value = e.message || '刷新失败'
    } finally {
      loading.value = false
    }
  }

  async function loadMcpAudits(projectId: string, mcpId: string) {
    try {
      mcpAudits.value = await mcpApi.audits(projectId, mcpId)
    } catch (e: any) {
      error.value = e.message || '加载审计失败'
    }
  }

  // ===== Session Resource Status =====
  async function loadSessionResourceStatus(sessionId: string) {
    try {
      sessionResourceStatus.value = await sessionResourceApi.getStatus(sessionId)
    } catch (e: any) {
      // Silently fail for session resource status
    }
  }

  // ===== Selection =====
  function selectResource(type: 'SKILL' | 'MCP' | null, id: string | null) {
    selectedResourceType.value = type
    selectedResourceId.value = id
  }

  function clearError() {
    error.value = null
  }

  return {
    // State
    skills,
    mcps,
    selectedResourceId,
    selectedResourceType,
    refreshStatus,
    skillAudits,
    mcpAudits,
    sessionResourceStatus,
    loading,
    error,

    // Computed
    skillCount,
    enabledSkillCount,
    errorSkillCount,
    mcpCount,
    enabledMcpCount,
    mcpToolCount,
    selectedSkill,
    selectedMcp,

    // Skill Actions
    loadSkills,
    uploadSkill,
    updateSkillZip,
    enableSkill,
    disableSkill,
    deleteSkill,
    refreshSkills,
    loadSkillAudits,

    // MCP Actions
    loadMcps,
    importMcps,
    enableMcp,
    disableMcp,
    testMcp,
    refreshMcpTools,
    refreshMcps,
    loadMcpAudits,

    // Session
    loadSessionResourceStatus,

    // Selection
    selectResource,
    clearError,
  }
})