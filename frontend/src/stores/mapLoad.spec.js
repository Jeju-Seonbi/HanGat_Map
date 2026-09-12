import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/* 서비스는 전부 흉내낸다 - 여기서 보는 건 "언제 다시 부르는가"뿐 */
const placeApi = { getAll: vi.fn(), getLayer: vi.fn(), search: vi.fn(), getById: vi.fn(), getDetail: vi.fn() }
const crowdApi = { getForecast: vi.fn() }
const weatherApi = { load: vi.fn(), reset: vi.fn(), byDate: vi.fn(), issuedAt: vi.fn() }
const favApi = { ids: vi.fn(), add: vi.fn(), remove: vi.fn() }
vi.mock('../services/map/MapPlaceService', () => ({ default: placeApi, MapPlaceService: placeApi, LAZY_LAYERS: ['food', 'dine', 'stay', 'cafe', 'cvs', 'mart'] }))
vi.mock('../services/map/CrowdService', () => ({ default: crowdApi, CrowdService: crowdApi, attachSeries: vi.fn() }))
vi.mock('../services/map/MapWeatherService', () => ({ default: weatherApi, WeatherService: weatherApi }))
vi.mock('../services/map/FavoriteApiService', () => ({ default: favApi, FavoriteApiService: favApi }))

const layers = () => ({ spot: [{ id: 1, n: '성산일출봉', x: 126.94, y: 33.46 }], food: [], dine: [], cafe: [], cvs: [], stay: [], mart: [] })
const ok = (failed = []) => ({ live: true, layers: layers(), failed })
const forecast = (live = true) => ({ live, from: '2026-09-12', days: live ? 3 : 0, values: {} })

/** Storage 흉내 - 탭 하나의 sessionStorage/localStorage */
function storage () {
  const m = new Map()
  return { getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k) }
}

/* mapStore 는 "마지막으로 받은 시각"을 모듈 변수로 들고 있어 케이스마다 모듈을 새로 읽는다 */
async function freshStore () {
  vi.resetModules()
  vi.stubGlobal('sessionStorage', storage())
  vi.stubGlobal('localStorage', storage())
  return import('./mapStore.js')
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-09-12T10:00:00+09:00'))
  placeApi.getAll.mockReset().mockResolvedValue(ok())
  crowdApi.getForecast.mockReset().mockResolvedValue(forecast())
  weatherApi.load.mockReset().mockResolvedValue(true)
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('loadPlaces 재진입 재사용 (성능 B 커밋 1)', () => {
  it('첫 진입은 장소·예보·날씨를 한 번씩 받는다', async () => {
    const s = await freshStore()
    expect(s.canReuse()).toBe(false)
    await s.loadPlaces()
    expect(placeApi.getAll).toHaveBeenCalledTimes(1)
    expect(crowdApi.getForecast).toHaveBeenCalledTimes(1)
    expect(weatherApi.load).toHaveBeenCalledTimes(1)
    expect(s.state.layers.spot).toHaveLength(1)
    expect(s.state.forecastVersion).toBe(1)
    expect(s.state.loading).toBe(false)
    expect(s.canReuse()).toBe(true)
  })

  it('같은 날 12시간 안 재진입은 아무것도 다시 받지 않고 상태도 건드리지 않는다', async () => {
    const s = await freshStore()
    await s.loadPlaces()
    const before = s.state.layers
    vi.setSystemTime(new Date('2026-09-12T21:59:00+09:00'))   // 11시간 59분 뒤
    await s.loadPlaces()
    expect(placeApi.getAll).toHaveBeenCalledTimes(1)
    expect(crowdApi.getForecast).toHaveBeenCalledTimes(1)
    expect(weatherApi.load).toHaveBeenCalledTimes(1)
    expect(s.state.layers).toBe(before)          // 같은 객체 - 핀이 회색으로 바뀌었다 돌아오는 일이 없다
    expect(s.state.loading).toBe(false)          // 목록이 "불러오는 중"으로 비지 않는다
    expect(s.state.forecastVersion).toBe(1)
  })

  it('12시간이 지나면 다시 받는다', async () => {
    const s = await freshStore()
    await s.loadPlaces()
    vi.setSystemTime(new Date('2026-09-12T22:01:00+09:00'))   // 12시간 1분 뒤, 같은 날
    await s.loadPlaces()
    expect(placeApi.getAll).toHaveBeenCalledTimes(2)
    expect(crowdApi.getForecast).toHaveBeenCalledTimes(2)
  })

  it('날짜가 바뀌면 12시간 안이라도 다시 받는다 - 예보가 오늘 기준으로 붙어 있어서', async () => {
    vi.setSystemTime(new Date('2026-09-12T23:50:00+09:00'))
    const s = await freshStore()
    await s.loadPlaces()
    vi.setSystemTime(new Date('2026-09-13T00:10:00+09:00'))   // 20분 뒤, 다음 날
    expect(s.canReuse()).toBe(false)
    await s.loadPlaces()
    expect(placeApi.getAll).toHaveBeenCalledTimes(2)
  })

  it('레이어 하나라도 못 받았으면 기록하지 않아 다음 진입에 전부 다시 받는다(재시도)', async () => {
    placeApi.getAll.mockResolvedValueOnce(ok(['stay']))
    const s = await freshStore()
    await s.loadPlaces()
    expect(s.canReuse()).toBe(false)
    await s.loadPlaces()
    expect(placeApi.getAll).toHaveBeenCalledTimes(2)
  })

  it('예보가 비어 왔으면(실패·미적재) 다음 진입에 다시 받는다', async () => {
    crowdApi.getForecast.mockResolvedValueOnce(forecast(false))
    const s = await freshStore()
    await s.loadPlaces()
    expect(s.canReuse()).toBe(false)
    await s.loadPlaces()
    expect(crowdApi.getForecast).toHaveBeenCalledTimes(2)
  })
})
