import { describe, expect, it } from 'vitest'
import { courseGenerationResponseFixture } from '../test/courseGenerationFixtures'
import {
  congestionForVisit,
  formatCourseTime,
  weatherForVisit,
} from './courseGenerationPresentation'

describe('course generation presentation', () => {
  it('formats backend LocalTime as HH:mm', () => {
    expect(formatCourseTime('09:00:00')).toBe('09:00')
    expect(formatCourseTime(null)).toBe('시간 미정')
  })

  it('does not turn missing congestion or weather into a calm or synthetic fact', () => {
    const item = courseGenerationResponseFixture().days[0].items[0]
    expect(congestionForVisit(item, '2026-08-28')).toBeUndefined()
    expect(weatherForVisit(item, '2026-08-28')).toBeUndefined()
  })
})
