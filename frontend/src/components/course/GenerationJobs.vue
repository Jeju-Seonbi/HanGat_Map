<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth.js'
import { ASYNC_COURSES_ENABLED } from '../../api/notifications.js'
import { listGenerationJobs } from '../../api/courseGeneration.js'
const auth = useAuthStore()
const items = ref([])
const error = ref('')
const busy = ref(false)
let epoch = 0
const labels = { QUEUED: '접수됨', RUNNING: '생성 중', SUCCEEDED: '생성 완료', FAILED: '생성 실패' }
async function load () {
  const ticket = ++epoch
  if (!ASYNC_COURSES_ENABLED || !auth.isLoggedIn) return
  busy.value = true; error.value = ''
  try {
    const response = await listGenerationJobs()
    if (ticket === epoch) items.value = response.items
  } catch { if (ticket === epoch) error.value = '최근 생성 요청을 불러오지 못했어요.' }
  finally { if (ticket === epoch) busy.value = false }
}
watch(() => auth.user?.userId, () => { epoch++; items.value = []; error.value = ''; busy.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { epoch++ })
</script>
<template>
  <section v-if="ASYNC_COURSES_ENABLED && auth.isLoggedIn" class="recent-jobs" aria-label="최근 AI 코스 생성 요청">
    <div class="jobs-heading"><h3>최근 코스 생성 요청</h3><button type="button" :disabled="busy" @click="load">새로고침</button></div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="busy">요청을 확인하고 있어요.</p>
    <p v-else-if="!items.length">접수된 요청이 없어요.</p>
    <ul><li v-for="job in items" :key="job.jobId">
      <RouterLink :to="{ name: 'course-generation-job', params: { jobId: job.jobId } }">
        <span>{{ job.startDate }} ~ {{ job.endDate }}</span><strong>{{ labels[job.status] || '상태 확인' }}</strong>
      </RouterLink>
    </li></ul>
  </section>
</template>
<style scoped>
.recent-jobs { margin: 20px 0; padding: 18px 0; border-block: 1px solid var(--line); }
.jobs-heading, a { display: flex; gap: 12px; align-items: center; justify-content: space-between; }
h3 { margin: 0; font-size: 15px; }
button, strong { color: var(--ac); font-size: 13px; }
ul { list-style: none; padding: 0; margin-bottom: 0; }
a { padding: 10px 0; font-size: 14px; flex-wrap: wrap; }
p { color: var(--tx3); font-size: 13px; }
</style>
