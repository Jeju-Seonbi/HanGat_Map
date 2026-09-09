import { describe, it, expect } from 'vitest'
import { dailyWeatherLabel, dayWeatherLabels } from './dailyWeather'
describe('stored daily weather', () => {
  const evidence = { source_code: 'KMA_MID', region_code: 'EAST', spatial_scope: 'JEJU_ISLAND', granularity: 'DAILY', issued_at_utc: '2026-09-07T09:00:00', temp_min: 23, temp_max: 29 }
  it('labels island-wide daily evidence, not an hourly temperature', () => {
    const text = dailyWeatherLabel({ visit_date: '2026-09-11', weather: [{ forecast_date: '2026-09-11', sky_condition_code: '흐림', daily_evidence: evidence }] })
    expect(text).toBe('흐림 · 23~29°C · 강수확률 정보 없음')
    expect(text).not.toMatch(/UTC|EAST|기상청|발표|시부터/)
  })
  it('deduplicates daily evidence and labels island scope', () => {
    const item = { visit_date: '2026-09-11', weather: [{ forecast_date: '2026-09-11', sky_condition_code: '맑음', precipitation_probability: 10, daily_evidence: evidence }] }
    expect(dayWeatherLabels([item, item, item])).toEqual(['제주 전역 중기예보 · 맑음 · 23~29°C · 강수확률 10%'])
    const local = (region: string) => ({ ...item, weather: [{ ...item.weather[0], daily_evidence: { ...evidence, spatial_scope: 'REGION', region_code: region } }] })
    expect(dayWeatherLabels([local('EAST'), local('EAST'), local('WEST')])).toEqual(['동부 · 맑음 · 23~29°C · 강수확률 10%', '서부 · 맑음 · 23~29°C · 강수확률 10%'])
  })
  it('preserves component gaps, explicit zero probability and observed precipitation type', () => {
    const item = { visit_date: '2026-09-11', weather: [{ forecast_date: '2026-09-11', precipitation_probability: 0, daily_evidence: { ...evidence, temp_min: null, temp_max: null } }] }
    expect(dailyWeatherLabel(item)).toBe('상태 정보 없음 · 기온 정보 없음 · 강수확률 0%')
    expect(dailyWeatherLabel({ ...item, weather: [{ ...item.weather[0], sky_condition_code: 'UNKNOWN' }] })).toBe('상태 정보 없음 · 기온 정보 없음 · 강수확률 0%')
    expect(dailyWeatherLabel({ ...item, weather: [{ ...item.weather[0], precipitation_type_code: 'RAIN' }] })).toBe('비 · 기온 정보 없음 · 강수확률 0%')
  })
  it('missing/different-day evidence is unavailable, never sunny or zero', () => {
    expect(dailyWeatherLabel({ visit_date: '2026-09-11' })).toBe('날씨 정보 없음')
    expect(dailyWeatherLabel({ visit_date: '2026-09-12', weather: [{ forecast_date: '2026-09-11', daily_evidence: evidence }] })).toBe('날씨 정보 없음')
  })
})
