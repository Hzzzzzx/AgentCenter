<script setup lang="ts">
import type { AgentStateStatus } from '../../api/types'

const props = defineProps<{
  nodeState: AgentStateStatus | null
  nodeInstanceId: string | null
}>()

const emit = defineEmits<{
  action: [action: string, nodeInstanceId: string]
}>()
</script>

<template>
  <div v-if="nodeState && nodeInstanceId" class="wf-control">
    <div class="wf-control__hint">
      <template v-if="nodeState === 'IN_PROGRESS'">节点执行中，可补充输入</template>
      <template v-else-if="nodeState === 'NEEDS_USER_INPUT'">需要你的输入</template>
      <template v-else-if="nodeState === 'READY_TO_ADVANCE'">节点已完成，可进入下一步</template>
      <template v-else-if="nodeState === 'BLOCKED'">节点已阻塞</template>
    </div>
    <div class="wf-control__buttons">
      <button v-if="nodeState === 'IN_PROGRESS'" type="button" class="wf-control__btn" @click="emit('action', 'CONTINUE_CURRENT', nodeInstanceId)">继续当前</button>
      <button v-if="nodeState === 'READY_TO_ADVANCE'" type="button" class="wf-control__btn wf-control__btn--primary" @click="emit('action', 'ADVANCE_NEXT', nodeInstanceId)">进入下一步</button>
      <button type="button" class="wf-control__btn wf-control__btn--ghost" @click="emit('action', 'RERUN_NODE', nodeInstanceId)">重跑节点</button>
      <button type="button" class="wf-control__btn wf-control__btn--ghost" @click="emit('action', 'SKIP_NODE', nodeInstanceId)">跳过</button>
      <button type="button" class="wf-control__btn wf-control__btn--ghost" @click="emit('action', 'PAUSE_WORKFLOW', nodeInstanceId)">暂停</button>
    </div>
  </div>
</template>

<style scoped>
.wf-control {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
}

.wf-control__hint {
  flex: 1;
  font-size: 12px;
  color: var(--text-secondary);
}

.wf-control__buttons {
  display: flex;
  gap: 6px;
}

.wf-control__btn {
  padding: 4px 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.wf-control__btn--primary {
  border-color: var(--accent-blue);
  background: var(--accent-blue);
  color: var(--on-brand);
}

.wf-control__btn--ghost {
  background: transparent;
}
</style>
