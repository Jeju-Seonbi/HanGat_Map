<script setup>
/**
 * 로딩 / 오류 공통 표시 (명세서 COM_003: 조회 실패 시 안내 + 재시도).
 * 무한 로딩 대신 항상 끝 상태를 보여준다.
 */
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: [Object, String], default: null },
  rows: { type: Number, default: 3 }
})
defineEmits(['retry'])
</script>

<template>
  <div v-if="loading" class="skel" aria-busy="true" aria-live="polite">
    <span class="sr-only">불러오는 중</span>
    <div v-for="i in rows" :key="i" class="sk" :style="{ animationDelay: `${i * 90}ms` }" />
  </div>

  <div v-else-if="error" class="err" role="alert">
    <div class="t">{{ typeof error === 'string' ? error : (error.message || '불러오지 못했어요') }}</div>
    <p class="h">잠시 뒤 다시 시도해 주세요.</p>
    <button class="btn2" @click="$emit('retry')">다시 불러오기</button>
  </div>
</template>

<style scoped>
.skel { display: flex; flex-direction: column; gap: 10px; }
.sk {
  height: 92px; border-radius: 14px;
  background: linear-gradient(100deg, var(--surf2) 30%, var(--line) 50%, var(--surf2) 70%);
  background-size: 220% 100%;
  animation: sh 1.15s linear infinite;
}
@keyframes sh { from { background-position: 120% 0; } to { background-position: -20% 0; } }
@media (prefers-reduced-motion: reduce) { .sk { animation: none; } }

.err { padding: 30px 20px; text-align: center; }
.err .t { font-size: 13.5px; font-weight: 800; color: var(--busy); letter-spacing: -.02em; }
.err .h { font-size: 12px; color: var(--tx3); margin: 5px 0 14px; }
</style>
