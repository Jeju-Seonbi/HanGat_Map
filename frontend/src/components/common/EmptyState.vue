<script setup>
/**
 * 빈 상태 (MY_001 / MY_004 / MY_006 / 명세서 MY_010).
 * 원본 .empty(index.html:198) 스타일 + "~없어요 + 다음 행동" 2줄 구조(디자인.md §5.9).
 */
defineProps({
  title: { type: String, required: true },
  hint: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
  actionTo: { type: [String, Object], default: null }
})
</script>

<template>
  <div class="empty">
    <div class="ttl">{{ title }}</div>
    <p v-if="hint" class="hint">{{ hint }}</p>
    <RouterLink v-if="actionLabel && actionTo" :to="actionTo" class="go">{{ actionLabel }}</RouterLink>
    <slot />
  </div>
</template>

<style scoped>
.ttl { font-size: 13.5px; font-weight: 800; color: var(--tx2); letter-spacing: -.02em; }
.hint { margin-top: 4px; }
.go {
  display: inline-block; margin-top: 14px;
  padding: 9px 18px; border-radius: 12px;
  background: var(--ac); color: var(--on-ac);
  font-size: 12.5px; font-weight: 800; letter-spacing: -.02em;
  box-shadow: var(--sh-cta);
}
.go:hover { background: var(--ac-dk); }
</style>
