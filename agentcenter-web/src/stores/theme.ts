import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  type ThemeId,
  DEFAULT_THEME_ID,
  isValidThemeId,
  themes,
} from '../theme/themes'

const STORAGE_KEY = 'agentcenter.theme'

export const useThemeStore = defineStore('theme', () => {
  const currentThemeId = ref<ThemeId>(DEFAULT_THEME_ID)

  const currentTheme = computed(() =>
    themes.find((t) => t.id === currentThemeId.value)!,
  )

  const isDark = computed(() => currentTheme.value.tone === 'dark')

  function setTheme(themeId: ThemeId): void {
    if (!isValidThemeId(themeId)) return
    currentThemeId.value = themeId
    document.documentElement.dataset.theme = themeId
    try { localStorage.setItem(STORAGE_KEY, themeId) } catch { /* storage unavailable */ }
  }

  function initFromStorage(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored && isValidThemeId(stored)) {
        currentThemeId.value = stored
        document.documentElement.dataset.theme = stored
        return
      }
    } catch { /* storage unavailable */ }
    document.documentElement.dataset.theme = currentThemeId.value
  }

  return { currentThemeId, currentTheme, isDark, setTheme, initFromStorage }
})
