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
    expect(cards[0].levelLabel).toBe('한산')
  })

  it('백엔드가 죽으면 목업 폴백이고, 목업 카드는 목업 id를 쓰는 상세로 보낸다', async () => {
    vi.mocked(apiGet).mockRejectedValue(new Error('HTTP 500'))

    const { live, cards } = await CalmPlaceService.getCalmPlaces(3)

    expect(live).toBe(false)
    expect(cards).toHaveLength(3)
    cards.forEach(card => expect(card.to).toMatch(/^\/places\/[^/]+$/))
  })

  it('모든 카드에 이동 경로가 있다 - 눌러도 아무 일 없는 카드를 만들지 않는다', async () => {
    vi.mocked(apiGet).mockResolvedValue([row, { ...row, placeId: 1518, name: '제주맥주' }])

    const { cards } = await CalmPlaceService.getCalmPlaces()

    expect(cards.every(card => card.to.length > 0)).toBe(true)
  })
})
