<script setup>
/**
 * 기본 레이아웃 — 대부분의 화면.
 *
 * 통합 전에는 App.vue 가 헤더·배너·탭바·토스트를 직접 그렸다.
 * 지도처럼 다른 뼈대가 필요한 화면이 생겨 레이아웃으로 분리했다.
 *
 * 푸터는 옵션이다. 랜딩 성격 화면(메인·AI코스)에만 어울려서
 * 라우트가 meta.footer 로 켠 곳에만 그린다.
 */
import { computed } from 'vue'
import AppHeader from './AppHeader.vue'
import MobileTabBar from './MobileTabBar.vue'
import AppFooter from '../common/AppFooter.vue'
import { useAuthStore } from '../../stores/auth.js'

const props = defineProps({
  footer: { type: Boolean, default: false },
  compactHeader: { type: Boolean, default: false },
  contentStyles: { type: Boolean, default: false }
})

const auth = useAuthStore()

/** 세션이 끊긴 이유별 안내 — 무한 로딩 대신 무엇을 해야 하는지 알려준다 (명세서 AUTH_005) */
const banner = computed(() => {
  if (auth.isLoggedIn || !auth.endedReason) return null
  if (auth.endedReason === 'SESSION_REVOKED') {
    return { tone: 'bad', text: '보안을 위해 모든 기기에서 로그아웃했어요.' }
  }
  return { tone: 'warn', text: '로그인 후 시간이 오래 지나 자동으로 로그아웃했어요.' }
})
</script>

<template>
  <AppHeader :compact="props.compactHeader" />

  <div v-if="banner" class="banner" :class="banner.tone" role="status">
    {{ banner.text }}
    <RouterLink :to="{ name: 'login', query: auth.returnTo ? { redirect: auth.returnTo } : {} }">
      다시 로그인하기
    </RouterLink>
  </div>

  <div class="route-content" :class="{ 'content-scope': props.contentStyles }">
    <slot />
    <AppFooter v-if="props.footer" />
  </div>

  <MobileTabBar />
</template>

<style scoped>
.banner {
  font-size: 12.5px;
  font-weight: 700;
  padding: 10px 18px;
  text-align: center;
}
.banner.warn { background: var(--mid-bg); color: var(--mid); }
.banner.bad { background: var(--busy-bg); color: var(--busy); }
.banner a { text-decoration: underline; margin-left: 6px; }
.route-content { display: contents; }
</style>

<style src="../../assets/styles.css"></style>
