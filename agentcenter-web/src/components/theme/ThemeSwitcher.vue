<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { themes, type ThemeId } from '../../theme/themes'
import { useThemeStore } from '../../stores/theme'

const themeStore = useThemeStore()
const open = ref(false)
const popoverRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}

function selectTheme(id: ThemeId) {
  themeStore.setTheme(id)
  open.value = false
}

function onClickOutside(e: MouseEvent) {
  if (!open.value) return
  const target = e.target as Node
  if (
    popoverRef.value?.contains(target) ||
    triggerRef.value?.contains(target)
  ) {
    return
  }
  open.value = false
}

function onEscape(e: KeyboardEvent) {
  if (e.key === 'Escape' && open.value) {
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onClickOutside)
  document.addEventListener('keydown', onEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onClickOutside)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div class="theme-switcher">
    <button
      ref="triggerRef"
      class="theme-switcher__trigger"
      title="主题"
      aria-label="切换主题"
      @click="toggle"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path
          d="M12 2.7a1 1 0 01.9.6l1.7 3.7a1 1 0 00.5.5l3.7 1.7a1 1 0 010 1.8l-3.7 1.7a1 1 0 00-.5.5l-1.7 3.7a1 1 0 01-1.8 0l-1.7-3.7a1 1 0 00-.5-.5L4.7 11a1 1 0 010-1.8l3.7-1.7a1 1 0 00.5-.5l1.7-3.7a1 1 0 01.9-.6z"
          stroke="currentColor"
          stroke-width="1.8"
        />
        <circle cx="18" cy="18" r="2.5" stroke="currentColor" stroke-width="1.8" />
      </svg>
    </button>

    <Transition name="theme-switcher-fade">
      <div
        v-if="open"
        ref="popoverRef"
        class="theme-switcher__popover"
        role="listbox"
        aria-label="选择主题"
      >
        <div class="theme-switcher__title">主题</div>
        <div class="theme-switcher__grid">
          <button
            v-for="theme in themes"
            :key="theme.id"
            class="theme-switcher__card"
            :class="{ 'theme-switcher__card--active': theme.id === themeStore.currentThemeId }"
            role="option"
            :aria-selected="theme.id === themeStore.currentThemeId"
            @click="selectTheme(theme.id)"
          >
            <span
              class="theme-switcher__swatch"
              :style="{ background: theme.swatches[2] }"
            >
              <svg
                v-if="theme.id === themeStore.currentThemeId"
                class="theme-switcher__check"
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
              >
                <path
                  d="M5 13l4 4L19 7"
                  stroke="#fff"
                  stroke-width="3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="theme-switcher__label">{{ theme.label }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.theme-switcher {
  position: relative;
  display: inline-flex;
}

.theme-switcher__trigger {
  position: relative;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.theme-switcher__trigger:hover {
  color: var(--text-primary);
  background: var(--bg-tertiary);
}

.theme-switcher__popover {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 100;
  min-width: 240px;
  padding: 12px;
  border: 1px solid var(--border-default, var(--border-color));
  border-radius: 10px;
  background: var(--surface-overlay, var(--bg-secondary));
  box-shadow: var(--shadow-popover, 0 8px 30px rgba(0, 0, 0, 0.18));
}

.theme-switcher__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

.theme-switcher__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.theme-switcher__card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 2px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.theme-switcher__card:hover {
  background: var(--bg-tertiary);
}

.theme-switcher__card--active {
  border-color: var(--accent-blue, #3b82f6);
  background: var(--bg-tertiary);
}

.theme-switcher__swatch {
  position: relative;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
}

.theme-switcher__check {
  filter: drop-shadow(0 0 1px rgba(0, 0, 0, 0.3));
}

.theme-switcher__label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.theme-switcher-fade-enter-active,
.theme-switcher-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.theme-switcher-fade-enter-from,
.theme-switcher-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
