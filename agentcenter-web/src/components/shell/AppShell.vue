<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import TitleBar from './TitleBar.vue'
import LeftSidebar from './LeftSidebar.vue'
import CenterWorkbench from './CenterWorkbench.vue'
import RightPanel from './RightPanel.vue'
import StatusBar from './StatusBar.vue'
import type { AgentSessionDto, ArtifactDto, WorkItemDto } from '../../api/types'
import type { ProjectContextOptions, ProjectContextSelection } from '../../types/projectContext'

interface Props {
  activeView?: string
  selectedWorkItem?: WorkItemDto | null
  selectedArtifact?: ArtifactDto | null
  projectContext?: ProjectContextSelection
  projectContextOptions?: ProjectContextOptions
}

const props = withDefaults(defineProps<Props>(), {
  activeView: 'home',
  projectContext: () => ({
    id: '',
    project: '',
    cloudeReqProject: '',
    space: '',
    iteration: '',
  }),
  projectContextOptions: () => ({
    cloudeReqProjects: [],
    spaces: [],
    iterations: [],
  }),
})

const emit = defineEmits<{
  'update:activeView': [value: string]
  'handle-confirmation': [id: string]
  'select-session': [session: AgentSessionDto]
  'create-general-session': []
  'navigate-settings': [tab: string]
  'start-workflow': [workItemId: string]
  'enter-work-item-conversation': [id: string]
  'confirmations-changed': [workItemId?: string | null]
  'close-artifact': []
  'update-project-context': [value: ProjectContextSelection]
}>()

const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const rightExpanded = ref(false)
const leftWidth = ref(280)
const rightWidth = ref(360)

type ResizeTarget = 'left' | 'right'

let resizeState: {
  target: ResizeTarget
  startX: number
  startLeftWidth: number
  startRightWidth: number
} | null = null

const shellStyle = computed(() => ({
  '--left-w': `${leftWidth.value}px`,
  '--right-w': `${rightWidth.value}px`,
}))

function handleNavigate(viewId: string) {
  emit('update:activeView', viewId)
}

function handleCloseArtifact() {
  rightExpanded.value = false
  emit('close-artifact')
}

function handleRightCollapsedChange(value: boolean) {
  rightCollapsed.value = value
  if (value) {
    rightExpanded.value = false
  }
}

function expandRightPanel() {
  rightCollapsed.value = false
}

defineExpose({ expandRightPanel })

function handleRightExpandedChange(value: boolean) {
  rightExpanded.value = value
  if (value) {
    rightCollapsed.value = false
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function handleResizeStart(target: ResizeTarget, event: PointerEvent) {
  if (rightExpanded.value) return
  resizeState = {
    target,
    startX: event.clientX,
    startLeftWidth: leftWidth.value,
    startRightWidth: rightWidth.value,
  }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('pointermove', handleResizeMove)
  window.addEventListener('pointerup', handleResizeEnd, { once: true })
}

function handleResizeMove(event: PointerEvent) {
  if (!resizeState) return
  const deltaX = event.clientX - resizeState.startX
  if (resizeState.target === 'left') {
    leftWidth.value = clamp(resizeState.startLeftWidth + deltaX, 220, 380)
    return
  }
  rightWidth.value = clamp(resizeState.startRightWidth - deltaX, 280, 620)
}

function handleResizeEnd() {
  resizeState = null
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('pointermove', handleResizeMove)
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handleResizeMove)
  window.removeEventListener('pointerup', handleResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})

watch(() => props.selectedArtifact, (artifact) => {
  if (!artifact) {
    rightExpanded.value = false
  }
})
</script>

<template>
  <div
    class="app-shell"
    :style="shellStyle"
    :class="{
      'app-shell--left-collapsed': leftCollapsed,
      'app-shell--right-collapsed': rightCollapsed,
      'app-shell--right-expanded': rightExpanded && !rightCollapsed,
    }"
  >
    <div class="app-shell__titlebar">
      <TitleBar
        :project-context="props.projectContext"
        :iteration-options="props.projectContextOptions.iterations"
        @update-project-context="(value) => emit('update-project-context', value)"
      />
    </div>

    <div class="app-shell__sidebar-left">
      <LeftSidebar
        :active-view="activeView"
        :collapsed="leftCollapsed"
        @navigate="handleNavigate"
        @select-session="(session) => emit('select-session', session)"
        @create-general-session="emit('create-general-session')"
        @update:collapsed="leftCollapsed = $event"
        @navigate-settings="(tab) => emit('navigate-settings', tab)"
      />
    </div>

    <div class="app-shell__center">
      <CenterWorkbench>
        <slot name="center" />
      </CenterWorkbench>
    </div>

    <div
      v-if="!leftCollapsed && !(rightExpanded && !rightCollapsed)"
      class="app-shell__resizer app-shell__resizer--left"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整左侧栏宽度"
      @pointerdown.prevent="handleResizeStart('left', $event)"
    />

    <div class="app-shell__right-panel">
      <RightPanel
        :collapsed="rightCollapsed"
        :expanded="rightExpanded"
        :active-view="activeView"
        :selected-work-item="selectedWorkItem"
        :selected-artifact="selectedArtifact"
        @update:collapsed="handleRightCollapsedChange"
        @update:expanded="handleRightExpandedChange"
        @handle-confirmation="(id: string) => emit('handle-confirmation', id)"
        @start-workflow="(workItemId: string) => emit('start-workflow', workItemId)"
        @enter-conversation="(id: string) => emit('enter-work-item-conversation', id)"
        @confirmations-changed="(workItemId?: string | null) => emit('confirmations-changed', workItemId)"
        @close-artifact="handleCloseArtifact"
      />
    </div>

    <div
      v-if="!rightCollapsed && !(rightExpanded && !rightCollapsed)"
      class="app-shell__resizer app-shell__resizer--right"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整右侧栏宽度"
      @pointerdown.prevent="handleResizeStart('right', $event)"
    />

    <div class="app-shell__statusbar">
      <StatusBar
        system-status="normal"
        :tool-connections="12"
        :agents-online="3"
      />
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  --gutter: var(--shell-gutter);
  --left-w: var(--sidebar-width);
  --left-cw: var(--sidebar-collapsed-width);
  --right-w: var(--right-panel-width);
  --right-cw: var(--right-panel-collapsed-width);

  display: grid;
  grid-template-columns:
    var(--gutter)
    var(--left-w)
    var(--gutter)
    minmax(640px, 1fr)
    var(--gutter)
    var(--right-w)
    var(--gutter);
  grid-template-rows: var(--titlebar-height) minmax(0, 1fr) var(--statusbar-height);
  grid-template-areas:
    "titlebar  titlebar  titlebar  titlebar  titlebar  titlebar  titlebar"
    "gutter-l  left      gutter-c  center   gutter-r  right     gutter-rr"
    "statusbar statusbar statusbar statusbar statusbar statusbar statusbar";
  height: 100dvh;
  overflow: hidden;
  transition: grid-template-columns 180ms ease;
}

.app-shell:has(.app-shell__resizer:hover),
.app-shell:has(.app-shell__resizer:active) {
  transition: none;
}

.app-shell--left-collapsed {
  grid-template-columns:
    var(--gutter)
    var(--left-cw)
    var(--gutter)
    minmax(640px, 1fr)
    var(--gutter)
    var(--right-w)
    var(--gutter);
}

.app-shell--right-collapsed {
  grid-template-columns:
    var(--gutter)
    var(--left-w)
    var(--gutter)
    minmax(640px, 1fr)
    var(--gutter)
    var(--right-cw)
    var(--gutter);
}

.app-shell--left-collapsed.app-shell--right-collapsed {
  grid-template-columns:
    var(--gutter)
    var(--left-cw)
    var(--gutter)
    minmax(640px, 1fr)
    var(--gutter)
    var(--right-cw)
    var(--gutter);
}

.app-shell__titlebar {
  grid-area: titlebar;
}

.app-shell__sidebar-left {
  grid-area: left;
  min-height: 0;
  overflow: hidden;
}

.app-shell__center {
  grid-area: center;
  min-height: 0;
  overflow: hidden;
}

.app-shell__right-panel {
  grid-area: right;
  min-height: 0;
  overflow: hidden;
}

.app-shell__resizer {
  grid-row: 2;
  min-height: 0;
  cursor: col-resize;
  z-index: 6;
  position: relative;
  touch-action: none;
}

.app-shell__resizer::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: transparent;
  transition: background 120ms ease, box-shadow 120ms ease;
}

.app-shell__resizer:hover::before,
.app-shell__resizer:active::before {
  background: var(--accent-blue);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent-blue) 14%, transparent);
}

.app-shell__resizer--left {
  grid-column: gutter-c;
}

.app-shell__resizer--right {
  grid-column: gutter-r;
}

.app-shell--right-expanded .app-shell__center {
  visibility: hidden;
  pointer-events: none;
}

.app-shell--right-expanded .app-shell__right-panel {
  grid-column: center-start / right-end;
  grid-row: 2;
  z-index: 4;
  box-shadow: -16px 0 28px rgba(15, 23, 42, 0.08);
}

.app-shell--left-collapsed.app-shell--right-expanded .app-shell__right-panel {
  grid-column: gutter-c-start / right-end;
}

.app-shell--left-collapsed .app-shell__sidebar-left,
.app-shell--right-collapsed .app-shell__right-panel {
  overflow: hidden;
}

@media (max-width: 980px) {
  .app-shell__resizer {
    display: none;
  }
}

.app-shell__statusbar {
  grid-area: statusbar;
}
</style>
