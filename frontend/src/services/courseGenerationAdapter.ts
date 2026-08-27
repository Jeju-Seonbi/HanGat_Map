import type {
  CourseCondition,
  CourseGenerationItem,
  CourseGenerationResponse,
  GeneratedCourseItem,
  GeneratedCourseResult,
  WeatherCondition,
} from '../assets/types/course'

const shortTime = (value: string | null) => value?.slice(0, 5)

function categoryName(item: CourseGenerationItem) {
  const category = item.tour_category
  return category?.category3 || category?.category2 || category?.category1 || '카테고리 정보 없음'
}

function weatherCondition(precipitationType: string | null, skyCondition: string | null): WeatherCondition | undefined {
  if (precipitationType === '3') return 'SNOW'
  if (precipitationType === '1' || precipitationType === '2' || precipitationType === '4') return 'RAIN'
  if (skyCondition === '1') return 'SUNNY'
  if (skyCondition === '3' || skyCondition === '4') return 'CLOUDY'
  return undefined
}

function adaptItem(item: CourseGenerationItem, dayNo: number, visitDate: string): GeneratedCourseItem {
  const startTime = shortTime(item.start_time)
  const congestion = item.congestion.find(fact => fact.date === visitDate && fact.rate != null)
  const weather = item.weather?.find(fact =>
    fact.forecast_date === visitDate && shortTime(fact.forecast_time) === startTime)

  return {
    candidate_id: item.candidate_id,
    place_name: item.place_name,
    category_name: categoryName(item),
    image_url: item.image_url ?? undefined,
    latitude: item.latitude ?? undefined,
    longitude: item.longitude ?? undefined,
    day_no: dayNo,
    position: item.position,
    visit_date: visitDate,
    start_time: startTime,
    item_source: item.item_source,
    congestion_rate: congestion?.rate ?? undefined,
    congestion_level: congestion?.level ?? undefined,
    recommendation_reason: item.recommendation_reason ?? undefined,
    weather_condition: weather
      ? weatherCondition(weather.precipitation_type_code, weather.sky_condition_code)
      : undefined,
    temperature: weather?.temperature ?? undefined,
    precipitation_probability: weather?.precipitation_probability ?? undefined,
    costs: [],
  }
}

export function adaptCourseGenerationResponse(
  response: CourseGenerationResponse,
  condition: CourseCondition,
): GeneratedCourseResult {
  const days = response.days.map(day => ({
    day_no: day.day_no,
    visit_date: day.visit_date,
    items: day.items.map(item => adaptItem(item, day.day_no, day.visit_date)),
  }))
  const rates = days.flatMap(day => day.items)
    .map(item => item.congestion_rate)
    .filter((rate): rate is number => rate != null)

  return {
    course_type: 'USER',
    generation_reason: 'INITIAL',
    status: 'READY',
    start_date: response.start_date,
    end_date: response.end_date,
    people: condition.people,
    budget_total: condition.budget_total,
    transport: condition.transport,
    accommodation: condition.accommodation,
    average_congestion_rate: rates.length
      ? rates.reduce((sum, rate) => sum + rate, 0) / rates.length
      : undefined,
    days,
  }
}
