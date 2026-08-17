import type { SampleCourse } from '../../data/courses'
import type { Place } from '../../assets/types'
import { levelOf } from '../../utils/congestion'

export interface ResolvedCourseDay {
  day: number
  label: string
  places: Place[]
}

export interface ResolvedCourseDetail extends Omit<SampleCourse, 'days'> {
  days: ResolvedCourseDay[]
  places: Place[]
}

export function resolveCourseDetail(
  courseId: string,
  courses: SampleCourse[],
  places: Place[],
): ResolvedCourseDetail | null {
  const course = courses.find(candidate => candidate.id === courseId)
  if (!course) return null

  const placesById = new Map(places.map(place => [place.id, place]))
  const days = course.days.map(day => ({
    day: day.day,
    label: day.label,
    places: day.items.flatMap(item => {
      const place = placesById.get(item.placeId)
      if (!place) return []
      const score = item.scoreOverride ?? place.score
      return [{ ...place, score, level: levelOf(score) }]
    }),
  }))

  return {
    ...course,
    days,
    places: days.flatMap(day => day.places),
  }
}
