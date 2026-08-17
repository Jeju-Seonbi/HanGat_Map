import { describe, expect, it } from 'vitest'
import { accommodationMockService } from './accommodationMockService'
import { accommodationSearchService, normalizeKakaoAccommodationResults } from './accommodationSearchService'
import type { CourseResult } from '../assets/types/course'

function eastCentredCourse(): CourseResult {
  return {
    id: 1,
    course_type: 'USER',
    generation_reason: 'INITIAL',
    status: 'READY',
    start_date: '2026-08-13',
    end_date: '2026-08-15',
    people: 2,
    budget_total: 500000,
    transport: 'RENTAL_CAR',
    days: [1, 2, 3].map(dayNo => ({
      day_no: dayNo,
      visit_date: `2026-08-${12 + dayNo}`,
      items: [{
        id: dayNo,
        course_id: 1,
        place_id: 100 + dayNo,
        place_name: ['비자림', '섭지코지', '세화해변'][dayNo - 1],
        category_name: '관광지',
        day_no: dayNo,
        position: 1,
        visit_date: `2026-08-${12 + dayNo}`,
        item_source: 'AI_RECOMMENDED',
        costs: [],
      }],
    })),
  }
}

describe('accommodationMockService', () => {
  it('returns only partial-name matches and requires at least two characters', async () => {
    expect((await accommodationSearchService.searchAccommodations('롯')).items).toEqual([])
    const matches = (await accommodationSearchService.searchAccommodations('롯데')).items
    expect(matches.length).toBeGreaterThan(0)
    expect(matches.every(item => item.place_name.includes('롯데'))).toBe(true)
    expect(matches.some(item => item.place_name.includes('성산'))).toBe(false)
    expect(matches.every(item => item.source_code === 'KAKAO_LOCAL' && typeof item.source_place_id === 'string')).toBe(true)
    expect(matches.every(item => !('place_id' in item))).toBe(true)
  })

  it('returns five accommodations per page without accumulating previous results', async () => {
    const first = await accommodationSearchService.searchAccommodations('제주', 1)
    const second = await accommodationSearchService.searchAccommodations('제주', 2)
    expect(first.items).toHaveLength(5)
    expect(first.has_next_page).toBe(true)
    expect(second.items).toHaveLength(3)
    expect(second.has_next_page).toBe(false)
    expect(new Set([...first.items, ...second.items].map(item => item.source_place_id)).size).toBe(8)
  })

  it('keeps valid Jeju stays, removes unrelated results, and preserves exact-name accuracy first', () => {
    const results = normalizeKakaoAccommodationResults([
      { id: '1', place_name: '제주 리조트', address_name: '제주특별자치도 제주시', x: '126.5', y: '33.5', category_group_code: 'AD5', category_name: '여행 > 숙박' },
      { id: '2', place_name: '롯데호텔 제주', address_name: '제주특별자치도 서귀포시', x: '126.4', y: '33.2', category_name: '여행 > 호텔' },
      { id: '3', place_name: '서울 호텔', address_name: '서울특별시 중구', x: '126.9', y: '37.5', category_group_code: 'AD5', category_name: '여행 > 숙박' },
      { id: '4', place_name: '제주 식당', address_name: '제주특별자치도 제주시', x: '126.5', y: '33.5', category_name: '음식점' },
    ], '롯데호텔 제주')

    expect(results.map(item => item.source_place_id)).toEqual(['2', '1'])
  })

  it('recommends at most three stays near the generated course region', async () => {
    const recommendations = await accommodationMockService.getRecommendedAccommodations(eastCentredCourse())
    expect(recommendations).toHaveLength(3)
    expect(recommendations[0].region).toBe('EAST')
    expect(recommendations.filter(item => item.region === 'EAST').length).toBeGreaterThan(recommendations.filter(item => item.region !== 'EAST').length)
    expect(recommendations.every(item => item.recommendation_reason.length > 0)).toBe(true)
  })
})
