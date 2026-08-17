/**
 * 장소 마스터 데이터 (샘플).
 *
 * 출처: 원본 index.html:437~465 의 `SPOTS` 배열을 **값 변경 없이** 옮기고,
 *       마이페이지 요구사항(MY_006: 주소 / 운영 상태 표시)에 필요한 필드만 덧붙였다.
 *
 * 필드 매핑 (원본 → 여기)
 *   n → name        x,y → x,y        r → region      c → category
 *   b → base(기본 집중률)             fee → fee       d → stayMinutes
 *   park → park     wc → toilet      in → indoor
 *
 * ⚠️ 아래 두 필드는 원본에도, 요구사항 정의서에도 실제 값이 없다:
 *   - addr  : 권역에서 파생한 **읍·면 단위 근사 주소**다. 번지 주소를 지어내지 않았다.
 *             실서비스는 한국관광공사 TourAPI `addr1` 을 그대로 쓴다.
 *   - hours : 유료·실내 시설만 대표 운영시간을 넣은 **샘플**이다.
 *             무료 야외 장소는 null(상시 개방)로 둔다.
 *   두 필드는 화면에서 "샘플 데이터" 고지와 함께 노출된다.
 */

const REGION_ADDR = {
  '애월·한림': '제주특별자치도 제주시 애월읍·한림읍 일대',
  제주시: '제주특별자치도 제주시 일대',
  '성산·구좌': '제주특별자치도 서귀포시 성산읍 · 제주시 구좌읍 일대',
  '서귀포·중문': '제주특별자치도 서귀포시 중문·서귀포 일대'
}

/** 원본 SPOTS (index.html:437~465) */
const RAW = [
  { n: '금오름', x: 126.306, y: 33.356, r: '애월·한림', c: '오름', b: 20, fee: 0, d: 90, park: 1, wc: 1, in: 0 },
  { n: '새별오름', x: 126.357, y: 33.364, r: '애월·한림', c: '오름', b: 34, fee: 0, d: 80, park: 1, wc: 1, in: 0 },
  { n: '저지오름', x: 126.268, y: 33.332, r: '애월·한림', c: '오름', b: 18, fee: 0, d: 70, park: 1, wc: 0, in: 0 },
  { n: '제주현대미술관', x: 126.271, y: 33.339, r: '애월·한림', c: '전시', b: 16, fee: 2000, d: 60, park: 1, wc: 1, in: 1 },
  { n: '김창열미술관', x: 126.276, y: 33.336, r: '애월·한림', c: '전시', b: 14, fee: 2000, d: 50, park: 1, wc: 1, in: 1 },
  { n: '성이시돌목장', x: 126.332, y: 33.332, r: '애월·한림', c: '목장', b: 41, fee: 0, d: 60, park: 1, wc: 1, in: 0 },
  { n: '수월봉', x: 126.167, y: 33.298, r: '애월·한림', c: '해안', b: 26, fee: 0, d: 60, park: 1, wc: 1, in: 0 },
  { n: '항몽유적지', x: 126.360, y: 33.431, r: '애월·한림', c: '역사', b: 15, fee: 0, d: 50, park: 1, wc: 1, in: 0 },
  { n: '협재해수욕장', x: 126.240, y: 33.394, r: '애월·한림', c: '해변', b: 64, fee: 0, d: 90, park: 1, wc: 1, in: 0 },
  { n: '한담해안산책로', x: 126.332, y: 33.458, r: '애월·한림', c: '해안', b: 58, fee: 0, d: 60, park: 0, wc: 1, in: 0 },
  { n: '곽지해수욕장', x: 126.305, y: 33.451, r: '애월·한림', c: '해변', b: 49, fee: 0, d: 70, park: 1, wc: 1, in: 0 },
  { n: '한림공원', x: 126.240, y: 33.388, r: '애월·한림', c: '공원', b: 44, fee: 15000, d: 100, park: 1, wc: 1, in: 0 },
  { n: '애월카페거리', x: 126.338, y: 33.462, r: '애월·한림', c: '거리', b: 61, fee: 0, d: 60, park: 0, wc: 0, in: 0 },
  { n: '용두암', x: 126.512, y: 33.516, r: '제주시', c: '해안', b: 57, fee: 0, d: 40, park: 1, wc: 1, in: 0 },
  { n: '사라봉', x: 126.548, y: 33.520, r: '제주시', c: '오름', b: 22, fee: 0, d: 60, park: 1, wc: 1, in: 0 },
  { n: '삼양검은모래해변', x: 126.594, y: 33.523, r: '제주시', c: '해변', b: 29, fee: 0, d: 60, park: 1, wc: 1, in: 0 },
  { n: '넥슨컴퓨터박물관', x: 126.529, y: 33.451, r: '제주시', c: '전시', b: 31, fee: 8000, d: 80, park: 1, wc: 1, in: 1 },
  { n: '동문시장', x: 126.527, y: 33.512, r: '제주시', c: '시장', b: 66, fee: 0, d: 60, park: 0, wc: 1, in: 1 },
  { n: '성산일출봉', x: 126.942, y: 33.458, r: '성산·구좌', c: '오름', b: 70, fee: 5000, d: 90, park: 1, wc: 1, in: 0 },
  { n: '우도', x: 126.954, y: 33.503, r: '성산·구좌', c: '섬', b: 67, fee: 10500, d: 180, park: 0, wc: 1, in: 0 },
  { n: '만장굴', x: 126.771, y: 33.520, r: '성산·구좌', c: '동굴', b: 52, fee: 4000, d: 70, park: 1, wc: 1, in: 1 },
  { n: '비자림', x: 126.810, y: 33.492, r: '성산·구좌', c: '숲', b: 46, fee: 3000, d: 80, park: 1, wc: 1, in: 0 },
  { n: '세화해변', x: 126.860, y: 33.524, r: '성산·구좌', c: '해변', b: 27, fee: 0, d: 60, park: 1, wc: 1, in: 0 },
  { n: '천지연폭포', x: 126.554, y: 33.250, r: '서귀포·중문', c: '폭포', b: 55, fee: 2000, d: 50, park: 1, wc: 1, in: 0 },
  { n: '주상절리대', x: 126.427, y: 33.238, r: '서귀포·중문', c: '해안', b: 51, fee: 2000, d: 50, park: 1, wc: 1, in: 0 },
  { n: '카멜리아힐', x: 126.369, y: 33.293, r: '서귀포·중문', c: '정원', b: 43, fee: 10000, d: 90, park: 1, wc: 1, in: 0 },
  { n: '오설록티뮤지엄', x: 126.289, y: 33.305, r: '서귀포·중문', c: '전시', b: 59, fee: 0, d: 60, park: 1, wc: 1, in: 1 },
  { n: '외돌개', x: 126.542, y: 33.240, r: '서귀포·중문', c: '해안', b: 38, fee: 0, d: 50, park: 1, wc: 1, in: 0 }
]

export const PLACES = RAW.map((s, i) => ({
  id: `p${i + 1}`,
  name: s.n,
  n: s.n, // crowd.js 가 원본과 같은 키(n, b)를 쓰므로 유지
  b: s.b,
  x: s.x,
  y: s.y,
  region: s.r,
  category: s.c,
  base: s.b,
  fee: s.fee,
  stayMinutes: s.d,
  park: !!s.park,
  toilet: !!s.wc,
  indoor: !!s.in,
  addr: REGION_ADDR[s.r] || '제주특별자치도',
  // 유료 또는 실내 시설만 운영시간 샘플을 둔다. null = 상시 개방
  hours: s.fee > 0 || s.in ? { open: '09:00', close: '18:00' } : null
}))

export const PLACE_BY_ID = Object.fromEntries(PLACES.map(p => [p.id, p]))
export const PLACE_BY_NAME = Object.fromEntries(PLACES.map(p => [p.name, p]))

/** 카테고리별 썸네일 색상 — 실제 사진이 없으므로 CSS 그라디언트 플레이스홀더에 쓴다 */
export const CATEGORY_HUE = {
  오름: 148, 해변: 200, 해안: 192, 전시: 268, 목장: 96, 역사: 32,
  공원: 130, 거리: 330, 시장: 18, 섬: 210, 동굴: 258, 숲: 118,
  폭포: 186, 정원: 340
}

/**
 * 운영 상태 (MY_006).
 * hours 가 없으면 '상시 개방', 있으면 현재 시각 기준 운영 중/종료를 판정한다.
 */
export function operationStatus (place, now = new Date()) {
  if (!place.hours) return { code: 'ALWAYS', label: '상시 개방' }
  const [oh, om] = place.hours.open.split(':').map(Number)
  const [ch, cm] = place.hours.close.split(':').map(Number)
  const cur = now.getHours() * 60 + now.getMinutes()
  const open = oh * 60 + om
  const close = ch * 60 + cm
  return cur >= open && cur < close
    ? { code: 'OPEN', label: `운영 중 · ${place.hours.close} 종료` }
    : { code: 'CLOSED', label: `운영 종료 · ${place.hours.open} 오픈` }
}
