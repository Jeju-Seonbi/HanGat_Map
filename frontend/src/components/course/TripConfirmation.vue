<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth.js'
import { TRIP_ALERTS_ENABLED } from '../../api/notifications.js'
import { getConfirmedTrip, confirmTrip, cancelConfirmedTrip } from '../../api/tripAlerts.js'
const props = defineProps({ courseId: { type: [String, Number], required: true } })
const auth = useAuthStore()
const confirmation = ref(null)
const loading = ref(false)
const ready = ref(false)
const error = ref('')
const notice = ref('')
const choosing = ref(false)
let epoch = 0
const mine = computed(() => String(confirmation.value?.courseId) === String(props.courseId))
const hasActive = computed(() => confirmation.value?.courseId != null)
async function load () {
  const ticket = ++epoch
  choosing.value = false; notice.value = ''
  if (!TRIP_ALERTS_ENABLED || !auth.isLoggedIn) return
  ready.value = false; loading.value = true
  try {
    const value = await getConfirmedTrip()
    if (ticket === epoch) { confirmation.value = value; ready.value = true; error.value = '' }
  } catch { if (ticket === epoch) error.value = '확정한 여행을 확인하지 못했어요.' }
  finally { if (ticket === epoch) loading.value = false }
}
async function save () {
  if (loading.value || !ready.value || !auth.isLoggedIn) return
  const ticket = ++epoch
  const cancelling = mine.value
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const value = mine.value
      ? await cancelConfirmedTrip(confirmation.value.version)
      : await confirmTrip(Number(props.courseId), confirmation.value?.version ?? null)
    if (ticket === epoch) {
      confirmation.value = value; choosing.value = false
      notice.value = cancelling
        ? '여행 확정을 취소했어요. 이후 이 여행의 알림은 받지 않아요.'
        : '여행을 확정했어요. 켜 둔 수신 항목의 알림 대상이 됐어요.'
    }
  } catch (failure) {
    if (ticket === epoch) { error.value = failure.status === 409 ? '다른 화면에서 여행이 변경됐어요. 새로고침 후 다시 선택해 주세요.' : '여행 확정을 변경하지 못했어요.'; ready.value = false }
  } finally { if (ticket === epoch) loading.value = false }
}
watch(() => [props.courseId, auth.isLoggedIn, auth.user?.userId], () => {
  epoch++; confirmation.value = null; choosing.value = false; ready.value = false
  loading.value = false; error.value = ''; notice.value = ''; void load()
}, { immediate: true })
onBeforeUnmount(() => { epoch++ })
</script>
<template>
  <section v-if="TRIP_ALERTS_ENABLED && auth.isLoggedIn" class="trip-confirmation" aria-label="여행 확정과 알림">
    <h2>{{ mine ? '확정한 여행이에요' : '실제로 떠날 여행인가요?' }}</h2>
    <p>여행을 확정하면 이 코스를 여행 알림 대상으로 설정해요. 수신 설정에서 켠 항목만 해당 기능이 제공될 때 안내해요. 숙소나 교통편 예약은 아니에요.</p>
    <p v-if="mine && confirmation.startDate && confirmation.endDate">확정 일정: {{ confirmation.startDate }} ~ {{ confirmation.endDate }}</p>
    <p v-if="mine && confirmation.scheduleChanged" role="alert">코스 일정이 변경되어 여행 알림이 중단됐어요. 여행 확정을 취소한 뒤 다시 확정해 주세요.</p>
    <p v-if="hasActive && !mine">현재 확정한 여행: {{ confirmation.courseTitle }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="choosing" class="confirmation-choice">
      <p>{{ mine ? '여행 확정을 취소할까요? 이후 이 여행의 알림은 중단되며, 저장한 코스와 이미 받은 알림은 유지돼요.' : hasActive ? '기존 여행의 이후 알림을 중단하고 이 여행으로 바꿀까요?' : '이 코스를 여행 알림 대상으로 확정할까요?' }}</p>
      <button type="button" class="btn primary" :disabled="loading || !ready" @click="save">{{ mine ? '여행 확정 취소' : '여행 확정' }}</button>
      <button type="button" class="btn" :disabled="loading" @click="choosing = false">취소</button>
    </div>
    <button v-else type="button" class="btn" :disabled="loading || !ready" @click="choosing = true">{{ mine ? '여행 확정 취소' : '여행 확정' }}</button>
    <button v-if="error" type="button" class="btn" :disabled="loading" @click="load">새로고침</button>
    <RouterLink :to="{ name: 'my-profile' }" class="settings-link">알림 수신 설정</RouterLink>
  </section>
</template>
<style scoped>
.trip-confirmation { margin: 24px 0; padding: 22px; border: 1px solid var(--line); border-radius: 16px; background: var(--surf); }
h2 { font-size: 18px; margin: 0 0 10px; }
p { color: var(--tx2); line-height: 1.7; }
.btn { margin: 8px 8px 0 0; }
.settings-link { display: inline-block; margin-top: 12px; font-size: 13px; color: var(--ac); text-decoration: underline; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--ac); outline-offset: 3px; }
</style>
