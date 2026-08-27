import type { AccommodationInput, AccommodationRecommendation, CourseCondition, CourseResultView, RegionRef } from '../assets/types/course'

type RegionCode = RegionRef['code']

const pause = (ms = 220) => new Promise(resolve => setTimeout(resolve, ms))

export const mockAccommodations: AccommodationInput[] = [
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9001', place_name: '롯데호텔 제주', address: '제주특별자치도 서귀포시 중문관광로 72번길 35', region: 'SOUTH', latitude: 33.2485, longitude: 126.4105, image_url: '/images/coast.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9002', place_name: '롯데시티호텔 제주', address: '제주특별자치도 제주시 도령로 83', region: 'NORTH', latitude: 33.4907, longitude: 126.4869, image_url: '/images/village.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9003', place_name: '성산 마리나 호텔', address: '제주특별자치도 서귀포시 성산읍 성산등용로 28', region: 'EAST', latitude: 33.4612, longitude: 126.9324, image_url: '/images/coast.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9004', place_name: '월정리 오션스테이', address: '제주특별자치도 제주시 구좌읍 월정리 482', region: 'EAST', latitude: 33.5544, longitude: 126.7972, image_url: '/images/coast.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9005', place_name: '애월 바다정원 호텔', address: '제주특별자치도 제주시 애월읍 애월해안로 394', region: 'WEST', latitude: 33.4635, longitude: 126.3201, image_url: '/images/coast.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9006', place_name: '협재 선셋 리조트', address: '제주특별자치도 제주시 한림읍 협재리 2497', region: 'WEST', latitude: 33.3948, longitude: 126.2394, image_url: '/images/coast.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9007', place_name: '서귀포 칼 호텔', address: '제주특별자치도 서귀포시 칠십리로 242', region: 'SOUTH', latitude: 33.2460, longitude: 126.5822, image_url: '/images/village.svg' },
  { source_code: 'KAKAO_LOCAL', source_place_id: 'MOCK_KAKAO_9008', place_name: '제주공항 스테이', address: '제주특별자치도 제주시 서해안로 442', region: 'NORTH', latitude: 33.5112, longitude: 126.4930, image_url: '/images/village.svg' },
]

const eastNames = new Set(['비자림', '세화해변', '성산일출봉', '섭지코지', '아부오름', '월정리 카페거리', '김녕해수욕장', '만장굴', '다랑쉬오름'])
const westNames = new Set(['애월 해안도로', '한담해변', '새별오름'])
const southNames = new Set(['산방산', '제주민속촌', '정방폭포'])
const regionCentres: Record<RegionCode, { lat: number; lng: number }> = {
  EAST: { lat: 33.46, lng: 126.83 },
  WEST: { lat: 33.36, lng: 126.31 },
  SOUTH: { lat: 33.25, lng: 126.56 },
  NORTH: { lat: 33.48, lng: 126.55 },
}

function regionOfPlace(placeName: string): RegionCode {
  if (eastNames.has(placeName)) return 'EAST'
  if (westNames.has(placeName)) return 'WEST'
  if (southNames.has(placeName)) return 'SOUTH'
  return 'NORTH'
}

function analyseCourse(course: CourseResultView) {
  const counts: Record<RegionCode, number> = { EAST: 0, WEST: 0, SOUTH: 0, NORTH: 0 }
  const regions = course.days.flatMap(day => day.items.map(item => regionOfPlace(item.place_name)))
  regions.forEach(region => { counts[region] += 1 })
  const mainRegion = (Object.entries(counts) as [RegionCode, number][]).sort((a, b) => b[1] - a[1])[0][0]
  const centres = regions.map(region => regionCentres[region])
  const centre = centres.length
    ? { lat: centres.reduce((sum, item) => sum + item.lat, 0) / centres.length, lng: centres.reduce((sum, item) => sum + item.lng, 0) / centres.length }
    : regionCentres[mainRegion]
  return { mainRegion, centre }
}

function distanceKm(first: { lat: number; lng: number }, second: { lat: number; lng: number }) {
  const toRadians = (degrees: number) => degrees * Math.PI / 180
  const latDistance = toRadians(second.lat - first.lat)
  const lngDistance = toRadians(second.lng - first.lng)
  const value = Math.sin(latDistance / 2) ** 2
    + Math.cos(toRadians(first.lat)) * Math.cos(toRadians(second.lat)) * Math.sin(lngDistance / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value))
}

function recommendationScore(accommodation: AccommodationInput, mainRegion: RegionCode, centre: { lat: number; lng: number }, course: CourseResultView) {
  const routeDistance = distanceKm(centre, { lat: accommodation.latitude, lng: accommodation.longitude })
  let score = routeDistance != null && routeDistance <= 35 ? 3 : 0
  if (accommodation.region === mainRegion) score += 3
  const coveredDays = course.days.filter(day => day.items.some(item => regionOfPlace(item.place_name) === accommodation.region)).length
  if (coveredDays >= Math.ceil(course.days.length / 2)) score += 2
  return { score, routeDistance, coveredDays }
}

export const accommodationMockService = {
  async getRecommendedAccommodations(course: CourseResultView): Promise<AccommodationRecommendation[]> {
    await pause()
    const { mainRegion, centre } = analyseCourse(course)
    return mockAccommodations
      .map(item => {
        const match = recommendationScore(item, mainRegion, centre, course)
        return {
          ...item,
          score: match.score,
          recommendation_reason: match.routeDistance != null && match.routeDistance <= 35
            ? '코스 주요 장소의 중심과 가까운 숙소예요.'
            : item.region === mainRegion
              ? `코스 일정이 ${regionLabel[mainRegion]}에 집중되어 있어 이동 부담을 줄일 수 있어요.`
              : match.coveredDays > 0
                ? '여러 DAY의 출발 위치로 사용하기 좋은 지역이에요.'
                : '코스 중심에서 이동 가능한 인접 권역의 숙소예요.',
        }
      })
      .sort((a, b) => b.score - a.score || a.source_place_id.localeCompare(b.source_place_id))
      .slice(0, 3)
      .map(({ score: _score, ...item }) => item)
  },

  selectAccommodation(condition: CourseCondition, accommodation: AccommodationInput): CourseCondition {
    return { ...JSON.parse(JSON.stringify(condition)) as CourseCondition, accommodation: { ...accommodation } }
  },
}

const regionLabel: Record<RegionCode, string> = { EAST: '동부', WEST: '서부', SOUTH: '남부', NORTH: '북부' }
