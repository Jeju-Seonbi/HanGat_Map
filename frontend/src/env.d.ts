/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 백엔드 API 주소 - 미지정 시 http://localhost:8080 (services/apiClient.ts) */
  readonly VITE_API_BASE_URL?: string
  readonly VITE_TOUR_API_KEY?: string
  /** services/map/KakaoMapLoader.ts (코스 상세의 지도) 가 읽는 이름 */
  readonly VITE_KAKAO_MAP_APP_KEY?: string
  /** composables/useKakaoLoader.js (지도 페이지) 가 읽는 이름 */
  readonly VITE_KAKAO_MAP_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
