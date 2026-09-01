import type { AccommodationInput } from '../assets/types/course'
import { kakaoPlaceSearchService, normalizeKakaoPlaceResults } from './kakaoPlaceSearchService'
import type { KakaoPlaceSearchDocument } from './map/KakaoPlaceSearchMapper'

export interface AccommodationSearchPage {
  items: AccommodationInput[]
  page: number
  has_next_page: boolean
  total_count?: number
  source: 'KAKAO' | 'MOCK'
}

export function normalizeKakaoAccommodationResults(results: KakaoPlaceSearchDocument[], query: string) {
  return normalizeKakaoPlaceResults(results, query, 'ACCOMMODATION')
}

export const accommodationSearchService = {
  async searchAccommodations(query: string, page = 1): Promise<AccommodationSearchPage> {
    const result = await kakaoPlaceSearchService.search(query, { mode: 'ACCOMMODATION', page })
    return { items: result.items, page: result.current_page, has_next_page: result.current_page < result.last_page, total_count: result.total_count, source: result.source }
  },
}
