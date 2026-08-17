import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth.js'

const savedValues = new Map()
vi.stubGlobal('localStorage', {
  getItem: key => savedValues.get(key) ?? null,
  setItem: (key, value) => savedValues.set(key, String(value)),
  removeItem: key => savedValues.delete(key),
})

const mapStore = await import('./mapStore.js')

function sampleCourse() {
  return {
    days: 1,
    bud: 150000,
    spent: 3000,
    avg: 24,
    pav: 55,
    move: 20,
    stops: [{ d: 1, t: '10:00', o: { n: '금오름' } }],
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  savedValues.clear()
  mapStore.state.course = sampleCourse()
  mapStore.state.courses = []
  mapStore.state.favs = []
  mapStore.state.reviews = {}
  mapStore.state.toast = ''
})

describe('map member actions', () => {
  it('uses the shared demo-account session for save, favorite, and review actions', () => {
    const auth = useAuthStore()
    auth.user = { userId: 'u1', nickname: '한갓이', email: 'test@hangat.kr' }

    expect(mapStore.saveCourse('데모 코스')).toBe(true)
    expect(mapStore.toggleFav('금오름')).toBe(true)
    expect(mapStore.addReview('금오름', {
      star: 5,
      crowdVote: 'calm',
      text: '한적했어요',
      photos: [],
    })).toBe(true)

    expect(mapStore.state.courses).toHaveLength(1)
    expect(mapStore.state.favs).toContain('금오름')
    expect(mapStore.state.reviews['금오름'][0].u).toBe('한갓이')
    expect(mapStore.state).not.toHaveProperty('user')
  })

  it('rejects the same member actions after the shared session is cleared', () => {
    const auth = useAuthStore()
    auth.user = null

    expect(mapStore.saveCourse('로그아웃 코스')).toBe(false)
    expect(mapStore.toggleFav('금오름')).toBe(false)
    expect(mapStore.addReview('금오름', {
      star: 5,
      crowdVote: '',
      text: '',
      photos: [],
    })).toBe(false)

    expect(mapStore.state.courses).toEqual([])
    expect(mapStore.state.favs).toEqual([])
    expect(mapStore.state.reviews).toEqual({})
  })
})
