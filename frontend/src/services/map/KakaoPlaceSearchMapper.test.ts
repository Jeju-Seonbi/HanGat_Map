import { describe, expect, it } from 'vitest'
import { mapKakaoPlaceSearchResult } from './KakaoPlaceSearchMapper'

describe('mapKakaoPlaceSearchResult', () => {
  it('keeps the Kakao ID as an external source ID and maps x/y to longitude/latitude', () => {
    const result = mapKakaoPlaceSearchResult({
      id: '123456',
      place_name: '롯데호텔 제주',
      address_name: '제주특별자치도 서귀포시',
      road_address_name: '제주특별자치도 서귀포시 중문관광로',
      x: '126.123',
      y: '33.456',
    })

    expect(result).toMatchObject({
      source_code: 'KAKAO_LOCAL',
      source_place_id: '123456',
      place_name: '롯데호텔 제주',
      longitude: 126.123,
      latitude: 33.456,
    })
    expect(result).not.toHaveProperty('place_id')
    expect(result).not.toHaveProperty('placeId')
  })
})
