<script setup>
/* 지도 페이지 (MAP_001~009) — 패널 배치와 컴포넌트 사이 연결을 담당한다 */
import { ref, computed, watch, onMounted } from 'vue'
import MapCanvas from '@/components/map/MapCanvas.vue'
import FilterPanel from '@/components/map/FilterPanel.vue'
import DatePicker from '@/components/map/DatePicker.vue'
import PlaceDetail from '@/components/map/PlaceDetail.vue'
import CoursePanel from '@/components/map/CoursePanel.vue'
import PhotoLightbox from '@/components/map/PhotoLightbox.vue'
import { state, toast, loadPlaces, findPlaceById, loadFavorites } from '@/stores/mapStore'
import { useAuthStore } from '@/stores/auth'

import { at, iso, D0, FORECAST_DAYS } from '@/utils/date'
import { useRouter, useRoute } from 'vue-router'
import { popAiCourse, toMapCourse, toMapCourseFromDetail } from '@/services/map/CourseBridge'
import CourseService from '@/services/CourseService'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

/* MAP_009 찜 하트는 회원의 서버 찜 목록을 따른다. 세션 복원(앱 시작 직후엔 user 가 null 이었다가 채워진다)·
   로그인·로그아웃 어느 시점에든 맞도록 회원 ID 변화를 본다 */
watch(() => auth.user?.userId ?? null, id => loadFavorites(id), { immediate: true })
import { mapBridge } from '@/composables/mapBridge'
import MapToast from '@/components/map/MapToast.vue'

const lightbox = ref(null)

/* 열린 패널 수만큼 날짜 버튼이 오른쪽으로 비켜난다 */
const openCount = computed(() => (state.sel ? 1 : 0) + (state.course ? 1 : 0))

function openPlace(nameOrSpot) {
  const s = typeof nameOrSpot === 'string' ? state.layers.spot.find(x => x.n === nameOrSpot) : nameOrSpot
  if (!s) return
  if (!s) return
  mapBridge.panTo(s.y, s.x)
  state.sel = s
}
const closeDetail = () => { state.sel = null }

/* 열린 장소를 URL에 반영한다(?place=id) — 링크 복사·새로고침·공유가 이 값으로 복원된다.
   다른 파라미터(?course= 등)는 건드리지 않는다. 목업 장소는 id가 없어 쓰지 않는다 */
function syncPlaceURL() {
  const q = new URLSearchParams(location.search)
  q.delete('placeId')   // 구형 파라미터는 place 로 정규화한다 - 남기면 닫아도 새로고침에 되살아난다
  if (state.sel?.id != null) q.set('place', state.sel.id)
  else q.delete('place')
  const qs = q.toString()
  history.replaceState(null, '', qs ? '?' + qs : location.pathname)
}
watch(() => state.sel, syncPlaceURL)

function toggleCourse() {
  if (state.course) {
    state.course = null
    state.courseDay = 'all'
    history.replaceState(null, '', location.pathname)
    syncPlaceURL()   // 코스 URL을 지워도 열려 있는 장소는 남긴다
    return
  }
  // 샘플 생성기 대신 실 기능으로 안내한다 - 가짜 코스를 화면에 올리지 않는다
  router.push('/ai-course')
}

/** placeId → 적재 장소. 매칭되면 코스 핀 클릭 시 상세도 열린다 */
function placeFinder() {
  const byId = new Map(state.layers.spot.filter(p => p.id != null).map(p => [p.id, p]))
  return id => (id != null && byId.get(id)) || null
}

/** 변환된 코스를 화면에 올린다 - 슬라이더를 여행 시작일로(예보 밖이면 오늘 유지), 경로가 다 보이게 줌 */
function applyCourse(course) {
  state.course = course
  state.courseDay = 'all'
  const k = Math.round((new Date(course.startDate + 'T00:00:00') - D0) / 864e5)
  if (k >= 0 && k < FORECAST_DAYS) state.di = k
  const pts = course.stops.map(s => [s.o.y, s.o.x])
  if (pts.length > 1) mapBridge.fitPoints(pts, 12)
}

/** AI코스 페이지가 담아 둔 코스를 꺼내 그린다 (?course=ai) */
function loadAiCourse() {
  const raw = popAiCourse()
  if (!raw) return false
  applyCourse(toMapCourse(raw, placeFinder()))
  return true
}

/** 저장 코스를 URL의 id로 불러와 그린다 (?course=123) - 새로고침·링크 공유가 된다 */
async function loadSavedCourse(id) {
  const detail = await CourseService.getCourseDetail(id)
  if (!detail) { toast('코스를 불러오지 못했어요'); return false }
  applyCourse(toMapCourseFromDetail(detail, placeFinder()))
  return true
}

function loadFromURL() {
  const p = new URLSearchParams(location.search)
  const dp = p.get('d')
  /* 지난 날짜이거나 예보 범위 밖이면 무시하고 오늘로 둔다 */
  if (dp) {
    const k = Math.round((new Date(dp + 'T00:00:00') - D0) / 864e5)
    if (k >= 0 && k < FORECAST_DAYS) state.di = k
  }
  const r = p.get('r')
  if (r && ['전체', '동부', '서부', '남부', '북부'].includes(r)) state.F.reg = r
  if (p.get('b')) state.F.bud = +p.get('b') || state.F.bud
}

/** ?place=(공유 링크·마이페이지) 와 ?placeId=(장소 상세 페이지 링크) 둘 다 받는다 */
async function openPlaceFromURL() {
  const raw = route.query.place ?? route.query.placeId
  if (!/^\d+$/.test(raw ?? '')) return
  const { place, error } = await findPlaceById(+raw)
  if (place) openPlace(place)
  else if (error) toast('장소 정보를 불러오지 못했어요 — 새로고침해 주세요')
  else toast('공유받은 장소를 찾지 못했어요')
}

onMounted(async () => {
  // 장소·예보를 먼저 받아야 URL의 ?place= 로 들어온 장소를 찾을 수 있다
  await loadPlaces()
  // 링크 복원은 단계마다 따로 감싼다 - 코스가 깨져도 장소 딥링크까지 조용히 죽지 않게
  let courseDrawn = false
  try {
    if (route.query.course === 'ai') courseDrawn = loadAiCourse()
    if (!courseDrawn && /^\d+$/.test(route.query.course ?? '')) courseDrawn = await loadSavedCourse(route.query.course)
  } catch (e) {
    console.error('코스 링크 복원 실패', e)
    toast('링크의 코스를 그리지 못했어요')
  }
  if (!courseDrawn) loadFromURL()
  // 코스와 장소가 함께 온 링크도 있다(코스를 보다 장소를 열고 공유) - 코스를 그린 뒤 장소를 연다
  try {
    await openPlaceFromURL()
  } catch (e) {
    console.error('장소 링크 복원 실패', e)
    toast('공유받은 장소를 여는 중 문제가 생겼어요')
  }
})

const reload = () => location.reload()
</script>

<template>
  <div class="stage" :class="{ both: openCount === 2, 'sheet-open': openCount > 0 }">
    <MapCanvas @select="openPlace" @blank-click="closeDetail" />

    <!-- 장소를 하나도 못 받은 상태(백엔드 다운). 가짜 데이터로 채우지 않고 사실대로 알린다 -->
    <div v-if="!state.loading && !state.live" class="map-offline" role="alert">
      장소 데이터를 불러오지 못했어요
      <button type="button" @click="reload">새로고침</button>
    </div>

    <FilterPanel :mobile-suppressed="openCount > 0"
      @open-place="openPlace" @toggle-course="toggleCourse" />

    <PlaceDetail v-if="state.sel" :place="state.sel" @close="closeDetail"
      @open-place="openPlace" @open-photo="p => lightbox.show(p.photos, p.index)" />

    <CoursePanel @close="state.course = null" @open-place="openPlace" />

    <div class="slid" :class="{ s1: openCount === 1, s2: openCount === 2 }">
      <DatePicker />
    </div>

    <PhotoLightbox ref="lightbox" />
  </div>
  <MapToast />
</template>
