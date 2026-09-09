<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth.js'
import { TRIP_ALERTS_ENABLED } from '../../api/notifications.js'
import { getNotificationPreferences, saveNotificationPreferences } from '../../api/tripAlerts.js'
const auth = useAuthStore()
const settings = ref(null)
const busy = ref(false)
const notice = ref('')
const error = ref('')
const ready = ref(false)
let epoch = 0
const fields = [
  ['aiCourse', 'AI 코스 생성 결과', '코스 생성이 완료되거나 실패했을 때'],
  ['weatherWarning', '공식 기상특보', '확정 여행 지역에 기상특보가 발표됐을 때'],
  ['forecastChange', '날씨 예보 변경', '확정한 일정의 날씨 예보가 달라졌을 때'],
  ['congestion', '혼잡 예보 악화', '확정한 일정의 장소가 더 혼잡해질 것으로 예상될 때'],
  ['tripSummary', '여행 일정 요약', '확정 여행의 출발 전날과 여행 당일'],
  ['reviewRequest', '여행 후 리뷰 요청', '확정한 여행이 끝난 뒤']
]
async function load () {
  const ticket = ++epoch
  ready.value = false
  if (!TRIP_ALERTS_ENABLED || !auth.isLoggedIn) return
  busy.value = true; notice.value = ''; error.value = ''
  try {
    const value = await getNotificationPreferences()
    if (ticket === epoch) { settings.value = value; ready.value = true }
  }
  catch { if (ticket === epoch) error.value = '알림 설정을 불러오지 못했어요. 다시 불러와 주세요.' }
  finally { if (ticket === epoch) busy.value = false }
}
async function toggle (key, label) {
  if (busy.value || !ready.value || !settings.value || !auth.isLoggedIn) return
  const ticket = ++epoch
  const previous = settings.value
  const next = { ...previous, [key]: !previous[key] }
  settings.value = next
  busy.value = true; notice.value = ''; error.value = ''
  try {
    const value = await saveNotificationPreferences(next)
    if (ticket !== epoch) return
    settings.value = value
    notice.value = `${label} 알림을 ${value[key] ? '켰어요' : '껐어요'}.`
  } catch (failure) {
    if (ticket !== epoch) return
    settings.value = previous
    // 응답만 유실됐을 수도 있으므로 서버 상태를 다시 읽기 전에는 재저장하지 않는다.
    ready.value = false
    error.value = failure.status === 409
      ? '다른 화면에서 설정이 변경됐어요. 다시 불러온 뒤 선택해 주세요.'
      : '저장 결과를 확인하지 못했어요. 다시 불러와 현재 설정을 확인해 주세요.'
  }
  finally { if (ticket === epoch) busy.value = false }
}
watch(() => [auth.isLoggedIn, auth.user?.userId], () => {
  settings.value = null; busy.value = false; error.value = ''; notice.value = ''
  void load()
}, { immediate: true })
onBeforeUnmount(() => { epoch++ })
</script>
<template>
  <section v-if="auth.isLoggedIn" class="notification-settings" aria-labelledby="notification-settings-title" :aria-busy="busy">
    <h2 id="notification-settings-title">알림 수신 설정</h2>
    <p v-if="!TRIP_ALERTS_ENABLED" class="note">알림 수신 설정을 준비 중이에요.</p>
    <template v-else>
      <p class="note">스위치를 바꾸면 바로 저장돼요. 끈 항목은 이후 새 알림을 받지 않으며, 이미 받은 알림은 유지돼요.</p>
      <div v-if="settings" class="preference-list">
        <div v-for="[key, label, description] in fields" :key="key" class="preference-row">
          <div><h3 :id="`notification-${key}`">{{ label }}</h3><p :id="`notification-${key}-description`" class="note">{{ description }}</p></div>
          <button type="button" role="switch" class="preference-switch" :class="{ enabled: settings[key] }"
            :aria-checked="settings[key]" :aria-labelledby="`notification-${key}`"
            :aria-describedby="`notification-${key}-description`" :disabled="busy || !ready"
            @click="toggle(key, label)"><span aria-hidden="true" class="switch-track"><span /></span><span aria-hidden="true">{{ settings[key] ? '켜짐' : '꺼짐' }}</span></button>
        </div>
      </div>
      <p v-if="busy" class="note" role="status">{{ settings ? '알림 설정을 저장하고 있어요.' : '알림 설정을 불러오고 있어요.' }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button v-if="!ready && !busy" type="button" class="retry" @click="load">다시 불러오기</button>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <p class="note policy">여행 관련 알림은 여행을 확정하고 해당 알림 기능이 제공되는 경우에 받을 수 있어요. 여행 확정 취소 시 이후 여행 관련 알림은 중단돼요.<br>로그인·비밀번호 변경과 중요 공지는 항상 알림함에 보관해요. 브라우저를 닫으면 실시간 수신은 중단돼요.</p>
    </template>
  </section>
</template>
<style scoped>
.notification-settings { padding-block: 22px; border-bottom: 1px solid var(--line); }
h2 { margin: 0 0 12px; font-size: 18px; font-weight: 700; color: var(--tx); }
h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--tx); }
.note { margin: 5px 0 0; font-size: 13px; color: var(--tx3); line-height: 1.7; }
.preference-list { margin-top: 14px; }
.preference-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-block: 14px; border-bottom: 1px solid var(--line); }
.preference-row:last-child { border-bottom: 0; }
.preference-switch { display: flex; flex: 0 0 auto; align-items: center; gap: 8px; min-height: 44px; padding: 0 2px; color: var(--tx3); font-size: 12px; }
.switch-track { display: inline-flex; align-items: center; width: 42px; height: 24px; box-sizing: border-box; border-radius: 20px; padding: 3px; background: var(--tx3); }
.switch-track > span { width: 18px; height: 18px; border-radius: 50%; background: var(--surf); }
.enabled { color: var(--ac); }
.enabled .switch-track { background: var(--ac); }
.enabled .switch-track > span { transform: translateX(18px); }
button:disabled { opacity: .6; cursor: wait; }
button:focus-visible { outline: 2px solid var(--ac); outline-offset: 4px; border-radius: 6px; }
.policy { margin-top: 16px; }
.error { font-size: 13px; color: var(--tx); line-height: 1.7; }
.retry { padding: 10px 0; color: var(--ac); text-decoration: underline; }
.notice { font-size: 13px; color: var(--ac); }
</style>
