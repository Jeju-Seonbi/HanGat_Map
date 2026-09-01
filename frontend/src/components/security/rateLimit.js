/**
 * 인증 시도 제한 (브루트포스 · 크리덴셜 스터핑 방어).
 *
 * ─────────────────────────────────────────────────────────────
 * 왜 필요한가 — 이건 권고가 아니라 **국내법상 의무**다
 * ─────────────────────────────────────────────────────────────
 * 개인정보의 안전성 확보조치 기준 [시행 2025.10.31.] 개인정보보호위원회고시 제2025-9호
 *   제5조(접근 권한의 관리) ⑥
 *   "개인정보처리자는 정당한 권한을 가진 자만이 개인정보처리시스템에 접근할 수 있도록
 *    **일정 횟수 이상 인증에 실패한 경우 개인정보처리시스템에 대한 접근을 제한**하는 등
 *    필요한 조치를 하여야 한다."
 *
 * OWASP ASVS 5.0 6.3.1
 *   "Verify that controls to prevent attacks such as credential stuffing and password
 *    brute force are implemented according to the application's security documentation."
 *
 * OWASP Authentication Cheat Sheet
 *   · "The counter of failed logins should be associated with the account itself,
 *      rather than the source IP address"
 *   · "Some applications use an exponential lockout, where the lockout duration starts as
 *      a very short period (e.g., one second), but doubles after each failed login attempt"
 *   · "care must be taken to prevent it from being used to cause a denial of service by
 *      locking out other users' accounts"
 *
 * ─────────────────────────────────────────────────────────────
 * 설계
 * ─────────────────────────────────────────────────────────────
 * 계정 단위와 전역 단위를 **둘 다** 센다.
 *   · 계정 단위만 있으면 → 공격자가 계정 하나당 1~2회씩 훑는 스터핑을 못 막는다
 *   · 전역 단위만 있으면 → 특정 계정 집중 공격을 못 막는다
 *
 * 잠금은 **점진적 지연**을 먼저 쓴다. 곧바로 장시간 잠그면
 * 공격자가 남의 계정을 일부러 잠가 서비스 거부를 만들 수 있다(OWASP 경고).
 *
 * ⚠️ 여기 구현은 **서버가 해야 할 일을 목으로 흉내 낸 것**이다.
 *    클라이언트 저장소에 있는 카운터는 지우면 그만이라 실제 방어력이 없다.
 *    실서버에서는 동일 정책을 서버 저장소(Redis 등)에 두고,
 *    전역 키는 브라우저 식별자가 아니라 **출발지 IP + 디바이스 지문**으로 잡아야 한다.
 */

export const LIMITS = {
  /** 이 횟수를 넘기면 응답을 늦추기 시작한다 */
  softThreshold: 3,
  /** 지연 기본값. 실패 1회마다 2배가 된다 */
  delayBaseMs: 1000,
  delayMaxMs: 30_000,
  /** 이 횟수를 넘기면 계정을 잠근다 */
  hardThreshold: 10,
  lockoutMs: 15 * 60 * 1000,
  /** 실패 기록 관찰 창. 이 시간이 지난 실패는 잊는다 */
  windowMs: 15 * 60 * 1000,
  /** 전역(한 출발지) 실패 상한 — 스터핑 방어 */
  globalThreshold: 30,
  globalWindowMs: 15 * 60 * 1000
}

/** 로그인 외 동작(비밀번호 재설정 요청, 닉네임 조회 등)의 호출량 제한 */
export const ACTION_LIMITS = {
  'password-reset': { max: 5, windowMs: 60 * 60 * 1000 },
  /**
   * 6자리 재설정 코드 확인 시도.
   * NIST SP 800-63B-4 §3.1.3.2 — 비밀이 64비트 미만이면 레이트리밋이 **SHALL**.
   * 코드 자체에도 연속 5회 실패 시 폐기가 걸려 있고, 이건 계정 단위 2차 방어선이다.
   */
  'reset-code-verify': { max: 20, windowMs: 10 * 60 * 1000 },
  'signup': { max: 10, windowMs: 60 * 60 * 1000 },
  'nickname-check': { max: 30, windowMs: 10 * 60 * 1000 },
  'verify-resend': { max: 5, windowMs: 60 * 60 * 1000 }
}

function bucketOf (store, key) {
  store.throttle = store.throttle || {}
  store.throttle[key] = store.throttle[key] || { fails: [], lockedUntil: 0 }
  return store.throttle[key]
}

function prune (bucket, windowMs, now) {
  bucket.fails = bucket.fails.filter(t => now - t < windowMs)
}

/**
 * 계정 단위 상태를 조회한다. 시도 **전에** 부른다.
 * @returns {{allowed:boolean, retryAfterMs:number, delayMs:number, remaining:number, reason?:string}}
 */
export function checkLoginAllowed (store, accountKey, now = Date.now()) {
  const acct = bucketOf(store, `login:${accountKey}`)
  const global = bucketOf(store, 'login:__global__')
  prune(acct, LIMITS.windowMs, now)
  prune(global, LIMITS.globalWindowMs, now)

  if (acct.lockedUntil > now) {
    return {
      allowed: false,
      retryAfterMs: acct.lockedUntil - now,
      delayMs: 0,
      remaining: 0,
      reason: 'ACCOUNT_LOCKED'
    }
  }
  if (global.fails.length >= LIMITS.globalThreshold) {
    const oldest = global.fails[0]
    return {
      allowed: false,
      retryAfterMs: Math.max(0, LIMITS.globalWindowMs - (now - oldest)),
      delayMs: 0,
      remaining: 0,
      reason: 'TOO_MANY_REQUESTS'
    }
  }

  const over = Math.max(0, acct.fails.length - LIMITS.softThreshold)
  const delayMs = over === 0
    ? 0
    : Math.min(LIMITS.delayMaxMs, LIMITS.delayBaseMs * 2 ** (over - 1))

  return {
    allowed: true,
    retryAfterMs: 0,
    delayMs,
    remaining: Math.max(0, LIMITS.hardThreshold - acct.fails.length)
  }
}

/** 인증 실패를 기록한다. 잠금에 걸리면 lockedUntil 을 세운다. */
export function recordLoginFailure (store, accountKey, now = Date.now()) {
  const acct = bucketOf(store, `login:${accountKey}`)
  const global = bucketOf(store, 'login:__global__')
  prune(acct, LIMITS.windowMs, now)
  prune(global, LIMITS.globalWindowMs, now)

  acct.fails.push(now)
  global.fails.push(now)

  if (acct.fails.length >= LIMITS.hardThreshold) {
    acct.lockedUntil = now + LIMITS.lockoutMs
    acct.fails = []
  }
  return acct
}

/** 인증 성공 — 계정 카운터만 비운다. 전역 카운터는 유지한다(스터핑 흔적). */
export function recordLoginSuccess (store, accountKey) {
  const acct = bucketOf(store, `login:${accountKey}`)
  acct.fails = []
  acct.lockedUntil = 0
}

/**
 * 로그인 외 동작의 호출량 제한.
 * @returns {{allowed:boolean, retryAfterMs:number}}
 */
export function consumeAction (store, action, key, now = Date.now()) {
  const cfg = ACTION_LIMITS[action]
  if (!cfg) return { allowed: true, retryAfterMs: 0 }
  const bucket = bucketOf(store, `action:${action}:${key}`)
  prune(bucket, cfg.windowMs, now)
  if (bucket.fails.length >= cfg.max) {
    return { allowed: false, retryAfterMs: Math.max(0, cfg.windowMs - (now - bucket.fails[0])) }
  }
  bucket.fails.push(now)
  return { allowed: true, retryAfterMs: 0 }
}

/** 남은 시간을 사람이 읽을 문장으로 */
export function formatRetryAfter (ms) {
  const s = Math.ceil(ms / 1000)
  if (s < 60) return `${s}초`
  const m = Math.ceil(s / 60)
  return `${m}분`
}

/** 테스트·개발용 초기화 */
export function resetThrottle (store) {
  store.throttle = {}
}
