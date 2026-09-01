<script setup>
/**
 * 지도 전용 — 헤더 아래를 뷰포트 끝까지 채우고 문서 스크롤을 막는다.
 *
 * 통합 전 hangat.css 는 `body{overflow:hidden}` 과 `.stage{height:calc(100% - 58px)}` 로
 * 이걸 전역에 걸었다. 전역으로 두면 마이페이지처럼 세로로 긴 화면이 잘린다.
 * 그래서 지도 라우트에서만 켜지도록 레이아웃으로 옮겼다.
 *
 * 높이는 --nav-h(tokens.css)를 쓴다. 하드코딩돼 있던 58px 는 통합 헤더(80px)와 맞지 않는다.
 */
import { onBeforeUnmount, onMounted } from 'vue'
import AppHeader from './AppHeader.vue'

/* App.vue 는 세 레이아웃에 같은 props(footer, compactHeader)를 넘긴다.
   여기는 둘 다 안 쓰고 루트가 fragment 라, 끄지 않으면 Vue 가 경고를 낸다. */
defineOptions({ inheritAttrs: false })

/* 지도 화면에서만 문서 스크롤을 막는다. 떠날 때 반드시 되돌린다 */
onMounted(() => { document.body.style.overflow = 'hidden' })
onBeforeUnmount(() => { document.body.style.overflow = '' })
</script>

<template>
  <AppHeader />
  <div class="map-shell">
    <slot />
  </div>
</template>

<style scoped>
.map-shell {
  height: calc(100vh - var(--nav-h));
  min-height: 0;
  position: relative;
  overflow: hidden;
}
</style>

<style src="../../assets/hangat.css"></style>
