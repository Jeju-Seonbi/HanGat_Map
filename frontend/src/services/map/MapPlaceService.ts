/**
 * 지도 페이지 장소 데이터 (MAP-01 ~ MAP-03).
 *
 * 백엔드 `GET /places?type=` (KTO 관광정보 실적재 2,138곳 + 소상공인 상가).
 * 실패 처리: 레이어별로 따로 받아 하나가 실패해도 나머지는 살린다(failed 목록으로 알림).
 * 전부 실패하면 live=false - 화면이 "불러오지 못했어요 · 새로고침" 안내를 띄운다.
 * ⚠️ 예전엔 실패 시 하드코딩 목업(data/placesMap)으로 바꿔치기했는데 2026-09-07 제거했다 -
 *    운영에서 가짜 장소가 진짜처럼 보이고, id가 없어 후기·공유·찜이 전부 죽는 문제.
 *
 * ★ 백엔드는 전체 필드명(`name`/`latitude`/…)을 쓰고 화면은 축약 키(`n`/`y`/…)를 쓴다.
 *   변환을 여기 한 곳에 모아 두면 MapCanvas·SearchBox·PlaceDetail이 지금 코드 그대로 돈다.
 *   ⚠️ `x`가 경도, `y`가 위도다 - 뒤집으면 제주 전역 핀이 통째로 엉뚱한 곳에 찍힌다.
 */
import { apiGet } from '../apiClient'

/** 화면이 쓰는 장소 한 건. 기존 placesMap.js 한 줄과 같은 모양이다. */
export interface MapPlace {
  /** 백엔드 placeId. 혼잡 예보(`/crowd/forecast`)와 잇는 키다. 목업은 null */
  id: number | null
  n: string
  /** 경도 */
  x: number
  /** 위도 */
  y: number
  /** 권역 표시명 (동부/서부/남부/북부) */
  r: string
  /** 세부분류 표시명 (오름/해수욕장/박물관…). 미분류면 '정보 없음' */
  c: string
  /** 세부분류 코드 (관광공사 NA010100 등). 관광지 핀 아이콘 묶음을 앞자리로 정한다 - 없으면 null */
  tc: string | null
  /** 카테고리 코드 (TOURIST/FOOD/CAFE/…) - 검색 결과의 핀 색 구분용 */
  cat: string
  addr: string | null
  tel: string | null
  hours: string | null
  /** 착한가격 지정 여부 - 상세 메뉴 섹션의 '착한가격' 뱃지 조건 (일반 식당 메뉴엔 안 붙인다) */
  good: boolean
  park: boolean | null
  wc: boolean | null
  /** 그 장소의 날짜별 집중률. CrowdService가 채운다. 예보 없으면 null */
  series: (number | null)[] | null
  /**
   * 폴백 전용 기준 집중률. 백엔드가 죽었을 때만 값이 있다.
   * 실데이터에는 없다 - 근거 없는 샘플값이라 실서비스 화면에 올리지 않는다.
   */
  b: number | null
  /** 아직 수집 전인 값들 - 0으로 채우지 않는다(없는 정보를 있는 것처럼 만들지 않기 위해) */
  fee: number | null
  d: number | null
  in: number | null
  /** 폐업(businessStatus CLOSED). 목록·검색엔 안 나오고 찜·공유 링크로 열릴 때만 true - 패널이 '폐업' 배지를 단다 */
  closed: boolean
}

/** 백엔드 PlaceListResponse (map/model/dto/PlaceListResponse.java 와 동일 모양) */
interface BackendPlace {
  id: number
  name: string
  regionCode: string
  regionName: string
  categoryCode: string
  categoryName: string
  tagCode: string | null
  tagName: string | null
  roadAddress: string | null
  lotAddress: string | null
  latitude: number | null
  longitude: number | null
  phone: string | null
  operatingHoursText: string | null
  parkingAvailable: boolean | null
  toiletAvailable: boolean | null
  businessStatus: string
  goodPrice: boolean
  hiddenGem: boolean
}

/**
 * 장소 상세에서만 오는 값 (MAP-07). 목록 응답에는 없다 -
 * 입장료 원문이 최대 1,000자인데 값이 있는 곳은 27곳뿐이라 목록에 싣지 않았다.
 */
export interface PlaceDetail {
  /** 휴무일 원문. null = 정보 없음(연중무휴가 아니다) */
  rest: string | null
  /** 입장료 원문. "[개인]- 일반 1,500원..." 처럼 요금표가 통째로 온다 */
  feeText: string | null
  /** '무료'만 뜻할 때 true. 조건부 무료("무료※ 단, 특별전시 제외")는 false다 */
  free: boolean
  /** 별점 평균. 별점 후기가 없으면 null - 0.0 으로 그리지 않는다 */
  ratingAvg: number | null
  reviewCount: number
  /** 장소 사진(순서대로). 스트립은 thumb, 확대는 url 을 쓴다 */
  images: PlaceImage[]
  /** 사진 출처 표기 문구 - 사진이 있으면 화면에 반드시 보여준다(공공누리) */
  imageAttribution: string | null
  /** 소개 원문. 착한가격업소는 "대표메뉴: ○○ 9,000원 · …" 형태 - 핀 툴팁이 쓴다 */
  overview: string | null
  /** 착한가격 명단 기준일(행안부 CSV 발행일, ISO 날짜). 착한가격이 아니거나 모르면 null - 가격표 밑 출처 줄이 쓴다 */
  goodPriceBaseDate: string | null
}

export interface PlaceImage {
  url: string
  thumb: string
  caption: string | null
}

/** 백엔드 PlaceDetailResponse 중 이 화면이 쓰는 부분 */
interface BackendPlaceDetail {
  restDayText: string | null
  useFeeText: string | null
  free: boolean
  ratingAvg: number | null
  reviewCount: number
  images: BackendPlaceImage[]
  overview: string | null
  goodPriceBaseDate?: string | null
}

interface BackendPlaceImage {
  url: string
  thumbnailUrl: string | null
  caption: string | null
  attribution: string | null
}

/** 화면 레이어 키 → 백엔드 type 파라미터 */
export type LayerKey = 'spot' | 'food' | 'dine' | 'cafe' | 'cvs' | 'stay' | 'mart'

/**
 * 첫 진입에 싣지 않는 대용량 레이어 (소상공인 상가 5,419곳).
 * 칩을 처음 켤 때 getLayer()로 그때 받아온다 - 안 쓰는 사람은 다운로드 비용 0.
 */
export const LAZY_LAYERS: LayerKey[] = ['cafe', 'cvs', 'mart']

export interface MapPlaces {
  /** true = 레이어를 하나라도 받았다. false = 전부 실패(백엔드 다운) */
  live: boolean
  layers: Record<LayerKey, MapPlace[]>
  /** 이번 진입에서 못 받아온 레이어. 화면이 안내하고, 칩을 다시 켜면 그 레이어만 재시도한다 */
  failed: LayerKey[]
}

export const MapPlaceService = {
  /**
   * 지도가 쓰는 레이어를 한 번에 받아온다.
   * 레이어마다 호출이 나가지만 전부 같은 테이블이라 서버 부담은 크지 않고,
   * 하나가 실패해도 나머지가 살아 있도록 개별로 처리한다.
   */
  async getAll (): Promise<MapPlaces> {
    const keys: LayerKey[] = ['spot', 'food', 'dine', 'stay']   // 기본 레이어만 - 대용량은 LAZY_LAYERS
    const results = await Promise.allSettled(keys.map(k => apiGet<BackendPlace[]>(`/places?type=${k}`)))
    const layers = emptyLayers()
    const failed: LayerKey[] = []
    results.forEach((r, i) => {
      if (r.status === 'fulfilled') layers[keys[i]] = r.value.map(toMapPlace)
      else failed.push(keys[i])
    })
    return { live: failed.length < keys.length, layers, failed }
  },

  /** 칩을 처음 켤 때 한 레이어만 받아온다. 실패하면 null - 호출부가 토스트로 알린다. */
  async getLayer (key: LayerKey): Promise<MapPlace[] | null> {
    try {
      const rows = await apiGet<BackendPlace[]>(`/places?type=${key}`)
      return rows.map(toMapPlace)
    } catch {
      return null
    }
  },

  /** 통합 검색 (MAP_002) - 이름·메뉴 부분 일치 상위 20건. 화면 필터(권역·업종) 범위를 함께 보낸다. 실패하면 빈 배열. */
  async search (q: string, opts?: { region?: string | null, categories?: string[] }): Promise<MapPlace[]> {
    try {
      const params = new URLSearchParams({ q })
      if (opts?.region) params.set('region', opts.region)
      if (opts?.categories?.length) params.set('categories', opts.categories.join(','))
      const rows = await apiGet<BackendPlace[]>(`/places/search?${params}`)
      return rows.map(toMapPlace)
    } catch {
      return []
    }
  },

  /**
   * 상세 패널을 열 때만 부른다. 실패하면 null - 패널은 목록 데이터로 계속 그려진다.
   * 목업 모드는 id 가 null 이라 호출부에서 걸러진다.
   */
  async getDetail (id: number): Promise<PlaceDetail | null> {
    try {
      const row = await apiGet<BackendPlaceDetail>(`/places/${id}`)
      const images = (row.images ?? []).map(i => ({
        url: i.url,
        thumb: i.thumbnailUrl ?? i.url,
        caption: i.caption
      }))
      return {
        rest: row.restDayText,
        feeText: row.useFeeText,
        free: row.free,
        ratingAvg: row.ratingAvg,
        reviewCount: row.reviewCount ?? 0,
        images,
        imageAttribution: row.images?.[0]?.attribution ?? null,
        overview: row.overview ?? null,
        goodPriceBaseDate: row.goodPriceBaseDate ?? null
      }
    } catch {
      return null
    }
  },

  /**
   * 목록에 없는 장소를 id 로 받는다 - 폐업(CLOSED) 장소는 목록·검색에서 빠지지만 찜·공유 링크로는 들어온다.
   * 상세 응답은 목록 응답의 상위 집합이라 같은 변환을 쓴다. 없거나 실패하면 null.
   */
  async getById (id: number): Promise<MapPlace | null> {
    try {
      return toMapPlace(await apiGet<BackendPlace>(`/places/${id}`))
    } catch {
      return null
    }
  }
}

/** 백엔드 응답 → 화면 형식. 좌표가 없는 장소는 지도에 못 그리므로 호출부에서 걸러진다. */
function toMapPlace (row: BackendPlace): MapPlace {
  return {
    id: row.id,
    n: row.name,
    x: row.longitude ?? 0,
    y: row.latitude ?? 0,
    r: row.regionName,
    // 세부분류가 없는 장소가 있다 - 빈 문자열로 두면 드롭다운에 빈 항목이 생긴다
    c: row.tagName ?? '정보 없음',
    tc: row.tagCode ?? null,   // 관광공사 분류 코드 - 관광지 핀 아이콘 묶음(spotIconGroup)이 앞자리로 나눈다
    cat: row.categoryCode,
    addr: row.roadAddress ?? row.lotAddress,
    tel: row.phone,
    hours: row.operatingHoursText,
    good: row.goodPrice,
    park: row.parkingAvailable,
    wc: row.toiletAvailable,
    series: null,
    b: null,
    fee: null,
    d: null,
    in: null,
    closed: row.businessStatus === 'CLOSED'
  }
}

/** 좌표가 없으면 지도에 찍을 수 없다. 목록·검색에는 남기고 싶으면 호출부에서 따로 거른다. */
export function hasCoords (p: MapPlace): boolean {
  return p.x !== 0 && p.y !== 0
}

function emptyLayers (): Record<LayerKey, MapPlace[]> {
  return { spot: [], food: [], dine: [], cafe: [], cvs: [], stay: [], mart: [] }
}

export default MapPlaceService
