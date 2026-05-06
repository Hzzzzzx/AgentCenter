<script setup lang="ts">
import { computed } from 'vue'
import type { ArtifactDto } from '../../api/types'

const props = defineProps<{
  artifact: ArtifactDto | null
}>()

const renderedContent = computed(() => {
  if (!props.artifact?.content) return ''

  if (props.artifact.artifactType === 'JSON') {
    try {
      return JSON.stringify(JSON.parse(props.artifact.content), null, 2)
    } catch {
      return props.artifact.content
    }
  }

  if (props.artifact.artifactType === 'MARKDOWN') {
    let html = props.artifact.content
    // Code blocks
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
    // Headings
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')
    // Bold
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // Italic
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Links
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
    // Line breaks
    html = html.replace(/\n/g, '<br />')
    return html
  }

  return props.artifact.content
})

const isJson = computed(() => props.artifact?.artifactType === 'JSON')
</script>

<template>
  <div class="artifact-viewer">
    <div v-if="!artifact" class="artifact-viewer__empty">
      暂无产物
    </div>
    <template v-else>
      <div class="artifact-viewer__header">
        <span class="artifact-viewer__title">{{ artifact.title }}</span>
        <span class="artifact-viewer__type">{{ artifact.artifactType }}</span>
      </div>
      <div class="artifact-viewer__body">
        <pre v-if="isJson" class="artifact-viewer__json">{{ renderedContent }}</pre>
        <div v-else class="artifact-viewer__markdown" v-html="renderedContent" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.artifact-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.artifact-viewer__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
}

.artifact-viewer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 8px;
}

.artifact-viewer__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.artifact-viewer__type {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  background-color: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
  font-weight: 600;
}

.artifact-viewer__body {
  flex: 1;
  overflow-y: auto;
}

.artifact-viewer__json {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  padding: 12px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
}

.artifact-viewer__markdown {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
}

.artifact-viewer__markdown :deep(h1) {
  font-size: 18px;
  font-weight: 600;
  margin: 12px 0 8px;
}

.artifact-viewer__markdown :deep(h2) {
  font-size: 16px;
  font-weight: 600;
  margin: 10px 0 6px;
}

.artifact-viewer__markdown :deep(h3) {
  font-size: 14px;
  font-weight: 600;
  margin: 8px 0 4px;
}

.artifact-viewer__markdown :deep(code) {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  padding: 2px 5px;
  background-color: #f1f3f5;
  border-radius: 3px;
}

.artifact-viewer__markdown :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  overflow-x: auto;
}

.artifact-viewer__markdown :deep(pre code) {
  padding: 0;
  background: none;
}

.artifact-viewer__markdown :deep(a) {
  color: var(--accent-blue);
  text-decoration: none;
}

.artifact-viewer__markdown :deep(a:hover) {
  text-decoration: underline;
}
</style>
