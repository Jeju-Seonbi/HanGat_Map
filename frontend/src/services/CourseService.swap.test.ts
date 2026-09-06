import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../api/backendClient'
import { ApiError } from '../api/errors.js'
import CourseService from './CourseService'

vi.mock('../api/backendClient', () => ({ apiRequest: vi.fn() }))

afterEach(() => vi.clearAllMocks())

describe('CourseService 대안·스왑 (코스 상세 화면, 담당 정동현)', () => {
  it('대안은 방문 날짜와 코스의 다른 장소를 제외 조건으로 묻는다', async () => {
    const requestMock = vi.mocked(apiRequest).mockResolvedValue([{ place_id: 601, place_name: '두산봉' }])

    const result = await CourseService.getAlternatives(501, '2026-09-12', [502, 503])

    expect(requestMock).toHaveBeenCalledWith('/places/501/alternatives?date=2026-09-12&limit=3&exclude=502%2C503')
    expect(result).toEqual([{ place_id: 601, place_name: '두산봉' }])
  })

  it('예보가 없는 날짜의 3401은 그대로 던진다 - 화면이 이유를 보여준다', async () => {
    vi.mocked(apiRequest).mockRejectedValue(new ApiError(400, 3401, '해당 날짜의 혼잡 예보가 없습니다.'))

    await expect(CourseService.getAlternatives(501, '2026-09-12', [])).rejects.toMatchObject({ code: 3401 })
  })

  it('교체는 선택한 장소만 보내고, 저장 코스(본인)일 때만 JWT를 붙인다', async () => {
    const requestMock = vi.mocked(apiRequest).mockResolvedValue({
      course_id: 7, average_congestion_rate: 26.04, congestion_level: 'QUIET', congestion_label: '여유', updated_items: [], message: '바꿨어요',
    })

    const summary = await CourseService.swapItem('7', 31, 601, true)

    expect(requestMock).toHaveBeenCalledWith('/courses/7/items/31/swap', { method: 'POST', body: { place_id: 601 }, auth: true })
    expect(summary).toEqual({ averageRate: 26.04, levelLabel: '여유', message: '바꿨어요' })

    await CourseService.swapItem('7', 31, 601, false)
    expect(requestMock).toHaveBeenLastCalledWith('/courses/7/items/31/swap', expect.objectContaining({ auth: false }))
  })
})
