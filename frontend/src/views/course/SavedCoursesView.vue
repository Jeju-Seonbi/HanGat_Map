<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import CourseService, { type CourseCard } from '../../services/CourseService'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import KakaoMap from '../../components/map/KakaoMap.vue'
import { useSavedCourseExplorer, sheetHeight } from '../../composables/useSavedCourseExplorer'
import { preloadKakao } from '../../composables/useKakaoShare.js'
import { hasNaviCoordinates, isNaviMobile, startNavi, type NaviSdk } from '../../services/kakaoNavi'
import type { Place } from '../../assets/types'

const cards = ref<CourseCard[]>([])
const loading = ref(true)
const ok = ref(true)
const page = ref(1)
const totalPages = ref(1)
const totalElements = ref(0)
const { course, selectedId, expanded, loading: detailLoading, failed, select, reset } =
  useSavedCourseExplorer(id => CourseService.getCourseDetail(id))
const activeDay = ref(1)
const selectedStop = ref('')
const currentDay = computed(() => course.value?.days.find(day => day.dayNo === activeDay.value))
const destination = computed(() => currentDay.value?.items.find(item => String(item.id) === selectedStop.value))
const mapPlaces = computed<Place[]>(() => (currentDay.value?.items ?? []).map(item => ({
  id: String(item.id), name: item.placeName, latitude: item.latitude ?? undefined,
  longitude: item.longitude ?? undefined, region: item.regionName ?? '', category: item.categoryName,
  address: '', description: item.reason ?? '', image: item.imageUrl ?? '',
  score: item.congestionRate ?? 0, level: item.congestionLevel ?? 'QUIET',
  pinLabel: String(item.position), pinDescription: `${item.position}번째 방문지`,
  congestionUnknown: item.congestionLevel == null,
  time: '', stay: '', cost: '', tags: [],
})))
const mapFailed = ref(false)
const mapAttempt = ref(0)
const notice = ref('')
const deleting = ref(false)
const mobile = ref(false)
const naviReady = ref(false)
const naviLoading = ref(false)
const workspace = ref<HTMLElement>()
const courseBrowser = ref<HTMLElement>()
const browserHeight = ref(180)
let browserObserver: ResizeObserver | undefined
const panelHeight = ref(55)
let listSequence = 0
let alive = true
let drag: { id: number; y: number; height: number; moved: boolean } | null = null
let suppressClick = false

watch(course, value => {
  activeDay.value = value?.days[0]?.dayNo ?? 1
  selectedStop.value = String(value?.days[0]?.items[0]?.id ?? '')
})
watch(activeDay, () => { selectedStop.value = String(currentDay.value?.items[0]?.id ?? '') })

async function load(target: number) {
  const request = ++listSequence
  reset()
  loading.value = true
  try {
    const result = await CourseService.getSavedCourses(target - 1, 10)
    if (!alive || request !== listSequence) return
    cards.value = result.cards
    ok.value = result.ok
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
    page.value = target
    if (result.ok && result.cards[0]) {
      panelHeight.value = 55
      void select(result.cards[0].id)
    }
  } catch {
    if (alive && request === listSequence) { ok.value = false; cards.value = [] }
  } finally {
    if (alive && request === listSequence) loading.value = false
  }
}
async function choose(id: string) {
  notice.value = ''
  const pending = select(id)
  panelHeight.value = expanded.value ? 55 : 18
  await pending
}
function togglePanel() {
  if (suppressClick) { suppressClick = false; return }
  expanded.value = !expanded.value
  panelHeight.value = expanded.value ? 55 : 18
}
function dragStart(event: PointerEvent) {
  if (event.button !== 0) return
  suppressClick = false
  drag = { id: event.pointerId, y: event.clientY, height: panelHeight.value, moved: false }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}
function dragMove(event: PointerEvent) {
  if (!drag || event.pointerId !== drag.id) return
  const delta = event.clientY - drag.y
  if (Math.abs(delta) > 4) drag.moved = true
  if (!drag.moved) return
  panelHeight.value = sheetHeight(drag.height, delta, workspace.value?.clientHeight ?? 0)
  expanded.value = panelHeight.value > 23
}
function dragEnd(event: PointerEvent) {
  if (!drag || event.pointerId !== drag.id) return
  suppressClick = drag.moved
  drag = null
}
function panelKey(event: KeyboardEvent) {
  if (!['ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  panelHeight.value = event.key === 'Home' ? 18 : event.key === 'End' ? 90
    : sheetHeight(panelHeight.value, event.key === 'ArrowUp' ? -15 : 15, 100)
  expanded.value = panelHeight.value > 23
}
async function prepareNavi() {
  naviLoading.value = true
  const ready = await preloadKakao()
  if (!alive) return
  naviReady.value = ready && !!(window as Window & { Kakao?: NaviSdk }).Kakao?.Navi
  naviLoading.value = false
}
function navigate() {
  if (!destination.value) return
  const sdk = (window as Window & { Kakao?: NaviSdk }).Kakao
  if (!startNavi(destination.value, mobile.value, sdk)) notice.value = '카카오내비를 열지 못했어요. 잠시 후 다시 시도해 주세요.'
}
async function removeCourse() {
  const target = course.value
  if (!target?.manageable || deleting.value || !window.confirm('이 코스를 삭제할까요? 삭제한 코스는 복구할 수 없어요.')) return
  deleting.value = true
  try {
    await CourseService.deleteCourse(target.id)
    await load(cards.value.length === 1 && page.value > 1 ? page.value - 1 : page.value)
    notice.value = '코스를 삭제했어요.'
  } catch { notice.value = '삭제하지 못했어요. 다시 시도해 주세요.' }
  finally { deleting.value = false }
}
async function copyAddress() {
  if (!course.value) return
  const owned = course.value.manageable
  try {
    await navigator.clipboard.writeText(new URL(`/courses/${course.value.id}`, location.origin).href)
    notice.value = owned ? '주소를 복사했어요. 내 저장 코스는 본인만 열 수 있어요.' : '코스 주소를 복사했어요.'
  } catch { notice.value = '주소를 복사하지 못했어요. 코스 상세 화면에서 주소를 확인해 주세요.' }
}
onMounted(() => {
  browserObserver = new ResizeObserver(entries => {
    browserHeight.value = entries[0]?.target.getBoundingClientRect().height ?? 180
  })
  if (courseBrowser.value) browserObserver.observe(courseBrowser.value)
  mobile.value = isNaviMobile(navigator.userAgent, navigator.maxTouchPoints)
  if (mobile.value) void prepareNavi()
  void load(1)
})
onBeforeUnmount(() => { alive = false; listSequence++; browserObserver?.disconnect(); reset() })
</script>

<template>
  <section ref="workspace" class="saved-workspace" :style="{ '--sheet-height': panelHeight + '%', '--browser-height': browserHeight + 'px', '--map-bottom': selectedId ? panelHeight + '%' : '0px' }" aria-label="저장한 코스">
    <aside class="course-sidebar">
      <div ref="courseBrowser" class="course-browser">
        <div class="course-heading">
          <div><span class="saved-eyebrow">SAVED ITINERARY</span><h1>저장한 코스 일정</h1></div>
          <RouterLink class="new-course" to="/ai-course">새 코스 +</RouterLink>
        </div>
        <p v-if="loading" role="status">저장한 코스를 불러오는 중이에요.</p>
        <div v-else-if="!ok" role="alert">코스를 불러오지 못했어요. <button type="button" @click="load(page)">다시 시도</button></div>
        <p v-else-if="!cards.length">아직 저장한 코스가 없어요. 새 코스를 만들어 보세요.</p>
        <template v-else>
          <div class="course-tabs" aria-label="저장 코스 선택">
            <button v-for="card in cards" :key="card.id" type="button" class="course-tab"
              :class="{ active: selectedId === card.id }" :aria-expanded="selectedId === card.id && expanded"
              aria-controls="saved-itinerary" :disabled="deleting" @click="choose(card.id)">
              <span class="course-tab-title">{{ card.title }}<span aria-hidden="true">{{ selectedId === card.id && expanded ? '∧' : '∨' }}</span></span>
              <strong>{{ card.stops || card.conditionLabel }}</strong>
            </button>
          </div>
          <nav class="saved-pagination" aria-label="저장 코스 페이지">
            <small>총 {{ totalElements }}개</small>
            <template v-if="totalPages > 1">
              <button type="button" :disabled="page <= 1 || loading || deleting" aria-label="이전 페이지" @click="load(page - 1)">‹</button>
              <span>{{ page }} / {{ totalPages }}</span>
              <button type="button" :disabled="page >= totalPages || loading || deleting" aria-label="다음 페이지" @click="load(page + 1)">›</button>
            </template>
          </nav>
        </template>
      </div>
      <section v-if="selectedId" id="saved-itinerary" class="itinerary-sheet" :class="{ collapsed: !expanded }" aria-label="선택한 코스 일정">
        <button class="sheet-handle" type="button" :aria-expanded="expanded" aria-label="일정 패널 펼치기 또는 접기. 위아래 방향키로 높이 조절"
          @pointerdown="dragStart" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd" @lostpointercapture="dragEnd"
          @click="togglePanel" @keydown="panelKey"><span /></button>
        <div v-if="detailLoading" class="sheet-message" role="status">일정을 불러오는 중이에요.</div>
        <div v-else-if="failed" class="sheet-message" role="alert">일정을 불러오지 못했어요. <button type="button" @click="select(selectedId)">다시 시도</button></div>
        <template v-else-if="course">
          <div class="course-summary">
            <div><CongestionBadge v-if="course.level" :level="course.level" /><span v-else>혼잡 정보 없음</span><small>{{ course.conditionLabel }}</small></div>
            <strong>{{ course.budgetLabel }}</strong>
          </div>
          <div v-show="expanded" class="sheet-body">
            <div class="course-actions">
              <RouterLink :to="`/courses/${course.id}`">{{ course.swappable ? '상세 · 일정 편집' : '코스 상세' }}</RouterLink>
              <button type="button" @click="copyAddress">주소 복사</button>
              <button v-if="course.manageable" type="button" :disabled="deleting" aria-label="선택한 코스 삭제" @click="removeCourse">{{ deleting ? '삭제 중…' : '삭제' }}</button>
            </div>
            <p v-if="!course.days.length" class="sheet-message">등록된 일정이 없어요.</p>
            <section v-for="day in course.days" :key="day.dayNo" class="itinerary-day">
              <button class="day-heading" type="button" :aria-expanded="activeDay === day.dayNo" :aria-controls="`day-${day.dayNo}`" @click="activeDay = activeDay === day.dayNo ? 0 : day.dayNo">
                <span class="day-number">DAY {{ day.dayNo }}</span><strong>{{ day.visitDate }}</strong><span aria-hidden="true">{{ activeDay === day.dayNo ? '∧' : '∨' }}</span>
              </button>
              <ol v-if="activeDay === day.dayNo" :id="`day-${day.dayNo}`" class="stop-list">
                <li v-for="item in day.items" :key="item.id">
                  <p v-if="item.inboundTravelMinutes != null || item.inboundDistanceM != null" class="travel-step">
                    <span v-if="item.inboundTravelMinutes != null">이동 약 {{ item.inboundTravelMinutes }}분</span>
                    <span v-if="item.inboundDistanceM != null"> · {{ (item.inboundDistanceM / 1000).toFixed(1) }}km</span>
                  </p>
                  <span class="stop-number">{{ item.position }}</span>
                  <button type="button" class="stop-card" :class="{ selected: selectedStop === String(item.id) }" :aria-pressed="selectedStop === String(item.id)" @click="selectedStop = String(item.id)">
                    <img v-if="item.imageUrl" :src="item.imageUrl" alt="" loading="lazy" @error="($event.target as HTMLImageElement).hidden = true">
                    <span class="stop-copy"><span class="stop-time">{{ item.startTime?.slice(0, 5) || `${item.position}번째 방문` }}</span><strong>{{ item.placeName }}</strong><span v-if="item.reason" class="stop-description">{{ item.reason }}</span>
                      <CongestionBadge v-if="item.congestionLevel" :level="item.congestionLevel" /><small v-else>혼잡 정보 없음</small>
                    </span>
                  </button>
                  <RouterLink class="place-detail" :to="`/places/${item.placeId}`">장소 상세</RouterLink>
                </li>
              </ol>
            </section>
          </div>
          <div v-if="mobile && expanded" class="navi-action">
            <button v-if="!naviReady" type="button" :disabled="naviLoading" @click="prepareNavi">{{ naviLoading ? '카카오내비 연결 준비 중…' : '카카오내비 연결 다시 시도' }}</button>
            <button v-else type="button" :disabled="!destination || !hasNaviCoordinates(destination)" @click="navigate">{{ destination ? `${destination.placeName} 길안내` : '길안내할 장소를 선택해 주세요' }}</button>
            <small>{{ destination && !hasNaviCoordinates(destination) ? '좌표가 없는 장소는 길안내를 사용할 수 없어요.' : '카카오내비 앱으로 차량 길안내 · 미설치 시 설치 화면으로 이동' }}</small>
          </div>
        </template>
        <p v-if="notice" class="saved-notice" role="status">{{ notice }}</p>
      </section>
    </aside>
    <div class="saved-map" aria-label="선택한 일정 지도">
      <KakaoMap v-if="!mapFailed" :key="mapAttempt" :places="mapPlaces" :selected-id="selectedStop" show-route @select="selectedStop = $event.id" @error="mapFailed = true" />
      <div v-else class="map-error" role="status"><strong>지도를 불러오지 못했어요.</strong><p>일정은 계속 확인할 수 있어요.</p><button type="button" @click="mapFailed = false; mapAttempt++">지도 다시 시도</button></div>
      <div v-if="!mapFailed" class="map-caption">{{ currentDay ? `DAY ${currentDay.dayNo} 방문 순서` : '제주 여행 지도' }}<small>연결선은 실제 도로 경로가 아니에요.</small></div>
    </div>
  </section>
</template>

<style scoped src="./savedCourses.css"></style>
