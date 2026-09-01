<script setup>
/**
 * 테마 전환.
 *
 * variant="icon"  헤더용. 지금 보이는 것의 반대로 뒤집는다.
 * variant="full"  설정 화면용. 시스템 / 라이트 / 다크 3-상태를 직접 고른다.
 *
 * '시스템'이 기본값인 이유: OS 가 시간대나 사용자 설정에 따라 이미 결정을 내려 뒀고,
 * 앱이 그걸 무시하는 건 대부분 사용자 의도와 어긋난다.
 */
import { useTheme } from '../../composables/useTheme.js'

defineProps({ variant: { type: String, default: 'icon' } })

const { choice, resolved, systemDark, setTheme, toggle } = useTheme()

const OPTIONS = [
  { key: 'system', label: '시스템' },
  { key: 'light', label: '라이트' },
  { key: 'dark', label: '다크' }
]
</script>

<template>
  <button
    v-if="variant === 'icon'"
    class="tbtn"
    type="button"
    :aria-label="resolved === 'dark' ? '라이트 테마로 바꾸기' : '다크 테마로 바꾸기'"
    :title="choice === 'system' ? `시스템 설정(${systemDark ? '다크' : '라이트'})을 따르는 중` : (choice === 'dark' ? '다크 고정' : '라이트 고정')"
    @click="toggle"
  >
    <!-- 해 / 달. 원본 날씨 아이콘과 같은 24 그리드·같은 선 굵기를 쓴다 -->
    <svg v-if="resolved === 'dark'" width="17" height="17" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M20.3 14.6A8.4 8.4 0 0 1 9.4 3.7a8.6 8.6 0 1 0 10.9 10.9Z"
        fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"
      />
    </svg>
    <svg v-else width="17" height="17" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="4.1" fill="none" stroke="currentColor" stroke-width="1.9" />
      <g stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
        <path d="M12 2.6v2.1M12 19.3v2.1M2.6 12h2.1M19.3 12h2.1
                 M5.3 5.3l1.5 1.5M17.2 17.2l1.5 1.5M5.3 18.7l1.5-1.5M17.2 6.8l1.5-1.5" />
      </g>
    </svg>
    <span class="sr-only">{{ resolved === 'dark' ? '다크' : '라이트' }} 테마</span>
  </button>

  <div v-else class="seg" role="radiogroup" aria-label="테마">
    <button
      v-for="o in OPTIONS"
      :key="o.key"
      type="button"
      role="radio"
      :aria-checked="String(choice === o.key)"
      :class="{ on: choice === o.key }"
      @click="setTheme(o.key)"
    >{{ o.label }}</button>
  </div>
</template>

<style scoped>
.tbtn {
  width: 34px; height: 34px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--tx2); flex-shrink: 0;
  transition: background .15s, color .15s;
}
.tbtn:hover { background: var(--surf2); color: var(--tx); }
</style>
