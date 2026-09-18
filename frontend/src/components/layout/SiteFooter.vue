<script setup>
/**
 * 사이트 푸터 - 로고·메뉴·출처. 메인이 먼저 쓴다(2026-09-18 메인 시안).
 * 출처 표기는 관광공사 지침(ⓒ한국관광공사, 'TourAPI' 표기 금지)을 따른다.
 */
import { NAV_TABS } from '../../config/navTabs.js'
</script>

<template>
  <footer class="sf">
    <div class="sf-in">
      <div class="sf-top">
        <RouterLink to="/" class="sf-logo"><img src="/hangat-mark.png" alt="" width="32" height="28">한갓지도</RouterLink>
        <nav class="sf-nav" aria-label="푸터 메뉴">
          <RouterLink v-for="t in NAV_TABS" :key="t.to" :to="t.to">{{ t.label }}</RouterLink>
          <RouterLink to="/mypage">마이페이지</RouterLink>
        </nav>
      </div>
      <div class="sf-line" />
      <div class="sf-src">
        <span>관광정보·사진 ⓒ한국관광공사 · 착한가격업소 행정안전부 · 날씨 기상청 · 지도 카카오</span>
        <span>혼잡도는 한국관광공사 집중률 예보를 바탕으로 하며 실제와 다를 수 있습니다.</span>
        <span>2026 관광데이터 활용 공모전 출품작 · 팀 제주선비</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.sf{background:var(--deep);color:rgba(255,255,255,.86)}
.sf-in{max-width:1240px;margin:0 auto;padding:40px 24px 34px;display:flex;flex-direction:column;gap:22px}
.sf-top{display:flex;align-items:center;gap:28px;flex-wrap:wrap}
.sf-logo{display:flex;align-items:center;gap:8px;font-size:19px;font-weight:800;color:#fff}
.sf-logo img{width:32px;height:28px;object-fit:contain}
.sf-nav{display:flex;gap:20px;font-size:13.5px;font-weight:600;flex-wrap:wrap}
.sf-nav a{color:rgba(255,255,255,.86)}
.sf-nav a:hover{color:#fff}
.sf-line{height:1px;background:rgba(255,255,255,.14)}
.sf-src{display:flex;flex-direction:column;gap:6px;font-size:12.5px;line-height:1.7;color:rgba(255,255,255,.72)}
@media (max-width:767px){
  .sf-in{padding:26px 16px 30px;gap:14px}
  .sf-nav{gap:14px;font-size:13px}
  .sf-src{font-size:11.5px}
}
</style>
