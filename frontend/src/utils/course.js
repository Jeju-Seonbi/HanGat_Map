import { SPOTS, FOOD, RELATE } from '@/data/placesMap'
import { crowd } from './crowd'
import { drive } from './geo'
import { wxOf } from './weather'

/* 코스 생성 — 실서비스에서는 AI 코스 페이지(COURSE_004)가 만들고 지도는 결과 표시만 한다(MAP_006).
   여기서는 연동 전 샘플 생성기로만 쓴다. 점수식은 기능설명서 공개용 원본 그대로 */

/** 1박2일 = 7슬롯 (관광 s / 식사 m) */
export function slotsFor(n) {
  const s = []
  for (let d = 1; d <= n; d++) {
    const first = d === 1, last = d === n
    s.push({ d, t: first ? '10:00' : '09:30', k: 's' })
    s.push({ d, t: '12:30', k: 'm' })
    if (!last) s.push({ d, t: '15:00', k: 's' }, { d, t: '18:00', k: 'm' })
    else s.push({ d, t: '14:30', k: 's' })
  }
  return s
}

/**
 * 샘플 코스를 만든다. 후보가 부족하면 null.
 * 결측(b:null) 장소는 제외 — 실서비스 규칙은 권역 중앙값 부여(0점 취급 금지)
 */
export function buildCourse({ region, budget, dayIndex, useRain }) {
  const days = 2
  const rain = useRain && wxOf(dayIndex).rain
  const pool = SPOTS.filter(s => (region === '전체' || s.r === region) && s.b != null)
  const fds = FOOD.filter(f => region === '전체' || f.r === region)
  if (pool.length < 2) return null

  const used = new Set()
  let prev = null, spent = 0
  const stops = []

  slotsFor(days).forEach(sl => {
    const day = dayIndex + sl.d - 1
    if (sl.k === 's') {
      const cs = pool.filter(s => !used.has(s.n)).map(s => {
        const c = crowd(s, day)
        const rel = prev && RELATE[prev.n] ? RELATE[prev.n].indexOf(s.n) : -1
        const mv = prev ? drive(prev, s) : 0
        const over = Math.max(0, spent + s.fee - budget)
        return { s, c, mv, rel, sc: (100 - c) + (rel >= 0 ? (4 - rel) * 6 : 0) - mv * 0.8 - over / 1000 * 1.2 + (rain && s.in ? 22 : 0) }
      }).sort((a, b) => b.sc - a.sc)
      if (!cs.length) return
      const top = cs[0]
      const wst = pool.map(s => ({ s, c: crowd(s, day) })).sort((a, b) => b.c - a.c)[0]
      const why = rain && top.s.in ? '비 예보라 실내로 골랐어요'
        : top.rel >= 0 && prev ? `${prev.n} 가는 사람들이 함께 들르는 곳 ${top.rel + 1}위`
          : wst.c - top.c > 25 ? `오늘 혼잡한 ${wst.s.n} 대신 골랐어요` : '이 날 이 권역에서 가장 한산해요'
      used.add(top.s.n); spent += top.s.fee; prev = top.s
      stops.push({ ...sl, o: top.s, c: top.c, why, cost: top.s.fee, mv: top.mv, alt: cs.slice(1, 4).map(x => x.s.n) })
    } else {
      const cs = fds.filter(f => !used.has(f.n)).map(f => {
        const mv = prev ? drive(prev, f) : 0
        return { f, mv, sc: -mv * 1.2 - f.p / 1000 * (spent > budget * 0.6 ? 2.2 : 1) }
      }).sort((a, b) => b.sc - a.sc)
      if (!cs.length) return
      const top = cs[0], cost = top.f.p * 2
      used.add(top.f.n); spent += cost
      stops.push({ ...sl, f: top.f, cost, mv: top.mv, why: prev ? `${prev.n}에서 ${top.mv}분` : '착한가격업소', alt: cs.slice(1, 4).map(x => x.f.n) })
    }
  })

  const ss = stops.filter(s => s.o)
  const popular = pool.map(s => ({ s, c: crowd(s, dayIndex) })).sort((a, b) => b.c - a.c).slice(0, ss.length)
  return {
    stops, spent, bud: budget, days,
    avg: Math.round(ss.reduce((a, b) => a + b.c, 0) / ss.length),
    pav: Math.round(popular.reduce((a, b) => a + b.c, 0) / popular.length),
    move: stops.reduce((a, b) => a + (b.mv || 0), 0),
  }
}

/** 날짜가 바뀌면 각 경유지의 혼잡도와 평균을 다시 계산한다 */
export function refreshCourse(course, { region, dayIndex }) {
  if (!course) return
  course.stops.forEach(s => { if (s.o) s.c = crowd(s.o, dayIndex + s.d - 1) })
  const ss = course.stops.filter(x => x.o && x.c != null)
  if (ss.length) course.avg = Math.round(ss.reduce((a, b) => a + b.c, 0) / ss.length)
  const pool = SPOTS.filter(s => (region === '전체' || s.r === region) && s.b != null)
  const popular = pool.map(s => ({ s, c: crowd(s, dayIndex) })).sort((a, b) => b.c - a.c).slice(0, ss.length)
  if (popular.length) course.pav = Math.round(popular.reduce((a, b) => a + b.c, 0) / popular.length)
}

/** '다른 곳' 버튼 — 대안 목록을 한 칸 돌린다 */
export function swapStop(course, time, day, dayIndex) {
  const s = course.stops.find(x => x.t === time && x.d === day)
  if (!s || !s.alt.length) return false
  const nm = s.alt.shift()
  if (s.o) {
    const o = SPOTS.find(x => x.n === nm)
    if (!o) return false
    s.alt.push(s.o.n); s.o = o; s.c = crowd(o, dayIndex + s.d - 1); s.cost = o.fee; s.why = '직접 바꾼 곳이에요'
  } else {
    const f = FOOD.find(x => x.n === nm)
    if (!f) return false
    s.alt.push(s.f.n); s.f = f; s.cost = f.p * 2; s.why = '직접 바꾼 곳'
  }
  const ss = course.stops.filter(x => x.o)
  course.spent = course.stops.reduce((a, b) => a + (b.cost || 0), 0)
  course.avg = Math.round(ss.reduce((a, b) => a + b.c, 0) / ss.length)
  return true
}

/** 공유 URL로 받은 코스 복원 (COM_002) */
export function courseFromNames(names, { region, budget, dayIndex }) {
  const slots = slotsFor(2)
  let prev = null, spent = 0
  const stops = []
  slots.forEach((sl, i) => {
    const nm = names[i]
    if (!nm) return
    if (sl.k === 's') {
      const o = SPOTS.find(x => x.n === nm); if (!o) return
      const mv = prev ? drive(prev, o) : 0
      stops.push({ ...sl, o, c: crowd(o, dayIndex + sl.d - 1), why: '공유받은 코스예요', cost: o.fee, mv, alt: [] })
      spent += o.fee; prev = o
    } else {
      const f = FOOD.find(x => x.n === nm); if (!f) return
      const mv = prev ? drive(prev, f) : 0, cost = f.p * 2
      stops.push({ ...sl, f, cost, mv, why: prev ? `${prev.n}에서 ${mv}분` : '착한가격업소', alt: [] })
      spent += cost; prev = f
    }
  })
  const ss = stops.filter(s => s.o && s.c != null)
  if (!ss.length) return null
  const pool = SPOTS.filter(s => (region === '전체' || s.r === region) && s.b != null)
  const popular = pool.map(s => ({ s, c: crowd(s, dayIndex) })).sort((a, b) => b.c - a.c).slice(0, ss.length)
  return {
    stops, spent, bud: budget, days: 2,
    avg: Math.round(ss.reduce((a, b) => a + b.c, 0) / ss.length),
    pav: popular.length ? Math.round(popular.reduce((a, b) => a + b.c, 0) / popular.length) : 0,
    move: stops.reduce((a, b) => a + (b.mv || 0), 0),
  }
}
