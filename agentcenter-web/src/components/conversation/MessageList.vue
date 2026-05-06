<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { AgentMessageDto } from '../../api/types'

const props = defineProps<{
  messages: AgentMessageDto[]
}>()

const listRef = ref<HTMLElement | null>(null)

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  }
)

function formatTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '刚刚'
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return '刚刚'
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<template>
  <div ref="listRef" class="message-list">
    <div v-if="messages.length === 0" class="message-list__empty">
      <strong>会话已就绪</strong>
      <span>输入问题，或点击上方场景开始和 OpenCode Runtime 对话。</span>
    </div>
    <div
      v-for="msg in messages"
      :key="msg.id"
      class="message-item"
      :class="`message-item--${msg.role.toLowerCase()}`"
    >
      <div class="message-item__avatar">
        <svg v-if="msg.role === 'USER'" width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M20 21V19C20 17.94 19.58 16.92 18.83 16.17C18.08 15.42 17.06 15 16 15H8C6.94 15 5.92 15.42 5.17 16.17C4.42 16.92 4 17.94 4 19V21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/>
        </svg>
        <svg v-else-if="msg.role === 'ASSISTANT'" width="16" height="16" viewBox="0 0 24 24" fill="none">
          <rect x="3" y="3" width="18" height="18" rx="4" stroke="currentColor" stroke-width="2"/>
          <circle cx="9" cy="10" r="1.5" fill="currentColor"/>
          <circle cx="15" cy="10" r="1.5" fill="currentColor"/>
          <path d="M9 15C9 15 10.5 16.5 12 16.5C13.5 16.5 15 15 15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <svg v-else-if="msg.role === 'TOOL'" width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" stroke="currentColor" stroke-width="2"/>
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
          <path d="M12 16V12M12 8H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="message-item__body">
        <div class="message-item__content">{{ msg.content }}</div>
        <div class="message-item__time">{{ formatTime(msg.createdAt) }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-list__empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 14px;
}

.message-list__empty strong {
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.message-item {
  display: flex;
  gap: 8px;
  max-width: 80%;
}

.message-item--user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-item--assistant {
  align-self: flex-start;
}

.message-item--system {
  align-self: center;
  max-width: 90%;
}

.message-item--tool {
  align-self: flex-start;
  max-width: 85%;
}

.message-item__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
}

.message-item--user .message-item__avatar {
  background-color: rgba(59, 130, 246, 0.12);
  color: var(--accent-blue);
}

.message-item--assistant .message-item__avatar {
  background-color: rgba(16, 185, 129, 0.12);
  color: var(--success);
}

.message-item__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-item__content {
  font-size: 13px;
  line-height: 1.5;
  padding: 8px 12px;
  border-radius: 8px;
  word-break: break-word;
}

.message-item--user .message-item__content {
  background-color: var(--accent-blue);
  color: #ffffff;
  border-bottom-right-radius: 2px;
}

.message-item--assistant .message-item__content {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 2px;
}

.message-item--system .message-item__content {
  background-color: transparent;
  font-style: italic;
  font-size: 12px;
  color: var(--text-secondary);
  text-align: center;
  padding: 4px 12px;
}

.message-item--tool .message-item__content {
  background-color: #f8f9fa;
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 2px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.message-item__time {
  font-size: 10px;
  color: var(--text-secondary);
}

.message-item--user .message-item__time {
  text-align: right;
}
</style>
