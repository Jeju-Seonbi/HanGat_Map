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

  it('호출이 실패하면 null - 패널은 목록 데이터로 계속 그려진다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')))

    expect(await MapPlaceService.getDetail(9)).toBeNull()
  })
})
