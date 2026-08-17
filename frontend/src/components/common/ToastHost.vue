<script setup>
import { useUiStore } from '../../stores/ui.js'
const ui = useUiStore()
</script>

<template>
  <div class="toast-host" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="t">
      <div v-for="t in ui.toasts" :key="t.id" class="toast">{{ t.message }}</div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
/* 이동 거리 2px — 원본 모션 규칙(디자인.md §7) */
.t-enter-active, .t-leave-active { transition: opacity .15s, transform .15s; }
.t-enter-from, .t-leave-to { opacity: 0; transform: translateY(2px); }
</style>
