import { describe, expect, it } from 'vitest'

const groupedViews = import.meta.glob('./**/*View.vue')
const legacyPages = import.meta.glob('../pages/*.vue')

describe('route view organization', () => {
  it('keeps every route screen inside its feature view folder', () => {
    expect(Object.keys(groupedViews)).toEqual(expect.arrayContaining([
      './ai-course/AiCourseView.vue',
      './ai-course/RecommendationView.vue',
      './ai-course/TravelSearchView.vue',
      './course/CourseDetailView.vue',
      './course/SavedCoursesView.vue',
      './home/HomeView.vue',
      './map/MapView.vue',
      // place/PlaceDetailView.vue 는 목업 전용이라 제거됐다 - 장소 상세는 지도 패널(/map?place=)이다
      './share/ShareCourseView.vue',
      './system/NotFoundView.vue',
      './system/OutOfScopeView.vue',
    ]))
  })

  it('keeps the mock-only place detail screen removed', () => {
    // /places/:id 는 지도 패널(/map?place=)로 리다이렉트한다 - 목업 전용 화면이 되살아나면 실패시킨다
    expect(Object.keys(groupedViews)).not.toContain('./place/PlaceDetailView.vue')
  })

  it('does not leave route screens in the legacy pages folder', () => {
    expect(Object.keys(legacyPages)).toEqual([])
  })

  it('provides a lazy loader for each grouped view', () => {
    for (const loadView of Object.values(groupedViews)) {
      expect(loadView).toBeTypeOf('function')
    }
  })
})
