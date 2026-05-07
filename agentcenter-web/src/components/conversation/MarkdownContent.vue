<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { marked, Renderer, type Tokens } from 'marked'
import { useThemeStore } from '../../stores/theme'

const props = withDefaults(defineProps<{
  content: string | null | undefined
  renderMermaid?: boolean
}>(), {
  renderMermaid: true,
})

type MermaidApi = typeof import('mermaid')['default']

const themeStore = useThemeStore()
const rootRef = ref<HTMLElement | null>(null)
const renderer = createMarkdownRenderer()
let mermaidPromise: Promise<MermaidApi> | null = null
let mermaidRenderVersion = 0

const renderedHtml = computed(() => {
  const raw = marked.parse(props.content ?? '', {
    async: false,
    breaks: false,
    gfm: true,
    renderer,
  })

  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ['target', 'rel', 'class', 'data-mermaid-state', 'data-mermaid-source'],
  })
})

watch(
  renderedHtml,
  () => {
    if (props.renderMermaid) {
      void renderMermaidBlocks()
    }
  },
  { flush: 'post', immediate: true }
)

watch(
  () => themeStore.currentThemeId,
  async () => {
    if (!props.renderMermaid) return
    mermaidPromise = null
    const root = rootRef.value
    if (!root) return
    const blocks = Array.from(root.querySelectorAll<HTMLElement>('.markdown-content__mermaid'))
    if (blocks.length === 0) return
    for (const block of blocks) {
      if (block.dataset.mermaidState === 'rendered') {
        const svg = block.querySelector('svg')
        const code = block.querySelector('code')
        if (svg && !code) {
          const pre = document.createElement('pre')
          pre.className = 'markdown-content__mermaid-source'
          const codeEl = document.createElement('code')
          codeEl.textContent = block.dataset.mermaidSource ?? ''
          pre.appendChild(codeEl)
          block.replaceChildren(pre)
        }
        block.dataset.mermaidState = 'pending'
      }
    }
    await nextTick()
    void renderMermaidBlocks()
  },
)

async function renderMermaidBlocks(): Promise<void> {
  const currentVersion = ++mermaidRenderVersion
  await nextTick()

  const root = rootRef.value
  if (!root) return

  const blocks = Array.from(root.querySelectorAll<HTMLElement>('.markdown-content__mermaid'))
  if (blocks.length === 0) return

  let mermaid: MermaidApi
  try {
    mermaid = await getMermaid()
  } catch {
    markMermaidBlocksAsSource(blocks)
    return
  }

  await Promise.all(blocks.map(async (block, index) => {
    const source = block.querySelector('code')?.textContent?.trim() ?? ''
    if (!source) return

    if (!block.dataset.mermaidSource) {
      block.dataset.mermaidSource = source
    }

    try {
      const renderId = `agentcenter-mermaid-${Date.now()}-${currentVersion}-${index}-${hashText(source)}`
      const result = await mermaid.render(renderId, source)
      if (currentVersion !== mermaidRenderVersion || !rootRef.value?.contains(block)) return
      block.innerHTML = DOMPurify.sanitize(result.svg)
      block.dataset.mermaidState = 'rendered'
    } catch {
      block.dataset.mermaidState = 'source'
    }
  }))
}

function markMermaidBlocksAsSource(blocks: HTMLElement[]): void {
  blocks.forEach((block) => {
    block.dataset.mermaidState = 'source'
  })
}

async function getMermaid(): Promise<MermaidApi> {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((module) => {
      module.default.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: 'base',
        themeVariables: {
          primaryColor: getComputedStyle(document.documentElement).getPropertyValue('--brand-soft').trim() || '#eef6ff',
          primaryTextColor: getComputedStyle(document.documentElement).getPropertyValue('--text-primary').trim() || '#0f172a',
          primaryBorderColor: getComputedStyle(document.documentElement).getPropertyValue('--brand-border').trim() || '#93c5fd',
          lineColor: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() || '#64748b',
          secondaryColor: getComputedStyle(document.documentElement).getPropertyValue('--success-soft').trim() || '#ecfdf5',
          tertiaryColor: getComputedStyle(document.documentElement).getPropertyValue('--surface-hover').trim() || '#f8fafc',
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        },
      })
      return module.default
    })
  }

  return mermaidPromise
}

function createMarkdownRenderer(): Renderer {
  const customRenderer = new Renderer()

  customRenderer.code = (token: Tokens.Code): string => {
    const lang = getPrimaryLanguage(token.lang)
    const escapedText = escapeHtml(token.text)

    if (lang === 'mermaid') {
      return [
        '<div class="markdown-content__mermaid" data-mermaid-state="pending">',
        '<pre class="markdown-content__mermaid-source"><code>',
        escapedText,
        '</code></pre>',
        '</div>',
      ].join('')
    }

    const className = lang ? ` class="language-${escapeAttribute(lang)}"` : ''
    return `<pre><code${className}>${escapedText}</code></pre>`
  }

  customRenderer.link = (token: Tokens.Link): string => {
    const href = safeUrl(token.href)
    const title = token.title ? ` title="${escapeAttribute(token.title)}"` : ''
    const label = marked.parseInline(token.text, { async: false, gfm: true })
    return `<a href="${escapeAttribute(href)}"${title} target="_blank" rel="noreferrer noopener">${label}</a>`
  }

  customRenderer.image = (token: Tokens.Image): string => {
    const src = safeUrl(token.href)
    const title = token.title ? ` title="${escapeAttribute(token.title)}"` : ''
    return `<img src="${escapeAttribute(src)}" alt="${escapeAttribute(token.text)}"${title} loading="lazy" />`
  }

  customRenderer.html = (): string => ''

  return customRenderer
}

function getPrimaryLanguage(value: string | undefined): string {
  return (value ?? '').trim().split(/\s+/)[0]?.toLowerCase() ?? ''
}

function safeUrl(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) return '#'
  if (/^(https?:|mailto:|tel:|#|\/|\.\/|\.\.\/)/i.test(trimmed)) return trimmed
  if (/^data:image\/(?:png|gif|jpeg|webp|svg\+xml);base64,/i.test(trimmed)) return trimmed
  return '#'
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttribute(value: string): string {
  return escapeHtml(value).replace(/`/g, '&#96;')
}

function hashText(value: string): string {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = Math.imul(31, hash) + value.charCodeAt(i)
  }
  return Math.abs(hash).toString(36)
}
</script>

<template>
  <div ref="rootRef" class="markdown-content" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-content {
  min-width: 0;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.markdown-content :deep(*) {
  max-width: 100%;
}

.markdown-content :deep(:first-child) {
  margin-top: 0;
}

.markdown-content :deep(:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(p) {
  margin: 0 0 10px;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  color: var(--text-primary);
  font-weight: 850;
  line-height: 1.28;
}

.markdown-content :deep(h1) {
  margin: 18px 0 10px;
  font-size: 22px;
}

.markdown-content :deep(h2) {
  margin: 16px 0 9px;
  font-size: 18px;
}

.markdown-content :deep(h3) {
  margin: 14px 0 8px;
  font-size: 16px;
}

.markdown-content :deep(h4) {
  margin: 12px 0 6px;
  font-size: 14px;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  display: grid;
  gap: 4px;
  margin: 8px 0 12px;
  padding-left: 22px;
}

.markdown-content :deep(li) {
  padding-left: 2px;
}

.markdown-content :deep(li > p) {
  margin: 0;
}

.markdown-content :deep(input[type='checkbox']) {
  width: 14px;
  height: 14px;
  margin: 0 7px 0 -20px;
  vertical-align: -2px;
  accent-color: var(--success);
}

.markdown-content :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--brand-border);
  border-radius: 0 8px 8px 0;
  background: var(--brand-soft);
  color: var(--text-secondary);
}

.markdown-content :deep(hr) {
  height: 1px;
  margin: 16px 0;
  border: 0;
  background: var(--border-color);
}

.markdown-content :deep(a) {
  color: var(--brand-primary);
  font-weight: 700;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(code) {
  border-radius: 5px;
  background: var(--inline-code-bg);
  color: var(--inline-code-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.92em;
  padding: 2px 5px;
}

.markdown-content :deep(pre) {
  margin: 12px 0;
  padding: 12px 14px;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--code-bg);
  color: var(--code-text);
  line-height: 1.58;
}

.markdown-content :deep(pre code) {
  display: block;
  min-width: max-content;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  font-size: 12px;
  white-space: pre;
}

.markdown-content :deep(table) {
  display: block;
  width: 100%;
  min-width: 520px;
  max-width: 100%;
  margin: 12px 0;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  border-collapse: collapse;
  background: var(--surface-card);
  font-size: 13px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  vertical-align: top;
}

.markdown-content :deep(th) {
  background: var(--surface-hover);
  color: var(--text-primary);
  font-weight: 850;
}

.markdown-content :deep(tr:last-child td) {
  border-bottom: 0;
}

.markdown-content :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 12px 0;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.markdown-content :deep(.markdown-content__mermaid) {
  margin: 12px 0;
  padding: 14px;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card);
}

.markdown-content :deep(.markdown-content__mermaid svg) {
  display: block;
  width: auto;
  max-width: 100%;
  height: auto;
  margin: 0 auto;
}

.markdown-content :deep(.markdown-content__mermaid[data-mermaid-state='rendered'] pre) {
  display: none;
}

.markdown-content :deep(.markdown-content__mermaid-source) {
  margin: 0;
  border-color: var(--border-color);
  background: var(--surface-hover);
  color: var(--text-primary);
}

@media (max-width: 760px) {
  .markdown-content {
    font-size: 13px;
    line-height: 1.65;
  }

  .markdown-content :deep(table) {
    min-width: 440px;
  }
}
</style>
