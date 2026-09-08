import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiGet } from './apiClient'
import CalmPlaceService from './CalmPlaceService'

vi.mock('./apiClient', () => ({ apiGet: vi.fn() }))

afterEach(() => vi.clearAllMocks())

const row = {
  placeId: 1613, name: '제주현대미술관', regionLabel: '서부', categoryLabel: '관광지',
  imageUrl: null, rate: 10.2, level: 'QUIET' as const, levelLabel: '한산',
  reason: '이 날짜 혼잡 예보가 한산이에요',
}

describe('메인 한산 장소 카드 (MAIN_001, 담당 정동현)', () => {
  it('실데이터 카드는 지도 패널로 보낸다 - 백엔드 placeId를 아는 유일한 실 상세', async () => {
    vi.mocked(apiGet).mockResolvedValue([row])

    const { live, cards } = await CalmPlaceService.getCalmPlaces(3)

    expect(apiGet).toHaveBeenCalledWith('/main/calm-places?limit=3')
    expect(live).toBe(true)
    expect(cards[0].to).toBe('/map?place=1613')
  })

  it('백엔드가 죽으면 목업 폴백이고, 목업 카드는 목업 id를 쓰는 상세로 보낸다', async () => {
    vi.mocked(apiGet).mockRejectedValue(new Error('HTTP 500'))

    const { live, cards } = await CalmPlaceService.getCalmPlaces(3)

    expect(live).toBe(false)
    expect(cards).toHaveLength(3)
    cards.forEach(card => expect(card.to).toMatch(/^\/places\/[^/]+$/))
  })

  it('카드마다 자기 placeId로 링크를 만든다 - 지도가 찾을 수 있는 숫자 id여야 한다', async () => {
    vi.mocked(apiGet).mockResolvedValue([row, { ...row, placeId: 1518, name: '제주맥주' }])

    const { cards } = await CalmPlaceService.getCalmPlaces()

    // MapView의 ?place= 처리는 /^\d+$/만 받는다 - undefined가 섞이면 조용히 무시된다
    expect(cards.map(card => card.to)).toEqual(['/map?place=1613', '/map?place=1518'])
  })
})
