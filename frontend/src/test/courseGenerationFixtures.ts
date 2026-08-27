import type { CourseCondition, CourseGenerationResponse } from '../assets/types/course'

export const courseConditionFixture = (): CourseCondition => ({
  start_date: '2026-08-27',
  end_date: '2026-08-29',
  people: 2,
  budget_total: 500000,
  transport: 'RENTAL_CAR',
  course_regions: [{ region_id: 1, code: 'EAST', name: '동부' }],
  course_styles: [{ tag_id: 1, code: 'NATURE', name: '자연', weight: 1 }],
  course_place_preferences: [{
    source_code: 'KAKAO_LOCAL',
    source_place_id: 'want-1',
    place_name: '성산일출봉',
    preference_type: 'WANT',
    fixed_date: '2026-08-28',
    fixed_time: '09:00',
  }],
})

export const courseGenerationResponseFixture = (): CourseGenerationResponse => ({
  contract_version: '1.0',
  start_date: '2026-08-27',
  end_date: '2026-08-29',
  days: [{
    day_no: 1,
    visit_date: '2026-08-28',
    items: [{
      candidate_id: 'candidate-1',
      place_name: '성산일출봉',
      address: '제주특별자치도 서귀포시 성산읍',
      latitude: 33.458,
      longitude: 126.942,
      image_url: null,
      tour_category: { category1: 'A01', category2: null, category3: null },
      region_code: 'EAST',
      preference_type: 'WANT',
      confirmed_style_hints: ['NATURE'],
      position: 1,
      start_time: '09:00:00',
      item_source: 'USER_FIXED',
      recommendation_reason: '사용자가 고정한 일정과 동부 동선을 함께 고려했어요.',
      congestion: [],
      weather: null,
    }],
  }],
})
