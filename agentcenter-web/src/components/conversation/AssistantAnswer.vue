<script setup lang="ts">
import MarkdownContent from './MarkdownContent.vue'

const props = withDefaults(defineProps<{
  text: string
  streaming?: boolean
}>(), {
  streaming: false,
})
</script>

<template>
  <section class="assistant-answer">
    <pre
      v-if="text && streaming"
      class="assistant-answer__streaming-text"
    >{{ text }}</pre>
    <MarkdownContent
      v-else-if="text"
      :content="text"
    />
    <span v-if="streaming" class="stream-cursor">▍</span>
  </section>
</template>

<style scoped>
.assistant-answer {
  max-width: 100%;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.75;
}

.assistant-answer :deep(.markdown-content) {
  font-size: 15px;
  line-height: 1.75;
}

.assistant-answer__streaming-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  font: inherit;
  color: inherit;
}

.stream-cursor {
  color: var(--accent-blue);
  animation: stream-blink 0.8s step-end infinite;
}

@keyframes stream-blink {
  50% {
    opacity: 0;
  }
}

@media (max-width: 760px) {
  .assistant-answer {
    font-size: 14px;
    line-height: 1.68;
  }

  .assistant-answer :deep(.markdown-content) {
    font-size: 14px;
    line-height: 1.68;
  }
}
</style>
