<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ currentPage: number; lastPage: number; disabled?: boolean }>()
const emit = defineEmits<{ change: [page: number] }>()
const pages = computed(() => {
  const start = Math.floor((props.currentPage - 1) / 5) * 5 + 1
  const end = Math.min(start + 4, props.lastPage)
  return Array.from({ length: Math.max(0, end - start + 1) }, (_, index) => start + index)
})
</script>

<template>
  <nav v-if="lastPage > 1" class="kakao-pagination" aria-label="검색 결과 페이지">
    <button type="button" :disabled="disabled || currentPage <= 1" @click="emit('change', currentPage - 1)">‹ 이전</button>
    <button v-for="page in pages" :key="page" type="button" :class="{ active: page === currentPage }" :aria-current="page === currentPage ? 'page' : undefined" :disabled="disabled" @click="emit('change', page)">{{ page }}</button>
    <button type="button" :disabled="disabled || currentPage >= lastPage" @click="emit('change', currentPage + 1)">다음 ›</button>
  </nav>
</template>

<style scoped>
.kakao-pagination{display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap}.kakao-pagination button{border:1px solid var(--course-line);border-radius:8px;background:var(--course-surface);min-width:30px;padding:6px 8px;color:var(--course-text-2);font-size:.67rem;font-weight:800}.kakao-pagination button.active{border-color:var(--course-accent);background:var(--course-accent);color:var(--course-on-ac)}.kakao-pagination button:disabled{cursor:not-allowed;opacity:.4}
</style>
