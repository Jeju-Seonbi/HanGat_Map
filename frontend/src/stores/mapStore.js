import { reactive, computed } from 'vue'
import { SPOTS, FOOD } from '@/data/placesMap'
import { useAuthStore } from '@/stores/auth'
import { crowd, tier } from '@/utils/crowd'
import { at, iso, ago, D0 } from '@/utils/date'

/* 지도 페이지 전역 상태.
   Pinia와 같은 모양(state + action)으로 두어 나중에 옮기기 쉽게 했다.
   지금은 페이지가 하나뿐이라 의존성을 늘리지 않고 reactive() 하나로 충분하다 */

const readLS = (k, fallback) => {
  try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : fallback } catch { return fallback }
}
const writeLS = (k, v) => {
  try { localStorage.setItem(k, JSON.stringify(v)); return true } catch { return false }
}

const SAMPLE_REVIEWS = {
  /* 방문일은 오늘 기준 상대값 — 날짜가 흘러도 '며칠 전 방문'으로 자연스럽게 유지된다 */
  '협재해수욕장': [
    { u: '지민', r: 5, c: 'busy', d: ago(1), t: '물빛은 최고인데 오후엔 자리가 없어요. 아침 일찍 가세요.' },
    { u: '상현', r: 4, c: 'busy', d: ago(7), t: '주차장이 금방 차요' },
    { u: '해나', r: 5, c: 'mid', d: ago(22), t: '평일에 가니 한산하고 좋았어요' }],
  '금오름': [
    { u: '예린', r: 5, c: 'calm', d: ago(3), t: '분화구까지 20분이면 올라가요. 노을 시간 추천!' },
    { u: '도윤', r: 4, c: 'calm', d: ago(11), t: '사람 거의 없어서 좋았습니다' }],
  '성산일출봉': [{ u: '민서', r: 4, c: 'busy', d: ago(4), t: '경치는 좋은데 계단에서 계속 멈춰서요' }],
  '저지오름': [{ u: '현우', r: 5, c: 'calm', d: ago(9), t: '조용히 걷기 좋아요. 그늘도 많습니다' }],
}

export const REGIONS = ['전체', '동부', '서부', '남부', '북부']

/** 업종 필터 (MAP_001) — 한 번에 4개씩 보이고 좌우로 넘긴다 */
export const LAYERS = [
  { k: 'spot', t: '관광지' }, { k: 'food', t: '착한가격' }, { k: 'dine', t: '식당' },
  { k: 'cafe', t: '카페' }, { k: 'cvs', t: '편의점' }, { k: 'stay', t: '숙소' }, { k: 'mart', t: '마트' },
]
export const FILTER_VISIBLE = 4

export const state = reactive({
  di: 0,                 // 선택한 날짜 (오늘로부터 며칠 뒤)
  sel: null,             // 상세를 연 장소
  sort: 'calm',
  course: null,
  courseDay: 'all',
  filterOffset: 0,       // 업종 필터 캐러셀 위치
  F: { reg: '서부', bud: 150000, cat: '' },   // cat='' = 모든 종류
  L: { crowd: 1, spot: 1, food: 1, dine: 0, cafe: 0, cvs: 0, stay: 0, mart: 0, rain: 1 },
  favs: readLS('hangat_favs', []),
  reviews: readLS('hangat_reviews', null) || SAMPLE_REVIEWS,
  placeImgs: readLS('hangat_place_imgs', {}),
  courses: readLS('hangat_courses', []),
  toast: '',
})

/* ── 파생값 ── */
export const CATEGORIES = [...new Set(SPOTS.map(s => s.c))].sort()

/** 지역·종류 필터를 함께 적용 */
export const inFilter = s =>
  (state.F.reg === '전체' || s.r === state.F.reg) && (!state.F.cat || s.c === state.F.cat)

export const inRegion = o => state.F.reg === '전체' || o.r === state.F.reg

/** 좌측 목록 — 집중률 결측 장소는 순위에서 제외 (지도에는 회색 핀으로 남는다) */
export const rankedRows = computed(() =>
  SPOTS.filter(s => inFilter(s) && s.b != null)
    .map(s => ({ s, c: crowd(s, state.di), t: tier(crowd(s, state.di)) }))
    .sort((a, b) => (state.sort === 'calm' ? a.c - b.c : b.c - a.c))
    .slice(0, 8))

/* ── 액션 ── */
let toastTimer = null
export function toast(msg) {
  state.toast = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { state.toast = '' }, 1900)
}

const currentUser = () => useAuthStore().user
const currentUserName = user => user?.nickname || user?.name || '한갓이'

/** MAP_009 찜 — 회원 전용 */
export function toggleFav(name) {
  if (!currentUser()) { toast('찜은 로그인이 필요해요'); return false }
  const i = state.favs.indexOf(name)
  if (i >= 0) state.favs.splice(i, 1); else state.favs.push(name)
  writeLS('hangat_favs', state.favs)
  toast(i >= 0 ? '찜을 해제했어요' : '찜했어요 — 마이페이지에서 볼 수 있어요')
  return true
}
export const isFav = name => state.favs.includes(name)

/** MAP_008 후기 등록 — 별점 또는 혼잡 제보 중 하나는 있어야 한다 */
export function addReview(placeName, { star, crowdVote, text, photos }) {
  if (!star && !crowdVote) return false
  const user = currentUser()
  if (!user) { toast('후기 작성은 로그인이 필요해요'); return false }
  const d = at(state.di)
  const list = state.reviews[placeName] || (state.reviews[placeName] = [])
  list.unshift({
    u: currentUserName(user), r: star || 0, c: crowdVote || '',
    d: `${d.getMonth() + 1}/${d.getDate()}`, t: text, ph: [...photos],
  })
  if (!writeLS('hangat_reviews', state.reviews)) toast('저장 공간이 가득 찼어요 (데모 한계)')
  return true
}
export const reviewsOf = name => state.reviews[name] || []

export function savePlaceImgs() {
  if (!writeLS('hangat_place_imgs', state.placeImgs)) toast('저장 공간이 가득 찼어요 (데모 한계)')
}

/* ── 코스 저장 (MY_001) ── */
/** 같은 조건·같은 경유지면 같은 코스로 보고 중복 저장을 막는다 */
export const courseKey = c =>
  `${state.F.reg}|${iso(at(state.di))}|${c.stops.map(s => (s.o ? s.o.n : s.f.n)).join('|')}`

export const isCourseSaved = () =>
  !!state.course && state.courses.some(c => c.key === courseKey(state.course))

export function saveCourse(title) {
  if (!currentUser()) { toast('코스 저장은 로그인이 필요해요'); return false }
  const c = state.course
  if (!c) return false
  const key = courseKey(c)
  if (state.courses.some(x => x.key === key)) { toast('이미 저장한 코스예요'); return false }
  state.courses.unshift({
    key, title, region: state.F.reg, startDate: iso(at(state.di)), days: c.days,
    budget: c.bud, spent: c.spent, avg: c.avg, move: c.move,
    stops: c.stops.map(s => (s.o ? s.o.n : s.f.n)), savedAt: iso(new Date()),
  })
  if (!writeLS('hangat_courses', state.courses)) toast('저장 공간이 가득 찼어요 (데모 한계)')
  else toast(`'${title}' 저장했어요 · 마이페이지에서 볼 수 있어요`)
  return true
}

export { SPOTS, FOOD, D0 }
