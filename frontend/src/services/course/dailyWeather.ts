import type { CourseItem } from '../../assets/types/course'
type ItemWeather = Pick<CourseItem, 'visit_date' | 'weather'>
const regions: Record<string, string> = { NORTH: '북부', SOUTH: '남부', EAST: '동부', WEST: '서부' }
const precipitation: Record<string, string> = { RAIN: '비', RAIN_SNOW: '비·눈', SNOW: '눈', SHOWER: '소나기' }
const sky: Record<string, string> = { '맑음': '맑음', '구름많음': '구름많음', '구름 많음': '구름많음', '흐림': '흐림' }
function daily(item: ItemWeather) {
  return item.weather?.find(f => f.forecast_date === item.visit_date && f.daily_evidence?.granularity === 'DAILY')
}
export function dailyWeatherLabel(item: ItemWeather): string {
  const fact = daily(item)
  const evidence = fact?.daily_evidence
  if (!fact || !evidence) return '날씨 정보 없음'
  const state = precipitation[fact.precipitation_type_code ?? ''] || sky[fact.sky_condition_code ?? ''] || '상태 정보 없음'
  const temperature = evidence.temp_min != null && evidence.temp_max != null
    ? `${evidence.temp_min}~${evidence.temp_max}°C`
    : evidence.temp_min != null ? `최저 ${evidence.temp_min}°C · 최고 정보 없음`
      : evidence.temp_max != null ? `최저 정보 없음 · 최고 ${evidence.temp_max}°C` : '기온 정보 없음'
  const rain = fact.precipitation_probability != null ? `강수확률 ${fact.precipitation_probability}%` : '강수확률 정보 없음'
  return `${state} · ${temperature} · ${rain}`
}
/** Once per date/region; island forecasts are never presented as local precision. */
export function dayWeatherLabels(items: ItemWeather[]): string[] {
  const groups = new Map<string, { scope: string; text: string }>()
  for (const item of items) {
    const evidence = daily(item)?.daily_evidence
    const island = evidence?.spatial_scope === 'JEJU_ISLAND'
    const key = `${item.visit_date}:${island ? 'JEJU_ISLAND' : evidence?.region_code ?? 'MISSING'}`
    if (!groups.has(key)) groups.set(key, { scope: island ? '제주 전역 중기예보' : regions[evidence?.region_code ?? ''] ?? '', text: dailyWeatherLabel(item) })
  }
  return [...groups.values()].map(g => g.scope && (groups.size > 1 || g.scope === '제주 전역 중기예보') ? `${g.scope} · ${g.text}` : g.text)
}
