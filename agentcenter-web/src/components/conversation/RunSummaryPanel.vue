<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { WorkflowNodeStatus } from '../../api/types'

interface TodoItem {
  id: string
  label: string
  status: WorkflowNodeStatus
  meta?: string
}

interface ResultItem {
  id: string
  title: string
  source: 'workflow-node' | 'runtime-event'
  filePath?: string | null
  nodeName?: string
}

interface SourceItem {
  id: string
  label: string
  meta?: string
}

const props = defineProps<{
  visible: boolean
  todoItems: TodoItem[]
  results: ResultItem[]
  sources: SourceItem[]
  artifactOpen?: boolean
}>()

const emit = defineEmits<{
  'open-artifact': [artifactId: string]
}>()

const HOVER_EXPAND_DELAY_MS = 180
const HOVER_COLLAPSE_DELAY_MS = 720

const pinned = ref(false)
const hoverExpanded = ref(false)
let expandTimer: number | undefined
let collapseTimer: number | undefined

const canExpand = computed(() => props.visible && !props.artifactOpen)
const expanded = computed(() =>
  canExpand.value
  && (pinned.value || hoverExpanded.value)
)
const showRail = computed(() => props.visible && !expanded.value)
const visibleResults = computed(() => props.results.slice(0, 6))
const hiddenResultCount = computed(() => Math.max(0, props.results.length - visibleResults.value.length))
const visibleSources = computed(() => props.sources.slice(0, 6))

function resultMeta(result: ResultItem): string {
  if (result.nodeName) return result.nodeName
  if (!result.filePath) return '真实文件产物'
  const segments = result.filePath.split(/[\\/]/).filter(Boolean)
  const fileName = segments.at(-1)
  return fileName || '真实文件产物'
}

function statusLabel(status: WorkflowNodeStatus): string {
  const labels: Record<WorkflowNodeStatus, string> = {
    PENDING: '等待中',
    RUNNING: '运行中',
    READY: '待确认',
    WAITING_CONFIRMATION: '待确认',
    COMPLETED: '已完成',
    FAILED: '失败',
    SKIPPED: '已跳过',
  }
  return labels[status]
}

function clearExpandTimer() {
  if (expandTimer !== undefined) {
    window.clearTimeout(expandTimer)
    expandTimer = undefined
  }
}

function clearCollapseTimer() {
  if (collapseTimer !== undefined) {
    window.clearTimeout(collapseTimer)
    collapseTimer = undefined
  }
}

function scheduleExpand() {
  if (!canExpand.value) return
  clearCollapseTimer()
  if (expanded.value) return
  clearExpandTimer()
  expandTimer = window.setTimeout(() => {
    hoverExpanded.value = true
    expandTimer = undefined
  }, HOVER_EXPAND_DELAY_MS)
}

function expandNow() {
  if (!canExpand.value) return
  clearExpandTimer()
  clearCollapseTimer()
  hoverExpanded.value = true
}

function scheduleCollapse() {
  clearExpandTimer()
  if (pinned.value) return
  clearCollapseTimer()
  collapseTimer = window.setTimeout(() => {
    hoverExpanded.value = false
    collapseTimer = undefined
  }, HOVER_COLLAPSE_DELAY_MS)
}

function togglePinned() {
  if (pinned.value) {
    pinned.value = false
    scheduleCollapse()
    return
  }
  clearExpandTimer()
  clearCollapseTimer()
  hoverExpanded.value = true
  pinned.value = true
}

watch(
  () => [props.visible, props.artifactOpen] as const,
  ([visible, artifactOpen]) => {
    if (!visible || artifactOpen) {
      clearExpandTimer()
      clearCollapseTimer()
      hoverExpanded.value = false
    }
  }
)

onBeforeUnmount(() => {
  clearExpandTimer()
  clearCollapseTimer()
})
</script>

<template>
  <div
    v-if="visible"
    class="run-summary"
    :class="{
      'run-summary--expanded': expanded,
      'run-summary--rail': showRail,
      'run-summary--pinned': pinned,
    }"
    @mouseenter="scheduleExpand"
    @mouseleave="scheduleCollapse"
    @focusin="scheduleExpand"
    @focusout="scheduleCollapse"
  >
    <button
      v-if="showRail"
      type="button"
      class="run-summary__rail"
      :title="artifactOpen ? '产物预览打开中' : '展开运行摘要'"
      :aria-label="artifactOpen ? '产物预览打开中' : '展开运行摘要'"
      @click="expandNow"
    >
      <span></span>
    </button>

    <Transition name="run-summary-panel">
    <section v-if="expanded" class="run-summary__panel" aria-label="运行摘要">
      <header class="run-summary__header">
        <h2>任务进展</h2>
        <button
          type="button"
          class="run-summary__pin"
          :aria-label="pinned ? '取消固定运行摘要' : '固定运行摘要'"
          :title="pinned ? '取消固定' : '固定'"
          @click="togglePinned"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 16 16"
            aria-hidden="true"
            :class="{ 'is-pinned': pinned }"
          >
            <path
              v-if="pinned"
              fill="currentColor"
              d="M9.828.722a.5.5 0 0 1 .354.146l4.95 4.95a.5.5 0 0 1 0 .707c-.48.48-1.072.588-1.503.588a3.3 3.3 0 0 1-.46-.039l-3.134 3.134c.04.145.125.494.16 1.013.046.702-.032 1.687-.72 2.375a.5.5 0 0 1-.707 0l-2.182-2.182-3.182 3.182c-.195.195-1.219.902-1.414.707-.195-.195.512-1.22.707-1.414l3.182-3.182-2.182-2.182a.5.5 0 0 1 0-.707c.688-.688 1.673-.766 2.375-.72.52.035.868.12 1.013.16l3.134-3.133a3.3 3.3 0 0 1-.04-.461c0-.43.108-1.022.589-1.503a.5.5 0 0 1 .353-.146z"
            />
            <path
              v-else
              fill="currentColor"
              d="M9.828.722a.5.5 0 0 1 .354.146l4.95 4.95a.5.5 0 0 1 0 .707c-.48.48-1.072.588-1.503.588a3.3 3.3 0 0 1-.46-.039l-3.134 3.134c.04.145.125.494.16 1.013.046.702-.032 1.687-.72 2.375a.5.5 0 0 1-.707 0l-2.182-2.182-3.182 3.182c-.195.195-1.219.902-1.414.707-.195-.195.512-1.22.707-1.414l3.182-3.182-2.182-2.182a.5.5 0 0 1 0-.707c.688-.688 1.673-.766 2.375-.72.52.035.868.12 1.013.16l3.134-3.133a3.3 3.3 0 0 1-.04-.461c0-.43.108-1.022.589-1.503a.5.5 0 0 1 .353-.146zm.122 2.112a.5.5 0 0 1-.122.51L6.293 6.878a.5.5 0 0 1-.511.122 4.8 4.8 0 0 0-.517-.115c-.482-.079-.872-.04-1.14.098l4.892 4.891c.138-.267.177-.657.098-1.139A4.8 4.8 0 0 0 9 10.218a.5.5 0 0 1 .122-.511l3.535-3.535a.5.5 0 0 1 .51-.122l.018.004.064.01c.074.01.175.02.294.02.202 0 .43-.032.639-.154L9.95 1.697c-.122.208-.154.437-.154.639 0 .12.01.22.02.294l.01.064.004.018a.5.5 0 0 1 .12.12z"
            />
          </svg>
        </button>
      </header>

      <div v-if="todoItems.length" class="run-summary__section">
        <ol class="run-summary__todo">
          <li
            v-for="item in todoItems"
            :key="item.id"
            :class="`run-summary__todo-item run-summary__todo-item--${item.status.toLowerCase()}`"
          >
            <span class="run-summary__dot"></span>
            <span class="run-summary__todo-main">
              <strong>{{ item.label }}</strong>
              <small v-if="item.meta">{{ item.meta }}</small>
            </span>
            <em>{{ statusLabel(item.status) }}</em>
          </li>
        </ol>
      </div>

      <div class="run-summary__section">
        <h3>生成结果</h3>
        <div v-if="visibleResults.length" class="run-summary__results">
          <button
            v-for="result in visibleResults"
            :key="result.id"
            type="button"
            class="run-summary__result"
            @click="emit('open-artifact', result.id)"
          >
            <span class="run-summary__doc-icon">□</span>
            <span>
              <strong>{{ result.title }}</strong>
              <small>{{ resultMeta(result) }}</small>
            </span>
          </button>
          <span v-if="hiddenResultCount" class="run-summary__more">再显示 {{ hiddenResultCount }} 个</span>
        </div>
        <p v-else class="run-summary__empty">本轮还没有写入文件产物。</p>
      </div>

      <div v-if="visibleSources.length" class="run-summary__section">
        <h3>来源</h3>
        <div class="run-summary__sources">
          <span v-for="source in visibleSources" :key="source.id">
            {{ source.label }}<small v-if="source.meta"> · {{ source.meta }}</small>
          </span>
        </div>
      </div>
    </section>
    </Transition>
  </div>
</template>

<style scoped>
.run-summary {
  position: fixed;
  top: 50%;
  right: calc(var(--shell-gutter) + var(--right-panel-collapsed-width) + 12px);
  z-index: 42;
  display: flex;
  width: 28px;
  align-items: center;
  justify-content: flex-end;
  color: var(--text-primary);
  transform: translateY(-50%);
  transition:
    width 360ms cubic-bezier(0.2, 0.8, 0.2, 1),
    right 240ms ease,
    opacity 180ms ease;
}

.run-summary--expanded {
  width: min(336px, calc(100vw - 112px));
}

.run-summary__panel {
  flex: 0 0 min(336px, calc(100vw - 112px));
  width: min(336px, calc(100vw - 112px));
  max-height: calc(100dvh - var(--titlebar-height) - var(--statusbar-height) - 36px);
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
}

.run-summary__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px 10px;
}

.run-summary__header h2,
.run-summary__section h3 {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.run-summary__header h2 {
  color: var(--text-primary);
  font-size: 14px;
  letter-spacing: 0;
}

.run-summary__pin {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.run-summary__pin:hover {
  background: var(--brand-soft);
  color: var(--text-primary);
}

.run-summary__pin svg {
  transition: color 180ms ease;
}

.run-summary__pin svg.is-pinned {
  color: color-mix(in srgb, var(--text-secondary) 80%, var(--text-primary));
}

.run-summary__section {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 14px 18px;
  border-top: 1px solid var(--border-color);
}

.run-summary__section h3 {
  margin: 0;
  font-size: 13px;
}

.run-summary__todo {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.run-summary__todo-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.run-summary__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--border-color);
}

.run-summary__todo-item--running .run-summary__dot {
  background: var(--accent-blue);
  box-shadow: 0 0 0 5px var(--focus-ring);
}

.run-summary__todo-item--completed .run-summary__dot {
  background: var(--success);
}

.run-summary__todo-item--ready .run-summary__dot,
.run-summary__todo-item--waiting_confirmation .run-summary__dot {
  background: var(--warning);
}

.run-summary__todo-item--failed .run-summary__dot {
  background: var(--error);
}

.run-summary__todo-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.run-summary__todo-main strong,
.run-summary__result strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-summary__todo-main small,
.run-summary__result small {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-summary__todo-item em {
  color: var(--text-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 750;
}

.run-summary__results {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.run-summary__result {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 9px;
  width: 100%;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 6px 4px;
  text-align: left;
}

.run-summary__result:hover {
  background: var(--brand-soft);
}

.run-summary__doc-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1;
}

.run-summary__result span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.run-summary__more,
.run-summary__empty {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
}

.run-summary__empty {
  margin: 0;
}

.run-summary__sources {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.run-summary__sources span {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
}

.run-summary__sources small {
  color: var(--text-muted);
}

.run-summary__rail {
  display: grid;
  place-items: center;
  width: 28px;
  height: 88px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
  transition:
    background 180ms ease,
    box-shadow 180ms ease,
    transform 220ms ease;
}

.run-summary__rail span {
  width: 5px;
  height: 44px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.18);
}

.run-summary__rail:hover,
.run-summary--expanded .run-summary__rail {
  background: color-mix(in srgb, var(--accent-blue) 13%, rgba(255, 255, 255, 0.9));
  box-shadow: 0 10px 26px rgba(37, 99, 235, 0.16);
}

.run-summary-panel-enter-active,
.run-summary-panel-leave-active {
  transition:
    opacity 280ms ease,
    transform 340ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.run-summary-panel-enter-from,
.run-summary-panel-leave-to {
  opacity: 0;
  transform: translateX(18px) scale(0.985);
}

.run-summary-panel-enter-to,
.run-summary-panel-leave-from {
  opacity: 1;
  transform: translateX(0) scale(1);
}

@media (max-width: 1180px) {
  .run-summary {
    right: calc(var(--shell-gutter) + 10px);
  }
}
</style>
