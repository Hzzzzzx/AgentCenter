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
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  background-color: var(--bg-primary);
}
</style>
