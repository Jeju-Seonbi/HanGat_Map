/**
 * 빌드 단위 플래그.
 *
 * `SHOW_DEMO` — 데모(포트폴리오)용 보조 UI 를 띄울지.
 *   · 테스트 계정 안내 · 목 데이터 초기화 같은 **개발 편의 기능**
 *   · `npm run dev` 는 항상 켜짐
 *   · 빌드 결과물은 `--mode demo` 로 빌드했을 때만 켜짐 (frontend/.env.demo → VITE_DEMO=1)
 *
 * ⚠️ 실서버가 붙으면 이 플래그로 빌드하지 말 것 —
 *    테스트 계정 자격증명이 화면에 그대로 노출된다.
 *    (api/auth.js 의 devOnlyResetCode 는 이 플래그와 **무관하게** DEV 에서만 나온다.)
 */
export const SHOW_DEMO = !!import.meta.env.DEV || import.meta.env.VITE_DEMO === '1'
