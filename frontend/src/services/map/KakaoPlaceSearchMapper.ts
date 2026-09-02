import type { KakaoPlaceSearchResult } from '../../assets/types/course'

export interface KakaoPlaceSearchDocument {
  id: string
  place_name: string
  address_name?: string
  road_address_name?: string
  x: string
  y: string
  phone?: string
  place_url?: string
  category_name?: string
  category_group_code?: string
  category_group_name?: string
}

export function mapKakaoPlaceSearchResult(result: KakaoPlaceSearchDocument): KakaoPlaceSearchResult {
  return {
    source_code: 'KAKAO_LOCAL',
    source_place_id: result.id,
    place_name: result.place_name,
    address: result.address_name || undefined,
    road_address: result.road_address_name || undefined,
    longitude: Number(result.x),
    latitude: Number(result.y),
    phone: result.phone || undefined,
    place_url: result.place_url || undefined,
    category_name: result.category_name || undefined,
  }
}
