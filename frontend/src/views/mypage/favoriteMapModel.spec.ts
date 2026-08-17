import { describe, expect, it } from 'vitest'
import { toKakaoFavoritePlaces } from './favoriteMapModel'

describe('favoriteMapModel', () => {
  it('찜 장소 좌표와 혼잡 정보를 공통 카카오맵 장소 형식으로 변환한다', () => {
    const result = toKakaoFavoritePlaces([
      {
        placeId: 'p1',
        name: '금오름',
        category: '오름',
        region: '애월·한림',
        addr: '제주특별자치도 제주시',
        x: 126.306,
        y: 33.356,
        crowd: 28,
        fee: 0,
      },
      {
        placeId: 'p2',
        name: '성산일출봉',
        category: '오름',
        region: '성산·구좌',
        addr: '제주특별자치도 서귀포시',
        x: 126.942,
        y: 33.458,
        crowd: 76,
        fee: 5000,
      },
    ])

    expect(result).toMatchObject([
      {
        id: 'p1',
        name: '금오름',
        latitude: 33.356,
        longitude: 126.306,
        score: 28,
        level: 'RELAXED',
        cost: '무료',
      },
      {
        id: 'p2',
        name: '성산일출봉',
        latitude: 33.458,
        longitude: 126.942,
        score: 76,
        level: 'CROWDED',
        cost: '5,000원',
      },
    ])
  })
})
