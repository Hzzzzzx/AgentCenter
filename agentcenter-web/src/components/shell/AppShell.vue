<script setup lang="ts">
import { ref } from 'vue'
import TitleBar from './TitleBar.vue'
import LeftSidebar from './LeftSidebar.vue'
import CenterWorkbench from './CenterWorkbench.vue'
import RightPanel from './RightPanel.vue'
import StatusBar from './StatusBar.vue'
import type { AgentSessionDto } from '../../api/types'

interface Props {
  activeView?: string
}

const props = withDefaults(defineProps<Props>(), {
  activeView: 'home',
})

const emit = defineEmits<{
  'update:activeView': [value: string]
  'handle-confirmation': [id: string]
  'select-session': [session: AgentSessionDto]
  'create-general-session': []
}>()

const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

function handleNavigate(viewId: string) {
  emit('update:activeView', viewId)
}
</script>

<template>
  <div
    class="app-shell"
    :class="{
      'app-shell--left-collapsed': leftCollapsed,
      'app-shell--right-collapsed': rightCollapsed,
    }"
  >
    <div class="app-shell__titlebar">
      <TitleBar />
    </div>

    <div class="app-shell__sidebar-left">
      <LeftSidebar
        :active-view="activeView"
        :collapsed="leftCollapsed"
        @navigate="handleNavigate"
        @select-session="(session) => emit('select-session', session)"
        @create-general-session="emit('create-general-session')"
        @update:collapsed="leftCollapsed = $event"
      />
    </div>

    <div class="app-shell__center">
      <CenterWorkbench>
        <slot name="center" />
      </CenterWorkbench>
    </div>

    <div class="app-shell__right-panel">
      <RightPanel
        :collapsed="rightCollapsed"
        @update:collapsed="rightCollapsed = $event"
        @handle-confirmation="(id: string) => emit('handle-confirmation', id)"
      />
    </div>

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

.app-shell--left-collapsed .app-shell__sidebar-left,
.app-shell--right-collapsed .app-shell__right-panel {
  overflow: hidden;
}

.app-shell__statusbar {
  grid-area: statusbar;
}
</style>
