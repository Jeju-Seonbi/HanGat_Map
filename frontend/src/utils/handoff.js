/**
 * 화면 사이로 값을 한 번만 넘기는 통로.
 *
 * 왜 필요한가:
 *   가입 직후 "인증 메일 보냄" 화면으로 이메일과 개발용 토큰을 넘겨야 하는데,
 *   이전 구현은 `/signup/done?email=…&token=…` 처럼 **쿼리스트링**에 실었다.
 *   URL 에 담은 값은
 *     · 브라우저 히스토리에 남고
 *     · 서버·프록시 액세스 로그에 남고
 *     · 사용자가 주소를 복사해 공유하면 그대로 새어 나간다.
 *   (Referer 유출은 index.html 의 <meta name="referrer" content="no-referrer"> 로 막았지만,
 *    나머지 경로는 URL 에서 값을 빼는 수밖에 없다.)
 *
 * sessionStorage 를 쓰되 **읽는 즉시 지운다**. 탭을 닫으면 함께 사라진다.
 * 여기에도 비밀번호 같은 장기 비밀은 넣지 않는다.
 */

const PREFIX = 'hangat_handoff:'

export function putHandoff (key, value) {
  try {
    window.sessionStorage.setItem(PREFIX + key, JSON.stringify({ v: value, at: Date.now() }))
    return true
  } catch {
    return false
  }
}

/** 읽고 바로 지운다 (1회성) */
export function takeHandoff (key, maxAgeMs = 10 * 60 * 1000) {
  try {
    const raw = window.sessionStorage.getItem(PREFIX + key)
    window.sessionStorage.removeItem(PREFIX + key)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (Date.now() - parsed.at > maxAgeMs) return null
    return parsed.v
  } catch {
    return null
  }
}

/**
 * 주소창에서 민감한 쿼리 값을 지운다.
 * 인증·재설정 링크처럼 토큰이 URL 로 올 수밖에 없는 경우, 사용한 직후 호출한다.
 * 히스토리 항목을 새로 쌓지 않도록 replaceState 를 쓴다.
 */
export function stripQueryParams (names) {
  try {
    const url = new URL(window.location.href)
    let changed = false
    names.forEach(n => {
      if (url.searchParams.has(n)) {
        url.searchParams.delete(n)
        changed = true
      }
    })
    if (changed) {
      window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash)
    }
  } catch {
    /* 무시 — 주소 정리는 부가 조치다 */
  }
}
