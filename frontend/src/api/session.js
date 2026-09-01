/**
 * 세션 · 토큰 보관.
 *
 * ─────────────────────────────────────────────────────────────
 * 왜 localStorage 를 버렸나
 * ─────────────────────────────────────────────────────────────
 * OWASP Session Management Cheat Sheet
 *   "Do not store authentication tokens, session IDs, JWTs, refresh tokens, or any
 *    credential in `localStorage` or `sessionStorage`."
 *   권고: "HttpOnly; Secure; SameSite=Strict cookies (preferred) or a
 *          Backend-for-Frontend (BFF) pattern."
 *
 * OWASP HTML5 Security Cheat Sheet
 *   "Do not store session identifiers in local storage as the data is always accessible
 *    by JavaScript. Cookies can mitigate this risk using the `httpOnly` flag."
 *
 * 이전 구현은 액세스·리프레시 토큰을 둘 다 localStorage 에 넣었다.
 * XSS 하나면 두 토큰이 통째로 털리고, 리프레시 토큰까지 있으면 공격자가
 * 사용자가 로그아웃한 뒤에도 세션을 계속 갱신할 수 있다.
 *
 * ─────────────────────────────────────────────────────────────
 * 지금 구조
 * ─────────────────────────────────────────────────────────────
 *   액세스 토큰 : **메모리에만** 둔다. 새로고침하면 사라진다. 저장소에 흔적이 없다.
 *   리프레시 토큰: **쿠키**에 둔다. 앱 코드는 이 값을 절대 읽지 않는다.
 *                  refresh() 는 "쿠키가 자동으로 붙는다"는 전제로 서버에 요청만 한다.
 *
 * ⚠️ 정직하게 밝히는 한계
 *   HttpOnly 는 **서버만** 설정할 수 있다(Set-Cookie 헤더). 백엔드가 없는 이 저장소에서는
 *   JS 로 쿠키를 쓸 수밖에 없고, 그 쿠키는 JS 로 읽힌다. 즉 **지금 상태는 XSS에 안전하지 않다.**
 *   다만 앱 코드가 쿠키를 직접 다루지 않는 모양으로 맞춰 두었기 때문에,
 *   서버가 생기면 `readRefreshCookie` / `writeRefreshCookie` 두 함수만 지우면 된다.
 *   그 지점을 MOCK-ONLY 로 표시해 두었다.
 *
 * 추가로 넣은 것
 *   · 리프레시 토큰 회전(rotation) + 재사용 탐지 — 탈취된 토큰이 한 번 더 쓰이면 계열 전체를 끊는다
 *   · 유휴 만료(idle timeout)와 절대 만료(absolute timeout)를 분리
 *   · 로그인 성공 시 세션 ID 재발급 (세션 고정 공격 차단)
 */

import { ApiError } from './errors.js'
import { load, persist } from './db.js'

/** 액세스 토큰 수명. 짧게 둬야 탈취돼도 창이 좁다. */
export const ACCESS_TTL_MS = 10 * 60 * 1000
/** 리프레시 토큰 절대 수명 — 아무리 계속 써도 이 시점엔 끊긴다 */
export const REFRESH_ABSOLUTE_TTL_MS = 14 * 24 * 60 * 60 * 1000
/** 리프레시 토큰 유휴 수명 — 이 시간 동안 안 쓰면 끊긴다 */
export const REFRESH_IDLE_TTL_MS = 12 * 60 * 60 * 1000

const COOKIE_NAME = 'hangat_rt'

/* ───────────────────────── 메모리 (액세스 토큰) ───────────────────────── */

let accessToken = null

export function getAccessToken () {
  return accessToken
}

function setAccessToken (t) {
  accessToken = t
}

/* ───────────────────────── MOCK-ONLY: 쿠키 입출력 ─────────────────────────
   실서버에서는 이 두 함수가 통째로 사라진다.
   서버가 Set-Cookie: hangat_rt=…; HttpOnly; Secure; SameSite=Strict; Path=/auth 로 내려주고,
   브라우저가 알아서 붙인다. 앱 코드는 쿠키 존재조차 몰라야 한다.
   ──────────────────────────────────────────────────────────────────────── */

const isSecureContext = () =>
  typeof window !== 'undefined' && window.location?.protocol === 'https:'

function writeRefreshCookie (value, maxAgeSec) {
  if (typeof document === 'undefined') {
    memoryCookie = value ? { value, expires: Date.now() + maxAgeSec * 1000 } : null
    return
  }
  const parts = [
    `${COOKIE_NAME}=${value ? encodeURIComponent(value) : ''}`,
    'Path=/',
    // 크로스사이트 요청에 쿠키가 붙지 않게 한다 (CSRF 1차 방어)
    'SameSite=Strict'
  ]
  if (isSecureContext()) parts.push('Secure')
  parts.push(value ? `Max-Age=${maxAgeSec}` : 'Max-Age=0')
  document.cookie = parts.join('; ')
}

/** 테스트(document 없는 환경)용 대체 저장소 */
let memoryCookie = null

function readRefreshCookie () {
  if (typeof document === 'undefined') {
    if (!memoryCookie) return null
    if (memoryCookie.expires < Date.now()) {
      memoryCookie = null
      return null
    }
    return memoryCookie.value
  }
  const hit = document.cookie
    .split('; ')
    .find(c => c.startsWith(`${COOKIE_NAME}=`))
  return hit ? decodeURIComponent(hit.slice(COOKIE_NAME.length + 1)) : null
}

/* ───────────────────────── 토큰 생성 ───────────────────────── */

function b64url (obj) {
  const bytes = new TextEncoder().encode(JSON.stringify(obj))
  let bin = ''
  bytes.forEach(b => { bin += String.fromCharCode(b) })
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function unb64url (s) {
  const pad = s.replace(/-/g, '+').replace(/_/g, '/')
  const bin = atob(pad + '='.repeat((4 - (pad.length % 4)) % 4))
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, c => c.charCodeAt(0))))
}

/** CSPRNG. 없으면 만들지 않는다 — Math.random 으로 토큰을 만드는 건 예측 가능해서 위험하다. */
function randomToken (bytes = 32) {
  const c = globalThis.crypto
  if (!c?.getRandomValues) {
    throw new ApiError(500, 'NO_CSPRNG', '이 환경에서는 안전한 토큰을 만들 수 없어요')
  }
  const a = new Uint8Array(bytes)
  c.getRandomValues(a)
  return [...a].map(b => b.toString(16).padStart(2, '0')).join('')
}

/**
 * JWT 흉내. **서명이 없다** — 목이라서 그렇다.
 * 실서버에서는 RS256/EdDSA 로 서명하고 서버가 검증한다.
 * 클라이언트는 어떤 경우에도 토큰 내용을 신뢰해 권한을 판단하면 안 된다.
 */
function issueAccessToken (userId, sessionId) {
  return `mockjwt.${b64url({
    sub: userId,
    sid: sessionId,
    iat: Date.now(),
    exp: Date.now() + ACCESS_TTL_MS
  })}.unsigned`
}

export function decodeAccessToken (token) {
  try {
    return unb64url(String(token).split('.')[1])
  } catch {
    return null
  }
}

/* ───────────────────────── 세션 수명주기 ───────────────────────── */

function newSessionRecord (db, userId, familyId) {
  const now = Date.now()
  const rec = {
    sessionId: randomToken(8),
    familyId: familyId || randomToken(8),
    userId,
    refreshToken: randomToken(32),
    createdAt: now,
    lastUsedAt: now,
    absoluteExpiresAt: now + REFRESH_ABSOLUTE_TTL_MS,
    consumedAt: null,
    revokedAt: null
  }
  db.sessions.push(rec)
  return rec
}

/**
 * 로그인 성공 시 호출.
 * 이전 세션이 남아 있으면 끊고 새로 만든다 (세션 고정 공격 차단 —
 * OWASP: "session ID must be renewed or regenerated after any privilege level change").
 */
export function createSession (userId) {
  const db = load()
  revokeCurrentSession()

  const rec = newSessionRecord(db, userId)
  persist()

  setAccessToken(issueAccessToken(userId, rec.sessionId))
  writeRefreshCookie(rec.refreshToken, Math.floor(REFRESH_ABSOLUTE_TTL_MS / 1000))
  return { accessToken: accessToken, expiresIn: ACCESS_TTL_MS }
}

/** 로그아웃 — 서버 쪽 세션도 반드시 끊는다(클라이언트에서 지우는 것만으론 부족) */
export function destroySession () {
  revokeCurrentSession()
  setAccessToken(null)
  writeRefreshCookie(null, 0)
}

function revokeCurrentSession () {
  const token = readRefreshCookie()
  if (!token) return
  const db = load()
  const rec = db.sessions.find(s => s.refreshToken === token)
  if (rec) {
    rec.revokedAt = Date.now()
    persist()
  }
}

/** 한 계열(로그인 1회로 시작된 회전 사슬) 전체를 끊는다 */
function revokeFamily (db, familyId, reason) {
  db.sessions.forEach(s => {
    if (s.familyId === familyId && !s.revokedAt) {
      s.revokedAt = Date.now()
      s.revokeReason = reason
    }
  })
  persist()
}

/**
 * 리프레시 토큰으로 액세스 토큰을 재발급한다.
 *
 * 회전(rotation): 쓰인 리프레시 토큰은 즉시 폐기하고 새 토큰을 내려준다.
 * 재사용 탐지: 이미 쓴 토큰이 다시 오면 **탈취**로 보고 계열 전체를 끊는다.
 *   (정상 사용자와 공격자 중 누가 진짜인지 알 수 없으므로 둘 다 끊고 재로그인시키는 게 안전하다)
 */
export function refreshAccessToken () {
  const db = load()
  const token = readRefreshCookie()
  if (!token) {
    setAccessToken(null)
    throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
  }

  const rec = db.sessions.find(s => s.refreshToken === token)
  const now = Date.now()

  if (!rec) {
    destroySession()
    throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
  }

  // ── 재사용 탐지 ──
  if (rec.consumedAt) {
    revokeFamily(db, rec.familyId, 'REFRESH_TOKEN_REUSE')
    destroySession()
    throw new ApiError(401, 'SESSION_REVOKED', '보안을 위해 로그아웃했어요. 다시 로그인해 주세요')
  }
  if (rec.revokedAt) {
    destroySession()
    throw new ApiError(401, 'SESSION_REVOKED', '세션이 종료됐어요. 다시 로그인해 주세요')
  }
  if (rec.absoluteExpiresAt < now) {
    revokeFamily(db, rec.familyId, 'ABSOLUTE_TIMEOUT')
    destroySession()
    throw new ApiError(401, 'SESSION_EXPIRED', '로그인한 지 오래돼서 다시 로그인해야 해요')
  }
  if (now - rec.lastUsedAt > REFRESH_IDLE_TTL_MS) {
    revokeFamily(db, rec.familyId, 'IDLE_TIMEOUT')
    destroySession()
    throw new ApiError(401, 'SESSION_EXPIRED', '한동안 사용하지 않아 로그아웃했어요')
  }

  // ── 회전 ──
  rec.consumedAt = now
  const next = newSessionRecord(db, rec.userId, rec.familyId)
  next.absoluteExpiresAt = rec.absoluteExpiresAt // 절대 만료는 연장되지 않는다
  persist()

  setAccessToken(issueAccessToken(rec.userId, next.sessionId))
  writeRefreshCookie(next.refreshToken, Math.floor((next.absoluteExpiresAt - now) / 1000))
  return { accessToken, expiresIn: ACCESS_TTL_MS }
}

/** 유효한 액세스 토큰이 있으면 userId, 없으면 null */
export function currentUserId () {
  if (!accessToken) return null
  const p = decodeAccessToken(accessToken)
  if (!p || p.exp <= Date.now()) return null

  const db = load()
  const s = db.sessions.find(x => x.sessionId === p.sid)
  // 서버에서 끊긴 세션의 액세스 토큰은 만료 전이라도 무효다
  if (!s || s.revokedAt) return null
  return p.sub
}

/** 리프레시 쿠키라도 남아 있는지 — 앱 부팅 시 "복구 시도할 가치가 있나" 판단용 */
export function hasResumableSession () {
  return !!readRefreshCookie()
}

/** 남은 액세스 토큰 수명(초) */
export function accessTokenRemainSeconds () {
  const p = accessToken ? decodeAccessToken(accessToken) : null
  return p ? Math.max(0, Math.round((p.exp - Date.now()) / 1000)) : 0
}

/** 테스트용 전체 초기화 */
export function _resetSessionState () {
  accessToken = null
  memoryCookie = null
  if (typeof document !== 'undefined') writeRefreshCookie(null, 0)
}

/**
 * 테스트 전용: 리프레시 쿠키에 임의 값을 심는다.
 * 토큰 탈취·재사용 상황을 재현하려면 앱 코드가 감춰 둔 쿠키 경로에 직접 값을 넣어야 한다.
 * 프로덕션 코드에서는 절대 부르지 않는다.
 */
export function _setRefreshCookieForTest (value) {
  writeRefreshCookie(value, Math.floor(REFRESH_ABSOLUTE_TTL_MS / 1000))
}
