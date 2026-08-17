import type { IndoorOutdoor, WeatherCondition } from '../assets/types/course'

export interface MockWeather {
  weather_condition: WeatherCondition
  temperature: number
  precipitation_probability: number
}

const conditions: WeatherCondition[] = ['RAIN', 'SUNNY', 'CLOUDY', 'STRONG_WIND', 'SUNNY']

export function getMockWeather(visitDate: string, startTime = '12:00'): MockWeather {
  const day = Number(visitDate.slice(-2)) || 1
  const hour = Number(startTime.slice(0, 2)) || 12
  const weatherCondition = conditions[day % conditions.length]
  const precipitation = weatherCondition === 'RAIN' ? 70 : weatherCondition === 'SNOW' ? 60 : weatherCondition === 'CLOUDY' ? 30 : 10
  return {
    weather_condition: weatherCondition,
    temperature: Math.max(3, 24 + (day % 5) + (hour >= 12 && hour <= 16 ? 2 : 0)),
    precipitation_probability: precipitation,
  }
}

export function weatherRecommendationAdjustment(weather: MockWeather, indoorOutdoor: IndoorOutdoor, subcategory?: string) {
  const windSensitive = subcategory === 'OREUM' || subcategory === 'BEACH'
  if (weather.weather_condition === 'RAIN') {
    if (indoorOutdoor === 'INDOOR') return 5
    if (indoorOutdoor === 'OUTDOOR') return -6
    return -1
  }
  if (weather.weather_condition === 'STRONG_WIND') {
    if (indoorOutdoor === 'INDOOR') return 3
    return windSensitive ? -7 : -3
  }
  if (weather.weather_condition === 'SNOW' && indoorOutdoor === 'OUTDOOR') return -5
  if (weather.weather_condition === 'SUNNY' && indoorOutdoor === 'OUTDOOR') return 2
  return 0
}

export function weatherWarning(weather: MockWeather, indoorOutdoor: IndoorOutdoor, fixed: boolean) {
  if (indoorOutdoor === 'INDOOR') return undefined
  if (weather.weather_condition === 'RAIN') return fixed
    ? '비가 예상되는 시간대예요. 일정은 사용자 지정대로 유지했어요.'
    : '비 예보가 있어 야외 활동 전 확인이 필요해요.'
  if (weather.weather_condition === 'STRONG_WIND') return fixed
    ? '강풍 예보가 있어요. 일정은 사용자 지정대로 유지했어요.'
    : '강풍 예보가 있어 야외 활동 시 확인이 필요해요.'
  if (weather.weather_condition === 'SNOW') return '눈 예보가 있어 이동과 운영 여부 확인이 필요해요.'
  return undefined
}
