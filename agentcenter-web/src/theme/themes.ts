/**
 * AgentCenter Theme Registry
 * Public API: theme IDs, metadata, and validation helpers.
 * CSS token values live in src/styles/themes.css.
 */

export type ThemeId =
  | 'light-default'
  | 'midnight-black'
  | 'black-blue-command'
  | 'graphite-mint'
  | 'mist-cyan'
  | 'forest-ops'
  | 'plum-graphite'

export interface ThemeOption {
  id: ThemeId
  label: string
  description: string
  tone: 'light' | 'dark'
  swatches: string[]
}

export const DEFAULT_THEME_ID: ThemeId = 'light-default'

export const themes: ThemeOption[] = [
  {
    id: 'light-default',
    label: '经典白',
    description: '默认白色主题，适合日常办公和长时间阅读',
    tone: 'light',
    swatches: ['#f6f8fb', '#ffffff', '#3b82f6', '#8b5cf6', '#d9e2ec'],
  },
  {
    id: 'midnight-black',
    label: '墨黑',
    description: '纯黑方向但避免绝对黑压迫感，适合夜间工作',
    tone: 'dark',
    swatches: ['#08090c', '#101217', '#8ab4ff', '#c084fc', '#27303d'],
  },
  {
    id: 'black-blue-command',
    label: '黑蓝指挥台',
    description: '黑蓝指挥台主题，适合演示和运行监控场景',
    tone: 'dark',
    swatches: ['#020817', '#061225', '#38bdf8', '#60a5fa', '#16324f'],
  },
  {
    id: 'graphite-mint',
    label: '石墨青绿',
    description: '技术感强，但不完全依赖蓝色',
    tone: 'dark',
    swatches: ['#111315', '#181c1f', '#2dd4bf', '#a3e635', '#2e383a'],
  },
  {
    id: 'mist-cyan',
    label: '雾白青蓝',
    description: '浅色系的清爽替代款',
    tone: 'light',
    swatches: ['#f4f8f8', '#ffffff', '#0891b2', '#14b8a6', '#d7e5e7'],
  },
  {
    id: 'forest-ops',
    label: '松针灰绿',
    description: '偏企业运维和治理场景',
    tone: 'light',
    swatches: ['#f5f7f3', '#ffffff', '#16a34a', '#0f766e', '#dce5d6'],
  },
  {
    id: 'plum-graphite',
    label: '梅紫石墨',
    description: '更有品牌感，适合演示和个人偏好',
    tone: 'dark',
    swatches: ['#111016', '#191720', '#a78bfa', '#fb7185', '#332d42'],
  },
]

const themeIds = new Set<ThemeId>(themes.map((t) => t.id))

/** Type guard: checks whether a string is a valid ThemeId. */
export function isValidThemeId(value: string): value is ThemeId {
  return themeIds.has(value as ThemeId)
}

export function getThemeOption(id: ThemeId): ThemeOption | undefined {
  return themes.find((t) => t.id === id)
}

export function getAllThemeIds(): ThemeId[] {
  return themes.map((t) => t.id)
}
