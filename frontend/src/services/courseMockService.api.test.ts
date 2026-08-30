import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CourseCondition, CourseResult } from '../assets/types/course'
import { apiRequest } from '../api/backendClient.js'
import { courseMockService } from './courseMockService'

vi.mock('../api/backendClient.js', () => ({ apiRequest: vi.fn() }))

const condition: CourseCondition = {
  start_date: '2026-08-28',
  end_date: '2026-08-29',
  people: 2,
  budget_total: 400000,
  transport: 'RENTAL_CAR',
  course_regions: [{ region_id: 1, code: 'EAST', name: '동부' }],
  course_styles: [{ tag_id: 1, code: 'NATURE', name: '자연', weight: 1 }],
  course_place_preferences: [],
  accommodation: {
    source_code: 'KAKAO_LOCAL',
    source_place_id: 'accommodation-1',
    place_name: '제주 숙소',
    latitude: 33.45,
    longitude: 126.55,
  },
}

const response: CourseResult = {
  id: 101,
  course_type: 'USER',
  generation_reason: 'INITIAL',
  status: 'READY',
  start_date: condition.start_date,
  end_date: condition.end_date,
  people: condition.people,
  budget_total: condition.budget_total,
  transport: condition.transport,
  accommodation: condition.accommodation,
  days: [{
    day_no: 1,
    visit_date: condition.start_date,
    items: [{
      id: 201,
      course_id: 101,
      place_id: 301,
      candidate_id: 'candidate-kto-1',
      source_code: 'KTO',
      source_place_id: '125266',
      place_name: '비자림',
      address: '제주특별자치도 제주시 구좌읍 비자숲길 55',
      road_address: '제주특별자치도 제주시 구좌읍 비자숲길 55',
      category_name: '관광지',
      day_no: 1,
      position: 1,
      visit_date: condition.start_date,
      start_time: '09:00',
      item_source: 'AI_RECOMMENDED',
      congestion_rate: 22.5,
      congestion_level: 'QUIET',
      recommendation_reason: '혼잡도가 낮고 자연 취향에 잘 맞아요.',
      costs: [],
    }],
  }],
}

afterEach(() => vi.clearAllMocks())

describe('courseMockService Backend generation', () => {
  it('posts the unchanged CourseCondition and returns the Backend CourseResult without fabricating data', async () => {
    const requestMock = vi.mocked(apiRequest).mockResolvedValue(response)

    const result = await courseMockService.generateCourse(condition)

    expect(requestMock).toHaveBeenCalledOnce()
    expect(requestMock).toHaveBeenCalledWith('/courses', {
      method: 'POST',
      body: condition,
    })
    expect(result).toBe(response)
    expect(result.days[0].items[0]).toMatchObject({
      id: 201,
      course_id: 101,
      place_id: 301,
      candidate_id: 'candidate-kto-1',
      source_code: 'KTO',
      source_place_id: '125266',
      road_address: '제주특별자치도 제주시 구좌읍 비자숲길 55',
      costs: [],
    })
    expect(result.days[0].items[0].end_time).toBeUndefined()
    expect(result.estimated_cost_min).toBeUndefined()
  })

  it('propagates common client failures without falling back to mock generation', async () => {
    const requestMock = vi.mocked(apiRequest)
            .mockRejectedValue(new Error('코스 생성 API 요청에 실패했습니다.'))

    await expect(courseMockService.generateCourse(condition))
      .rejects.toThrow('코스 생성 API 요청에 실패했습니다.')
    expect(requestMock).toHaveBeenCalledOnce()
  })

  it('keeps regeneration unavailable instead of sending an INITIAL request or returning mock data', async () => {
    const requestMock = vi.mocked(apiRequest)

    await expect(courseMockService.regenerateCourse(condition)).rejects.toThrow('재생성은 아직 지원되지 않습니다')
    expect(requestMock).not.toHaveBeenCalled()
  })

  it('claims a generated guest course with authenticated API and removes the proof from the result', async () => {
    const guestCourse: CourseResult = {
      ...response,
      claim_token: 'opaque-proof',
      claim_expires_at: '2026-08-31T12:30:00Z',
    }
    const requestMock = vi.mocked(apiRequest).mockResolvedValue({
      id: 101,
      status: 'SAVED',
      title: '제주 여행',
      saved_at: '2026-08-31T12:00:00',
    })

    const result = await courseMockService.saveCourse(guestCourse, '제주 여행')

    expect(requestMock).toHaveBeenCalledWith('/courses/101/claim', {
      method: 'POST',
      auth: true,
      body: { claim_token: 'opaque-proof', title: '제주 여행' },
    })
    expect(result).toMatchObject({ id: 101, status: 'SAVED', title: '제주 여행' })
    expect(result.claim_token).toBeUndefined()
    expect(result.claim_expires_at).toBeUndefined()
  })
})
