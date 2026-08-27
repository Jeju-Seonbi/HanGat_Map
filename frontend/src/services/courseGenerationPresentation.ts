import type {
  CourseGenerationCongestionFact,
  CourseGenerationItem,
  CourseGenerationWeatherFact,
} from '../assets/types/course'

export function formatCourseTime(value: string | null) {
  if (!value) return '시간 미정'
  const match = /^(\d{2}:\d{2})/.exec(value)
  return match?.[1] ?? value
}

export function congestionForVisit(
  item: CourseGenerationItem,
  visitDate: string,
): CourseGenerationCongestionFact | undefined {
  return item.congestion.find(fact => fact.date === visitDate)
}

export function weatherForVisit(
  item: CourseGenerationItem,
  visitDate: string,
): CourseGenerationWeatherFact | undefined {
  if (!item.start_time || item.weather == null) return undefined
  const startTime = formatCourseTime(item.start_time)
  return item.weather.find(fact => fact.forecast_date === visitDate
    && formatCourseTime(fact.forecast_time) === startTime)
}

export function categoryCode(item: CourseGenerationItem) {
  return item.tour_category?.category3
    ?? item.tour_category?.category2
    ?? item.tour_category?.category1
    ?? '분류 정보 없음'
}
