/**
 * 장소 상세 페이지(/places/:placeId)가 쓰는 조회.
 *
 * 지도 패널(services/map/MapPlaceService.getDetail)은 패널이 쓰는 일부 필드만 뽑아 쓰므로
 * 이름·주소·운영시간·착한가격 같은 나머지가 없다. 상세 페이지는 응답 전체가 필요해서 따로 읽는다.
 *
 * 정직성: 값이 없으면 null로 남기고 화면이 "정보 없음"을 말한다. 0이나 '무료'로 지어내지 않는다.
 */
import { apiGet } from './apiClient'

export interface PlaceImage {
  url: string
  thumbnailUrl: string | null
  caption: string | null
  attribution: string | null
}

/** 백엔드 PlaceDetailResponse (map/model/dto/PlaceDetailResponse.java 와 동일 모양) */
export interface PlaceDetail {
  id: number
  name: string
  regionCode: string | null
  regionName: string | null
  categoryCode: string | null
  categoryName: string | null
  tagCode: string | null
  tagName: string | null
  roadAddress: string | null
  lotAddress: string | null
  latitude: number | null
  longitude: number | null
  overview: string | null
  phone: string | null
  operatingHoursText: string | null
  restDayText: string | null
  useFeeText: string | null
  free: boolean
  parkingAvailable: boolean | null
  toiletAvailable: boolean | null
  businessStatus: string | null
  goodPrice: boolean
  /** 착한가격업소 명단 기준일. 행정안전부가 분기마다 갱신한다 */
  goodPriceBaseDate: string | null
  hiddenGem: boolean
  hiddenGemScore: number | null
  /** 대표 사진(KTO firstimage). 상세 사진 배치를 안 돌린 장소도 값이 있다 */
  imageUrl: string | null
  images: PlaceImage[]
  ratingAvg: number | null
  reviewCount: number
}

/** 이 장소의 날짜별 집중률. 예보 대상(345곳)이 아니면 비어 있다 - 0으로 채우지 않는다 */
export interface PlaceForecast {
  /** 예보 첫 날 (YYYY-MM-DD) */
  from: string
  /** from 부터 하루씩. 예보가 없으면 빈 배열 */
  rates: number[]
}

/** 백엔드 DailyWeather (domain/weather/model/DailyWeather.java) - 현재 북부 권역 격자 기준이다 */
export interface DayWeather {
  date: string
  minTemp: number | null
  maxTemp: number | null
  sky: string | null
  rainProb: number | null
}

interface BackendForecast {
  from: string
  days: number
  values: Record<string, number[]>
}

export const PlaceDetailService = {
  /** @returns 없는 id·백엔드 장애면 null - 화면이 "장소를 찾지 못했어요"를 보여준다 */
  async getDetail (placeId: number): Promise<PlaceDetail | null> {
    try {
      return await apiGet<PlaceDetail>(`/places/${placeId}`, 15000)
    } catch {
      return null
    }
  },

  /**
   * 혼잡 예보는 장소별 엔드포인트가 없어 최신 발표분 전체에서 이 장소만 뽑는다.
   * 지도(CrowdService)와 같은 응답이라 브라우저 캐시가 겹친다.
   */
  async getForecast (placeId: number): Promise<PlaceForecast | null> {
    try {
      const res = await apiGet<BackendForecast>('/crowd/forecast', 15000)
      const rates = res.values?.[String(placeId)]
      return rates?.length ? { from: res.from, rates } : null
    } catch {
      return null
    }
  },

  /**
   * 기상청 예보 7일. 백엔드가 아직 북부 권역 격자만 내려주므로 화면도 그렇게 표기한다 -
   * 장소가 있는 권역의 날씨인 척하지 않는다.
   */
  async getWeather (): Promise<DayWeather[]> {
    try {
      return await apiGet<DayWeather[]>('/main/weather')
    } catch {
      return []
    }
  }
}

export default PlaceDetailService
