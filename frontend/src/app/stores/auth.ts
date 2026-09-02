/**
 * 메인·AI코스 화면이 `../app/stores/auth` 로 import 하던 자리.
 *
 * 통합(2026-08-17)에서 인증은 `src/stores/auth.js` 하나로 합쳤다.
 * 두 스토어 모두 defineStore id 가 'auth' 라 공존할 수 없었고,
 * 이쪽이 목 서버·토큰 회전·세션 종료 사유까지 갖고 있어 남겼다.
 *
 * 화면 코드를 고치지 않으려고 이 파일은 re-export 만 한다.
 * 저쪽이 쓰던 isAuthenticated 는 stores/auth.js 에 별칭 getter 로 넣어 두었다.
 */
export { useAuthStore } from '@/stores/auth.js'

/** 통합 전 localStorage 키. 지금은 api/session.js 가 저장을 담당해 쓰이지 않는다 */
export const AUTH_STORAGE_KEY = 'hangatjido.mock-auth'
