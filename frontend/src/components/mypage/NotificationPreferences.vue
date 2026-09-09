<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth.js'
import { TRIP_ALERTS_ENABLED } from '../../api/notifications.js'
import { getNotificationPreferences, saveNotificationPreferences } from '../../api/tripAlerts.js'
const auth = useAuthStore()
const settings = ref(null)
const busy = ref(false)
const notice = ref('')
let epoch = 0
const fields = [
  ['aiCourse', 'AI 코스 생성 완료·실패'], ['weatherWarning', '공식 기상특보'],
  ['forecastChange', '확정 여행의 예보 변경'], ['congestion', '혼잡 예보 악화'],
  ['tripSummary', '출발 전날·당일 일정 요약'], ['reviewRequest', '여행 후 리뷰 요청']
]
async function load () {
  const ticket = ++epoch
  if (!TRIP_ALERTS_ENABLED || !auth.isLoggedIn) return
  busy.value = true; notice.value = ''
  try { const value = await getNotificationPreferences(); if (ticket === epoch) settings.value = value }
  catch { if (ticket === epoch) notice.value = '알림 설정을 불러오지 못했어요.' }
  finally { if (ticket === epoch) busy.value = false }
}
async function save () {
  const ticket = ++epoch
  busy.value = true; notice.value = ''
  try {
    const value = await saveNotificationPreferences({ ...settings.value })
    if (ticket === epoch) { settings.value = value; notice.value = '알림 설정을 저장했어요.' }
  } catch { if (ticket === epoch) notice.value = '설정을 저장하지 못했어요. 다시 시도해 주세요.' }
  finally { if (ticket === epoch) busy.value = false }
}
watch(() => auth.user?.userId, () => { epoch++; settings.value = null; void load() }, { immediate: true })
onBeforeUnmount(() => { epoch++ })
</script>
<template>
  <details v-if="TRIP_ALERTS_ENABLED && auth.isLoggedIn" class="notification-settings">
    <summary>받을 알림 선택</summary>
    <p>로그인·비밀번호 변경과 서비스 이용에 필요한 중요 공지는 알림함에 항상 보관해요.</p>
    <form v-if="settings" @submit.prevent="save">
      <label v-for="[key, label] in fields" :key="key"><input v-model="settings[key]" type="checkbox" :disabled="busy">{{ label }}</label>
      <button type="submit" class="btn primary" :disabled="busy">알림 설정 저장</button>
    </form>
    <button v-else type="button" class="btn" :disabled="busy" @click="load">설정 불러오기</button>
    <p role="status">{{ notice }}</p>
  </details>
</template>
<style scoped>
.notification-settings { margin: 24px 0; padding-block: 18px; border-bottom: 1px solid var(--line); }
summary { font-size: 15px; cursor: pointer; color: var(--ac); }
p { font-size: 13px; color: var(--tx3); line-height: 1.7; }
label { display: flex; gap: 12px; align-items: center; min-height: 44px; }
input { accent-color: var(--ac); width: 18px; height: 18px; }
.btn { margin-top: 16px; padding: 10px 16px; border-radius: 10px; background: var(--ac); color: var(--on-ac); }
</style>
