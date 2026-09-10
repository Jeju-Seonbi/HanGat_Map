<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { congestionLabel } from '../../utils/congestion'
import { dayWeatherLabels } from '../../services/course/dailyWeather'
import { useTransitRoute } from '../../services/course/transitRoute'
import TransitDayRoute from '../../components/course/TransitDayRoute.vue'
import TransitLegCard from '../../components/course/TransitLegCard.vue'
import { todayKst, addCalendarDays, formatCalendarDate } from '../../utils/format.js'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../app/stores/auth'
import CourseConditionForm from '../../components/course/CourseConditionForm.vue'
import CourseItemCard from '../../components/course/CourseItemCard.vue'
import BudgetGauge from '../../components/course/BudgetGauge.vue'
import AlternativePlaceModal from '../../components/course/AlternativePlaceModal.vue'
import CongestionRescheduleModal from '../../components/course/CongestionRescheduleModal.vue'
import AccommodationRecommendations from '../../components/course/AccommodationRecommendations.vue'
import { courseGenerationErrorMessage, courseMockService, toCourseRequestPayload } from '../../services/courseMockService'
import { ASYNC_COURSES_ENABLED } from '../../api/notifications.js'
import { getGenerationResult, submitGenerationJob } from '../../api/courseGeneration.js'
import GenerationJobs from '../../components/course/GenerationJobs.vue'
import { storePendingCourseClaim, takePendingCourseClaim } from '../../services/pendingCourseClaim'
import { routeSummary, accessNotices } from '../../services/course/courseSummary'
import { ApiError } from '../../api/errors.js'
import { readRestore, rememberResult, rememberEditing, useResultRestore, validProof, singleFlight, useClaimRenewal, clearCourseProof } from '../../services/course/resultRestore'
import type { AccommodationInput, AccommodationRecommendation, AlternativePlace, CarDayRoute, CarRouteLeg, CongestionRescheduleOption, CourseCondition, CourseItem, CourseResult } from '../../assets/types/course'

const today = todayKst()

const condition = reactive<CourseCondition>({
  start_date: today,
  end_date: addCalendarDays(today, 2),
  people: 2,
  budget_total: 400000,
  transport: 'RENTAL_CAR',
  course_regions: [],
  course_styles: [],
  course_place_preferences: [],
})

const result = ref<CourseResult>()
const loading = ref(false)
const error = ref('')
const jobResultError = ref('')
const editing = ref(true)
const selected = ref<CourseItem>()
const alternatives = ref<AlternativePlace[]>([])
const altLoading = ref(false)
const altNotice = ref('')
const swapping = ref(false)
const rescheduleSelected = ref<CourseItem>()
const rescheduleOptions = ref<CongestionRescheduleOption[]>([])
const rescheduleLoading = ref(false)
const recommendedAccommodations = ref<AccommodationRecommendation[]>([])
const accommodationLoading = ref(false)
const accommodationError = ref('')
const accommodationPickerOpen = ref(false)
const routeLoading = ref(false)
const routeError = ref('')
const saveOpen = ref(false)
const title = ref('')
const saveError = ref('')
const saveLoading = ref(false)
const toast = ref('')
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const restoration = useResultRestore()
const renewal = useClaimRenewal()
const { notice: renewalNotice, renewing } = renewal
const { restoring, restoreError, terminal } = restoration
const restoringState = ref(readRestore())
// Same public numeric identifier pattern used by existing course links; never a claim credential.
if (typeof route.query.course === 'string' && /^[1-9]\d{0,14}$/.test(route.query.course)) {
  const id = Number(route.query.course)
  if (restoringState.value?.courseId !== id) restoringState.value = { mode: 'result', courseId: id, condition: restoringState.value?.condition ?? { ...condition } }
}
// The form clones its initial props: hydrate before its first render, not onMounted.
if (restoringState.value) Object.assign(condition, restoringState.value.condition)
const fetchRoute = singleFlight(courseMockService.getCarRoute)
const transit = useTransitRoute()
const { data: transitData, loading: transitLoading, error: transitError } = transit
const now = ref(Date.now())
let clock: ReturnType<typeof setInterval> | undefined
let viewEpoch = 0
let routeEpoch = 0
const canModify = computed(() => !!result.value && !renewing.value
  && (result.value.status === 'SAVED' ? auth.isAuthenticated : validProof(result.value, now.value)))
// Swap remains governed by its existing independent API contract, not the claim lifetime.
const canSwap = computed(() => !!result.value && result.value.swappable !== false)
function rememberCurrent() {
  if (result.value) {
    rememberResult(result.value, condition)
    restoringState.value = readRestore()
  }
}
function editConditions() {
  transit.cancel()
  restoration.cancel(); renewal.cancel(); viewEpoch++; routeEpoch++
  if (result.value) clearCourseProof(result.value)
  restoringState.value = null; editing.value = true; loading.value = false; routeLoading.value = false
  jobResultError.value = ''
  selected.value = undefined; rescheduleSelected.value = undefined; saveOpen.value = false
  accommodationPickerOpen.value = false
  rememberEditing(condition)
  if (route.query.course != null || route.query.job != null) void router.replace({ path: route.path, query: { ...route.query, course: undefined, job: undefined } })
}
watch(condition, () => { if (editing.value && !restoring.value) rememberEditing(condition) }, { deep: true })
async function restoreResult() {
  if (!restoringState.value || restoringState.value.mode !== 'result') return
  const ticket = viewEpoch
  const loaded = await restoration.restore(restoringState.value, auth.isAuthenticated)
  if (!loaded || ticket !== viewEpoch) return
  result.value = loaded
  if (loaded.status === 'READY' && validProof(restoringState.value)) {
    loaded.claim_token = restoringState.value.claim_token
    loaded.claim_expires_at = restoringState.value.claim_expires_at
  }
  Object.assign(condition, {
    start_date: loaded.start_date, end_date: loaded.end_date, people: loaded.people,
    budget_total: loaded.budget_total ?? condition.budget_total, transport: loaded.transport,
    accommodation: loaded.accommodation ?? undefined,
  })
  editing.value = false; rememberCurrent()
  void loadCarRoute()
  if (loaded.status === 'READY' && validProof(loaded)) {
    const id = loaded.id
    const proof = await renewal.renew(id, loaded)
    if (proof && ticket === viewEpoch && result.value?.id === id && result.value.status === 'READY' && !editing.value) {
      clearCourseProof(result.value)
      Object.assign(result.value, proof)
      rememberCurrent()
    }
  }
}
onUnmounted(() => { transit.cancel(); restoration.cancel(); renewal.cancel(); viewEpoch++; routeEpoch++; if (clock) clearInterval(clock) })
watch(now, () => {
  if (result.value?.claim_token && !validProof(result.value, now.value)) { clearCourseProof(result.value); rememberCurrent() }
})

/* Navigate through the existing saved-course URL without sharing route geometry. */
async function viewOnMap() {
  if (!result.value) return
  if (result.value.status !== 'SAVED') {
    toast.value = '코스를 저장한 뒤 지도에서 확인해 주세요.'
    return
  }
  await router.push({ path: '/map', query: { course: String(result.value.id) } })
}

async function loadCarRoute() {
  if (result.value?.transport === 'PUBLIC_TRANSIT') { await transit.load(result.value); return }
  transit.cancel()
  if (!result.value || result.value.transport !== 'RENTAL_CAR') return
  const course = result.value
  const ticket = ++routeEpoch
  routeLoading.value = true
  routeError.value = ''
  try {
    const route = await fetchRoute(JSON.stringify([course.id, course.days, course.accommodation]), course)
    if (ticket === routeEpoch && result.value?.id === course.id && !editing.value) result.value = { ...result.value, car_route: route }
  } catch {
    if (ticket === routeEpoch) routeError.value = '이동 경로를 불러오지 못했어요.'
  } finally {
    if (ticket === routeEpoch) routeLoading.value = false
  }
}

function transitLeg(dayNo: number, fromId: string) {
  return transitData.value?.days.find(d => d.day_no === dayNo)?.legs.find(l => l.from.id === fromId)
}

const routeForDay = (dayNo: number): CarDayRoute | undefined =>
  result.value?.car_route?.days.find(day => day.day_no === dayNo)
const inboundRoute = (dayNo: number, itemId: number): CarRouteLeg | undefined =>
  routeForDay(dayNo)?.legs.find(leg => leg.to.type === 'COURSE_ITEM' && leg.to.id === String(itemId))
const formatDuration = (seconds?: number | null) => seconds == null ? '정보 없음' : `${Math.round(seconds / 60)}분`

const transportLabel = {
  RENTAL_CAR: '렌터카',
  PUBLIC_TRANSIT: '대중교통',
  TAXI: '택시',
  WALK_BIKE: '도보·자전거',
}

const tripDays = computed(() => result.value?.days.length ?? 0)
const tripNights = computed(() => Math.max(0, tripDays.value - 1))
const visitCount = computed(() => result.value?.days.reduce((count, day) => count + day.items.length, 0) ?? 0)
const regionSummary = computed(() => condition.course_regions.map(region => region.name).join(' · ') || '전체')
const styleSummary = computed(() => condition.course_styles.map(style => style.name).join(' · '))
const estimatedCost = computed(() => {
  const summary = result.value?.budget_summary
  if (!summary?.has_cost_data) return '정보 없음'
  if (summary.total_expected_min == null || summary.total_expected_max == null) return '정보 없음'
  return summary.total_expected_min === summary.total_expected_max
    ? `${summary.total_expected_max.toLocaleString()}원`
    : `${summary.total_expected_min.toLocaleString()} ~ ${summary.total_expected_max.toLocaleString()}원`
})

async function generate(next: CourseCondition, regenerate = false) {
  if (loading.value) return
  transit.cancel()
  restoration.cancel(); renewal.cancel(); routeEpoch++
  const ticket = ++viewEpoch
  Object.assign(condition, JSON.parse(JSON.stringify(next)) as CourseCondition)
  loading.value = true
  error.value = ''
  try {
    if (ASYNC_COURSES_ENABLED && auth.isAuthenticated && !regenerate) {
      const job = await submitGenerationJob(toCourseRequestPayload(condition))
      if (ticket === viewEpoch) await router.push({ name: 'course-generation-job', params: { jobId: job.jobId } })
      return
    }
    const generated = regenerate
      ? await courseMockService.regenerateCourse(condition)
      : await courseMockService.generateCourse(condition)
    if (ticket !== viewEpoch) return
    result.value = generated
    recommendedAccommodations.value = []
    editing.value = false
    rememberCurrent()
    void loadCarRoute()
    if (!condition.accommodation) {
      accommodationLoading.value = true
      accommodationError.value = ''
      void courseMockService.getRecommendedAccommodations(result.value).then((items) => {
        if (ticket === viewEpoch) recommendedAccommodations.value = items
      }).catch(() => {
        if (ticket !== viewEpoch) return
        recommendedAccommodations.value = []
        accommodationError.value = '주변 숙소를 불러오지 못했어요.'
      }).finally(() => {
        if (ticket === viewEpoch) accommodationLoading.value = false
      })
    }
    requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  } catch (generationFailure) {
    if (ticket === viewEpoch) error.value = courseGenerationErrorMessage(generationFailure)
  } finally {
    if (ticket === viewEpoch) loading.value = false
  }
}

async function selectRecommendedAccommodation(accommodation: AccommodationInput) {
  if (!result.value || !canModify.value) return
  loading.value = true
  error.value = ''
  try {
    const savedAccommodation = await courseMockService.updateAccommodation(
      result.value,
      accommodation,
    )
    condition.accommodation = { ...savedAccommodation }
    result.value = courseMockService.applyAccommodationSelection(
      result.value,
      savedAccommodation,
    )
    delete result.value.car_route
    void loadCarRoute()
    recommendedAccommodations.value = []
    accommodationPickerOpen.value = false
    rememberCurrent()
  } catch {
    error.value = '숙소를 저장하지 못했어요. 기존 일정은 그대로 유지됩니다.'
  } finally {
    loading.value = false
  }
}

async function chooseAccommodation() {
  if (!result.value || !canModify.value || accommodationLoading.value) return
  const course = result.value
  const ticket = viewEpoch
  accommodationPickerOpen.value = true
  accommodationLoading.value = true
  accommodationError.value = ''
  try {
    const items = await courseMockService.getRecommendedAccommodations(course)
    if (ticket === viewEpoch && result.value?.id === course.id) recommendedAccommodations.value = items
  } catch {
    if (ticket === viewEpoch) accommodationError.value = '주변 숙소를 불러오지 못했어요. 기존 숙소는 유지됩니다.'
  } finally {
    if (ticket === viewEpoch) accommodationLoading.value = false
  }
}

async function openAlternatives(item: CourseItem) {
  if (!result.value || !canSwap.value) return
  selected.value = item
  alternatives.value = []
  altNotice.value = ''
  altLoading.value = true
  try {
    alternatives.value = await courseMockService.getAlternativePlaces(result.value, item.id, condition)
  } catch (failure) {
    // 3401 = 그 날짜 혼잡 예보 없음. 빈 목록으로 뭉개지 않고 이유를 보여준다(정직성)
    altNotice.value = failure instanceof ApiError && Number(failure.code) === 3401
      ? '이 날짜의 혼잡 예보가 아직 없어 대안을 고를 수 없어요.'
      : '대안을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'
  } finally {
    altLoading.value = false
  }
}

async function replace(alternative: AlternativePlace) {
  if (!result.value || !selected.value || swapping.value || !canSwap.value) return
  const previousAverage = result.value.average_congestion_rate
  const replacementName = alternative.place_name
  swapping.value = true
  altNotice.value = ''
  try {
    result.value = await courseMockService.replaceCourseItem(result.value, selected.value.id, alternative)
  } catch (failure) {
    // 서버 메시지(중복 장소·권한·예보 없음)를 모달 안에 보여준다 - 토스트는 모달 뒤에 가려진다. 기존 일정은 그대로
    altNotice.value = failure instanceof ApiError ? `바꾸지 못했어요. ${failure.message}` : '장소를 바꾸지 못했어요. 기존 일정은 그대로예요.'
    return
  } finally {
    swapping.value = false
  }
  selected.value = undefined
  rememberCurrent()
  // 렌터카 경로는 교체된 장소 기준으로 다시 받는다 - 숙소 변경과 같은 처리
  void loadCarRoute()
  toast.value = previousAverage != null && result.value.average_congestion_rate != null
    ? `${replacementName}으로 변경했어요. 평균 혼잡도는 ${congestionLabel(result.value.average_congestion_rate)}이에요.`
    : `${replacementName}으로 변경하고 동선을 다시 계산했어요.`
  setTimeout(() => { toast.value = '' }, 2600)
}

async function openReschedule(item: CourseItem) {
  if (!result.value || !canSwap.value) return
  rescheduleSelected.value = item
  rescheduleOptions.value = []
  rescheduleLoading.value = true
  try {
    rescheduleOptions.value = await courseMockService.getQuieterTimeOptions(result.value, item.id)
  } finally {
    rescheduleLoading.value = false
  }
}

async function reschedule(option: CongestionRescheduleOption) {
  if (!result.value || !rescheduleSelected.value) return
  const item = rescheduleSelected.value
  result.value = await courseMockService.rescheduleCourseItem(result.value, item.id, option)
  rescheduleSelected.value = undefined
  toast.value = `${item.place_name} 방문 시간을 ${formatDate(option.visit_date)} ${option.start_time}로 변경했어요. 변경 시간대는 ${congestionLabel(option.congestion_rate)}으로 예상돼요.`
  setTimeout(() => { toast.value = '' }, 3200)
}

function openSave() {
  if (!canModify.value) return
  title.value = result.value?.title || ''
  saveError.value = ''
  saveOpen.value = true
}

async function save() {
  const clean = title.value.trim()
  if (!result.value || clean.length < 1 || clean.length > 100 || saveLoading.value) return
  saveLoading.value = true
  saveError.value = ''
  try {
    if (!auth.isAuthenticated) {
      storePendingCourseClaim(result.value, condition, clean)
      saveOpen.value = false
      await router.push({ path: '/login', query: { redirect: '/ai-course' } })
      return
    }
    result.value = await courseMockService.saveCourse(result.value, clean)
    renewal.cancel(); clearCourseProof(result.value)
    rememberCurrent()
    saveOpen.value = false
    toast.value = '코스를 저장했어요.'
  } catch (saveFailure) {
    saveError.value = saveFailure instanceof Error ? saveFailure.message : '코스를 저장하지 못했어요.'
  } finally {
    saveLoading.value = false
  }
}

async function restoreJobResult () {
  const id = route.query.job
  if (typeof id !== 'string' || !ASYNC_COURSES_ENABLED) return false
  if (!auth.isAuthenticated) {
    await router.replace({ name: 'login', query: { redirect: route.fullPath } })
    return true
  }
  const ticket = ++viewEpoch
  restoration.cancel(); renewal.cancel(); loading.value = true; jobResultError.value = ''
  try {
    if (!/^[a-f\d]{8}(-[a-f\d]{4}){3}-[a-f\d]{12}$/i.test(id)) throw new Error('INVALID_JOB')
    const response = await getGenerationResult(id)
    if (ticket !== viewEpoch) return true
    // 알림은 며칠 뒤에도 열 수 있다. 생성 당시 JSON 대신 현재 저장/교체된 일정으로 복원한다.
    const current = await restoration.restore({ mode: 'result', courseId: response.course.id,
      condition: response.request, claim_token: response.course.claim_token,
      claim_expires_at: response.course.claim_expires_at }, true)
    if (ticket !== viewEpoch) return true
    if (!current) throw new Error('RESULT_UNAVAILABLE')
    if (current.status === 'READY') {
      current.claim_token = response.course.claim_token
      current.claim_expires_at = response.course.claim_expires_at
    }
    result.value = current
    Object.assign(condition, response.request, { accommodation: current.accommodation ?? undefined })
    editing.value = false; error.value = ''; rememberCurrent(); void loadCarRoute()
  } catch {
    if (ticket === viewEpoch) { jobResultError.value = '생성 결과를 불러오지 못했어요. 다시 조회하거나 작업 상태를 확인해 주세요.' }
  } finally { if (ticket === viewEpoch) loading.value = false }
  return true
}
watch(() => route.query.job, (id, previous) => {
  viewEpoch++; routeEpoch++; jobResultError.value = ''
  if (id) void restoreJobResult()
  else if (previous) { loading.value = false; editing.value = true; result.value = undefined }
})
watch(() => (auth.user as { userId: number } | null)?.userId, () => {
  viewEpoch++; routeEpoch++; restoration.cancel(); renewal.cancel()
  if (route.query.job) { result.value = undefined; editing.value = true; loading.value = false; void restoreJobResult() }
})
onMounted(async () => {
  clock = setInterval(() => { now.value = Date.now() }, 1000)
  if (await restoreJobResult()) return
  const pending = auth.isAuthenticated ? takePendingCourseClaim() : null
  if (!pending) { await restoreResult(); return }

  saveLoading.value = true
  try {
    result.value = await courseMockService.saveCourse(pending.course, pending.title)
    renewal.cancel(); clearCourseProof(result.value)
    Object.assign(condition, pending.condition)
    editing.value = false
    rememberCurrent()
    void loadCarRoute()
    toast.value = '코스를 저장했어요.'
  } catch {
    error.value = '로그인 전 생성한 코스를 저장하지 못했어요. 다시 생성해 주세요.'
  } finally {
    saveLoading.value = false
  }
})

const formatDate = (value: string) => formatCalendarDate(value)
const formatShortDate = (value: string) => formatCalendarDate(value, { month: 'numeric', day: 'numeric' })

const formatDistance = (metres?: number | null) => metres == null ? '정보 없음' : `${(metres / 1000).toFixed(1)}km`
</script>

<template>
  <div class="ai-course-page">
    <header class="course-page-header">
      <div>
        <span>AI 코스 만들기</span>
        <h1>{{ loading ? '제주 여행을 구성하고 있어요' : editing ? '나만의 제주 여행' : '추천 코스' }}</h1>
        <p>{{ loading ? '선택한 조건을 바탕으로 잠시만 기다려 주세요.' : editing ? '여행 조건을 선택하면 혼잡도와 동선을 고려해 코스를 추천해드려요.' : '선택한 조건과 예상 혼잡도를 반영한 제주 여행 일정이에요.' }}</p>
      </div>
    </header>

    <section v-if="jobResultError" class="course-shell generation-state" role="alert">
      <h2>{{ jobResultError }}</h2>
      <button class="btn" @click="restoreJobResult">결과 다시 불러오기</button>
      <RouterLink class="btn" :to="{ name: 'course-generation-job', params: { jobId: String(route.query.job) } }">작업 상태 확인</RouterLink>
    </section>
    <section v-else-if="restoring || restoreError" class="course-shell generation-state" aria-live="polite">
      <h2>{{ restoring ? '저장된 코스를 다시 불러오고 있어요.' : restoreError }}</h2>
      <button v-if="!restoring && !terminal" class="btn" @click="restoreResult">다시 불러오기</button>
      <button class="btn" @click="editConditions">새 코스 만들기</button>
    </section>
    <section v-else-if="loading" class="course-shell generation-state" aria-live="polite">
      <div class="generation-spinner" aria-hidden="true" />
      <span class="result-label">AI 코스 생성 중</span>
      <h2>여행 조건을 분석하고 있어요.</h2>
      <p>혼잡도와 이동 동선을 고려해 코스를 만들고 있어요.</p>
    </section>

    <section v-else-if="editing" class="course-shell">
      <GenerationJobs />
      <CourseConditionForm :initial="condition" :loading="loading" @submit="generate" @draft="draft => Object.assign(condition, draft, { accommodation: draft.accommodation })" />
      <p v-if="error" class="course-error">{{ error }} <button class="text-link" @click="generate(condition)">다시 시도</button></p>
    </section>

    <section v-else-if="result" class="course-shell result-shell">
      <header class="course-result-head">
        <div class="result-heading-copy">
          <span class="result-label">추천 코스</span>
          <h2>{{ tripNights }}박 {{ tripDays }}일 제주 여행</h2>
          <p>{{ formatShortDate(result.start_date) }} ~ {{ formatShortDate(result.end_date) }} · {{ result.people }}명 · {{ transportLabel[result.transport] }}</p>
          <div class="result-condition-tags">
            <span v-if="regionSummary">{{ regionSummary }}</span>
            <span v-if="styleSummary">{{ styleSummary }}</span>
          </div>
          <div class="result-metrics">
            <span>평균 혼잡도 <b>{{ congestionLabel(result.average_congestion_rate) }}</b></span>
            <span>예상 비용 <b>{{ estimatedCost }}</b></span>
          </div>
        </div>
        <div class="result-actions-row">
          <button class="btn" @click="viewOnMap">지도에서 보기</button>
          <button class="btn result-save" :disabled="result.status === 'SAVED' || !canModify" @click="openSave">{{ result.status === 'SAVED' ? '저장 완료' : '코스 저장' }}</button>
        </div>
      </header>

      <p v-if="renewing" class="route-status">코스 저장 증명을 갱신하고 있어요.</p>
      <p v-else-if="renewalNotice" class="route-status">{{ renewalNotice }}</p>
      <p v-else-if="!canModify && result.status !== 'SAVED'" class="route-status">유효한 코스 저장 증명이 없어 저장·숙소 변경을 사용할 수 없어요. 일정 조회는 유지됩니다.</p>
      <div class="course-result-grid">
        <main>
          <section v-for="day in result.days" :key="day.day_no" class="course-day">
            <header><b>DAY {{ day.day_no }}</b><span>{{ formatDate(day.visit_date) }}</span></header>
            <p v-for="weather in dayWeatherLabels(day.items)" :key="weather" class="daily-weather">{{ weather }}</p>
            <TransitDayRoute v-if="result.transport === 'PUBLIC_TRANSIT'" :day="transitData?.days.find(d => d.day_no === day.day_no)" :loading="transitLoading" :error="transitError" />
            <p v-if="result.transport === 'RENTAL_CAR'" class="route-summary">
              총 이동 {{ routeSummary([routeForDay(day.day_no) ?? {}], routeLoading) }}
            </p>
            <div class="day-timeline">
              <p v-for="notice in accessNotices(routeForDay(day.day_no) ? [routeForDay(day.day_no)!] : [])" :key="notice" class="route-status">{{ notice }}</p>
              <TransitLegCard v-if="result.transport === 'PUBLIC_TRANSIT' && result.accommodation && day.items.length" :leg="transitLeg(day.day_no, 'ACCOMMODATION')" :from="result.accommodation.place_name" :to="day.items[0]!.place_name" :loading="transitLoading" />
              <div v-if="result.accommodation && result.transport !== 'PUBLIC_TRANSIT'" class="travel-line"><span>↓</span> 숙소 출발 · {{ result.accommodation.place_name }}<template v-if="day.accommodation_departure_travel_minutes"> · {{ transportLabel[result.transport] }} {{ day.accommodation_departure_travel_minutes }}분 · {{ formatDistance(day.accommodation_departure_distance_m) }}</template></div>
              <template v-for="(item,itemIndex) in day.items" :key="item.id">
                <div v-if="inboundRoute(day.day_no, item.id)" class="travel-line">
                  <span>↓</span> 이동 약 {{ formatDuration(inboundRoute(day.day_no, item.id)?.duration_seconds) }} ·
                  {{ formatDistance(inboundRoute(day.day_no, item.id)?.distance_meters) }}
                </div>
                <CourseItemCard :item="item" :transport="result.transport" :readonly="!canSwap" @alternative="openAlternatives" @reschedule="openReschedule" />
                <TransitLegCard v-if="result.transport === 'PUBLIC_TRANSIT' && (day.items[itemIndex + 1] || result.accommodation)" :leg="transitLeg(day.day_no, `ITEM:${item.id}`)" :from="item.place_name" :to="day.items[itemIndex + 1]?.place_name ?? result.accommodation!.place_name" :loading="transitLoading" />
              </template>
              <div v-if="result.accommodation && result.transport !== 'PUBLIC_TRANSIT'" class="travel-line"><span>↓</span> 숙소 복귀 · {{ result.accommodation.place_name }}<template v-if="day.accommodation_return_travel_minutes"> · {{ transportLabel[result.transport] }} {{ day.accommodation_return_travel_minutes }}분 · {{ formatDistance(day.accommodation_return_distance_m) }}</template></div>
            </div>
          </section>
          <p v-if="routeLoading" class="route-status">자동차 이동 경로를 불러오는 중이에요.</p>
          <p v-else-if="routeError" class="course-error">{{ routeError }}</p>
        </main>

        <aside class="course-side">
          <BudgetGauge :summary="result.budget_summary" />
          <section class="course-summary-card">
            <span class="summary-kicker">TRIP SUMMARY</span>
            <h3>코스 요약</h3>
            <dl>
              <dt>여행 일정</dt><dd>{{ tripNights }}박 {{ tripDays }}일</dd>
              <dt>방문 장소</dt><dd>{{ visitCount }}곳</dd>
              <dt>평균 혼잡도</dt><dd>{{ congestionLabel(result.average_congestion_rate) }}</dd>
              <dt>예상 비용</dt><dd>{{ estimatedCost }}</dd>
              <dt>전체 예산</dt><dd>{{ result.budget_total?.toLocaleString() }}원</dd>
              <dt>이동수단</dt><dd>{{ transportLabel[result.transport] }}</dd>
              <dt>숙소</dt><dd>{{ result.accommodation?.place_name ?? '미정' }}</dd>
            </dl>
          </section>
          <button v-if="canModify" class="btn" :disabled="accommodationLoading" @click="chooseAccommodation">{{ result.accommodation ? '숙소 변경' : '숙소 찾아보기' }}</button>
          <AccommodationRecommendations
            v-if="canModify && (!result.accommodation || accommodationPickerOpen)"
            :items="recommendedAccommodations"
            :loading="accommodationLoading"
            :error="accommodationError"
            @select="selectRecommendedAccommodation"
          />
        </aside>
      </div>

      <div class="result-actions">
        <button class="btn" @click="editConditions">조건 수정</button>
        <button class="btn primary" :disabled="loading" @click="editConditions">다른 코스 만들기</button>
      </div>
    </section>

    <AlternativePlaceModal v-if="selected" :item="selected" :alternatives="alternatives" :loading="altLoading" :notice="altNotice" :busy="swapping" @close="selected = undefined" @select="replace" />
    <CongestionRescheduleModal v-if="rescheduleSelected" :item="rescheduleSelected" :options="rescheduleOptions" :loading="rescheduleLoading" @close="rescheduleSelected = undefined" @select="reschedule" />
    <div v-if="saveOpen" class="modal-backdrop" @click.self="saveOpen = false">
      <section class="course-modal save-modal">
        <button class="modal-close" @click="saveOpen = false">×</button>
        <h2>코스 저장</h2>
        <label>코스명<input v-model="title" maxlength="100" placeholder="1~100자"></label>
        <p v-if="!title.trim()" class="course-error">공백이 아닌 코스명을 입력해 주세요.</p>
        <p v-if="saveError" class="course-error">{{ saveError }}</p>
        <button class="btn primary wide" :disabled="!title.trim() || saveLoading" @click="save">{{ saveLoading ? '저장 중…' : '저장' }}</button>
      </section>
    </div>
    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<style scoped>
@media (max-width: 767px) {
  .course-result-grid {
    display: flex;
    flex-direction: column;
  }

  .course-side {
    display: contents;
  }

  .course-result-grid > main {
    order: 2;
    min-width: 0;
    width: 100%;
  }

  :deep(.accommodation-recommendations) {
    order: 1;
    min-width: 0;
    width: 100%;
  }

  :deep(.budget) {
    order: 3;
    min-width: 0;
    width: 100%;
  }

  .course-summary-card {
    order: 4;
    min-width: 0;
    width: 100%;
  }
}
</style>
