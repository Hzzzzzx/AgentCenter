<script setup lang="ts">
</script>

<template>
  <main class="center-workbench">
    <slot />
  </main>
</template>

<style scoped>
.center-workbench {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
  background-color: var(--bg-primary);
}
</style>
