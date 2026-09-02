/**
 * 유출 비밀번호 대조 (Have I Been Pwned — Pwned Passwords Range API).
 *
 * 근거
 *   · NIST SP 800-63B-4 §3.1.1.2 — "Verifiers SHALL compare the prospective secret against
 *     a blocklist that contains known commonly used, expected, or compromised passwords",
 *     including "Passwords obtained from previous breach corpuses."
 *   · OWASP ASVS 5.0 6.2.12 — 가입·비밀번호 변경 시 유출 목록 대조.
 *
 * k-익명성(k-anonymity) 방식이라 **비밀번호도, 전체 해시도 밖으로 나가지 않는다.**
 *   1. 비밀번호를 SHA-1 해서 40자 16진수를 만든다
 *   2. 그중 **앞 5자만** api.pwnedpasswords.com/range/{prefix} 로 보낸다
 *   3. 서버는 그 prefix 로 시작하는 해시들의 **나머지 35자 + 유출 횟수** 목록을 돌려준다
 *   4. 비교는 브라우저 안에서 한다
 * 서버는 5자 prefix 만 보므로(평균 수백 개 후보) 어떤 비밀번호를 물어봤는지 알 수 없다.
 * `Add-Padding: true` 를 붙이면 응답 길이도 800~1000개로 맞춰져 길이 기반 추론까지 막는다.
 *
 * 확인 사항 (공식 문서 기준)
 *   · API 키 불필요, 요청 수 제한 없음
 *   · SHA-1 을 쓰는 이유는 보안 강도가 아니라 데이터셋 색인 형식이기 때문이다.
 *     여기서 SHA-1 은 "저장·인증용 해시"가 아니라 "조회 키"다. 저장은 서버가 BCrypt/Argon2id 로 한다.
 *
 * ⚠️ 한계
 *   · crypto.subtle 은 **보안 컨텍스트(https 또는 localhost)** 에서만 동작한다.
 *   · 네트워크 실패 시 **가입을 막지 않는다**(fail-open). 조회 실패로 사용자를 잠그면
 *     외부 서비스 장애가 곧 우리 서비스 장애가 된다. 대신 상태를 'unavailable' 로 알린다.
 *   · 이 조회는 **보조 수단**이다. 실제 서비스는 서버에서도 같은 검사를 해야 한다.
 *     클라이언트 검사는 우회할 수 있다.
 */

const ENDPOINT = 'https://api.pwnedpasswords.com/range/'
const TIMEOUT_MS = 4000

/** 같은 prefix 를 반복 조회하지 않도록 세션 캐시 */
const cache = new Map()

async function sha1Hex (text) {
  const subtle = globalThis.crypto?.subtle
  if (!subtle) throw new Error('INSECURE_CONTEXT')
  const buf = await subtle.digest('SHA-1', new TextEncoder().encode(text))
  return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase()
}

/**
 * @param {string} password 정규화(NFKC)된 비밀번호
 * @returns {Promise<{status:'ok'|'unavailable', breached:boolean, count:number, reason?:string}>}
 */
export async function checkBreached (password) {
  if (!password) return { status: 'ok', breached: false, count: 0 }

  let hash
  try {
    hash = await sha1Hex(password)
  } catch (e) {
    return {
      status: 'unavailable',
      breached: false,
      count: 0,
      reason: e.message === 'INSECURE_CONTEXT'
        ? 'https(또는 localhost)가 아니라 유출 조회를 못 했어요'
        : '해시를 만들지 못했어요'
    }
  }

  const prefix = hash.slice(0, 5)
  const suffix = hash.slice(5)

  try {
    let body = cache.get(prefix)
    if (body == null) {
      const ctrl = new AbortController()
      const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS)
      try {
        const res = await fetch(ENDPOINT + prefix, {
          // 응답 크기를 800~1000개로 패딩해 길이 기반 추론을 막는다
          headers: { 'Add-Padding': 'true' },
          signal: ctrl.signal,
          referrerPolicy: 'no-referrer',
          credentials: 'omit',
          cache: 'no-store'
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        body = await res.text()
        cache.set(prefix, body)
      } finally {
        clearTimeout(timer)
      }
    }

    for (const line of body.split('\n')) {
      const idx = line.indexOf(':')
      if (idx < 0) continue
      if (line.slice(0, idx).trim().toUpperCase() === suffix) {
        const count = parseInt(line.slice(idx + 1), 10) || 0
        // 패딩으로 끼워 넣은 항목은 count 가 0 이다 — 유출로 세면 안 된다
        if (count > 0) return { status: 'ok', breached: true, count }
      }
    }
    return { status: 'ok', breached: false, count: 0 }
  } catch (e) {
    return {
      status: 'unavailable',
      breached: false,
      count: 0,
      reason: e.name === 'AbortError' ? '유출 조회가 시간 안에 끝나지 않았어요' : '유출 조회 서버에 닿지 못했어요'
    }
  }
}

/** 테스트에서 캐시를 비우기 위한 훅 */
export function _clearBreachCache () {
  cache.clear()
}
