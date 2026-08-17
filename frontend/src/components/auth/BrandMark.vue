<script setup>
/**
 * 소셜 로그인 버튼용 브랜드 심볼.
 *
 * AppIcon 과 분리한 이유: AppIcon 은 `currentColor` 단색 path 하나만 그린다.
 * 구글 "G" 는 4색 다중 path 라 그 모델에 들어가지 않는다.
 *
 * ⚠️ 상표 관련
 *  · google — Google 이 배포하는 공식 "G" 로고의 path 좌표(48x48)를 그대로 쓴다.
 *    Google Identity 브랜딩 가이드는 로고를 변형하지 말 것을 요구하므로 색·비율을 건드리지 않는다.
 *  · kakao — 카카오 심볼은 상표다. 여기 있는 말풍선은 **공식 자산이 아니라 근사 도형**이다.
 *    실제 서비스로 낼 때는 Kakao Developers 가 배포하는 로그인 버튼 자산으로 교체해야 한다.
 */
defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 }
})
</script>

<template>
  <!-- 카카오: 말풍선 (근사 도형, currentColor 로 버튼 글자색을 따라간다) -->
  <svg
    v-if="name === 'kakao'"
    :width="size" :height="size" viewBox="0 0 24 24"
    aria-hidden="true" focusable="false" class="bm"
  >
    <path
      fill="currentColor"
      d="M12 3C6.48 3 2 6.58 2 11c0 2.85 1.87 5.35 4.68 6.76l-1.18 4.33c-.1.38.33.68.66.46l5.19-3.43c.21.01.43.02.65.02 5.52 0 10-3.58 10-8.14C22 6.58 17.52 3 12 3z"
    />
  </svg>

  <!-- 구글: 공식 "G" (48x48 좌표계, 4색 고정) -->
  <svg
    v-else-if="name === 'google'"
    :width="size" :height="size" viewBox="0 0 48 48"
    aria-hidden="true" focusable="false" class="bm"
  >
    <path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z" />
    <path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z" />
    <path fill="#FBBC05" d="M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24s.85 6.91 2.34 9.88l7.35-5.7z" />
    <path fill="#EA4335" d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z" />
  </svg>
</template>

<style scoped>
.bm { display: block; flex-shrink: 0; }
</style>
