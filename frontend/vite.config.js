import { createHash } from 'node:crypto'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * index.html 의 %CSP% 를 모드에 맞는 Content-Security-Policy 로 바꾼다.
 *
 * - script-src: 'self' + 인라인 테마 스크립트의 sha256 해시.
 *   해시를 쓰면 'unsafe-inline' 없이도 그 블록 하나만 허용된다.
 * - style-src: 'unsafe-inline' 이 필요하다. 개발 모드에서 Vue SFC 의 <style> 이
 *   런타임 JS 로 주입되고, 일부 데이터 시각화가 동적 style 속성을 사용한다.
 *   script-src 의 'unsafe-inline' 과 달리 style-src 쪽은 XSS 실행 경로가 아니라 위험이 훨씬 낮다.
 * - connect-src: HIBP Pwned Passwords(k-익명성 유출 조회) + 개발 모드 HMR 웹소켓.
 * - img-src: 지도 타일 CDN + data: (플레이스홀더).
 *
 * ⚠️ meta 태그로 전달한 CSP 는 frame-ancestors 를 무시한다(스펙상).
 *    클릭재킹 차단은 서버 헤더로 넣어야 한다. README 참고.
 */
function htmlCspPlugin () {
  return {
    name: 'html-csp',
    enforce: 'post',
    transformIndexHtml (html, ctx) {
      {
        const dev = !!ctx.server

        // 인라인 <script> 본문을 그대로 해시한다 (여는/닫는 태그 제외)
        const hashes = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)]
          .map(m => `'sha256-${createHash('sha256').update(m[1], 'utf8').digest('base64')}'`)

        const directives = [
          "default-src 'none'",
          /*
            통합(2026-08-17): 지도 화면이 들어오며 카카오 출처를 열었다.
            SDK 는 dapi.kakao.com 에서 내려오고, 그 스크립트가 다시 타일 이미지를
            daumcdn / kakaocdn 에서 가져온다.
            폰트는 index.html 의 <link> 로 불러오므로 googleapis / gstatic 도 필요하다.
          */
          /* dapi.kakao.com 의 sdk.js 는 로더일 뿐이고, 실제 지도 코드는
             t1.daumcdn.net/mapjsapi/... 에서 2차로 내려온다. 둘 다 열어야 한다. */
          `script-src 'self' https://dapi.kakao.com http://t1.daumcdn.net https://t1.daumcdn.net ${hashes.join(' ')}`.trim(),
          "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
          /* 지도 타일은 mts.daumcdn.net, 마커·아이콘은 t1.daumcdn.net 에서 온다.
             카카오 SDK 가 http 로 요청하므로 http 와일드카드도 필요하다
             (운영 빌드는 upgrade-insecure-requests 가 https 로 승격시킨다). */
          /* tong.visitkorea.or.kr: TourAPI 장소 사진(한산 장소 캐러셀 등) - 공사 이미지는 http 원본이라
             http도 열되, 운영 빌드는 upgrade-insecure-requests 가 https 로 승격시킨다. */
          "img-src 'self' data: blob: http://*.daumcdn.net https://*.daumcdn.net http://*.kakaocdn.net https://*.kakaocdn.net http://tong.visitkorea.or.kr https://tong.visitkorea.or.kr",
          /* 개발 모드는 백엔드(hangat-api)를 직접 호출한다.
             운영 빌드는 같은 도메인 /api 프록시 경유라 'self' 로 충분하다. */
          `connect-src 'self' https://api.pwnedpasswords.com https://dapi.kakao.com${dev ? ' http://localhost:8080 ws: wss:' : ''}`,
          "font-src 'self' https://fonts.gstatic.com",
          "form-action 'self'",
          "base-uri 'none'",
          "object-src 'none'",
          "frame-src 'none'",
          "manifest-src 'self'",
          ...(dev ? [] : ['upgrade-insecure-requests'])
        ]

        // content="%CSP%" 형태만 정확히 바꾼다.
        // 단순 replace('%CSP%') 를 쓰면 주석에 적힌 설명 문구가 먼저 걸린다(실제로 겪은 버그).
        return html.replace(
          /content="%CSP%"/,
          `content="${directives.join('; ')}"`
        )
      }
    }
  }
}

export default defineConfig(() => ({
  plugins: [vue(), htmlCspPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 4173,
    // 포트를 못 잡으면 조용히 다른 번호로 옮기지 말고 실패시킨다
    strictPort: true,
    headers: {
      // 개발 서버에도 배포와 같은 헤더를 걸어 두면 로컬에서 미리 깨진다
      'X-Frame-Options': 'DENY',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'Permissions-Policy': 'geolocation=(self), camera=(), microphone=(), payment=()',
      'Cross-Origin-Opener-Policy': 'same-origin'
    }
  },
  preview: {
    port: 4173,
    strictPort: true
  },
  test: {
    environment: 'node',
    include: ['src/**/*.spec.js', 'src/**/*.spec.ts', 'src/**/*.test.ts']
  }
}))
