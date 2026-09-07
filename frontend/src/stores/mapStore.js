import { reactive, computed } from 'vue'
import { crowd, tier } from '@/utils/crowd'
import { iso, D0 } from '@/utils/date'
import MapPlaceService, { LAZY_LAYERS } from '@/services/map/MapPlaceService'
import CrowdService, { attachSeries } from '@/services/map/CrowdService'
import WeatherService from '@/services/map/MapWeatherService'

/* 지도 페이지 전역 상태.
   Pinia와 같은 모양(state + action)으로 두어 나중에 옮기기 쉽게 했다.
   지금은 페이지가 하나뿐이라 의존성을 늘리지 않고 reactive() 하나로 충분하다 */

export const REGIONS = ['전체', '동부', '서부', '남부', '북부']

/** 업종 필터 (MAP_001) — 한 번에 4개씩 보이고 좌우로 넘긴다 */
export const LAYERS = [
  { k: 'spot', t: '관광지' }, { k: 'food', t: '착한가격' }, { k: 'dine', t: '식당' },
  { k: 'cafe', t: '카페' }, { k: 'cvs', t: '편의점' }, { k: 'stay', t: '숙소' }, { k: 'mart', t: '마트' },
]
export const FILTER_VISIBLE = 4

export const state = reactive({
  /* ── 서버에서 받아오는 장소 데이터 ──
     하드코딩 시절엔 import 하는 순간 값이 있었지만 이제 비동기라 처음엔 비어 있다.
     reactive 라서 loadPlaces() 가 채우면 화면이 알아서 다시 그려진다 */
  layers: { spot: [], food: [], dine: [], cafe: [], cvs: [], stay: [], mart: [] },
  loading: true,
  /** false = 장소 레이어를 하나도 못 받았다(백엔드 다운) - 화면이 '새로고침' 안내를 띄운다 */
  live: false,
  /** 이번 진입에서 못 받아온 레이어 키 - 칩을 다시 켜면 그 레이어만 재시도한다 */
  loadFailed: [],
  /** 예보 일수. 화면은 30일 캘린더인데 실측은 21~22일이라 남는 날은 '정보 없음' */
  forecastDays: 0,

  di: 0,                 // 선택한 날짜 (오늘로부터 며칠 뒤)
  sel: null,             // 상세를 연 장소
  sort: 'calm',
  course: null,
  courseDay: 'all',
  filterOffset: 0,       // 업종 필터 캐러셀 위치
  F: { reg: '서부', bud: 150000, cat: '' },   // cat='' = 모든 종류
  L: { crowd: 1, spot: 1, food: 1, dine: 0, cafe: 0, cvs: 0, stay: 0, mart: 0, rain: 1 },
  toast: '',
})

/* ── 파생값 ── */

/** 화면이 SPOTS/FOOD 를 직접 import 하던 자리를 대신한다 */
export const spots = computed(() => state.layers.spot)
export const foods = computed(() => state.layers.food)

/**
 * 세부분류 드롭다운 (MAP-01).
 *
 * 가나다순이 아니라 <b>장소가 많은 순</b>이다 - 실데이터 기준 110종인데 그중 67종이
 * 5곳 미만이라, 가나다순으로 두면 1곳짜리 분류가 위에 오고 오름(92곳)이 한참 밑으로 간다.
 */
export const CATEGORIES = computed(() => {
  const count = new Map()
  for (const s of state.layers.spot) {
    if (s.c) count.set(s.c, (count.get(s.c) ?? 0) + 1)
  }
  return [...count.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([name, n]) => ({ name, n }))
})

/**
 * 업종 칩 토글. 비어 있는 레이어는 켜는 순간 받아온다 - 대용량(카페·편의점·마트 5,419곳)은
 * 첫 진입에 싣지 않아서, 첫 진입 때 실패한 기본 레이어는 재시도가 되도록.
 * 한 번 받으면 메모리에 남아 재요청이 없다. 실패하면 칩을 되돌린다 - 켜진 칩에 핀이 없는
 * 상태 불일치를 남기지 않는다.
 */
const layerLoading = new Set()
export async function toggleLayer (key) {
  state.L[key] ^= 1
  if (!state.L[key]) return
  if (state.layers[key].length || layerLoading.has(key)) return
  layerLoading.add(key)
  const rows = await MapPlaceService.getLayer(key)
  layerLoading.delete(key)
  if (rows) {
    state.layers[key] = rows
    state.loadFailed = state.loadFailed.filter(k => k !== key)
  } else {
    state.L[key] = 0
    toast('데이터를 불러오지 못했어요 — 칩을 다시 켜면 재시도해요')
  }
}

/**
 * 딥링크(?place=id) 복원 — 공유 링크·마이페이지 "장소 보기"가 이걸 탄다.
 * 이미 받아온 레이어에서 먼저 찾고, 없으면 지연 레이어(카페·편의점·마트)를
 * 하나씩 내려받아 찾는다. 찾은 장소의 업종 칩은 켠다 - 핀이 보여야 상세가 말이 된다.
 */
export async function findPlaceById (id) {
  for (const [k, rows] of Object.entries(state.layers)) {
    const p = rows.find(x => x.id === id)
    if (p) { state.L[k] = 1; return { place: p, error: false } }
  }
  // 장소를 하나도 못 받은 상태(백엔드 다운)면 더 두드리지 않는다 -
  // 레이어 7개를 5초씩 순서대로 재시도하면 안내가 40초 뒤에 뜬다(실측)
  if (!state.live) return { place: null, error: true }
  // 못 불러온 레이어(첫 진입 실패분 + 지연 레이어)를 하나씩 받아 보며 찾는다.
  // 어느 하나라도 못 받았으면 error - "없는 장소"가 아니라 "못 불러온 것"으로 안내해야 한다
  let error = false
  const candidates = [...new Set([...state.loadFailed, ...LAZY_LAYERS])]
  for (const k of candidates) {
    if (state.layers[k].length || layerLoading.has(k)) continue
    layerLoading.add(k)
    const rows = await MapPlaceService.getLayer(k)
    layerLoading.delete(k)
    if (!rows) { error = true; continue }
    state.layers[k] = rows
    state.loadFailed = state.loadFailed.filter(x => x !== k)
    const p = rows.find(x => x.id === id)
    if (p) { state.L[k] = 1; return { place: p, error: false } }
  }
  return { place: null, error }
}

/**
 * 장소·예보를 받아 state에 채운다. 지도 화면 진입 시 한 번 호출한다.
 * 예보는 장소보다 늦게 와도 되므로 따로 기다렸다가 붙인다 - 지도가 먼저 뜬다.
 */
export async function loadPlaces () {
  state.loading = true
  const { live, layers, failed } = await MapPlaceService.getAll()
  state.layers = layers
  state.live = live
  state.loadFailed = failed
  state.loading = false
  // 전부 실패는 화면 배너(MapView)가 맡고, 일부 실패만 여기서 알린다
  if (live && failed.length) {
    const names = failed.map(k => LAYERS.find(l => l.k === k)?.t ?? k).join('·')
    toast(`${names} 데이터를 불러오지 못했어요 — 칩을 다시 켜면 재시도해요`)
  }

  const [forecast] = await Promise.all([CrowdService.getForecast(), WeatherService.load()])
  state.forecastDays = forecast.days
  attachSeries(state.layers.spot, forecast, iso(new Date()))
}

/** 지역·종류 필터를 함께 적용 */
export const inFilter = s =>
  (state.F.reg === '전체' || s.r === state.F.reg) && (!state.F.cat || s.c === state.F.cat)

export const inRegion = o => state.F.reg === '전체' || o.r === state.F.reg

/** 좌측 목록 — 필터 안 관광지 전체를 정렬해 보여준다. 집중률 결측 장소는 순위에서 제외 (지도에는 회색 핀으로 남는다) */
export const rankedRows = computed(() =>
  state.layers.spot.filter(s => inFilter(s) && crowd(s, state.di) != null)
    .map(s => ({ s, c: crowd(s, state.di), t: tier(crowd(s, state.di)) }))
    .sort((a, b) => (state.sort === 'calm' ? a.c - b.c : b.c - a.c)))

/* ── 액션 ── */
let toastTimer = null
export function toast(msg) {
  state.toast = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { state.toast = '' }, 1900)
}

export { D0 }
