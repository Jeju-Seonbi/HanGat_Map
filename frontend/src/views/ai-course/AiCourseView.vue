<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../app/stores/auth'
import CourseConditionForm from '../../components/course/CourseConditionForm.vue'
import CourseItemCard from '../../components/course/CourseItemCard.vue'
import BudgetGauge from '../../components/course/BudgetGauge.vue'
import AlternativePlaceModal from '../../components/course/AlternativePlaceModal.vue'
import CongestionRescheduleModal from '../../components/course/CongestionRescheduleModal.vue'
import AccommodationRecommendations from '../../components/course/AccommodationRecommendations.vue'
import { courseMockService } from '../../services/courseMockService'
import { accommodationMockService } from '../../services/accommodationMockService'
import { courseApiService, courseGenerationErrorMessage } from '../../services/courseApiService'
import { adaptCourseGenerationResponse } from '../../services/courseGenerationAdapter'
import type {
  AccommodationInput,
  AccommodationRecommendation,
  AlternativePlace,
  CongestionRescheduleOption,
  CourseCondition,
  CourseItem,
  CourseItemView,
  CourseResult,
  CourseResultView,
} from '../../assets/types/course'

const now = new Date()
const later = new Date(now)
later.setDate(now.getDate() + 2)
const iso = (date: Date) => date.toISOString().slice(0, 10)

const condition = reactive<CourseCondition>({
  start_date: iso(now),
  end_date: iso(later),
  people: 2,
  budget_total: 400000,
  transport: 'RENTAL_CAR',
  course_regions: [],
  course_styles: [],
  course_place_preferences: [],
})

const result = ref<CourseResultView>()
const loading = ref(false)
const error = ref('')
const editing = ref(true)
const selected = ref<CourseItem>()
const alternatives = ref<AlternativePlace[]>([])
const altLoading = ref(false)
const rescheduleSelected = ref<CourseItem>()
const rescheduleOptions = ref<CongestionRescheduleOption[]>([])
const rescheduleLoading = ref(false)
const recommendedAccommodations = ref<AccommodationRecommendation[]>([])
const accommodationLoading = ref(false)
const saveOpen = ref(false)
const title = ref('')
const saveError = ref('')
const saveLoading = ref(false)
const toast = ref('')
const auth = useAuthStore()
const router = useRouter()

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
  if (result.value?.estimated_cost_min == null || result.value.estimated_cost_max == null) return '정보 없음'
  return `${result.value.estimated_cost_min.toLocaleString()} ~ ${result.value.estimated_cost_max.toLocaleString()}원`
})
const congestionLabel = (rate?: number) => rate == null ? '-' : rate < 35 ? '한산' : rate < 65 ? '보통' : '혼잡'
const isPersistedCourse = (course: CourseResultView): course is CourseResult => typeof course.id === 'number'
const isPersistedItem = (item: CourseItemView): item is CourseItem => typeof item.id === 'number'

function showUnavailable(message: string) {
  toast.value = message
  setTimeout(() => { toast.value = '' }, 3000)
}

async function generate(next: CourseCondition, _regenerate = false) {
  if (loading.value) return false
  Object.assign(condition, JSON.parse(JSON.stringify(next)) as CourseCondition)
  loading.value = true
  error.value = ''
  try {
    const response = await courseApiService.createCourse(condition)
    result.value = adaptCourseGenerationResponse(response, condition)
    recommendedAccommodations.value = []
    editing.value = false
    if (!condition.accommodation) {
      accommodationLoading.value = true
      void accommodationMockService.getRecommendedAccommodations(result.value).then((items) => {
        recommendedAccommodations.value = items
      }).finally(() => {
        accommodationLoading.value = false
      })
    }
    requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
    return true
  } catch (failure) {
    error.value = courseGenerationErrorMessage(failure)
    return false
  } finally {
    loading.value = false
  }
}

async function selectRecommendedAccommodation(accommodation: AccommodationInput) {
  Object.assign(condition, accommodationMockService.selectAccommodation(condition, accommodation))
  const generated = await generate(condition)
  if (!generated) delete condition.accommodation
}

async function openAlternatives(item: CourseItemView) {
  if (!result.value || !isPersistedCourse(result.value) || !isPersistedItem(item)) {
    showUnavailable('대안 장소 기능은 코스 저장 API 연결 후 사용할 수 있어요.')
    return
  }
  selected.value = item
  alternatives.value = []
  altLoading.value = true
  try {
    alternatives.value = await courseMockService.getAlternativePlaces(result.value, item.id, condition)
  } finally {
    altLoading.value = false
  }
}

async function replace(alternative: AlternativePlace) {
  if (!result.value || !isPersistedCourse(result.value) || !selected.value) return
  const previousAverage = result.value.average_congestion_rate
  const replacementName = alternative.place_name
  result.value = await courseMockService.replaceCourseItem(result.value, selected.value.id, alternative)
  selected.value = undefined
  toast.value = previousAverage != null && result.value.average_congestion_rate != null
    ? `${replacementName}으로 변경했어요. 평균 혼잡도는 ${congestionLabel(result.value.average_congestion_rate)}이에요.`
    : `${replacementName}으로 변경하고 동선과 비용을 다시 계산했어요.`
  setTimeout(() => { toast.value = '' }, 2600)
}

async function openReschedule(item: CourseItemView) {
  if (!result.value || !isPersistedCourse(result.value) || !isPersistedItem(item)) {
    showUnavailable('재배치 기능은 코스 저장 API 연결 후 사용할 수 있어요.')
    return
  }
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
  if (!result.value || !isPersistedCourse(result.value) || !rescheduleSelected.value) return
  const item = rescheduleSelected.value
  result.value = await courseMockService.rescheduleCourseItem(result.value, item.id, option)
  rescheduleSelected.value = undefined
  toast.value = `${item.place_name} 방문 시간을 ${formatDate(option.visit_date)} ${option.start_time}로 변경했어요. 변경 시간대는 ${congestionLabel(option.congestion_rate)}으로 예상돼요.`
  setTimeout(() => { toast.value = '' }, 3200)
}

function openSave() {
  if (!result.value || !isPersistedCourse(result.value)) {
    showUnavailable('코스 저장 기능은 저장 API 연결 후 사용할 수 있어요.')
    return
  }
  if (!auth.isAuthenticated) {
    alert('코스를 저장하려면 로그인이 필요해요.')
    router.push({ path: '/login', query: { redirect: '/ai-course' } })
    return
  }
  title.value = result.value.title || ''
  saveError.value = ''
  saveOpen.value = true
}

async function save() {
  const clean = title.value.trim()
  if (!result.value || !isPersistedCourse(result.value) || clean.length < 1 || clean.length > 100 || saveLoading.value) return
  saveLoading.value = true
  saveError.value = ''
  try {
    result.value = await courseMockService.saveCourse(result.value, clean)
    saveOpen.value = false
    toast.value = '코스를 저장했어요.'
  } catch (saveFailure) {
    saveError.value = saveFailure instanceof Error ? saveFailure.message : '코스를 저장하지 못했어요.'
  } finally {
    saveLoading.value = false
  }
}

const formatDate = (value: string) => new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  timeZone: 'UTC',
}).format(new Date(`${value}T00:00:00Z`))

const formatShortDate = (value: string) => new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  timeZone: 'UTC',
}).format(new Date(`${value}T00:00:00Z`))

const formatDistance = (metres?: number) => metres == null ? '' : `${(metres / 1000).toFixed(1)}km`
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

    <section v-if="loading" class="course-shell generation-state" aria-live="polite">
      <div class="generation-spinner" aria-hidden="true" />
      <span class="result-label">AI 코스 생성 중</span>
      <h2>여행 조건을 분석하고 있어요.</h2>
      <p>혼잡도와 이동 동선을 고려해 코스를 만들고 있어요.</p>
    </section>

    <section v-else-if="editing" class="course-shell">
      <CourseConditionForm :initial="condition" :loading="loading" @submit="generate" />
      <p v-if="error" class="course-error" role="alert">{{ error }} <button class="text-link" :disabled="loading" @click="generate(condition)">다시 시도</button></p>
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
        <button class="btn result-save" :disabled="!isPersistedCourse(result) || result.status === 'SAVED'" @click="openSave">{{ result.status === 'SAVED' ? '저장 완료' : '코스 저장' }}</button>
      </header>

      <div class="course-result-grid">
        <main>
          <section v-for="day in result.days" :key="day.day_no" class="course-day">
            <header><b>DAY {{ day.day_no }}</b><span>{{ formatDate(day.visit_date) }}</span></header>
            <div class="day-timeline">
              <div v-if="result.accommodation" class="travel-line"><span>↓</span> 숙소 출발 · {{ result.accommodation.place_name }}<template v-if="day.accommodation_departure_travel_minutes"> · {{ transportLabel[result.transport] }} {{ day.accommodation_departure_travel_minutes }}분 · {{ formatDistance(day.accommodation_departure_distance_m) }}</template></div>
              <CourseItemCard v-for="item in day.items" :key="item.id ?? item.candidate_id" :item="item" :transport="result.transport" :actions-available="isPersistedCourse(result)" @alternative="openAlternatives" @reschedule="openReschedule" />
              <div v-if="result.accommodation" class="travel-line"><span>↓</span> 숙소 복귀 · {{ result.accommodation.place_name }}<template v-if="day.accommodation_return_travel_minutes"> · {{ transportLabel[result.transport] }} {{ day.accommodation_return_travel_minutes }}분 · {{ formatDistance(day.accommodation_return_distance_m) }}</template></div>
            </div>
          </section>
        </main>

        <aside class="course-side">
          <BudgetGauge :budget="result.budget_total" :min="result.estimated_cost_min" :max="result.estimated_cost_max" :summary="result.cost_summary" />
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
          <AccommodationRecommendations
            v-if="!result.accommodation"
            :items="recommendedAccommodations"
            :loading="accommodationLoading"
            @select="selectRecommendedAccommodation"
          />
        </aside>
      </div>

      <div class="result-actions">
        <button class="btn" @click="editing = true">조건 수정</button>
        <button class="btn primary" :disabled="loading" @click="generate(condition, true)">다른 코스 만들기</button>
      </div>
    </section>

    <AlternativePlaceModal v-if="selected" :item="selected" :alternatives="alternatives" :loading="altLoading" @close="selected = undefined" @select="replace" />
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
