import { reactive, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { crowd, tier } from '@/utils/crowd'
import { iso, D0 } from '@/utils/date'
import MapPlaceService, { LAZY_LAYERS } from '@/services/map/MapPlaceService'
import CrowdService, { attachSeries } from '@/services/map/CrowdService'
import WeatherService from '@/services/map/MapWeatherService'
import FavoriteApiService from '@/services/map/FavoriteApiService'

/* 지도 페이지 전역 상태.
   Pinia와 같은 모양(state + action)으로 두어 나중에 옮기기 쉽게 했다.
   지금은 페이지가 하나뿐이라 의존성을 늘리지 않고 reactive() 하나로 충분하다 */

/* 찜을 브라우저에 이름으로 저장하던 시절의 키 - 이제 백엔드가 단일 저장소라 남은 값은 지운다(2026-09-07) */
try { localStorage.removeItem('hangat_favs') } catch { /* 저장소 접근 불가 - 무시 */ }

export const REGIONS = ['전체', '동부', '서부', '남부', '북부']

/** 업종 필터 (MAP_001) — 한 번에 4개씩 보이고 좌우로 넘긴다 */
export const LAYERS = [
  { k: 'spot', t: '관광지' }, { k: 'food', t: '착한가격' }, { k: 'dine', t: '식당' },
  { k: 'cafe', t: '카페' }, { k: 'cvs', t: '편의점' }, { k: 'stay', t: '숙소' }, { k: 'mart', t: '마트' },
]
export const FILTER_VISIBLE = 4

/* 권역 기본값은 '전체'. 탭이 살아 있는 동안은 마지막 선택을 기억한다(sessionStorage) -
   메인↔지도를 오가도 보던 권역이 유지되고, 탭을 닫으면 다시 '전체'로 시작한다.
   공유 링크의 ?r= 은 MapView.loadFromURL 이 이 값 위에 덮어쓴다 (MAP_001, 2026-09-11 결정) */
const REGION_KEY = 'hangat_map_region'
export function savedRegion (storage = globalThis.sessionStorage) {
  try {
    const v = storage?.getItem(REGION_KEY)
    return REGIONS.includes(v) ? v : '전체'
  } catch {
    return '전체'   // 저장소 접근 불가(프라이빗 모드 등) - 기본값으로
  }
}

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
  /** 예보를 장소에 붙일 때마다 1 증가 - 지도가 이걸 보고 핀 색을 다시 칠한다.
      일수(forecastDays)로는 안 된다: 재진입 때 22→22 로 값이 같아 watch 가 안 깨어나 핀이 회색으로 굳는다 */
  forecastVersion: 0,

  di: 0,                 // 선택한 날짜 (오늘로부터 며칠 뒤)
  sel: null,             // 상세를 연 장소
  sort: 'calm',
  course: null,
  courseDay: 'all',
  filterOffset: 0,       // 업종 필터 캐러셀 위치
  F: { reg: savedRegion(), bud: 150000, cat: '' },   // reg 기본 '전체'(탭 안 마지막 선택 기억), cat='' = 모든 종류
  // 기본은 관광지 핀만 - 착한가격(271)까지 켜면 전체 권역에서 분홍 마커가 혼잡 색을 가린다. 칩으로 켠다 (2026-09-11 결정)
  L: { crowd: 1, spot: 1, food: 0, dine: 0, cafe: 0, cvs: 0, stay: 0, mart: 0, rain: 1 },
  /** 로그인한 회원이 찜한 장소 ID (MAP_009). 백엔드 /favorites 가 원본이고 이건 화면용 사본. 비로그인이면 빈 배열 */
  favIds: [],
  toast: '',
})

watch(() => state.F.reg, r => {
  try { sessionStorage.setItem(REGION_KEY, r) } catch { /* 저장소 접근 불가 - 기억만 못 할 뿐 */ }
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
  // 어느 레이어에도 없는 장소 - 폐업(CLOSED)은 목록에서 빠지지만 찜·공유 링크로는 들어온다. 상세를 직접 받아 연다
  const single = await MapPlaceService.getById(id)
  if (single) return { place: single, error: false }
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
  state.forecastVersion++
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

const currentUser = () => useAuthStore().user

/* ── MAP_009 찜 — 회원 전용. 백엔드 /favorites 가 단일 저장소라 마이페이지 찜 목록과 항상 같다 ── */

let favOwner = null
/**
 * 로그인한 회원의 찜 장소 ID를 받아온다. MapView 가 로그인 상태가 바뀔 때마다 부른다(로그아웃이면 null).
 * 실패해도 던지지 않는다 - 하트만 안 켜질 뿐 지도는 정상이어야 한다. 늦게 온 응답이 다른 계정 것을 덮지 않게 주인을 확인한다.
 */
export async function loadFavorites (userId) {
  favOwner = userId ?? null
  if (favOwner == null) { state.favIds = []; return }
  try {
    const ids = await FavoriteApiService.ids()
    if (favOwner === userId) state.favIds = ids
  } catch (e) {
    console.error('찜 목록을 불러오지 못했어요', e)
  }
}

export const isFav = place => place?.id != null && state.favIds.includes(place.id)

function setFav (id, on) {
  const i = state.favIds.indexOf(id)
  if (on && i < 0) state.favIds.push(id)
  if (!on && i >= 0) state.favIds.splice(i, 1)
}

/**
 * 하트 토글. 화면을 먼저 바꾸고 서버에 알린다 - 응답을 기다렸다 바꾸면 늦게 켜지는 하트를 두 번 누르게 된다.
 * 실패하면 되돌리고 알린다. 비로그인은 토스트만(로그인 페이지로 보내지 않는다 - 2026-09-07 결정).
 * @returns {Promise<boolean>} 서버에 반영됐으면 true
 */
export async function toggleFav (place) {
  if (!currentUser()) { toast('찜은 로그인이 필요해요'); return false }
  if (place?.id == null) { toast('이 장소는 찜할 수 없어요'); return false }
  const on = !isFav(place)
  setFav(place.id, on)
  try {
    const res = on ? await FavoriteApiService.add(place.id) : await FavoriteApiService.remove(place.id)
    setFav(place.id, res.favorited)   // 서버가 말한 상태가 정답이다(연타로 순서가 엇갈려도 마지막 응답 기준)
    toast(res.favorited ? '찜했어요 — 마이페이지에서 볼 수 있어요' : '찜을 해제했어요')
    return true
  } catch (e) {
    setFav(place.id, !on)
    toast(e?.status === 401
      ? '로그인이 만료됐어요 — 다시 로그인해 주세요'
      : '찜을 저장하지 못했어요 — 잠시 후 다시 시도해 주세요')
    return false
  }
}

export { D0 }
