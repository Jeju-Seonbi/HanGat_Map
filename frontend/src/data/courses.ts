// 코스 목업 (메인 코스 추천 카드 + 코스 상세 페이지 공용)
// 코스는 야간 배치로 사전 생성된다는 전제의 시연 데이터. 장소는 data/data.ts의 id를 참조한다.
// scoreOverride: 그 코스 날짜의 예보(저장 시점 planned_rate 개념) - 장소 기본 예보와 다를 때만 지정

export interface SampleCourseItem {
  placeId: string
  scoreOverride?: number
}
export interface SampleCourseDay {
  day: number
  label: string
  items: SampleCourseItem[]
}
export interface SampleCourse {
  id: string
  title: string
  conditionLabel: string
  budgetLabel: string
  highlight: string
  days: SampleCourseDay[]
}

export const sampleCourses: SampleCourse[] = [
  {
    id: 'sample-aewol',
    title: '애월 돌담과 노을 1박 2일',
    conditionLabel: '애월 1박 2일 · 2인 · 15만',
    budgetLabel: '검증가 식비 포함 148,000원대',
    highlight: '서부 해안을 따라 붐비지 않는 동선으로 이었어요',
    days: [
      {
        day: 1,
        label: '애월 바다와 마을',
        items: [{ placeId: 'aewol' }, { placeId: 'aewol-restaurant' }],
      },
      {
        day: 2,
        label: '억새 능선의 아침',
        items: [{ placeId: 'saebyeol' }],
      },
    ],
  },
  {
    id: 'sample-seongsan',
    title: '성산 곁 숨은 동부 1박 2일',
    conditionLabel: '성산 1박 2일 · 2인 · 15만',
    budgetLabel: '검증가 식비 포함 152,000원대',
    highlight: '일출봉 인파 대신 같은 동부의 한적한 명소를 골랐어요',
    days: [
      {
        day: 1,
        label: '성산 곁 연못과 카페',
        items: [{ placeId: 'honinji' }, { placeId: 'pyeongdaedang' }],
      },
      {
        day: 2,
        label: '숲과 오름',
        items: [{ placeId: 'bijarim' }, { placeId: 'darangshi' }],
      },
    ],
  },
  {
    id: 'sample-jejusi',
    title: '제주시 가볍게 당일치기',
    conditionLabel: '제주시 당일 · 2인 · 6만',
    budgetLabel: '검증가 식비 포함 58,000원대',
    highlight: '공항에서 가까운 해안 순서로 반나절 동선을 짰어요',
    days: [
      {
        day: 1,
        label: '해안 따라 반나절',
        items: [
          { placeId: 'yongduam' },
          { placeId: 'aewol' },
          { placeId: 'aewol-restaurant' },
        ],
      },
    ],
  },
  {
    // 추천 결과 페이지의 데모 코스를 저장한 최종본 (스왑·날짜 이동 반영 후)
    id: 'demo-course',
    title: '바람 따라, 한적한 동·서부 3일',
    conditionLabel: 'AI 생성 · 2026.08.13 ~ 08.15 · 2인 · 렌터카',
    budgetLabel: '검증가 식비 포함',
    highlight: '붐비는 곳은 예보가 낮은 날짜와 한적한 대안으로 바꿔 이었어요',
    days: [
      {
        day: 1,
        label: '동부의 숲과 오름',
        items: [
          { placeId: 'bijarim' },
          { placeId: 'pyeongdaedang' },
          { placeId: 'darangshi' },
          { placeId: 'gwangchigi', scoreOverride: 58 },
        ],
      },
      {
        day: 2,
        label: '성산 곁 바다',
        items: [{ placeId: 'honinji' }],
      },
      {
        day: 3,
        label: '서부 바다와 마을',
        items: [
          { placeId: 'aewol' },
          { placeId: 'aewol-restaurant' },
          { placeId: 'saebyeol' },
        ],
      },
    ],
  },
]

// 메인 페이지 코스 추천 카드에 노출하는 3종 (데모 코스 제외)
export const homeCourses = sampleCourses.filter((c) => c.id !== 'demo-course')
