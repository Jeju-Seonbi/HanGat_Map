<script setup>
/* MAP_001 좌측 카드 — 정렬 / 권역 / 업종(4개씩 넘김) / 종류 / 한산한 곳 목록 / 코스 버튼 / 범례.
   폰(≤768px)에서는 같은 마크업이 가운데 모달 + [조건]·[목록] 탭으로 열린다(2026-09-14 결정, 시안 B) -
   바텀시트 때는 조건 UI가 시트의 85%를 먹어 목록이 1.3줄(88px)만 보였다(최종점검 #58) */
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import SearchBox from './SearchBox.vue'
import LayerIcon from './LayerIcon.vue'
import { state, rankedRows, CATEGORIES, REGIONS, LAYERS, FILTER_VISIBLE, toggleLayer, placeKey } from '@/stores/mapStore'
import { at, fmtK } from '@/utils/date'
import { sourceLine } from '@/utils/dataSources'
import { mapBridge } from '@/composables/mapBridge'

const emit = defineEmits(['open-place', 'toggle-course'])
const props = defineProps({ mobileSuppressed: { type: Boolean, default: false } })
const mobileOpen = ref(false)
/** 모달 탭. 목록이 주인공이라 기본은 목록, 조건은 한 탭 뒤 - 검색창은 두 탭 공통(가장 잦은 행동이라 탭 전환 없이) */
const mtab = ref('list')

/* 폰 폭 여부 - 업종 줄을 폰에선 7개 한 줄로 펼치고(‹ › 넘김 없음) 데스크톱에선 4개씩 넘긴다 */
const mq = typeof matchMedia === 'function' ? matchMedia('(max-width:768px)') : null
const isMobile = ref(!!mq?.matches)
const onMq = e => { isMobile.value = e.matches }
onMounted(() => mq?.addEventListener('change', onMq))
onBeforeUnmount(() => mq?.removeEventListener('change', onMq))

watch(() => props.mobileSuppressed, suppressed => {
  if (suppressed) mobileOpen.value = false
})

/* 목록 탭 위 조건 요약 한 줄 - 조건 탭을 안 열어도 무슨 기준의 목록인지 읽힌다 */
const layerNames = computed(() => LAYERS.filter(l => state.L[l.k]).map(l => l.t))
const summary = computed(() => [
  fmtK(at(state.di)),
  state.sort === 'calm' ? '한산한 순' : '혼잡한 순',
  state.F.reg,
  layerNames.value.length ? layerNames.value.join('·') : '업종 없음',
  state.F.cat || '모든 종류',
].join(' · '))

const sectionTitle = computed(() =>
  `${fmtK(at(state.di))} ${state.sort === 'calm' ? '한산한' : '혼잡한'} 곳`)

/* 예보를 못 받은 날만 범례 아래에 알린다 - 그날은 지도 전체가 회색이라 "원래 예보 없는 곳"과 구분해야 한다.
   평소 회색 핀의 뜻은 범례 라벨('예보 없음')과 상세 문장이 말한다 */
const forecastDown = computed(() => !state.loading && state.live && state.forecastDays === 0)

/* 데이터 출처 - 지도에서 보는 데이터의 출처는 지도에 적는다(공공데이터 이용조건·심사 확인 항목). 켜진 레이어에 따라 원천이 늘어난다 */
const sources = computed(() => sourceLine(state.L))

const maxOffset = computed(() => Math.max(0, LAYERS.length - FILTER_VISIBLE))
const shift = computed(() => `translateX(-${state.filterOffset * (100 / FILTER_VISIBLE)}%)`)

/** 한 번에 4칸씩 밀되 마지막은 오른쪽 끝에 맞춰 멈춘다 */
function moveFilter(d) {
  state.filterOffset = Math.min(maxOffset.value, Math.max(0, state.filterOffset + d * FILTER_VISIBLE))
}

function setRegion(r) {
  state.F.reg = r
  mapBridge.fitRegion()
}

function openPlace(place) {   // 장소 객체(목록 행·검색 결과) - 이름이 아니라 id 로 열려야 동명 장소가 구분된다
  mobileOpen.value = false
  emit('open-place', place)
}

function toggleCourse() {
  mobileOpen.value = false
  emit('toggle-course')
}
</script>

<template>
  <button
    type="button"
    class="filter-fab"
    :aria-expanded="mobileOpen"
    aria-controls="mobile-map-filter-sheet"
    :aria-label="mobileOpen ? '장소 검색 닫기' : '장소 검색 및 필터 열기'"
    @click="mobileOpen = !mobileOpen"
  >
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      stroke-width="2.1" stroke-linecap="round" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m15.5 15.5 5 5" />
    </svg>
  </button>

  <div id="mobile-map-filter-sheet" class="fl cond" :class="{ 'mobile-open': mobileOpen }">
    <div class="mobile-filter-head">
      <div>
        <b>장소 찾기</b>
        <span>한산한 제주를 조건별로 찾아보세요</span>
      </div>
      <button type="button" class="mobile-filter-close" aria-label="장소 검색 닫기"
        @click="mobileOpen = false">×</button>
    </div>

    <SearchBox @pick-spot="openPlace" />

    <!-- 폰 전용 탭. 데스크톱에선 CSS 로 숨긴다(.mtabs/.msum/.mapply display:none) -->
    <div class="mtabs" role="tablist" aria-label="장소 찾기">
      <button type="button" role="tab" :aria-selected="mtab === 'cond'" :class="{ on: mtab === 'cond' }"
        @click="mtab = 'cond'">조건</button>
      <button type="button" role="tab" :aria-selected="mtab === 'list'" :class="{ on: mtab === 'list' }"
        @click="mtab = 'list'">목록 <span class="n">{{ rankedRows.length }}</span></button>
    </div>

    <div id="cond-body" :class="mtab === 'list' ? 'tab-list' : 'tab-cond'">
      <div class="msum">
        <span class="cur">{{ summary }}</span>
        <button type="button" class="edit" @click="mtab = 'cond'">조건 바꾸기 ›</button>
      </div>

      <div class="seg">
        <button :class="{ on: state.sort === 'calm' }" @click="state.sort = 'calm'">한산한 순</button>
        <button :class="{ on: state.sort === 'busy' }" @click="state.sort = 'busy'">혼잡한 순</button>
      </div>

      <div class="chips">
        <span v-for="r in REGIONS" :key="r" class="chip" :class="{ on: r === state.F.reg }"
          @click="setRegion(r)">{{ r }}</span>
      </div>

      <div class="ftr-wrap">
        <button class="ftr-nav" aria-label="이전 업종" :disabled="state.filterOffset <= 0"
          @click="moveFilter(-1)">‹</button>
        <div class="ftr-vp">
          <div class="ftr" :style="isMobile ? null : { transform: shift }">
            <button v-for="l in LAYERS" :key="l.k" :class="[l.k, { on: state.L[l.k] }]"
              @click="toggleLayer(l.k)">
              <span class="ico"><LayerIcon :name="l.k" /></span>{{ l.t }}
            </button>
          </div>
        </div>
        <button class="ftr-nav" aria-label="다음 업종" :disabled="state.filterOffset >= maxOffset"
          @click="moveFilter(1)">›</button>
      </div>

      <select class="catsel" :class="{ on: state.F.cat }" v-model="state.F.cat">
        <option value="">모든 종류의 관광지</option>
        <!-- 곳수를 함께 보여준다 - 110종 중 3분의 2가 5곳 미만이라 고르기 전에 규모를 알아야 한다 -->
        <option v-for="c in CATEGORIES" :key="c.name" :value="c.name">{{ c.name }} ({{ c.n }})</option>
      </select>

      <!-- 조건 탭 끝: 결과 건수와 함께 목록 탭으로 -->
      <button type="button" class="mapply" @click="mtab = 'list'">목록 {{ rankedRows.length }}곳 보기 ›</button>

      <div class="sect"><em>✦</em> <span>{{ sectionTitle }}</span></div>

      <div class="rows">
        <div v-if="state.loading" class="empty">장소를 불러오는 중이에요…</div>
        <!-- 빈 이유를 구분한다: 장소 못 받음 / 예보 못 받음 / 선택 날짜가 예보 범위 밖 / 진짜로 조건에 맞는 곳 없음 (2026-09-13) -->
        <div v-else-if="!rankedRows.length" class="empty">
          {{ !state.live ? '장소 데이터를 불러오지 못했어요 · 새로고침해 주세요'
            : forecastDown ? '혼잡 예보를 불러오지 못했어요 · 새로고침해 주세요'
            : (state.forecastUntil >= 0 && state.di > state.forecastUntil) ? `${fmtK(at(state.forecastUntil))} 이후는 아직 혼잡 예보가 없어요`
            : '이 조건에는 혼잡 예보가 있는 곳이 없어요' }}
        </div>
        <!-- 혼잡 상태는 왼쪽 핀 색으로만 표시한다 (오른쪽 뱃지와 의미가 중복되어 제거) -->
        <div v-for="(o, i) in rankedRows" :key="placeKey(o.s)" class="row" :class="o.t"
          @click="openPlace(o.s)">
          <span class="rk">{{ i + 1 }}</span>
          <span class="rpin" :class="o.t"></span>
          <span class="info">
            <span class="rn">{{ o.s.n }}</span>
            <span class="rs">{{ o.s.c }} · {{ o.s.r }}</span>
          </span>
        </div>
      </div>

      <!-- 코스가 있고 패널이 접혀 있으면 '코스 보기' - ×로 접은 패널을 다시 펼치는 유일한 버튼 -->
      <button class="cta" @click="toggleCourse">
        {{ !state.course ? 'AI 코스 만들기' : state.coursePanel ? '코스 지우기' : '코스 보기' }}
      </button>

      <!-- 도트는 핀과 같은 면색(-st), 글자는 가독용 진한 톤 - 주황 글자는 흰 배경에서 못 읽는다 -->
      <div class="cta-leg">
        <span style="color:var(--calm)"><i class="dot tier-bg calm"></i>한산</span>
        <span style="color:var(--mid)"><i class="dot tier-bg mid"></i>보통</span>
        <span style="color:var(--busy)"><i class="dot tier-bg busy"></i>혼잡</span>
        <span style="color:var(--tx3)" title="관광공사 혼잡 예측 대상이 아닌 장소"><i class="dot" style="background:var(--tx3)"></i>예보 없음</span>
      </div>
      <p v-if="forecastDown" class="cta-note">혼잡 예보를 불러오지 못했어요 · 새로고침해 주세요</p>
      <p class="cta-src">{{ sources }}</p>
    </div>
  </div>

  <!-- 폰 모달 뒤 어두운 배경 - 탭하면 닫힘. 데스크톱은 display:none -->
  <div class="cond-dim" :class="{ on: mobileOpen }" @click="mobileOpen = false"></div>
</template>
