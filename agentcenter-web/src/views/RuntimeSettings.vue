<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import type { RuntimeEnvironmentStatusDto } from '../api/types'
import { useRuntimeSettingsStore } from '../stores/runtimeSettings'

const runtimeSettings = useRuntimeSettingsStore()
const runtimeStatus = ref<RuntimeEnvironmentStatusDto | null>(null)
const isLoadingRuntimeStatus = ref(false)
const runtimeStatusError = ref('')

const runtimeStatusLabel = computed(() => {
  if (!runtimeStatus.value) return '未加载'
  if (!runtimeStatus.value.serverReachable) return '未连接'
  return runtimeStatus.value.isolated ? '已隔离' : '需检查'
})

async function loadRuntimeStatus() {
  isLoadingRuntimeStatus.value = true
  runtimeStatusError.value = ''
  try {
    runtimeStatus.value = await runtimeResourceApi.status()
  } catch (error) {
    runtimeStatusError.value = error instanceof Error ? error.message : '运行状态加载失败'
  } finally {
    isLoadingRuntimeStatus.value = false
  }
}

onMounted(() => {
  runtimeSettings.initFromStorage()
  void loadRuntimeStatus()
})
</script>

<template>
  <section class="runtime-settings" aria-label="运行设置">
    <header class="runtime-settings__header">
      <div>
        <h1>运行设置</h1>
        <p>控制工作流节点完成后的推进方式。设置会影响从首页、看板和详情面板启动的工作流。</p>
      </div>
      <span class="runtime-settings__mode">
        {{ runtimeSettings.autoRunWorkflow ? '自动运行' : '人工确认' }}
      </span>
    </header>

    <div class="runtime-settings__body">
      <section class="runtime-settings__section">
        <div class="runtime-settings__section-main">
          <h2>自动运行工作流</h2>
          <p>
            关闭时，Agent 判断当前 Skill 已完成后会暂停在当前节点，并让用户确认进入下一节点、继续补充或重新执行。
            开启时，Agent 判断输入输出已充分后会自动进入下一节点，仍会在需要用户输入或选择时暂停。
          </p>
        </div>
        <button
          type="button"
          class="runtime-settings__toggle"
          :class="{ 'runtime-settings__toggle--on': runtimeSettings.autoRunWorkflow }"
          role="switch"
          :aria-checked="runtimeSettings.autoRunWorkflow"
          @click="runtimeSettings.setAutoRunWorkflow(!runtimeSettings.autoRunWorkflow)"
        >
          <span></span>
        </button>
      </section>

      <section class="runtime-settings__section">
        <div class="runtime-settings__section-main">
          <h2>Prompt Debug 看板</h2>
          <p>
            开启后，会话工作台在收到 prompt_debug 运行事件时显示可拖拽调试看板，用来核对本轮发送给 OpenCode Runtime 的 prompt_async 请求。
            关闭后，调试事件仍保留在运行事件中，但不再显示浮层。
          </p>
        </div>
        <button
          type="button"
          class="runtime-settings__toggle"
          :class="{ 'runtime-settings__toggle--on': runtimeSettings.promptDebugPanelEnabled }"
          role="switch"
          :aria-checked="runtimeSettings.promptDebugPanelEnabled"
          aria-label="显示 Prompt Debug 看板"
          @click="runtimeSettings.setPromptDebugPanelEnabled(!runtimeSettings.promptDebugPanelEnabled)"
        >
          <span></span>
        </button>
      </section>

      <section class="runtime-settings__summary" aria-label="当前执行策略">
        <div>
          <strong>当前启动参数</strong>
          <code>{{ runtimeSettings.workflowRunMode }}</code>
        </div>
        <p>
          这个参数会随启动工作流请求发送到 Bridge，并持久化到工作流实例中，后续节点推进按该实例的模式执行。
        </p>
      </section>

      <section class="runtime-settings__runtime" aria-label="OpenCode Server">
        <header class="runtime-settings__runtime-header">
          <div>
            <h2>OpenCode Server</h2>
            <span
              class="runtime-settings__badge"
              :class="{
                'runtime-settings__badge--ok': runtimeStatus?.serverReachable && runtimeStatus?.isolated,
                'runtime-settings__badge--warn': runtimeStatus && (!runtimeStatus.serverReachable || !runtimeStatus.isolated),
              }"
            >
              {{ runtimeStatusLabel }}
            </span>
          </div>
          <button
            type="button"
            class="runtime-settings__refresh"
            :disabled="isLoadingRuntimeStatus"
            aria-label="刷新 OpenCode Server 状态"
            title="刷新 OpenCode Server 状态"
            @click="loadRuntimeStatus"
          >
            刷新
          </button>
        </header>

        <div v-if="runtimeStatus" class="runtime-settings__runtime-grid">
          <div class="runtime-settings__runtime-row">
            <span>Runtime</span>
            <strong>{{ runtimeStatus.runtimeType }}</strong>
          </div>
          <div class="runtime-settings__runtime-row">
            <span>Server URL</span>
            <code>{{ runtimeStatus.serverUrl }}</code>
          </div>
          <div class="runtime-settings__runtime-row runtime-settings__runtime-row--wide">
            <span>Bridge 解析工作路径</span>
            <code>{{ runtimeStatus.resolvedWorkingDirectory }}</code>
          </div>
          <div class="runtime-settings__runtime-row runtime-settings__runtime-row--wide">
            <span>OpenCode directory</span>
            <code>{{ runtimeStatus.serverDirectory || '未返回' }}</code>
          </div>
          <div class="runtime-settings__runtime-row runtime-settings__runtime-row--wide">
            <span>OpenCode worktree</span>
            <code>{{ runtimeStatus.serverWorktree || '未返回' }}</code>
          </div>
          <div class="runtime-settings__runtime-row runtime-settings__runtime-row--wide">
            <span>配置工作路径</span>
            <code>{{ runtimeStatus.configuredWorkingDirectory }}</code>
          </div>
        </div>

        <p
          v-if="runtimeStatus?.message || runtimeStatusError"
          class="runtime-settings__runtime-message"
          :class="{ 'runtime-settings__runtime-message--error': runtimeStatusError || runtimeStatus?.isolated === false }"
        >
          {{ runtimeStatusError || runtimeStatus?.message }}
        </p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.runtime-settings {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 28px;
  background: var(--bg-primary);
  color: var(--text-primary);
}

.runtime-settings__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.runtime-settings__header h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
}

.runtime-settings__header p,
.runtime-settings__section-main p,
.runtime-settings__summary p {
  margin: 8px 0 0;
  max-width: 720px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.runtime-settings__mode {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 5px 10px;
  border-radius: 7px;
  background: color-mix(in srgb, var(--accent-blue) 12%, var(--bg-card));
  color: var(--accent-blue);
  font-size: 13px;
  font-weight: 800;
}

.runtime-settings__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 18px;
}

.runtime-settings__section,
.runtime-settings__summary {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
}

.runtime-settings__section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px;
}

.runtime-settings__section-main {
  min-width: 0;
}

.runtime-settings__section-main h2 {
  margin: 0;
  font-size: 17px;
  line-height: 1.35;
}

.runtime-settings__toggle {
  position: relative;
  flex: 0 0 auto;
  width: 52px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--surface-hover);
  cursor: pointer;
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.runtime-settings__toggle span {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  transition: transform 0.16s ease;
}

.runtime-settings__toggle--on {
  border-color: var(--accent-blue);
  background: var(--accent-blue);
}

.runtime-settings__toggle--on span {
  transform: translateX(22px);
}

.runtime-settings__summary {
  padding: 16px 18px;
}

.runtime-settings__runtime {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
}

.runtime-settings__runtime-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.runtime-settings__runtime-header > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.runtime-settings__runtime h2 {
  margin: 0;
  font-size: 17px;
  line-height: 1.35;
}

.runtime-settings__badge {
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 7px;
  background: var(--surface-hover);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.runtime-settings__badge--ok {
  background: var(--success-soft);
  color: var(--success);
}

.runtime-settings__badge--warn {
  background: var(--warning-soft);
  color: var(--warning);
}

.runtime-settings__refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 54px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
}

.runtime-settings__refresh:hover:not(:disabled) {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.runtime-settings__refresh:disabled {
  cursor: progress;
  opacity: 0.6;
}

.runtime-settings__runtime-grid {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr);
  gap: 10px;
}

.runtime-settings__runtime-row {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border-radius: 7px;
  background: var(--surface-hover);
}

.runtime-settings__runtime-row--wide {
  grid-column: 1 / -1;
}

.runtime-settings__runtime-row span {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.runtime-settings__runtime-row strong,
.runtime-settings__runtime-row code {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 800;
}

.runtime-settings__runtime-message {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.runtime-settings__runtime-message--error {
  color: var(--warning);
}

.runtime-settings__summary div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.runtime-settings__summary strong {
  font-size: 14px;
}

.runtime-settings__summary code {
  padding: 3px 7px;
  border-radius: 6px;
  background: var(--surface-hover);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 680px) {
  .runtime-settings {
    padding: 18px;
  }

  .runtime-settings__header,
  .runtime-settings__section {
    flex-direction: column;
    align-items: stretch;
  }

  .runtime-settings__toggle {
    align-self: flex-start;
  }

  .runtime-settings__runtime-grid {
    grid-template-columns: 1fr;
  }
}
</style>
