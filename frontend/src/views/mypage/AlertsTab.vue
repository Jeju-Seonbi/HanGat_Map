<script setup>
/** AI 작업·여행·보안·공지 알림을 같은 서버 알림함에서 조회한다. */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '../../stores/notifications.js'
import { NOTIFICATIONS_ENABLED, notificationDestination } from '../../api/notifications.js'
import { fmtRelative } from '../../utils/format.js'
import GenerationJobs from '../../components/course/GenerationJobs.vue'
import NotificationPreferences from '../../components/mypage/NotificationPreferences.vue'
const notifications = useNotificationStore()
const router = useRouter()
const actionError = ref('')
const busy = ref(false)
onMounted(() => notifications.refresh())
async function open (item) {
  actionError.value = ''
  if (!item.readAt) {
    try { await notifications.markRead(item.id) }
    catch { actionError.value = '알림을 읽음으로 표시하지 못했어요.' }
  }
  await router.push(notificationDestination(item))
}
async function readAll () {
  busy.value = true; actionError.value = ''
  try { await notifications.markAllRead() }
  catch { actionError.value = '읽음 처리를 하지 못했어요. 다시 시도해 주세요.' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="notification-inbox" aria-labelledby="notification-title">
    <header>
      <div><h2 id="notification-title">알림 내역</h2><p>여행의 변화와 코스 생성 결과를 확인하세요.</p></div>
      <button v-if="notifications.unread" type="button" class="btn" :disabled="busy" @click="readAll">모두 읽음</button>
    </header>
    <GenerationJobs />
    <NotificationPreferences />
    <p v-if="!NOTIFICATIONS_ENABLED">알림 서비스 준비 중이에요.</p>
    <template v-else>
      <p class="delivery-note">{{ notifications.connected ? '실시간 알림 연결됨' : '실시간 연결 대기 중 · 새로고침으로 확인할 수 있어요' }}<br>브라우저를 닫으면 실시간 수신은 중단돼요. 알림은 내역에 보관됩니다.</p>
      <button class="btn" type="button" :disabled="notifications.loading" @click="notifications.refresh()">새로고침</button>
      <p v-if="notifications.error || actionError" role="alert">{{ actionError || notifications.error }}</p>
      <p v-if="notifications.loading" role="status">알림을 불러오고 있어요.</p>
      <p v-else-if="!notifications.error && !notifications.items.length" class="empty">아직 도착한 알림이 없어요.</p>
      <ol class="inbox-list">
        <li v-for="item in notifications.items" :key="item.id" :class="{ unread: !item.readAt }">
          <button type="button" @click="open(item)">
            <span class="heading"><strong>{{ item.title }}</strong><span v-if="!item.readAt" class="unread-label">안 읽음</span></span>
            <span class="message">{{ item.message }}</span>
            <time :datetime="item.createdAt">{{ fmtRelative(item.createdAt) }}</time>
            <span v-if="item.targetType === 'COURSE_GENERATION'" class="action">코스 생성 결과 확인</span>
            <span v-else-if="item.targetType === 'COURSE'" class="action">해당 코스 보기</span>
          </button>
        </li>
      </ol>
      <button v-if="notifications.nextCursor" class="btn" type="button" :disabled="notifications.loading" @click="notifications.refresh(true)">이전 알림 더 보기</button>
    </template>
  </section>
</template>
<style scoped>
.notification-inbox { color: var(--tx); }
header, .heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h2 { font-size: 22px; margin: 0 0 8px; }
header p, .delivery-note, time { color: var(--tx3); font-size: 13px; line-height: 1.7; }
.delivery-note { margin: 20px 0 12px; }
.inbox-list { list-style: none; padding: 0; margin: 16px 0; }
li { border-bottom: 1px solid var(--line); }
li.unread { background: var(--ac-bg); }
li > button { width: 100%; padding: 20px 14px; text-align: left; color: inherit; }
.heading { align-items: baseline; }
.message { display: block; margin: 8px 0; white-space: pre-line; overflow-wrap: anywhere; line-height: 1.65; }
.unread-label, .action { color: var(--ac); font-size: 12px; white-space: nowrap; }
.action { display: block; margin-top: 12px; font-weight: 700; }
.empty { padding: 32px 0; color: var(--tx3); }
button:focus-visible { outline: 2px solid var(--ac); outline-offset: 3px; }
.btn { padding: 10px 14px; border: 1px solid var(--line); border-radius: 10px; color: var(--ac); }
</style>
