/* 카카오맵 SDK 로더.
   index.html에 <script>를 박지 않고 필요할 때 한 번만 불러온다 —
   지도 페이지에 들어가지 않는 사용자는 SDK를 받지 않는다.

   ★ 두 가지가 모두 갖춰져야 로드된다
     1) 콘솔 > 앱 > 플랫폼 키 > JavaScript 키 > SDK 도메인에 현재 주소(origin)가 등록돼 있을 것
     2) 콘솔 > 제품 설정 > 카카오맵이 ON일 것 (없으면 disabled OPEN_MAP_AND_LOCAL service 오류)
   file:// 로 열면 origin이 없어 항상 실패한다 */

const KEY = import.meta.env.VITE_KAKAO_MAP_KEY
let loading = null

export function loadKakaoMap() {
  if (window.kakao?.maps?.Map) return Promise.resolve(window.kakao)
  if (loading) return loading

  loading = new Promise((resolve, reject) => {
    if (!KEY) return reject(new Error('VITE_KAKAO_MAP_KEY가 비어 있어요 (.env 확인)'))
    const el = document.createElement('script')
    el.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KEY}&autoload=false`
    el.async = true
    el.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
    el.onerror = () => reject(new Error('카카오맵 SDK를 불러오지 못했어요'))
    document.head.appendChild(el)
  })
  return loading
}
