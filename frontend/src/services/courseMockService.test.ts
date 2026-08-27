import { describe, expect, it } from 'vitest'
import { courseMockService, generateMockCourseForTest } from './courseMockService'
import type { CourseCondition, CourseItem, PlacePreference } from '../assets/types/course'

const east = { region_id: 1, code: 'EAST' as const, name: '동부' }
const nature = { tag_id: 1, code: 'NATURE', name: '자연', weight: 1 }
const eastPlaceNames = new Set(['비자림', '세화해변', '성산일출봉', '섭지코지', '아부오름', '월정리 카페거리', '김녕해수욕장', '만장굴', '다랑쉬오름'])
const westPlaceNames = new Set(['애월 해안도로', '한담해변', '새별오름'])

function makeCondition(placePreferences: PlacePreference[] = []): CourseCondition {
  return {
    start_date: '2026-08-13',
    end_date: '2026-08-15',
    people: 2,
    budget_total: 500000,
    transport: 'RENTAL_CAR',
    course_regions: [east],
    course_styles: [nature],
    course_place_preferences: placePreferences,
  }
}

function minutes(time: string) {
  const [hour, minute] = time.split(':').map(Number)
  return hour * 60 + minute
}

function itemsOverlap(a: CourseItem, b: CourseItem) {
  if (!a.start_time || !a.end_time || !b.start_time || !b.end_time) return false
  return minutes(a.start_time) < minutes(b.end_time) && minutes(b.start_time) < minutes(a.end_time)
}

describe('courseMockService logical Mock generation', () => {
  it('places USER_FIXED first and prevents every same-day time conflict', async () => {
    const condition = makeCondition([
      { place_id: 101, place_name: '비자림', preference_type: 'WANT', fixed_date: '2026-08-14', fixed_time: '13:00' },
    ])

    const result = await generateMockCourseForTest(condition, 'INITIAL')
    const fixedDay = result.days[1]
    const fixed = fixedDay.items.find(item => item.place_name === '비자림')

    expect(fixed).toMatchObject({ visit_date: '2026-08-14', start_time: '13:00', item_source: 'USER_FIXED' })
    expect(fixedDay.items.map(item => item.start_time)).toEqual([...fixedDay.items.map(item => item.start_time)].sort())
    for (let first = 0; first < fixedDay.items.length; first += 1) {
      for (let second = first + 1; second < fixedDay.items.length; second += 1) {
        expect(itemsOverlap(fixedDay.items[first], fixedDay.items[second])).toBe(false)
      }
    }
    expect(fixedDay.items.some(item => item.start_time === '12:00')).toBe(false)
  })

  it('prioritizes EAST recommendations when EAST is preferred', async () => {
    const result = await generateMockCourseForTest(makeCondition(), 'INITIAL')
    const recommendations = result.days.flatMap(day => day.items).filter(item => item.item_source === 'AI_RECOMMENDED')
    const eastCount = recommendations.filter(item => eastPlaceNames.has(item.place_name)).length
    const otherRegionCount = recommendations.length - eastCount

    expect(eastCount).toBeGreaterThan(otherRegionCount)
    for (const day of result.days) expect(day.items.some(item => eastPlaceNames.has(item.place_name))).toBe(true)
  })

  it('excludes an AVOID place by both name matching and candidate selection', async () => {
    const result = await generateMockCourseForTest(makeCondition([
      { place_id: -10, place_name: '성산일출봉', preference_type: 'AVOID' },
    ]), 'INITIAL')

    expect(result.days.flatMap(day => day.items).some(item => item.place_name === '성산일출봉')).toBe(false)
  })

  it('always includes a non-fixed WANT place', async () => {
    const result = await generateMockCourseForTest(makeCondition([
      { place_id: 101, place_name: '비자림', preference_type: 'WANT' },
    ]), 'INITIAL')
    const want = result.days.flatMap(day => day.items).find(item => item.place_name === '비자림')

    expect(want).toBeDefined()
    expect(want?.recommendation_reason).toBe('꼭 가고 싶은 장소로 선택해 일정에 포함했어요.')
  })

  it('builds recommendation reasons only from the actual place region and matched conditions', async () => {
    const cafeCondition = makeCondition()
    cafeCondition.course_styles = [{ tag_id: 3, code: 'CAFE', name: '카페', weight: 1 }]
    cafeCondition.course_place_preferences = [
      { place_id: 109, place_name: '애월 해안도로', preference_type: 'WANT', fixed_date: '2026-08-13', fixed_time: '12:00' },
    ]
    const result = await generateMockCourseForTest(cafeCondition, 'INITIAL')
    const westItems = result.days.flatMap(day => day.items).filter(item => westPlaceNames.has(item.place_name) && item.item_source === 'AI_RECOMMENDED')

    expect(westItems.length).toBeGreaterThan(0)
    expect(westItems.every(item => !item.recommendation_reason?.includes('동부 권역'))).toBe(true)
    expect(result.days.flatMap(day => day.items).every(item => Boolean(item.recommendation_reason))).toBe(true)
    const generalReasons = result.days.flatMap(day => day.items)
      .filter(item => item.item_source === 'AI_RECOMMENDED' && item.place_name !== '애월 해안도로')
      .map(item => item.recommendation_reason)
    expect(new Set(generalReasons).size).toBeGreaterThan(1)
  })

  it('keeps a late USER_FIXED time and exposes its long gap and operating-hours warning', async () => {
    const condition = makeCondition([
      { place_id: 101, place_name: '비자림', preference_type: 'WANT', fixed_date: '2026-08-13', fixed_time: '22:00' },
    ])
    condition.end_date = condition.start_date

    const result = await generateMockCourseForTest(condition, 'INITIAL')
    const fixed = result.days[0].items.find(item => item.place_name === '비자림')

    expect(fixed).toMatchObject({
      start_time: '22:00',
      item_source: 'USER_FIXED',
      recommendation_reason: '사용자가 지정한 일정으로 유지했어요.',
      operating_hours_warning: true,
    })
    expect(fixed?.gap_before).toMatchObject({ type: 'FREE_TIME', start_time: '14:00', end_time: '22:00', minutes: 480 })
  })

  it('places operating-hours-aware AI recommendations only in valid slots', async () => {
    const result = await generateMockCourseForTest(makeCondition(), 'INITIAL')
    const items = result.days.flatMap(day => day.items)
    const abuOreum = items.find(item => item.place_name === '아부오름')

    expect(abuOreum).toBeDefined()
    expect(minutes(abuOreum?.start_time ?? '00:00')).toBeGreaterThanOrEqual(minutes('10:00'))
    expect(minutes(abuOreum?.end_time ?? '23:59')).toBeLessThanOrEqual(minutes('18:00'))
    expect(items.filter(item => item.item_source === 'AI_RECOMMENDED').every(item => item.operating_hours_warning !== true)).toBe(true)
    expect(items.filter(item => item.item_source === 'AI_RECOMMENDED').every(item => item.start_time !== '22:00')).toBe(true)
  })

  it('recalculates realistic movement after sorting and preserves priorities on regeneration', async () => {
    const condition = makeCondition([
      { place_id: 101, place_name: '비자림', preference_type: 'WANT', fixed_date: '2026-08-14', fixed_time: '13:00' },
      { place_id: -2, place_name: '협재해수욕장', preference_type: 'WANT' },
      { place_id: 103, place_name: '성산일출봉', preference_type: 'AVOID' },
    ])
    const initial = await generateMockCourseForTest(condition, 'INITIAL')
    const regenerated = await generateMockCourseForTest(condition, 'USER_REGENERATE')
    const initialRecommendations = initial.days.flatMap(day => day.items).filter(item => item.place_id > 0 && item.place_name !== '비자림').map(item => item.place_name)
    const regeneratedItems = regenerated.days.flatMap(day => day.items)
    const regeneratedRecommendations = regeneratedItems.filter(item => item.place_id > 0 && item.place_name !== '비자림').map(item => item.place_name)
    const fixed = regeneratedItems.find(item => item.place_name === '비자림')

    expect(regeneratedItems.some(item => item.place_name === '협재해수욕장')).toBe(true)
    expect(regeneratedItems.some(item => item.place_name === '성산일출봉')).toBe(false)
    expect(fixed).toMatchObject({ visit_date: '2026-08-14', start_time: '13:00', item_source: 'USER_FIXED' })
    expect(regeneratedRecommendations).not.toEqual(initialRecommendations)

    for (const day of regenerated.days) {
      expect(day.items[0].inbound_travel_minutes).toBeUndefined()
      day.items.slice(1).forEach(item => {
        expect(item.inbound_distance_m).toBeGreaterThanOrEqual(1200)
        expect(item.inbound_travel_minutes).toBeGreaterThanOrEqual(5)
      })
    }
  })

  it('generates the existing course normally when accommodation is undefined', async () => {
    const result = await generateMockCourseForTest(makeCondition(), 'INITIAL')

    expect(result.accommodation).toBeUndefined()
    expect(result.days).toHaveLength(3)
    expect(result.days.every(day => day.items.length > 0)).toBe(true)
  })

  it('uses accommodation coordinates as the start and return route anchor', async () => {
    const condition = makeCondition()
    condition.course_regions = [{ region_id: 2, code: 'WEST', name: '서부' }]
    condition.accommodation = {
      source_code: 'KAKAO_LOCAL',
      source_place_id: 'MOCK_KAKAO_9003',
      place_name: '성산 오션호텔',
      address: '제주 서귀포시 성산읍',
      latitude: 33.4580,
      longitude: 126.9360,
    }

    const result = await generateMockCourseForTest(condition, 'INITIAL')
    expect(result.accommodation).toEqual(condition.accommodation)
    for (const day of result.days) {
      expect(eastPlaceNames.has(day.items[0].place_name)).toBe(true)
      expect(eastPlaceNames.has(day.items.at(-1)?.place_name ?? '')).toBe(true)
    }
    expect(result.days.flatMap(day => day.items).some(item => item.accommodation_influenced)).toBe(true)
  })

  it('keeps USER_FIXED unchanged even when it is far from the accommodation', async () => {
    const condition = makeCondition([
      { place_id: 103, place_name: '성산일출봉', preference_type: 'WANT', fixed_date: '2026-08-14', fixed_time: '13:00' },
    ])
    condition.accommodation = { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9005', place_name: '애월 호텔', latitude: 33.4622, longitude: 126.3098 }

    const result = await generateMockCourseForTest(condition, 'INITIAL')
    const fixed = result.days.flatMap(day => day.items).find(item => item.place_name === '성산일출봉')

    expect(fixed).toMatchObject({ visit_date: '2026-08-14', start_time: '13:00', item_source: 'USER_FIXED' })
    expect(result.accommodation?.place_name).toBe('애월 호텔')
  })

  it('keeps a WANT place even when it is far from the accommodation', async () => {
    const condition = makeCondition([
      { place_id: 108, place_name: '산방산', preference_type: 'WANT' },
    ])
    condition.accommodation = { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9003', place_name: '성산 호텔', latitude: 33.4580, longitude: 126.9360 }

    const result = await generateMockCourseForTest(condition, 'INITIAL')
    const want = result.days.flatMap(day => day.items).find(item => item.place_name === '산방산')

    expect(want).toBeDefined()
    expect(want?.recommendation_reason).toBe('꼭 가고 싶은 장소로 선택해 일정에 포함했어요.')
  })

  it('recalculates around a selected accommodation while preserving fixed, WANT, and AVOID priorities', async () => {
    const condition = makeCondition([
      { place_id: 101, place_name: '비자림', preference_type: 'WANT', fixed_date: '2026-08-14', fixed_time: '13:00' },
      { place_id: 108, place_name: '산방산', preference_type: 'WANT' },
      { place_id: 103, place_name: '성산일출봉', preference_type: 'AVOID' },
    ])
    const accommodation = { source_code: 'KAKAO_LOCAL' as const, source_place_id: 'MOCK_KAKAO_9003', place_name: '성산 마리나 호텔', region: 'EAST' as const, latitude: 33.4612, longitude: 126.9324 }

    const result = await courseMockService.recalculateRouteWithAccommodation(condition, accommodation)
    const items = result.days.flatMap(day => day.items)

    expect(result.accommodation).toEqual(accommodation)
    expect(items.find(item => item.place_name === '비자림')).toMatchObject({ visit_date: '2026-08-14', start_time: '13:00', item_source: 'USER_FIXED' })
    expect(items.some(item => item.place_name === '산방산')).toBe(true)
    expect(items.some(item => item.place_name === '성산일출봉')).toBe(false)
  })

  it('returns up to three same-subcategory calm alternatives within 10km first', async () => {
    const condition = makeCondition([
      { place_id: 103, place_name: '성산일출봉', preference_type: 'WANT', fixed_date: '2026-08-13', fixed_time: '09:00' },
    ])
    const course = await generateMockCourseForTest(condition, 'INITIAL')
    const target = course.days[0].items.find(item => item.place_name === '성산일출봉')!

    const alternatives = await courseMockService.getAlternativePlaces(course, target.id, condition)

    expect(alternatives).toHaveLength(3)
    expect(alternatives.every(item => item.category_name.includes('오름'))).toBe(true)
    expect(alternatives.every(item => item.congestion_level === 'QUIET' && (item.congestion_rate ?? 100) < 35)).toBe(true)
    expect(alternatives.every(item => item.distance_m <= 10000 && item.radius_km === 10)).toBe(true)
  })

  it('returns preference and route-scored alternatives for a non-crowded AI item', async () => {
    const condition = makeCondition()
    const course = await generateMockCourseForTest(condition, 'INITIAL')
    const scheduledIds = new Set(course.days.flatMap(day => day.items.map(item => item.place_id)))
    const candidates = course.days.flatMap(day => day.items).filter(item => item.item_source === 'AI_RECOMMENDED' && item.congestion_level !== 'CROWDED')
    let alternatives: Awaited<ReturnType<typeof courseMockService.getAlternativePlaces>> = []
    let target = candidates[0]
    for (const candidate of candidates) {
      const result = await courseMockService.getAlternativePlaces(course, candidate.id, condition)
      if (result.length) {
        target = candidate
        alternatives = result
        break
      }
    }

    expect(target).toBeDefined()
    expect(alternatives.length).toBeGreaterThan(0)
    expect(alternatives.length).toBeLessThanOrEqual(3)
    expect(alternatives.every(item => !scheduledIds.has(item.place_id))).toBe(true)
    expect(alternatives.every(item => item.recommendation_reason.length > 0 && item.replacement_reason.length > 0)).toBe(true)
  })

  it('excludes AVOID and already scheduled places from alternative candidates', async () => {
    const condition = makeCondition([
      { place_id: 103, place_name: '성산일출봉', preference_type: 'WANT', fixed_date: '2026-08-13', fixed_time: '09:00' },
      { place_id: 120, place_name: '대수산봉', preference_type: 'AVOID' },
    ])
    const course = await generateMockCourseForTest(condition, 'INITIAL')
    const target = course.days[0].items.find(item => item.place_name === '성산일출봉')!
    const duplicateSource = course.days.flatMap(day => day.items).find(item => item.id !== target.id)!
    duplicateSource.place_id = 121
    duplicateSource.place_name = '두산봉'

    const alternatives = await courseMockService.getAlternativePlaces(course, target.id, condition)

    expect(alternatives.some(item => item.place_id === 120)).toBe(false)
    expect(alternatives.some(item => item.place_id === 121)).toBe(false)
  })

  it('replaces only one item while preserving its schedule and recalculating route, cost, and congestion', async () => {
    const condition = makeCondition([
      { place_id: 103, place_name: '성산일출봉', preference_type: 'WANT', fixed_date: '2026-08-13', fixed_time: '09:00' },
    ])
    condition.accommodation = { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9003', place_name: '성산 마리나 호텔', region: 'EAST', latitude: 33.4612, longitude: 126.9324 }
    const course = await generateMockCourseForTest(condition, 'INITIAL')
    const target = course.days[0].items.find(item => item.place_name === '성산일출봉')!
    const alternatives = await courseMockService.getAlternativePlaces(course, target.id, condition)
    const replacement = alternatives[0]
    const originalSchedule = { visit_date: target.visit_date, start_time: target.start_time, end_time: target.end_time }
    const otherItems = course.days.flatMap(day => day.items).filter(item => item.id !== target.id).map(item => ({ id: item.id, place_id: item.place_id, visit_date: item.visit_date, start_time: item.start_time, end_time: item.end_time }))
    const previousAverage = course.average_congestion_rate!
    const previousCostMin = course.estimated_cost_min!
    const previousDepartureDistance = course.days[0].accommodation_departure_distance_m

    const replaced = await courseMockService.replaceCourseItem(course, target.id, replacement)
    const changed = replaced.days[0].items.find(item => item.id === target.id)!
    const remainingItems = replaced.days.flatMap(day => day.items).filter(item => item.id !== target.id).map(item => ({ id: item.id, place_id: item.place_id, visit_date: item.visit_date, start_time: item.start_time, end_time: item.end_time }))

    expect(changed).toMatchObject({
      ...originalSchedule,
      place_id: replacement.place_id,
      item_source: 'REPLACEMENT',
      replaced_from_place_id: 103,
      congestion_level: 'QUIET',
    })
    expect(remainingItems).toEqual(otherItems)
    expect(replaced.average_congestion_rate).toBeLessThan(previousAverage)
    expect(replaced.estimated_cost_min).toBeLessThan(previousCostMin)
    expect(replaced.days[0].accommodation_departure_distance_m).not.toBe(previousDepartureDistance)
    expect(replaced.days[0].items[1].inbound_distance_m).toBeDefined()
    expect(replaced.days[0].accommodation_return_travel_minutes).toBeDefined()
  })
})
