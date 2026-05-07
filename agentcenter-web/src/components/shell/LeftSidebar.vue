<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useSessionStore } from '../../stores/sessions'
import type { AgentSessionDto } from '../../api/types'

interface NavItem {
  id: string
  label: string
  icon: 'home' | 'board' | 'workflow'
}

interface Props {
  activeView?: string
  collapsed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  activeView: 'home',
  collapsed: false,
})

const emit = defineEmits<{
  'navigate': [viewId: string]
  'select-session': [session: AgentSessionDto]
  'create-general-session': []
  'update:collapsed': [value: boolean]
  'navigate-settings': [tab: string]
}>()

const sessionStore = useSessionStore()
const conversationOpen = ref(true)
const taskSessionsOpen = ref(false)
const settingsOpen = ref(false)

const navItems: NavItem[] = [
  { id: 'home', label: '首页', icon: 'home' },
  { id: 'board', label: '任务看板', icon: 'board' },
  { id: 'workflow', label: '任务编排', icon: 'workflow' },
]

const generalSessions = computed(() =>
  sessionStore.sessions.filter((session) => session.sessionType === 'GENERAL')
)

const taskSessions = computed(() =>
  sessionStore.sessions.filter((session) => session.sessionType === 'WORK_ITEM')
)

onMounted(() => {
  sessionStore.loadSessions()
})

watch(
  () => sessionStore.activeSession?.id,
  () => {
    if (sessionStore.activeSession?.sessionType === 'WORK_ITEM') {
      taskSessionsOpen.value = true
    }
  },
  { immediate: true }
)

function formatTime(value: string | null | undefined) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  const now = Date.now()
  const diffMinutes = Math.max(0, Math.round((now - date.getTime()) / 60000))
  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes} 分钟`
  if (diffMinutes < 1440) return `${Math.round(diffMinutes / 60)} 小时`
  return `${Math.round(diffMinutes / 1440)} 天`
}

function sessionTitle(session: AgentSessionDto) {
  return session.title || (session.sessionType === 'WORK_ITEM' ? '任务会话' : '通用会话')
}

function sessionMeta(session: AgentSessionDto) {
  if (session.sessionType === 'WORK_ITEM') {
    return [session.workItemId ? '任务上下文' : '任务会话', session.runtimeType].filter(Boolean).join(' · ')
  }
  return '自由讨论 · 平台上下文'
}
</script>

<template>
  <aside class="left-sidebar" :class="{ 'left-sidebar--collapsed': collapsed }">
    <div class="left-sidebar__topbar">
      <button
        class="left-sidebar__collapse"
        :title="collapsed ? '展开侧栏' : '收起侧栏'"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        @click="emit('update:collapsed', !collapsed)"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          :class="{ 'is-collapsed': collapsed }"
        >
          <rect x="4" y="5" width="16" height="14" rx="2.5" stroke="currentColor" stroke-width="1.8"/>
          <path d="M9 5v14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M15 9l-3 3 3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <div class="left-sidebar__body">
      <nav class="left-sidebar__nav" aria-label="固定工作台入口">
        <button
          v-for="item in navItems"
          :key="item.id"
          class="left-sidebar__nav-item"
          :class="{ 'left-sidebar__nav-item--active': props.activeView === item.id }"
          :title="collapsed ? item.label : undefined"
          @click="emit('navigate', item.id)"
        >
          <svg v-if="item.icon === 'home'" width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/>
            <rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/>
            <rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/>
            <rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/>
          </svg>
          <svg v-else-if="item.icon === 'board'" width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect x="4" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/>
            <rect x="10" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/>
            <rect x="16" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/>
          </svg>
          <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="6" cy="6" r="2.5" stroke="currentColor" stroke-width="2"/>
            <circle cx="18" cy="6" r="2.5" stroke="currentColor" stroke-width="2"/>
            <circle cx="12" cy="18" r="2.5" stroke="currentColor" stroke-width="2"/>
            <path d="M8.4 7.6l2 2.6M15.6 7.6l-2 2.6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span v-if="!collapsed">{{ item.label }}</span>
        </button>
      </nav>

      <section v-if="!collapsed" class="left-sidebar__sessions" aria-label="会话列表">
        <div class="left-sidebar__section-header">
          <button class="left-sidebar__section-toggle" @click="conversationOpen = !conversationOpen">
            <span>会话列表</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" :class="{ 'is-closed': !conversationOpen }">
              <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button class="left-sidebar__new-btn" aria-label="新建会话" @click="emit('create-general-session')">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <div v-show="conversationOpen" class="left-sidebar__session-body">
          <div class="left-sidebar__group">
            <div class="left-sidebar__group-title">
              <span class="left-sidebar__group-main">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  <path d="M21 15a4 4 0 01-4 4H8l-5 3V7a4 4 0 014-4h10a4 4 0 014 4v8z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
                </svg>
                通用会话
              </span>
              <button aria-label="新建通用会话" @click="emit('create-general-session')">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </button>
            </div>
            <button
              v-for="session in generalSessions"
              :key="session.id"
              class="left-sidebar__session-item"
              :class="{ 'left-sidebar__session-item--active': sessionStore.activeSession?.id === session.id }"
              @click="emit('select-session', session)"
            >
              <span class="left-sidebar__session-row">
                <strong>{{ sessionTitle(session) }}</strong>
                <em>{{ formatTime(session.createdAt) }}</em>
              </span>
              <span class="left-sidebar__session-meta">{{ sessionMeta(session) }}</span>
            </button>
            <div v-if="generalSessions.length === 0" class="left-sidebar__empty">暂无通用会话</div>
          </div>

          <div class="left-sidebar__group">
            <button class="left-sidebar__group-title left-sidebar__group-title--button" @click="taskSessionsOpen = !taskSessionsOpen">
              <span class="left-sidebar__group-main">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  <path d="M9 11l2 2 4-4M4 7h1M4 17h1M9 17h11M9 7h11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                任务会话
                <small>{{ taskSessions.length }}</small>
              </span>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" :class="{ 'is-closed': !taskSessionsOpen }">
                <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <div v-show="taskSessionsOpen">
              <button
                v-for="session in taskSessions"
                :key="session.id"
                class="left-sidebar__session-item"
                :class="{ 'left-sidebar__session-item--active': sessionStore.activeSession?.id === session.id }"
                @click="emit('select-session', session)"
              >
                <span class="left-sidebar__session-row">
                  <strong>{{ sessionTitle(session) }}</strong>
                  <em>{{ formatTime(session.createdAt) }}</em>
                </span>
                <span class="left-sidebar__session-meta">{{ sessionMeta(session) }}</span>
              </button>
              <div v-if="taskSessions.length === 0" class="left-sidebar__empty">从首页或看板进入任务后创建</div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-if="!collapsed" class="left-sidebar__footer">
      <div class="left-sidebar__settings-wrapper">
        <button
          class="left-sidebar__settings"
          :class="{ 'left-sidebar__settings--active': settingsOpen }"
          @click="settingsOpen = !settingsOpen"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>
            <path d="M19 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 01-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3A1.7 1.7 0 0014 21h-4a1.7 1.7 0 00-.8-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 01-2.8-2.8l.1-.1A1.7 1.7 0 005 15a1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.5A1.7 1.7 0 005 9a1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 012.8-2.8l.1.1A1.7 1.7 0 009 5a1.7 1.7 0 001-1.5V3a2 2 0 014 0v.5A1.7 1.7 0 0015 5a1.7 1.7 0 001.8-.3l.1-.1a2 2 0 012.8 2.8l-.1.1A1.7 1.7 0 0019 9a1.7 1.7 0 001.5 1H21a2 2 0 010 4h-.5A1.7 1.7 0 0019 15z" stroke="currentColor" stroke-width="2"/>
          </svg>
          <span>设置</span>
        </button>
        <div v-if="settingsOpen" class="left-sidebar__settings-menu">
          <button class="left-sidebar__settings-menu-item" @click="emit('navigate-settings', 'project'); settingsOpen = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M4 6.5A2.5 2.5 0 016.5 4h11A2.5 2.5 0 0120 6.5v11a2.5 2.5 0 01-2.5 2.5h-11A2.5 2.5 0 014 17.5v-11z" stroke="currentColor" stroke-width="2"/>
              <path d="M8 9h8M8 13h5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            项目管理
          </button>
          <button class="left-sidebar__settings-menu-item" @click="emit('navigate-settings', 'skills'); settingsOpen = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L2 7l10 5 10-5-10-5z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
              <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
              <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
            </svg>
            Skill 管理
          </button>
          <button class="left-sidebar__settings-menu-item" @click="emit('navigate-settings', 'mcps'); settingsOpen = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <rect x="2" y="2" width="8" height="8" rx="2" stroke="currentColor" stroke-width="2"/>
              <rect x="14" y="2" width="8" height="8" rx="2" stroke="currentColor" stroke-width="2"/>
              <rect x="2" y="14" width="8" height="8" rx="2" stroke="currentColor" stroke-width="2"/>
              <rect x="14" y="14" width="8" height="8" rx="2" stroke="currentColor" stroke-width="2"/>
            </svg>
            MCP 管理
          </button>
        </div>
      </div>
    </div>

  </aside>
</template>

<style scoped>
.left-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  overflow: hidden;
}

.left-sidebar--collapsed {
}

.left-sidebar__topbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  height: 36px;
  padding: 6px 8px 4px;
}

.left-sidebar--collapsed .left-sidebar__topbar {
  justify-content: center;
  padding-inline: 0;
}

.left-sidebar__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.left-sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px 10px;
  border-bottom: 1px solid rgba(217, 226, 236, 0.75);
}

.left-sidebar--collapsed .left-sidebar__nav {
  padding: 6px 8px;
}

.left-sidebar__nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 32px;
  width: 100%;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.left-sidebar--collapsed .left-sidebar__nav-item {
  justify-content: center;
  padding: 0;
}

.left-sidebar__nav-item:hover {
  background: var(--bg-card-hover);
}

.left-sidebar__nav-item--active {
  color: var(--accent-blue);
  background: rgba(59, 130, 246, 0.1);
}

.left-sidebar__sessions {
  flex: 1;
  overflow-y: auto;
  padding: 10px 0 14px;
}

.left-sidebar__section-header,
.left-sidebar__group-title,
.left-sidebar__session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left-sidebar__section-header {
  margin-bottom: 4px;
  padding: 0 14px 0 20px;
}

.left-sidebar__section-toggle,
.left-sidebar__new-btn,
.left-sidebar__group-title button,
.left-sidebar__group-title--button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.left-sidebar__section-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.left-sidebar__section-toggle svg,
.left-sidebar__group-title svg:last-child {
  transition: transform 0.18s ease;
}

.left-sidebar__section-toggle svg.is-closed,
.left-sidebar__group-title svg.is-closed {
  transform: rotate(-90deg);
}

.left-sidebar__new-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 5px;
  color: var(--text-muted);
}

.left-sidebar__new-btn:hover,
.left-sidebar__group-title button:hover {
  color: var(--accent-blue);
  background: rgba(59, 130, 246, 0.08);
}

.left-sidebar__group {
  margin-bottom: 4px;
}

.left-sidebar__group-title {
  width: 100%;
  min-height: 28px;
  padding: 8px 14px 4px 20px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.left-sidebar__group-title--button {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left-sidebar__group-main {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.left-sidebar__group-main small {
  display: grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
  font-size: 10px;
  font-weight: 600;
}

.left-sidebar__session-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  min-height: auto;
  margin: 0;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
}

.left-sidebar__session-item:hover {
  background: var(--bg-card-hover);
  border-color: var(--border-color);
}

.left-sidebar__session-item--active {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.28);
  color: var(--accent-blue);
}

.left-sidebar__session-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: inherit;
  font-size: 12px;
  font-weight: 600;
}

.left-sidebar__session-row em {
  flex-shrink: 0;
  margin-left: 8px;
  color: var(--text-muted);
  font-style: normal;
  font-size: 10px;
}

.left-sidebar__session-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-muted);
  font-size: 11px;
}

.left-sidebar__empty {
  padding: 6px 10px 6px 20px;
  color: #94a3b8;
  font-size: 11px;
}

.left-sidebar__footer {
  flex-shrink: 0;
  padding: 6px 8px;
  border-top: 1px solid var(--border-color);
}

.left-sidebar__settings {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 32px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.left-sidebar__settings:hover {
  background: var(--bg-card-hover);
}

.left-sidebar__settings-wrapper {
  position: relative;
}

.left-sidebar__settings--active {
  background: var(--bg-card-hover);
  color: var(--accent-blue);
}

.left-sidebar__settings-menu {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  margin-bottom: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  z-index: 100;
}

.left-sidebar__settings-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
}

.left-sidebar__settings-menu-item:hover {
  background: var(--bg-card-hover);
  color: var(--accent-blue);
}

.left-sidebar__collapse {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.left-sidebar__collapse:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.left-sidebar__collapse svg {
  transition: transform 0.18s ease;
}

.left-sidebar__collapse svg.is-collapsed {
  transform: rotate(180deg);
}
</style>
