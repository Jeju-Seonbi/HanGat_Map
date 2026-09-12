import { describe, it, expect, vi, afterEach } from 'vitest'
import MapPlaceService from './MapPlaceService'

/** getDetail 사진 매핑 검증. 값은 2026-08-30 실응답에서 가져왔다. */
const REAL_DETAIL = {
  restDayText: '연중무휴',
  useFeeText: null,
  free: false,
  images: [
    {
      url: 'https://tong.visitkorea.or.kr/cms/resource/86/3026686_image2_1.jpg',
      thumbnailUrl: 'https://tong.visitkorea.or.kr/cms/resource/86/3026686_image2_1.jpg',
      caption: '가시리국산화풍력발전단지',
      attribution: '출처: 한국관광공사 국문 관광정보 서비스',
      licenseCode: 'Type3',
      primary: true
    },
    {
      url: 'https://tong.visitkorea.or.kr/cms/resource/87/3026687_image2_1.jpg',
      thumbnailUrl: null,
      caption: null,
      attribution: '출처: 한국관광공사 국문 관광정보 서비스',
      licenseCode: 'Type3',
      primary: false
    }
  ]
}

function mockFetch (result: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: () => Promise.resolve({ success: true, code: 2000, message: '', result })
  }))
}

afterEach(() => vi.unstubAllGlobals())

describe('상세 조회 사진 매핑', () => {
  it('사진 목록과 출처 문구가 넘어온다', async () => {
    mockFetch(REAL_DETAIL)

    const d = await MapPlaceService.getDetail(9)

    expect(d?.images).toHaveLength(2)
    expect(d?.images[0].url).toContain('3026686')
    expect(d?.imageAttribution).toBe('출처: 한국관광공사 국문 관광정보 서비스')
  })

  it('썸네일이 없으면 원본을 쓴다', async () => {
    mockFetch(REAL_DETAIL)

    const d = await MapPlaceService.getDetail(9)

    expect(d?.images[1].thumb).toBe(d?.images[1].url)
  })

  it('사진이 없으면 빈 배열이고 출처도 없다 - 화면이 사진 영역을 숨기는 근거', async () => {
    mockFetch({ restDayText: null, useFeeText: null, free: false, images: [] })

    const d = await MapPlaceService.getDetail(1)

    expect(d?.images).toEqual([])
    expect(d?.imageAttribution).toBeNull()
  })

  it('착한가격 기준일은 그대로 넘기고, 없으면 null 이다', async () => {
    mockFetch({ ...REAL_DETAIL, goodPriceBaseDate: '2026-06-30' })
    expect((await MapPlaceService.getDetail(161))?.goodPriceBaseDate).toBe('2026-06-30')
    mockFetch(REAL_DETAIL)
    expect((await MapPlaceService.getDetail(9))?.goodPriceBaseDate).toBeNull()
  })

  it('호출이 실패하면 null - 패널은 목록 데이터로 계속 그려진다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')))

    expect(await MapPlaceService.getDetail(9)).toBeNull()
  })
})

/** 실응답(2026-09-05 /places?type=cafe 첫 행) - toMapPlace 가 읽는 필드가 전부 있다 */
const ROW = {
  id: 3729, name: '리치망고협제점', latitude: 33.4024033, longitude: 126.2517715,
  categoryCode: 'CAFE', categoryName: '카페', regionCode: 'WEST', regionName: '서부',
  roadAddress: '제주특별자치도 제주시 한림읍 한림로 482', lotAddress: null, phone: null,
  operatingHoursText: null, parkingAvailable: null, toiletAvailable: null,
  goodPrice: false, hiddenGem: false, businessStatus: 'UNKNOWN', tagCode: null, tagName: null
}

function mockFetchByUrl (failWhen: (url: string) => boolean) {
  vi.stubGlobal('fetch', vi.fn((url: string) => failWhen(url)
    ? Promise.reject(new Error('down'))
    : Promise.resolve({ ok: true, json: () => Promise.resolve({ success: true, code: 2000, message: '', result: [ROW] }) })))
}

describe('getAll 첫 진입 - 관광지만 받고, 목업으로 바꿔치기하지 않는다', () => {
  it('첫 진입 요청은 type=spot 하나뿐 - 꺼져 있는 착한가격·식당·숙소는 칩을 켤 때 받는다', async () => {
    mockFetchByUrl(() => false)

    const r = await MapPlaceService.getAll()

    const urls = vi.mocked(fetch).mock.calls.map(c => String(c[0]))
    expect(urls).toHaveLength(1)
    expect(urls[0]).toContain('/places?type=spot')
    expect(r.live).toBe(true)
    expect(r.failed).toEqual([])
    expect(r.layers.spot[0].id).toBe(3729)      // 목업이면 id가 null 이다
    expect(r.layers.dine).toEqual([])
  })

  it('관광지를 못 받으면 live=false 에 빈 레이어 - 가짜 장소를 만들어내지 않는다', async () => {
    mockFetchByUrl(() => true)

    const r = await MapPlaceService.getAll()

    expect(r.live).toBe(false)
    expect(r.failed).toEqual(['spot'])
    expect(Object.values(r.layers).every(l => l.length === 0)).toBe(true)
  })

  it('지연 레이어는 getLayer 로 하나씩 - 실패하면 null 이고 다른 레이어에 영향이 없다', async () => {
    mockFetchByUrl(url => url.includes('type=dine'))

    expect(await MapPlaceService.getLayer('dine')).toBeNull()
    const food = await MapPlaceService.getLayer('food')
    expect(food).toHaveLength(1)
    expect(food![0].id).toBe(3729)
  })
})

/** 폐업 장소 - 목록엔 없지만 찜·공유 링크로 열리므로 id 단건 조회와 closed 플래그가 필요하다 */
const CLOSED_ROW = {
  id: 77, name: '문닫은집', regionCode: 'WEST', regionName: '서부', categoryCode: 'FOOD', categoryName: '음식점',
  tagCode: null, tagName: '한식', roadAddress: '제주 제주시 애월읍', lotAddress: null, latitude: 33.4, longitude: 126.3,
  phone: null, operatingHoursText: null, parkingAvailable: null, toiletAvailable: null,
  businessStatus: 'CLOSED', goodPrice: false, hiddenGem: false
}

describe('폐업 장소', () => {
  it('목록 변환은 CLOSED 를 closed 플래그로 옮기고 나머지는 false 다', async () => {
    mockFetch([CLOSED_ROW, { ...CLOSED_ROW, id: 78, businessStatus: 'UNKNOWN' }])
    const rows = await MapPlaceService.getLayer('dine')
    expect(rows?.map(r => r.closed)).toEqual([true, false])
  })

  it('getById 는 상세 응답을 목록과 같은 모양으로 돌려준다', async () => {
    mockFetch({ ...CLOSED_ROW, overview: '소개', images: [] })
    const p = await MapPlaceService.getById(77)
    expect(p).toMatchObject({ id: 77, n: '문닫은집', x: 126.3, y: 33.4, cat: 'FOOD', closed: true })
  })

  it('getById 는 실패하면 null - 호출부가 "찾지 못했어요" 로 안내한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')))
    expect(await MapPlaceService.getById(1)).toBeNull()
  })
})
