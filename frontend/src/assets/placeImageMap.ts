/**
 * 장소 id → 대표 사진 경로.
 *
 * ⚠️ 이 파일은 전달본에 없었다 (통합 2026-08-17).
 *    data/data.ts 1행이 `getPlaceImage`를 import 하는데 정동현님 폴더에
 *    assets/placeImageMap 이 없어 빌드가 실패했다.
 *
 * data/data.ts의 대부분은 `/images/places/*.jpg`를 직접 적어 두었고,
 * 이 함수를 쓰는 곳은 두 곳뿐이다 — 'aewol-restaurant' 와 'pyeongdaedang'.
 * 두 장소의 사진은 public/images/places/ 에 없다(전달본에 9장만 있었다).
 * **없는 사진을 지어내지 않고** 카테고리 자리표시자로 떨어뜨린다.
 *
 * 사진이 추가되면 아래 MAP 에 한 줄씩 넣으면 된다.
 */

/** public/images/places/ 에 실제로 있는 파일만 적는다 */
const MAP: Record<string, string> = {
  bijarim: '/images/places/bijarim.jpg',
  seongsan: '/images/places/seongsan.jpg',
  darangshi: '/images/places/darangshi.jpg',
  aewol: '/images/places/aewol.jpg',
  cheonjeyeon: '/images/places/cheonjeyeon.jpg',
  yongduam: '/images/places/yongduam.jpg',
  honinji: '/images/places/honinji.jpg',
  gwangchigi: '/images/places/gwangchigi.jpg',
  saebyeol: '/images/places/saebyeol.jpg'
}

/** 사진이 없는 장소가 떨어질 자리 — public/images/placeholder.svg */
const FALLBACK = '/images/placeholder.svg'

export function getPlaceImage (placeId: string): string {
  return MAP[placeId] ?? FALLBACK
}

export default getPlaceImage
