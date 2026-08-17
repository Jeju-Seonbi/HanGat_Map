/**
 * 목 API 클라이언트.
 *
 * 실제 HTTP 대신 로컬 저장소를 다루지만, 서버로 바꿀 때 화면 코드가 그대로 남도록
 * 다음을 진짜 API 처럼 흉내 낸다.
 *   - 네트워크 지연
 *   - HTTP 상태코드 + 에러 코드 (ApiError)
 *   - 액세스 토큰 만료 → 리프레시 토큰으로 재발급 → 원 요청 1회 재시도
 *   - 시도 제한 (429 / 423)
 *
 * 토큰 보관 정책은 session.js 에 있다. 여기서는 만료 처리만 한다.
 */

import { load, persist, nextId } from './db.js'
import { ApiError } from './errors.js'
import {
  currentUserId, refreshAccessToken, hasResumableSession,
  ACCESS_TTL_MS
} from './session.js'

export { ApiError, ACCESS_TTL_MS }
export {
  createSession, destroySession, decodeAccessToken,
  accessTokenRemainSeconds, hasResumableSession, getAccessToken
} from './session.js'

/* ────────────────────────── 지연 ────────────────────────── */

let latencyMs = [140, 320]

/** 테스트에서 지연을 없애기 위한 훅 */
export function setLatency (min, max) {
  latencyMs = [min, max ?? min]
}

/**
 * 실패 누적에 따른 지수 backoff 를 실제로 기다릴지 여부.
 * 테스트에서는 끈다 — 지연 계산 자체는 components/security/rateLimit.spec.js가 따로 검증한다.
 * 프로덕션에서 이걸 끄면 브루트포스 방어가 약해지므로, 절대 기본값을 바꾸지 말 것.
 */
let backoffEnabled = true
export function setBackoffEnabled (v) { backoffEnabled = !!v }
export function isBackoffEnabled () { return backoffEnabled }

/**
 * 응답 시간 평탄화(padTo)의 배율.
 * 테스트에서 0.1 로 줄여 전체 실행 시간을 낮춘다.
 * 비율은 그대로라 "두 경로의 소요 시간이 같은가"를 보는 검증은 여전히 유효하다.
 * 프로덕션에서는 반드시 1 이어야 한다 — 줄이면 타이밍 오라클이 되살아난다.
 */
let paddingScale = 1
export function setPaddingScale (v) { paddingScale = Math.max(0, Number(v) || 0) }

export function sleep (ms) {
  return ms > 0 ? new Promise(r => setTimeout(r, ms)) : Promise.resolve()
}

function randomLatency () {
  const [min, max] = latencyMs
  return min + Math.random() * (max - min)
}

/**
 * 응답 시간을 목표치에 맞춰 늘린다.
 *
 * OWASP Authentication Cheat Sheet / ASVS 6.3.8
 *   "Verify that valid users cannot be deduced from failed authentication challenges,
 *    such as by basing on error messages, HTTP response codes, or **different response times**."
 *
 * 계정이 없을 때는 해시 비교를 건너뛰어 빨리 끝나므로, 그대로 두면
 * 응답 시간만으로 "이 이메일은 가입돼 있다"를 알아낼 수 있다.
 */
export async function padTo (startedAt, targetMs) {
  const target = targetMs * paddingScale
  const elapsed = Date.now() - startedAt
  if (elapsed < target) await sleep(target - elapsed)
}

/* ────────────────────────── 호출 래퍼 ────────────────────────── */

/**
 * @param {(ctx:{userId:string|null, db:object})=>any} handler
 * @param {{auth?:boolean}} opts auth:true 면 로그인 필수 (없으면 401)
 */
export async function call (handler, { auth = false } = {}) {
  await sleep(randomLatency())

  let userId = null
  if (auth) {
    userId = currentUserId()
    if (!userId) {
      // 액세스 토큰이 없거나 만료 → 리프레시 (여기서 실패하면 401 이 그대로 올라간다)
      if (!hasResumableSession()) {
        throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
      }
      refreshAccessToken()
      userId = currentUserId()
      if (!userId) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
    }
  }

  const db = load()
  const result = await handler({ userId, db })
  persist()
  return result
}

/* ────────────────────────── 감사 로그 ────────────────────────── */

/**
 * 인증 이벤트 기록.
 * OWASP Authentication Cheat Sheet: "Ensure that all failures are logged and reviewed",
 * "Ensure that all account lockouts are logged and reviewed".
 *
 * ⚠️ 로그에 비밀번호·토큰·전체 이메일을 남기지 않는다. 식별은 userId 나 해시된 키로만 한다.
 */
export function auditAuth (db, event, { userKey = null, outcome = 'FAIL', meta = null } = {}) {
  db.authLog = db.authLog || []
  db.authLog.push({
    at: new Date().toISOString(),
    event,
    userKey,
    outcome,
    meta
  })
  // 목이므로 최근 300건만 유지
  if (db.authLog.length > 300) db.authLog = db.authLog.slice(-300)
}

/**
 * 로그에 남길 계정 키.
 * 원문 이메일을 저장소에 그대로 쌓지 않기 위해 되돌릴 수 없는 짧은 지문으로 바꾼다.
 * (목이라 암호학적 강도는 없다. 서버에서는 HMAC + 별도 보관 키를 쓴다.)
 */
export function accountKeyOf (email) {
  const s = String(email || '').trim().toLowerCase()
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return 'acct_' + (h >>> 0).toString(36)
}

export { load, persist, nextId }
