import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./AiCourseView.vue', import.meta.url), 'utf8')

describe('AiCourseView backend generation integration', () => {
  it('preserves the original result UI components and flows', () => {
    for (const component of [
      'CourseConditionForm',
      'CourseItemCard',
      'BudgetGauge',
      'AlternativePlaceModal',
      'CongestionRescheduleModal',
      'AccommodationRecommendations',
    ]) {
      expect(source).toContain(component)
    }
    expect(source).toContain('코스 저장')
    expect(source).toContain('다른 코스 만들기')
  })

  it('uses POST /courses for generation instead of the mock generator', () => {
    const generateBody = source.slice(
      source.indexOf('async function generate'),
      source.indexOf('async function selectRecommendedAccommodation'),
    )
    expect(generateBody).toContain('courseApiService.createCourse')
    expect(generateBody).toContain('adaptCourseGenerationResponse')
    expect(generateBody).not.toContain('courseMockService.generateCourse')
    expect(generateBody).not.toContain('courseMockService.regenerateCourse')
  })

  it('keeps the original local loading and error flow around the API call', () => {
    expect(source).toContain('if (loading.value) return false')
    expect(source).toContain('loading.value = true')
    expect(source).toContain('error.value = courseGenerationErrorMessage(failure)')
    expect(source).toContain('finally {\n    loading.value = false')
  })
})
