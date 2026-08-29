<script setup>
/* 지도 페이지 (MAP_001~009) — 패널 배치와 컴포넌트 사이 연결을 담당한다 */
import { ref, computed, watch, onMounted } from 'vue'
import MapCanvas from '@/components/map/MapCanvas.vue'
import FilterPanel from '@/components/map/FilterPanel.vue'
import DatePicker from '@/components/map/DatePicker.vue'
import PlaceDetail from '@/components/map/PlaceDetail.vue'
import CoursePanel from '@/components/map/CoursePanel.vue'
import PhotoLightbox from '@/components/map/PhotoLightbox.vue'
import { state, toast, loadPlaces } from '@/stores/mapStore'

import { buildCourse, refreshCourse, courseFromNames } from '@/utils/course'
import { at, iso, D0, FORECAST_DAYS } from '@/utils/date'
import { mapBridge } from '@/composables/mapBridge'
import MapToast from '@/components/map/MapToast.vue'

const lightbox = ref(null)

/* 열린 패널 수만큼 날짜 버튼이 오른쪽으로 비켜난다 */
const openCount = computed(() => (state.sel ? 1 : 0) + (state.course ? 1 : 0))

function openPlace(nameOrSpot) {
  const s = typeof nameOrSpot === 'string' ? state.layers.spot.find(x => x.n === nameOrSpot) : nameOrSpot
  if (!s) return
  mapBridge.panTo(s.y, s.x)
  state.sel = s
}
const closeDetail = () => { state.sel = null }

function toggleCourse() {
  if (state.course) {
    state.course = null
    state.courseDay = 'all'
    history.replaceState(null, '', location.pathname)
    return
  }
  const c = buildCourse({
    region: state.F.reg, budget: state.F.bud, dayIndex: state.di, useRain: !!state.L.rain,
  })
  if (!c) { toast('해당 권역 후보가 부족해요'); return }
  state.course = c
  state.courseDay = 'all'
  syncURL()
  const pts = c.stops.filter(s => s.o).map(s => [s.o.y, s.o.x])
  if (pts.length > 1) mapBridge.fitPoints(pts, 12)
}

/* COM_002: 날짜는 순번이 아니라 실제 날짜로 저장한다 —
   기준일(오늘)이 매일 바뀌므로 순번은 링크마다 뜻이 달라진다 */
function syncURL() {
  if (!state.course) return
  history.replaceState(null, '', '?' + new URLSearchParams({
    d: iso(at(state.di)), r: state.F.reg, b: state.course.bud,
    s: state.course.stops.map(x => (x.o ? x.o.n : x.f.n)).join('|'),
  }))
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
  const names = (p.get('s') || '').split('|').filter(Boolean)
  if (!names.length) return
  const c = courseFromNames(names, { region: state.F.reg, budget: state.F.bud, dayIndex: state.di })
  if (c) { state.course = c; state.courseDay = 'all' }
}

/* 날짜가 바뀌면 코스의 혼잡도도 다시 계산한다 */
watch(() => state.di, () => {
  if (state.course) {
    refreshCourse(state.course, { region: state.F.reg, dayIndex: state.di })
    state.course = { ...state.course }
  }
})

onMounted(async () => {
  // 장소·예보를 먼저 받아야 URL의 ?place= 로 들어온 장소를 찾을 수 있다
  await loadPlaces()
  loadFromURL()
})
</script>

<template>
  <div class="stage" :class="{ both: openCount === 2, 'sheet-open': openCount > 0 }">
    <MapCanvas @select="openPlace" @blank-click="closeDetail" />

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
