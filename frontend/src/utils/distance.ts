// 하버사인 거리 - 백엔드 공통 코어 GeoService에 들어갈 로직의 프론트 시안
// Place의 좌표가 optional이라 좌표 없는 장소는 0km로 처리한다 (공공 API 결측 대비)
export interface LatLng {
  latitude?: number
  longitude?: number
}

export const distKm = (a: LatLng, b: LatLng) => {
  if (
    a.latitude === undefined ||
    a.longitude === undefined ||
    b.latitude === undefined ||
    b.longitude === undefined
  )
    return 0
  const rad = (x: number) => (x * Math.PI) / 180
  const dLat = rad(b.latitude - a.latitude)
  const dLon = rad(b.longitude - a.longitude)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2
  return 2 * 6371 * Math.asin(Math.sqrt(h))
}
