import { describe, expect, it } from 'vitest'
import {
  courseConditionFixture,
  courseGenerationResponseFixture,
} from '../test/courseGenerationFixtures'
import { adaptCourseGenerationResponse } from './courseGenerationAdapter'

describe('courseGenerationAdapter', () => {
  it('maps the backend response into the original result view model without fake DB IDs', () => {
    const response = courseGenerationResponseFixture()
    response.days[0].items[0].congestion = [{ date: '2026-08-28', rate: 22.5, level: 'QUIET' }]
    const result = adaptCourseGenerationResponse(response, courseConditionFixture())
    const item = result.days[0].items[0]

    expect(result).not.toHaveProperty('id')
    expect(item).not.toHaveProperty('id')
    expect(item).not.toHaveProperty('course_id')
    expect(item).not.toHaveProperty('place_id')
    expect(item.candidate_id).toBe('candidate-1')
    expect(item.start_time).toBe('09:00')
    expect(item.congestion_rate).toBe(22.5)
    expect(item.costs).toEqual([])
  })

  it('keeps missing congestion and weather as missing facts', () => {
    const result = adaptCourseGenerationResponse(
      courseGenerationResponseFixture(), courseConditionFixture())
    const item = result.days[0].items[0]

    expect(item.congestion_rate).toBeUndefined()
    expect(item.congestion_level).toBeUndefined()
    expect(item.weather_condition).toBeUndefined()
    expect(item.temperature).toBeUndefined()
  })

  it('maps only the exact visit-time weather fact into the existing card fields', () => {
    const response = courseGenerationResponseFixture()
    response.days[0].items[0].weather = [{
      forecast_date: '2026-08-28',
      forecast_time: '09:00:00',
      temperature: 27.5,
      precipitation_probability: 70,
      precipitation_type_code: '1',
      sky_condition_code: '4',
      wind_speed: 2.1,
      humidity: 65,
    }]
    const item = adaptCourseGenerationResponse(
      response, courseConditionFixture()).days[0].items[0]

    expect(item.weather_condition).toBe('RAIN')
    expect(item.temperature).toBe(27.5)
    expect(item.precipitation_probability).toBe(70)
  })
})
