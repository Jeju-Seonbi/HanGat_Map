import { describe, it, expect } from 'vitest'
import { crowdOn, weatherOn, tier, tierKo, dist, drive } from './crowd.js'
import { PLACES } from '../data/places.js'

/* ═══ 원본 index.html:496~532 의 구현을 그대로 붙여 넣은 대조군 ═══
   포팅한 crowd.js 가 원본과 같은 값을 내는지 검증하기 위한 것이다.
   원본 코드를 고치지 말 것. */
const D0 = new Date('2026-07-20T00:00:00')
const at = i => { const d = new Date(D0); d.setDate(d.getDate() + i); return d }
const isoRef = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
function hashRef (s) {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) }
  return Math.abs(h)
}
function crowdRef (s, i) {
  const d = at(i)
  const w = d.getDay()
  const wk = w === 6 ? 26 : w === 0 ? 19 : w === 5 ? 11 : 0
  return Math.max(6, Math.min(99, Math.round(s.b + wk + (hashRef(s.n + isoRef(d)) % 19) - 9)))
}
function wxRef (i) {
  const d = isoRef(at(i))
  const h = hashRef('wx' + d) % 100
  const t = 28 + (hashRef('t' + d) % 6)
  const w = h < 52 ? { k: '맑음', rain: 0 } : h < 80 ? { k: '구름', rain: 0 } : { k: '비', rain: 1 }
  return { ...w, t: w.rain ? t - 3 : t, tmin: (w.rain ? t - 3 : t) - 4 }
}
function distRef (a, b) {
  const R = 6371, p = Math.PI / 180
  const dy = (b.y - a.y) * p, dx = (b.x - a.x) * p
  const q = Math.sin(dy / 2) ** 2 + Math.cos(a.y * p) * Math.cos(b.y * p) * Math.sin(dx / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(q))
}
/* ═══ 대조군 끝 ═══ */

describe('crowdOn — 원본 crowd() 와 완전히 같은 값을 낸다', () => {
  it('28개 장소 × 30일 = 840개 전부 일치', () => {
    let checked = 0
    for (const p of PLACES) {
      for (let i = 0; i < 30; i++) {
        expect(crowdOn(p, at(i)), `${p.name} +${i}일`).toBe(crowdRef(p, i))
        checked++
      }
    }
    expect(checked).toBe(PLACES.length * 30)
  })

  it('항상 6~99 범위 안에 있다', () => {
    for (const p of PLACES) {
      for (let i = 0; i < 30; i++) {
        const c = crowdOn(p, at(i))
        expect(c).toBeGreaterThanOrEqual(6)
        expect(c).toBeLessThanOrEqual(99)
      }
    }
  })

  it('같은 날짜면 몇 번을 불러도 같은 값이다 (결정적)', () => {
    const p = PLACES[0]
    const a = crowdOn(p, '2026-08-15')
    const b = crowdOn(p, new Date('2026-08-15T13:20:00'))
    expect(a).toBe(b)
  })
})

describe('weatherOn — 원본 wxOf() 와 일치', () => {
  it('30일 전부 일치', () => {
    for (let i = 0; i < 30; i++) {
      const mine = weatherOn(at(i))
      const ref = wxRef(i)
      expect(mine.k, `+${i}일 종류`).toBe(ref.k)
      expect(mine.t, `+${i}일 최고`).toBe(ref.t)
      expect(mine.tmin, `+${i}일 최저`).toBe(ref.tmin)
      expect(mine.rain).toBe(ref.rain)
    }
  })
})

describe('tier / tierKo — 원본 경계값(40, 70) 유지', () => {
  it('경계에서 정확히 갈린다', () => {
    expect(tier(39)).toBe('calm')
    expect(tier(40)).toBe('mid')
    expect(tier(69)).toBe('mid')
    expect(tier(70)).toBe('busy')
  })
  it('값이 없으면 한산이 아니라 정보 없음이다 (MAP_004 예외 조항)', () => {
    expect(tier(null)).toBe('none')
    expect(tierKo(null)).toBe('정보 없음')
    expect(tierKo(10)).toBe('한산')
    expect(tierKo(50)).toBe('보통')
    expect(tierKo(90)).toBe('붐빔')
  })
})

describe('dist / drive', () => {
  it('원본 하버사인과 일치', () => {
    const a = PLACES[0]
    const b = PLACES[18]
    expect(dist(a, b)).toBeCloseTo(distRef(a, b), 10)
  })
  it('같은 지점 거리는 0, 이동시간은 최소 5분', () => {
    const a = PLACES[0]
    expect(dist(a, a)).toBe(0)
    expect(drive(a, a)).toBe(5)
  })
  it('알려진 두 지점 거리가 상식적인 범위다 (금오름 ↔ 성산일출봉)', () => {
    const geum = PLACES.find(p => p.name === '금오름')
    const seong = PLACES.find(p => p.name === '성산일출봉')
    const km = dist(geum, seong)
    expect(km).toBeGreaterThan(55)
    expect(km).toBeLessThan(70)
  })
})
