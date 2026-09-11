<script setup lang="ts">
// 코스 상세 (담당: 정동현) - 백엔드 GET /courses/{id} 실데이터만 그린다.
// 예전에는 문자열 id('sample-aewol')로 목업 코스를 그렸는데, 없는 id에 가짜 코스가 떠서 걷어냈다.
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import TripConfirmation from '../../components/course/TripConfirmation.vue'
import MapRenderer from '../../components/map/MapRenderer.vue'
import PlaceImage from '../../components/common/PlaceImage.vue'
import AlternativePlaceModal from '../../components/course/AlternativePlaceModal.vue'
import { ApiError } from '../../api/errors.js'
import CourseService, { type CourseDetail, type CourseDetailItem } from '../../services/CourseService'
import type { CongestionLevel, Place } from '../../assets/types'
import type { AlternativePlace, CourseItem } from '../../assets/types/course'

const route = useRoute()
const router = useRouter()
const courseId = String(route.params.courseId ?? '')
const editing = ref(false)
const live = ref<CourseDetail | null>(null)
const loading = ref(true)

// 장소 교체(#과밀지역 우회) - AI 코스 화면과 같은 대안 모달·같은 API를 쓴다
const swapTarget = ref<Stop | null>(null)
const alternatives = ref<AlternativePlace[]>([])
const altLoading = ref(false)
const altNotice = ref('')
const swapping = ref(false)
const toast = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

onMounted(async () => {
  live.value = await CourseService.getCourseDetail(courseId)
  loading.value = false
})

interface Stop {
  key: string
  name: string
  image: string | null
  timeLabel: string
  /** "이동 12분 · 6.6km · 근거" */
  metaLabel: string
  level: CongestionLevel | null
  /** 장소 상세 페이지(/places/<placeId>) - 백엔드 id가 없으면 null */
  detailPath: string | null
  /** 장소 교체가 백엔드 item id·날짜를 알아야 한다 */
  liveItem: CourseDetailItem
  dayNo: number
  visitDate: string
}

interface CourseView {
  title: string
  conditionLabel: string
  highlight: string
  budgetLabel: string
  averageText: string
  dayCount: number
  placeCount: number
  days: Array<{ day: number, label: string, stops: Stop[] }>
  mapPlaces: Place[]
  /** 저장 시점과 지금 예보가 달라졌을 때의 안내 문구 */
  forecastNote: string | null
  editable: boolean
}

const fmtDate = (iso: string) => {
  const [, m, d] = iso.split('-')
  return `${Number(m)}월 ${Number(d)}일`
}

const distanceText = (meters: number) =>
  meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`

/**
 * 지도는 좌표·이름·등급만 읽는다. 상세 조회가 주지 않는 텍스트 필드는 빈 값으로 둔다 -
 * 없는 설명을 지어내지 않는다.
 */
const toMapPlace = (name: string, id: number, lat: number | null, lng: number | null,
                    level: CongestionLevel | null, rate: number | null): Place => ({
  id: String(id),
  name,
  region: '',
  category: '',
  address: '',
  description: '',
  score: rate ?? 0,
  level: level ?? 'QUIET',
  time: '',
  stay: '',
  cost: '',
  image: '',
  tags: [],
  latitude: lat ?? undefined,
  longitude: lng ?? undefined,
})

const fromLive = (course: CourseDetail): CourseView => {
  const days = course.days.map(day => ({
    day: day.dayNo,
    label: fmtDate(day.visitDate),
    stops: day.items.map(item => {
      const move = item.inboundTravelMinutes != null && item.inboundDistanceM != null
        ? `이동 약 ${item.inboundTravelMinutes}분 · ${distanceText(item.inboundDistanceM)}`
        : null
      const swapped = item.replacedFromPlaceName
        ? `${item.replacedFromPlaceName}에서 바꾼 곳`
        : null
      return {
        key: `live-${item.id}`,
        name: item.placeName,
        image: item.imageUrl,
        timeLabel: item.startTime?.slice(0, 5) ?? `${item.position}번째`,
        metaLabel: [move, swapped ?? item.reason].filter(Boolean).join(' · '),
        level: item.congestionLevel,
        detailPath: item.placeId != null ? `/places/${item.placeId}` : null,
        liveItem: item,
        dayNo: day.dayNo,
        visitDate: day.visitDate,
      }
    }),
  }))
  // 저장 시점과 지금 예보가 벌어졌으면 알려준다 - 예보는 매일 갱신된다
  const gap = course.averageRate != null && course.plannedAverageRate != null
    ? Math.round(Math.abs(course.averageRate - course.plannedAverageRate))
    : 0
  return {
    title: course.title ?? '이름 없는 코스',
    conditionLabel: course.conditionLabel,
    highlight: `${fmtDate(course.startDate)} 출발 · ${course.durationText}`,
    budgetLabel: course.budgetLabel,
    averageText: course.averageRate != null
      ? `${Math.round(course.averageRate)} · ${course.levelLabel}`
      : '혼잡 정보 없음',
    dayCount: course.days.length,
    placeCount: course.days.reduce((sum, day) => sum + day.items.length, 0),
    days,
    mapPlaces: course.days.flatMap(day => day.items.map(item =>
      toMapPlace(item.placeName, item.placeId, item.latitude, item.longitude,
        item.congestionLevel, item.congestionRate))),
    forecastNote: gap >= 5
      ? `저장할 때 평균 ${Math.round(course.plannedAverageRate as number)}였는데 지금 예보는 ${Math.round(course.averageRate as number)}예요`
      : null,
    editable: course.swappable,
  }
}

const view = computed<CourseView | null>(() => (live.value ? fromLive(live.value) : null))

/** 대안 모달은 AI 코스 화면의 CourseItem 모양을 받는다 - 상세 응답에서 그 모양으로 옮긴다(없는 값은 비운다) */
const modalItem = computed<CourseItem | null>(() => {
  const stop = swapTarget.value
  if (!stop || !live.value) return null
  const item = stop.liveItem
  return {
    id: item.id,
    course_id: Number(live.value.id),
    place_id: item.placeId,
    place_name: item.placeName,
    category_name: item.categoryName,
    image_url: item.imageUrl ?? undefined,
    day_no: stop.dayNo,
    position: item.position,
    visit_date: stop.visitDate,
    start_time: item.startTime ?? undefined,
    item_source: item.replacedFromPlaceName ? 'REPLACEMENT' : 'AI_RECOMMENDED',
    inbound_distance_m: item.inboundDistanceM ?? undefined,
    inbound_travel_minutes: item.inboundTravelMinutes ?? undefined,
    congestion_rate: item.congestionRate ?? undefined,
    congestion_level: item.congestionLevel ?? undefined,
    recommendation_reason: item.reason ?? undefined,
    costs: [],
  }
})

const showToast = (text: string) => {
  toast.value = text
  if (toastTimer) clearTimeout(toastTimer)   // 연달아 뜨면 앞 타이머가 새 토스트를 지운다
  toastTimer = setTimeout(() => { toast.value = '' }, 2600)
}

/** 코스 상세는 공개 경로라 주소만 복사하면 된다. 복사가 안 되면 안 됐다고 말한다 - 성공을 지어내지 않는다 */
async function shareCourse () {
  const url = window.location.href
  try {
    if (!navigator.clipboard) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(url)
    showToast('코스 링크를 복사했어요.')
  } catch {
    showToast('복사하지 못했어요. 주소창의 주소를 직접 복사해 주세요.')
  }
}

async function openSwap (stop: Stop) {
  if (!live.value) return
  swapTarget.value = stop
  alternatives.value = []
  altNotice.value = ''
  altLoading.value = true
  const exclude = live.value.days.flatMap(day => day.items)
    .filter(item => item.id !== stop.liveItem.id)
    .map(item => item.placeId)
  try {
    alternatives.value = await CourseService.getAlternatives(stop.liveItem.placeId, stop.visitDate, exclude)
  } catch (failure) {
    // 3401 = 그 날짜 혼잡 예보 없음 - 빈 목록으로 뭉개지 않고 이유를 보여준다(정직성)
    altNotice.value = failure instanceof ApiError && Number(failure.code) === 3401
      ? '이 날짜의 혼잡 예보가 아직 없어 대안을 고를 수 없어요.'
      : '대안을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.'
  } finally {
    altLoading.value = false
  }
}

async function applySwap (alternative: AlternativePlace) {
  const target = swapTarget.value?.liveItem
  if (!target || !live.value || swapping.value) return   // 더블클릭이면 두 번째 스왑이 첫 교체를 '원래 장소'로 덮는다
  swapping.value = true
  altNotice.value = ''
  try {
    const summary = await CourseService.swapItem(courseId, target.id, alternative.place_id, live.value.manageable)
    // 서버가 교체 칸과 다음 칸 이동, 평균을 다시 계산했으니 상세를 다시 읽는다 - 로컬에서 흉내 내지 않는다
    const refreshed = await CourseService.getCourseDetail(courseId)
    swapTarget.value = null
    if (refreshed) {
      live.value = refreshed
      showToast(summary.levelLabel
        ? `${alternative.place_name}(으)로 바꿨어요. 평균 혼잡도는 ${summary.levelLabel}이에요.`
        : `${alternative.place_name}(으)로 바꿨어요.`)
    } else {
      // 교체는 됐는데 재조회가 실패 - 화면을 비우지 않고 알린다
      showToast(`${alternative.place_name}(으)로 바꿨지만 최신 일정을 불러오지 못했어요. 새로고침해 주세요.`)
    }
  } catch (failure) {
    // 서버 메시지(중복 장소·권한·예보 없음)는 모달 안에 - 토스트는 모달 뒤에 가려진다
    altNotice.value = failure instanceof ApiError ? `바꾸지 못했어요. ${failure.message}` : '장소를 바꾸지 못했어요. 기존 일정은 그대로예요.'
  } finally {
    swapping.value = false
  }
}
</script>

<template>
  <section
    v-if="loading"
    class="page"
  >
    <p class="muted">
      코스를 불러오는 중이에요
    </p>
  </section>

  <section
    v-else-if="view"
    class="page"
  >
    <div class="page-head">
      <div>
        <span class="eyebrow">SAVED COURSE · {{ view.dayCount }} DAYS</span>
        <h1>{{ view.title }}</h1>
        <p>{{ view.conditionLabel }}</p>
        <p class="muted course-highlight">
          {{ view.highlight }}
        </p>
        <p
          v-if="view.forecastNote"
          class="muted course-highlight"
        >
          {{ view.forecastNote }}
        </p>
      </div>
      <div class="actions">
        <button
          class="btn ghost"
          @click="router.push(`/map?course=${courseId}`)"
        >
          지도에서 보기
        </button>
        <button
          class="btn ghost"
          @click="shareCourse"
        >
          링크 공유
        </button>
      </div>
    </div>
    <TripConfirmation v-if="live?.manageable && live.status === 'SAVED'" :course-id="courseId" />
    <div class="metrics panel">
      <div>
        <small>예상 비용</small><b>{{ view.budgetLabel }}</b>
      </div>
      <div>
        <small>평균 혼잡도</small><b>{{ view.averageText }}</b>
      </div>
      <div>
        <small>방문 장소</small><b>{{ view.placeCount }}곳</b>
      </div>
      <div>
        <small>일정</small><b>{{ view.dayCount }}일</b>
      </div>
    </div>

    <div class="course-detail-grid">
      <div class="panel course-days">
        <section
          v-for="day in view.days"
          :key="day.day"
          class="course-day-section"
        >
          <div class="row course-day-head">
            <h2>DAY {{ day.day }} · {{ day.label }}</h2>
            <button
              v-if="view.editable"
              class="text-link"
              @click="editing = !editing"
            >
              {{ editing ? '수정 완료' : '일정 수정' }}
            </button>
          </div>

          <div class="simple-timeline">
            <div
              v-for="stop in day.stops"
              :key="stop.key"
            >
              <span>{{ stop.timeLabel }}</span>
              <!-- 사진이 없어도 자리를 비우지 않는다 - 3열 그리드가 밀려 이름이 세로로 접힌다 -->
              <PlaceImage
                :src="stop.image ?? '/images/placeholder.svg'"
                :alt="stop.name"
              />
              <div class="course-stop-content">
                <div class="course-stop-head">
                  <h3>{{ stop.name }}</h3>
                  <RouterLink
                    v-if="stop.detailPath"
                    class="place-detail-link"
                    :to="stop.detailPath"
                    :aria-label="`${stop.name} 상세 페이지 보기`"
                  >
                    상세 보기
                  </RouterLink>
                </div>
                <p v-if="stop.metaLabel">
                  {{ stop.metaLabel }}
                </p>
                <CongestionBadge
                  v-if="stop.level"
                  :level="stop.level"
                />
                <div
                  v-if="editing"
                  class="edit-actions"
                >
                  <!-- 시간 변경은 백엔드 API가 없어 두지 않는다 - 눌러도 아무 일 없는 버튼을 만들지 않는다 -->
                  <button @click="openSwap(stop)">
                    장소 교체
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <aside>
        <MapRenderer
          :places="view.mapPlaces"
          show-route
        />
        <p class="route-note">
          장소 간 추천 순서를 나타낸 선이며 실제 도로 경로와 다를 수 있습니다.
        </p>
        <div class="panel compact">
          <h3>코스 정보</h3>
          <dl>
            <dt>일정</dt><dd>{{ view.dayCount }}일</dd>
            <dt>장소</dt><dd>{{ view.placeCount }}곳</dd>
            <dt>평균 혼잡도</dt><dd>{{ view.averageText }}</dd>
          </dl>
        </div>
      </aside>
    </div>
  </section>

  <section
    v-else
    class="page course-not-found"
  >
    <div class="panel">
      <span class="eyebrow">COURSE NOT FOUND</span>
      <h1>코스를 찾을 수 없어요</h1>
      <p class="muted">
        저장 목록에서 코스를 다시 선택해 주세요.
      </p>
      <RouterLink
        class="btn primary"
        to="/courses"
      >
        저장 코스로 돌아가기
      </RouterLink>
    </div>
  </section>

  <!-- v-if/v-else 형제 체인 뒤에 둔다 - 사이에 끼우면 v-else가 끊긴다 -->
  <AlternativePlaceModal
    v-if="modalItem"
    :item="modalItem"
    :alternatives="alternatives"
    :loading="altLoading"
    :notice="altNotice"
    :busy="swapping"
    @close="swapTarget = null"
    @select="applySwap"
  />
  <div
    v-if="toast"
    class="toast"
  >
    {{ toast }}
  </div>
</template>

<style scoped>
.course-highlight{margin-top:8px}
.course-days{display:grid;gap:32px}
.course-day-section+.course-day-section{padding-top:6px;border-top:1px solid var(--border)}
.course-day-head{margin-bottom:2px}
.course-day-head h2{margin:0}
.simple-timeline :deep(.place-image){width:110px;height:100px;object-fit:cover;border-radius:12px;background:var(--muted)}
.course-stop-content{min-width:0}
.course-stop-head{display:flex;align-items:center;justify-content:space-between;gap:12px}
.course-stop-head h3{min-width:0}
.place-detail-link{flex-shrink:0;padding:7px 11px;border:1px solid var(--border);border-radius:10px;
  background:var(--muted);color:var(--primary);font-size:.75rem;font-weight:800;line-height:1}
.place-detail-link:hover{border-color:var(--primary);background:#e7f3ee}
.course-not-found{max-width:720px;text-align:center}
.course-not-found .panel{padding:64px 30px}
.course-not-found .btn{margin-top:20px}
@media(max-width:767px){
  .course-stop-head{align-items:flex-start}
  .place-detail-link{padding:6px 8px}
  .course-day-head{align-items:flex-start}
}
</style>
