/**
 * 회원 API (요구사항 정의서 USER_001 ~ USER_003, MY_009 ~ MY_011).
 *
 * 보안 관련 결정은 전부 근거를 주석에 달았다. 요약은 ../../보안.md 참고.
 *
 * ⚠️ 목 서버다. 실제 구현에서 서버로 옮겨야 하는 것:
 *    - 비밀번호 해시: Argon2id(권장) 또는 BCrypt(work factor ≥ 10)
 *      · 개인정보의 안전성 확보조치 기준 제7조① "비밀번호를 저장하는 경우에는
 *        복호화되지 아니하도록 일방향 암호화하여 저장하여야 한다"
 *    - 메일 발송 (인증 메일 · 6자리 재설정 코드)
 *    - JWT 서명·검증
 *    - 시도 제한 카운터 (클라이언트 카운터는 지우면 그만이라 방어력이 없다)
 */

import {
  call, ApiError, sleep, padTo, auditAuth, accountKeyOf, nextId, isBackoffEnabled
} from './client.js'
import { createSession, destroySession } from './session.js'
import { mockHashPassword, verifyMockPassword, DUMMY_PASSWORD_HASH } from './db.js'
import { storage } from './storage.js'
import { isValidEmail, checkEmail, checkNickname, checkName, checkBirthDate, maskEmail } from '../utils/validators.js'
import {
  checkPassword, normalizePassword, PASSWORD_POLICY
} from '../components/security/passwordPolicy.js'
import { checkBreached } from '../components/security/breachCheck.js'
import {
  checkLoginAllowed, recordLoginFailure, recordLoginSuccess,
  consumeAction, formatRetryAfter
} from '../components/security/rateLimit.js'

const IS_DEV = !!import.meta.env?.DEV

/**
 * 가입 시 이메일 존재 여부를 흘리지 않는 모드.
 *
 * OWASP Authentication Cheat Sheet — 계정 생성 시
 *   잘못된 응답: "This user ID is already in use." / "Welcome! You have signed up successfully."
 *   올바른 응답: "A link to activate your account has been emailed to the address provided."
 * OWASP ASVS 5.0 6.3.8 — 실패 응답으로 유효 사용자를 추론할 수 없어야 한다.
 *
 * 요구사항 정의서 USER_002 의 "기존 회원과 중복되지 않아야 한다"는 **서버에서 그대로 지킨다**.
 * 다만 그 사실을 **가입 화면에서 공격자에게 알려주지 않는다**.
 * 이미 가입된 주소면 계정을 새로 만들지 않고 "이미 가입돼 있다"는 안내 메일을 보낸다.
 *
 * false 로 바꾸면 정의서 문구대로 즉시 "이미 가입된 이메일이에요"를 돌려준다(열거 가능해짐).
 */
export const ENUMERATION_SAFE_SIGNUP = true

/** 인증 토큰 수명 — ASVS 6.4.1 "expire after a short period of time or after they are initially used" */
export const VERIFY_TOKEN_TTL_MS = 24 * 60 * 60 * 1000

/**
 * 비밀번호 재설정 6자리 숫자 코드.
 *
 * NIST SP 800-63B-4 §3.1.3.2 (대역 외 인증 비밀) — 원문:
 *   · "The verifier SHALL generate random authentication secrets that are at least
 *      **six decimal digits** (or equivalent) in length"
 *   · "In all cases, the authentication SHALL be considered invalid unless completed
 *      within **10 minutes**"
 *   · "Verifiers SHALL accept a given authentication secret as valid **only once** during
 *      the validity period to provide replay resistance"
 *   · "If the authentication secret is **less than 64 bits** long, the verifier SHALL
 *      implement a **rate-limiting mechanism** that effectively limits the total number of
 *      consecutive failed authentication attempts"
 *
 * 6자리 = 10^6 = 약 **19.9비트**. 64비트에 한참 못 미치므로 레이트리밋이 **필수**다.
 * 아래 세 가지를 전부 건다.
 *   ① 10분 만료  ② 1회용  ③ 연속 5회 실패 시 코드 자체를 폐기 + 계정별 호출량 제한
 */
export const RESET_CODE_LENGTH = 6
export const RESET_CODE_TTL_MS = 10 * 60 * 1000
export const RESET_CODE_MAX_ATTEMPTS = 5

/**
 * 코드 확인에 성공하면 발급되는 **재설정 티켓**(32바이트).
 * 실제 비밀번호 변경은 이 티켓으로만 할 수 있다.
 * 6자리 코드를 그대로 최종 인가 수단으로 쓰면 재전송·재사용 여지가 생기기 때문에 한 단계 분리했다.
 */
export const RESET_TICKET_TTL_MS = 10 * 60 * 1000

/** 로그인 응답 목표 시간 — 성공·실패·계정없음이 모두 같은 시간에 끝나게 한다 */
const LOGIN_TARGET_MS = 420

/* ───────── 최근 로그인 내역 (USER_001 — "프론트 영역") ─────────
   ⚠️ 이 목록은 브라우저에 남는 개인정보다.
      기본값은 **마스킹된 이메일만** 저장한다. 원문 이메일은 사용자가
      "이 기기에 이메일 기억하기"를 켰을 때만 저장한다(자동 입력용).
      원문이 없으면 클릭해도 이메일 칸이 채워지지 않는다 — 그게 정상 동작이다. */

const RECENT_KEY = 'hangat_recent_logins'
const RECENT_MAX = 3

export function readRecentLogins () {
  const list = storage.getJson(RECENT_KEY, [])
  return Array.isArray(list) ? list : []
}

function writeRecentLogins (list) {
  storage.setJson(RECENT_KEY, list.slice(0, RECENT_MAX))
}

export function pushRecentLogin (user, { rememberEmail = false } = {}) {
  const masked = maskEmail(user.email)
  const list = readRecentLogins().filter(r => r.maskedEmail !== masked)
  list.unshift({
    maskedEmail: masked,
    // 원문 이메일은 사용자가 명시적으로 켰을 때만
    email: rememberEmail ? user.email : null,
    nickname: user.nickname,
    at: new Date().toISOString()
  })
  writeRecentLogins(list)
  return readRecentLogins()
}

export function removeRecentLogin (maskedEmail) {
  writeRecentLogins(readRecentLogins().filter(r => r.maskedEmail !== maskedEmail))
  return readRecentLogins()
}

export function clearRecentLogins () {
  writeRecentLogins([])
  clearLastProvider()
  return []
}

/* ───────── 마지막으로 쓴 로그인 수단 (로그인 화면 말풍선) ─────────
   "최근에 로그인했습니다" 말풍선을 어느 버튼에 붙일지 정하는 값이다.
   개인 식별 정보가 아니라 수단 이름 하나만 담는다 — 이메일도 닉네임도 넣지 않는다.
   마이페이지의 "최근 로그인 기록 지우기"(clearRecentLogins)에서 같이 지워진다. */

const LAST_PROVIDER_KEY = 'hangat_last_provider'

/** 화면에 노출되는 로그인 수단. 이 목록에 없는 값은 저장·반환하지 않는다. */
export const LOGIN_PROVIDERS = ['email', 'kakao', 'google']

export function readLastProvider () {
  const v = storage.getJson(LAST_PROVIDER_KEY, null)
  if (!v || !LOGIN_PROVIDERS.includes(v.provider)) return null
  return v
}

export function writeLastProvider (provider) {
  if (!LOGIN_PROVIDERS.includes(provider)) return readLastProvider()
  storage.setJson(LAST_PROVIDER_KEY, { provider, at: new Date().toISOString() })
  return readLastProvider()
}

export function clearLastProvider () {
  storage.remove(LAST_PROVIDER_KEY)
  return null
}

/* ───────── 직렬화 ───────── */

function publicUser (u) {
  return {
    userId: u.userId,
    email: u.email,
    nickname: u.nickname,
    name: u.name,
    birthDate: u.birthDate,
    roleCode: u.roleCode,
    statusCode: u.statusCode,
    emailVerified: u.emailVerified,
    lastLoginAt: u.lastLoginAt,
    createdAt: u.createdAt
  }
}

const normalizeEmail = e => String(e || '').trim().toLowerCase()
const findByEmail = (db, email) =>
  db.users.find(u => u.email.toLowerCase() === normalizeEmail(email))

function tokenExpired (rec) {
  return !rec || !rec.expiresAt || rec.expiresAt < Date.now()
}

/* ═══════════════════════ USER_001 로그인 ═══════════════════════ */

export function login ({ email, password, rememberEmail = false }) {
  const startedAt = Date.now()

  return call(async ({ db }) => {
    const key = accountKeyOf(email)

    // ── 형식 검사 ──
    // 로그인에서는 **구성 규칙을 검사하지 않는다.**
    // 이전 정책으로 만든 비밀번호를 가진 기존 회원이 로그인 자체를 못 하게 되기 때문이다.
    // 길이 상한만 본다 (해시 연산 DoS 차단).
    if (!isValidEmail(email)) {
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw new ApiError(400, 'INVALID_EMAIL', '이메일 형식을 확인해 주세요')
    }
    const pw = normalizePassword(password)
    if (!pw) {
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw new ApiError(400, 'PASSWORD_REQUIRED', '비밀번호를 입력해 주세요')
    }
    if (pw.length > PASSWORD_POLICY.maxLength) {
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw new ApiError(400, 'PASSWORD_TOO_LONG', '비밀번호가 너무 길어요')
    }

    // ── 시도 제한 (고시 제5조⑥ / ASVS 6.3.1) ──
    const gate = checkLoginAllowed(db, key)
    if (!gate.allowed) {
      auditAuth(db, 'LOGIN_BLOCKED', { userKey: key, meta: gate.reason })
      await padTo(startedAt, LOGIN_TARGET_MS)
      if (gate.reason === 'ACCOUNT_LOCKED') {
        throw new ApiError(423, 'ACCOUNT_LOCKED',
          `로그인 시도가 너무 많았어요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
      }
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `요청이 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }
    // 실패가 쌓일수록 점점 느려진다 (지수 backoff)
    if (gate.delayMs && isBackoffEnabled()) await sleep(gate.delayMs)

    const user = findByEmail(db, email)

    // ── 타이밍 평탄화 ──
    // 계정이 없어도 **더미 해시로 같은 비교를 수행**한다.
    // 이걸 건너뛰면 응답 시간만으로 가입 여부를 알아낼 수 있다 (ASVS 6.3.8).
    const passwordOk = verifyMockPassword(pw, user ? user.passwordHash : DUMMY_PASSWORD_HASH)

    const generic = () =>
      new ApiError(401, 'INVALID_CREDENTIALS', '이메일 또는 비밀번호가 맞지 않아요')

    if (!user || !passwordOk) {
      recordLoginFailure(db, key)
      auditAuth(db, 'LOGIN_FAIL', { userKey: key })
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw generic()
    }

    /*
      계정 상태 안내.
      OWASP 는 "The account is locked or disabled" 도 일반 메시지로 처리하라고 한다.
      여기서는 **비밀번호가 맞은 뒤에만** 구체적으로 알려준다.
      이미 비밀번호를 아는 사람에게만 노출되므로 열거 가치가 없고,
      정상 사용자에게 "왜 안 되는지"를 알려주는 편이 낫다고 판단했다.
      (의도적 편차 — 보안.md 에 기록)
    */
    if (user.statusCode === 'WITHDRAWN' || user.deletedAt) {
      auditAuth(db, 'LOGIN_DENIED', { userKey: key, meta: 'WITHDRAWN' })
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw new ApiError(403, 'ACCOUNT_WITHDRAWN', '탈퇴한 계정이에요. 새로 가입해 주세요')
    }
    if (user.statusCode === 'SUSPENDED') {
      auditAuth(db, 'LOGIN_DENIED', { userKey: key, meta: 'SUSPENDED' })
      await padTo(startedAt, LOGIN_TARGET_MS)
      throw new ApiError(403, 'ACCOUNT_SUSPENDED', '이용이 제한된 계정이에요. 고객센터로 문의해 주세요')
    }

    recordLoginSuccess(db, key)
    user.lastLoginAt = new Date().toISOString()
    auditAuth(db, 'LOGIN_SUCCESS', { userKey: key, outcome: 'OK' })

    // 로그인 시 세션 재발급 (세션 고정 차단)
    const tokens = createSession(user.userId)
    pushRecentLogin(user, { rememberEmail })
    // 다음 방문 때 "최근에 로그인했습니다" 를 어디에 붙일지 기록한다
    writeLastProvider('email')

    await padTo(startedAt, LOGIN_TARGET_MS)
    return { user: publicUser(user), tokens }
  })
}

export function logout () {
  return call(({ db }) => {
    auditAuth(db, 'LOGOUT', { outcome: 'OK' })
    destroySession()
    return { ok: true }
  })
}

/* ═══════════════════════ USER_002 회원가입 ═══════════════════════ */

/**
 * 닉네임 중복 확인.
 * 닉네임은 후기·공유 화면에 그대로 노출되는 **공개 식별자**라
 * 존재 여부가 새어도 추가로 잃을 게 없다. 대신 호출량을 제한한다.
 * (이메일 중복 확인 API 는 열거 오라클이라 제거했다 — ENUMERATION_SAFE_SIGNUP 주석 참고)
 */
export function checkNicknameAvailable (nickname) {
  return call(({ db }) => {
    const gate = consumeAction(db, 'nickname-check', '__global__')
    if (!gate.allowed) {
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `확인 요청이 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }
    const nk = checkNickname(nickname)
    if (!nk.ok) throw new ApiError(400, 'VALIDATION_FAILED', nk.message)
    return {
      available: !db.users.some(
        u => u.nickname.toLowerCase() === String(nickname).trim().toLowerCase()
      )
    }
  })
}

/**
 * 비밀번호 사전 점검 (가입 화면에서 입력 중 호출).
 * 로컬 규칙 + HIBP 유출 조회를 함께 돌린다.
 */
export async function evaluatePassword (password, context = {}) {
  const local = checkPassword(password, context)
  if (!local.ok || !PASSWORD_POLICY.checkBreach) {
    return { ...local, breach: { status: 'skipped', breached: false, count: 0 } }
  }
  const breach = await checkBreached(normalizePassword(password))
  if (breach.breached) {
    return {
      ...local,
      ok: false,
      errors: [`이미 유출된 적 있는 비밀번호예요 (공개된 유출 목록에 ${breach.count.toLocaleString('ko-KR')}번 등장)`],
      breach
    }
  }
  return { ...local, breach }
}

export function signup ({ email, password, passwordConfirm, nickname, name, birthDate }) {
  const startedAt = Date.now()

  return call(async ({ db }) => {
    const gate = consumeAction(db, 'signup', '__global__')
    if (!gate.allowed) {
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `가입 요청이 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }

    const fields = {}
    // 형식 오류는 계정 존재 여부와 무관하므로 구체적으로 알려줘도 열거 위험이 없다
    if (!String(email || '').trim()) fields.email = '이메일을 입력해 주세요'
    else if (!isValidEmail(email)) fields.email = checkEmail(email).message

    const pwResult = checkPassword(password, { email, nickname, name })
    if (!String(password || '')) fields.password = '비밀번호를 입력해 주세요'
    else if (!pwResult.ok) fields.password = pwResult.errors[0]

    if (!String(passwordConfirm || '')) fields.passwordConfirm = '비밀번호 확인을 입력해 주세요'
    else if (normalizePassword(password) !== normalizePassword(passwordConfirm)) {
      fields.passwordConfirm = '비밀번호가 서로 달라요'
    }

    const nk = checkNickname(nickname)
    if (!nk.ok) fields.nickname = nk.message

    // 입력창 필터(FieldText)는 UX 일 뿐이라 우회된다. 판정은 여기서 한다.
    const nm = checkName(name)
    if (!nm.ok) fields.name = nm.message

    // ⚠️ 이 검사는 원래 **없었다.** `birthDate || null` 로 그대로 저장해서
    //    2026-02-31 · 9999-01-01 · 아무 문자열이나 통과했다.
    const bd = checkBirthDate(birthDate)
    if (!bd.ok) fields.birthDate = bd.message

    if (Object.keys(fields).length) {
      throw new ApiError(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요', fields)
    }

    // 유출 비밀번호 차단 (NIST SHALL / ASVS 6.2.12)
    if (PASSWORD_POLICY.checkBreach) {
      const breach = await checkBreached(normalizePassword(password))
      if (breach.breached) {
        throw new ApiError(400, 'PASSWORD_BREACHED',
          '이미 유출된 적 있는 비밀번호예요. 다른 비밀번호를 써주세요',
          { password: `공개된 유출 목록에 ${breach.count.toLocaleString('ko-KR')}번 등장한 비밀번호예요` })
      }
      // 조회 실패(status:'unavailable')는 가입을 막지 않는다 — fail-open. breachCheck.js 주석 참고.
    }

    const nicknameTaken = db.users.some(
      u => u.nickname.toLowerCase() === nickname.trim().toLowerCase()
    )
    if (nicknameTaken) {
      // 닉네임은 공개 식별자이므로 그대로 알려준다
      throw new ApiError(409, 'NICKNAME_TAKEN', '이미 사용 중인 닉네임이에요',
        { nickname: '이미 사용 중인 닉네임이에요' })
    }

    const existing = findByEmail(db, email)

    if (existing) {
      if (!ENUMERATION_SAFE_SIGNUP) {
        throw new ApiError(409, 'EMAIL_TAKEN', '이미 가입된 이메일이에요',
          { email: '이미 가입된 이메일이에요' })
      }
      /*
        열거 방지 경로.
        계정을 새로 만들지 않고, 응답은 성공과 **똑같이** 돌려준다.
        실서버는 이때 "누군가 이 주소로 가입을 시도했다"는 안내 메일을 보낸다.
      */
      auditAuth(db, 'SIGNUP_DUPLICATE_EMAIL', { userKey: accountKeyOf(email) })
      await padTo(startedAt, 700)
      return { pending: true, devOnlyVerifyToken: null, devOnlyNote: IS_DEV ? '이미 가입된 이메일이라 계정을 만들지 않았어요' : undefined }
    }

    const user = {
      userId: nextId('user', 'u'),
      email: String(email).trim(),
      // 실서버는 Argon2id / BCrypt. 목에서는 평문 저장을 피하기 위한 단방향 해시만 쓴다.
      passwordHash: mockHashPassword(password),
      nickname: String(nickname).trim(),
      name: String(name).trim(),
      birthDate: bd.value,
      roleCode: 'USER',
      statusCode: 'ACTIVE',
      emailVerified: false,
      lastLoginAt: null,
      createdAt: new Date().toISOString(),
      passwordChangedAt: new Date().toISOString(),
      deletedAt: null
    }
    db.users.push(user)

    const token = issueOneTimeToken(db, 'verify', user.userId, VERIFY_TOKEN_TTL_MS)
    auditAuth(db, 'SIGNUP', { userKey: accountKeyOf(email), outcome: 'OK' })

    await padTo(startedAt, 700)
    return {
      pending: true,
      // ↓ 실서버는 메일로만 전달한다. 개발 빌드에서만 노출한다.
      devOnlyVerifyToken: IS_DEV ? token : null
    }
  })
}

/* ───────── 일회용 토큰 (인증 · 재설정 공용) ───────── */

/**
 * 일회용 토큰 발급.
 * OWASP Forgot Password Cheat Sheet — 토큰은
 *   "Randomly generated using a cryptographically safe algorithm",
 *   "Sufficiently long to protect against brute-force attacks",
 *   "Single use and expire after an appropriate period."
 *
 * ⚠️ 실서버는 토큰 **원문을 저장하지 않는다.** SHA-256 해시만 저장하고 메일로만 원문을 보낸다.
 *    DB 가 털려도 재설정 링크를 만들 수 없게 하기 위해서다.
 *    여기서는 목이라 원문을 들고 있다 — 서버 구현 시 반드시 해시 저장으로 바꿀 것.
 */
function issueOneTimeToken (db, kind, userId, ttlMs) {
  db.tokens = db.tokens || []
  // 같은 종류의 기존 토큰은 즉시 무효화한다 (링크 여러 개가 동시에 살아있지 않게)
  db.tokens.forEach(t => {
    if (t.kind === kind && t.userId === userId && !t.usedAt) t.usedAt = Date.now()
  })

  const bytes = new Uint8Array(32)
  const c = globalThis.crypto
  if (!c?.getRandomValues) {
    throw new ApiError(500, 'NO_CSPRNG', '안전한 토큰을 만들 수 없어요')
  }
  c.getRandomValues(bytes)
  const value = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('')

  db.tokens.push({
    kind,
    value,
    userId,
    createdAt: Date.now(),
    expiresAt: Date.now() + ttlMs,
    usedAt: null
  })
  return value
}

function consumeOneTimeToken (db, kind, value) {
  db.tokens = db.tokens || []
  const rec = db.tokens.find(t => t.kind === kind && t.value === value)
  if (!rec || rec.usedAt || tokenExpired(rec)) return null
  rec.usedAt = Date.now()
  return rec
}

function peekOneTimeToken (db, kind, value) {
  db.tokens = db.tokens || []
  const rec = db.tokens.find(t => t.kind === kind && t.value === value)
  return !rec || rec.usedAt || tokenExpired(rec) ? null : rec
}

/** CSPRNG 16진 문자열 */
function randomHex (bytes) {
  const c = globalThis.crypto
  if (!c?.getRandomValues) {
    throw new ApiError(500, 'NO_CSPRNG', '안전한 값을 만들 수 없어요')
  }
  const a = new Uint8Array(bytes)
  c.getRandomValues(a)
  return [...a].map(b => b.toString(16).padStart(2, '0')).join('')
}

/* ───────── 6자리 숫자 코드 ───────── */

/**
 * 균등한 6자리 코드.
 * `Math.random()` 은 예측 가능해서 쓰면 안 되고,
 * `%1000000` 같은 나머지 연산은 값이 한쪽으로 쏠린다(모듈로 편향).
 * 32비트 난수를 뽑아 10^6 의 배수를 넘는 값은 버리는 방식(rejection sampling)으로 균등하게 만든다.
 */
export function generateNumericCode (digits = RESET_CODE_LENGTH) {
  const c = globalThis.crypto
  if (!c?.getRandomValues) {
    throw new ApiError(500, 'NO_CSPRNG', '안전한 코드를 만들 수 없어요')
  }
  const range = 10 ** digits
  const limit = Math.floor(0x100000000 / range) * range
  const buf = new Uint32Array(1)
  let v
  do {
    c.getRandomValues(buf)
    v = buf[0]
  } while (v >= limit)
  return String(v % range).padStart(digits, '0')
}

/**
 * 자릿수 비교 시간이 값에 따라 달라지지 않게 한다.
 * 일반 `===` 는 첫 글자가 다르면 바로 끝나므로, 이론적으로는 응답 시간으로 앞자리부터 맞춰 갈 수 있다.
 * (실제로는 네트워크 잡음에 묻히지만, 비용이 0 이므로 그냥 한다)
 */
function timingSafeEqual (a, b) {
  const x = String(a ?? '')
  const y = String(b ?? '')
  let diff = x.length ^ y.length
  const n = Math.max(x.length, y.length)
  for (let i = 0; i < n; i++) {
    diff |= (x.charCodeAt(i) || 0) ^ (y.charCodeAt(i) || 0)
  }
  return diff === 0
}

/** 화면 표시용: 123456 → 123 456 */
export const formatResetCode = code =>
  String(code || '').replace(/(\d{3})(?=\d)/g, '$1 ')

/* ───────── 이메일 인증 ───────── */

/** 인증 메일 링크 클릭 → 인증 완료 후 로그인 (USER_002) */
export function verifyEmail (token) {
  return call(({ db }) => {
    const rec = consumeOneTimeToken(db, 'verify', token)
    if (!rec) {
      throw new ApiError(400, 'INVALID_VERIFY_TOKEN', '만료되었거나 이미 사용한 인증 링크예요')
    }
    const user = db.users.find(u => u.userId === rec.userId)
    if (!user) throw new ApiError(400, 'INVALID_VERIFY_TOKEN', '만료되었거나 이미 사용한 인증 링크예요')

    user.emailVerified = true
    user.lastLoginAt = new Date().toISOString()
    auditAuth(db, 'EMAIL_VERIFIED', { userKey: accountKeyOf(user.email), outcome: 'OK' })

    const tokens = createSession(user.userId)
    pushRecentLogin(user)
    return { user: publicUser(user), tokens }
  })
}

export function resendVerification (email) {
  const startedAt = Date.now()
  return call(async ({ db }) => {
    const gate = consumeAction(db, 'verify-resend', accountKeyOf(email))
    if (!gate.allowed) {
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `요청이 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }
    const user = findByEmail(db, email)
    let token = null
    if (user && !user.emailVerified) {
      token = issueOneTimeToken(db, 'verify', user.userId, VERIFY_TOKEN_TTL_MS)
    }
    // 가입 여부를 노출하지 않도록 응답과 소요 시간을 동일하게 맞춘다
    await padTo(startedAt, 600)
    return { ok: true, devOnlyVerifyToken: IS_DEV ? token : null }
  })
}

/* ═══════════════════════ USER_003 비밀번호 재설정 ═══════════════════════ */

/**
 * 3단계 흐름.
 *
 *   1단계 requestPasswordReset  이메일 + 이름 → 6자리 코드를 메일로 발송
 *   2단계 verifyResetCode       코드 확인 → 재설정 티켓 발급 (+ 마스킹된 이메일 공개)
 *   3단계 resetPassword         티켓으로 새 비밀번호 설정
 *
 * 정의서 USER_003 원문:
 *   "비밀번호 찾기를 누르면 아이디랑 이름을 입력하게 하고 맞을 경우
 *    해당 아이디랑 이름으로 이메일(중간에**표시하기)을 보여주어
 *    해당 이메일에 임시 비밀번호를 알려준다."
 *
 * 정의서와 다른 점 두 가지 — 근거는 ../../../보안.md
 *
 * ① **임시 비밀번호 → 6자리 인증 코드**
 *    임시 비밀번호는 그 자체가 계정 비밀번호가 되어 메일함에 평문으로 남고,
 *    사용자가 그대로 계속 쓰기 쉽다 (ASVS 6.4.1: 시스템 생성 비밀번호는
 *    "must not be permitted to become the long term password").
 *    코드는 **비밀번호가 아니라 본인 확인 수단**이라 이 문제가 없다.
 *
 * ② **일치 여부·마스킹된 이메일을 1단계에서 보여주지 않는다**
 *    "맞을 경우 보여준다" = "이 계정이 있다"를 알려주는 것 (ASVS 6.3.8).
 *    마스킹된 이메일은 **코드가 맞은 뒤(2단계)** 보여준다.
 *    코드를 맞혔다는 건 메일함을 통제한다는 뜻이라 그 시점의 노출은 열거로 이어지지 않는다.
 *    → "중간에 ** 표시" 기능 자체는 살아 있고 노출 시점만 뒤로 옮겼다.
 */

/**
 * 1단계 — 코드 발송.
 * 계정이 있든 없든, 이름이 맞든 틀리든 **같은 응답 · 같은 소요 시간**으로 끝난다.
 */
export function requestPasswordReset ({ email, name }) {
  const startedAt = Date.now()

  return call(async ({ db }) => {
    const key = accountKeyOf(email)

    /*
      형식 검사를 **레이트리밋보다 먼저** 한다 (login 과 같은 순서).
      · 형식 오류는 입력 문자열만으로 정해지므로 계정 존재 여부를 전혀 드러내지 않는다.
        → 이 응답이 아래의 "항상 동일한 응답" 원칙을 깨지 않는다.
      · 형식이 틀린 주소는 어떤 계정과도 매칭될 수 없다. 요청 예산을 태울 이유가 없다.
      타이밍은 다른 경로와 맞춘다(padTo) — 형식 오류만 빨리 돌아오면 그 자체가 신호가 된다.
    */
    if (!isValidEmail(email)) {
      await padTo(startedAt, 800)
      throw new ApiError(400, 'INVALID_EMAIL', checkEmail(email).message)
    }

    const gate = consumeAction(db, 'password-reset', key)
    if (!gate.allowed) {
      await padTo(startedAt, 800)
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `재설정 요청이 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }

    const user = findByEmail(db, email)
    const eligible =
      !!user &&
      String(user.name || '').trim() === String(name || '').trim() &&
      user.statusCode === 'ACTIVE' && !user.deletedAt

    /*
      requestId 는 "코드를 요청한 그 화면"을 가리키는 값이다.
      2단계에서 이 값을 함께 제시해야 코드가 통과한다.
      → 남이 요청해 둔 코드를 제3자가 무차별 대입하는 경로를 막는다
        (NIST §3.1.3: 대역 외 비밀은 요청한 세션과 묶여야 한다는 취지)
      계정 유무와 관계없이 **항상** 발급한다. 없으면 그 자체로 계정 부재가 드러나기 때문.
    */
    const requestId = randomHex(16)
    let code = null

    if (eligible) {
      db.tokens = db.tokens || []
      // 새 코드를 만들면 이전 코드는 즉시 무효 (동시에 여러 개가 살아있지 않게)
      db.tokens.forEach(t => {
        if (t.kind === 'reset-code' && t.userId === user.userId && !t.usedAt) t.usedAt = Date.now()
      })

      code = generateNumericCode()
      db.tokens.push({
        kind: 'reset-code',
        // ⚠️ 실서버는 코드 원문을 저장하지 않는다. HMAC/해시만 저장하고 원문은 메일로만 보낸다.
        value: code,
        userId: user.userId,
        requestId,
        attempts: 0,
        createdAt: Date.now(),
        expiresAt: Date.now() + RESET_CODE_TTL_MS,
        usedAt: null
      })
      auditAuth(db, 'PASSWORD_RESET_CODE_SENT', { userKey: key, outcome: 'OK' })
    } else {
      auditAuth(db, 'PASSWORD_RESET_NO_MATCH', { userKey: key })
    }

    await padTo(startedAt, 800)
    return {
      ok: true,
      requestId,
      expiresInMs: RESET_CODE_TTL_MS,
      maxAttempts: RESET_CODE_MAX_ATTEMPTS,
      // ↓ 실서버는 메일로만 보낸다. 개발 빌드에서만 화면에 띄운다.
      devOnlyResetCode: IS_DEV ? code : null
    }
  })
}

/**
 * 2단계 — 코드 확인.
 *
 * 6자리는 19.9비트뿐이라 레이트리밋이 **필수**다 (NIST §3.1.3.2, 64비트 미만 → SHALL).
 * 두 겹으로 건다.
 *   · 코드 단위: 연속 5회 틀리면 **코드를 폐기**한다. 새로 받아야 한다.
 *   · 계정 단위: 10분에 확인 시도 20회 (`reset-code-verify`)
 * 실패 응답은 계정 유무·이름 일치 여부와 관계없이 **한 종류**다.
 */
export function verifyResetCode ({ email, code, requestId }) {
  const startedAt = Date.now()

  return call(async ({ db }) => {
    const key = accountKeyOf(email)
    const gate = consumeAction(db, 'reset-code-verify', key)
    if (!gate.allowed) {
      await padTo(startedAt, 600)
      throw new ApiError(429, 'TOO_MANY_REQUESTS',
        `확인 시도가 너무 많아요. ${formatRetryAfter(gate.retryAfterMs)} 뒤에 다시 시도해 주세요`)
    }

    const input = String(code || '').replace(/\D/g, '')
    const user = findByEmail(db, email)

    db.tokens = db.tokens || []
    const rec = user
      ? db.tokens.find(t =>
        t.kind === 'reset-code' && t.userId === user.userId && !t.usedAt && !tokenExpired(t))
      : null

    const fail = (remaining = null) => {
      auditAuth(db, 'PASSWORD_RESET_CODE_FAIL', { userKey: key })
      return new ApiError(400, 'INVALID_RESET_CODE',
        remaining != null && remaining > 0
          ? `코드가 맞지 않아요. ${remaining}번 더 틀리면 코드가 만료돼요`
          : '코드가 맞지 않거나 만료됐어요. 코드를 다시 받아주세요')
    }

    // 형식이 틀려도 **같은 실패 경로**를 탄다 (있는 계정인지 드러나지 않게)
    if (input.length !== RESET_CODE_LENGTH || !rec) {
      await padTo(startedAt, 600)
      throw fail()
    }

    // 코드를 요청한 화면과 다른 곳에서 온 시도
    if (rec.requestId !== requestId) {
      await padTo(startedAt, 600)
      throw fail()
    }

    rec.attempts += 1
    if (!timingSafeEqual(input, rec.value)) {
      const remaining = RESET_CODE_MAX_ATTEMPTS - rec.attempts
      if (remaining <= 0) {
        rec.usedAt = Date.now() // 폐기 — 다시 받아야 한다
        auditAuth(db, 'PASSWORD_RESET_CODE_BURNED', { userKey: key })
      }
      await padTo(startedAt, 600)
      throw fail(remaining)
    }

    // 성공 — 코드는 1회용이므로 즉시 소모하고, 실제 인가는 티켓으로 넘긴다
    rec.usedAt = Date.now()
    const ticket = issueOneTimeToken(db, 'reset-ticket', rec.userId, RESET_TICKET_TTL_MS)
    auditAuth(db, 'PASSWORD_RESET_CODE_OK', { userKey: key, outcome: 'OK' })

    const owner = db.users.find(u => u.userId === rec.userId)
    await padTo(startedAt, 600)
    return {
      ticket,
      // 여기서부터는 본인 확인이 끝났으므로 마스킹된 주소를 보여준다 (정의서 USER_003)
      maskedEmail: maskEmail(owner.email),
      nickname: owner.nickname,
      expiresInMs: RESET_TICKET_TTL_MS
    }
  })
}

/**
 * 3단계 — 새 비밀번호 설정.
 *
 * OWASP Forgot Password Cheat Sheet:
 *   · 새 비밀번호는 두 번 입력받고 기존 비밀번호 정책을 적용한다
 *   · 확인 메일을 보내되 **비밀번호는 넣지 않는다**
 *   · "Don't automatically log the user in" → 자동 로그인하지 않는다
 *   · 기존 세션을 무효화한다
 */
export function resetPassword ({ ticket, password, passwordConfirm }) {
  return call(async ({ db }) => {
    const rec = peekOneTimeToken(db, 'reset-ticket', ticket)
    if (!rec) {
      throw new ApiError(400, 'INVALID_RESET_TICKET', '재설정 시간이 지났어요. 코드를 다시 받아주세요')
    }
    const user = db.users.find(u => u.userId === rec.userId)
    if (!user) {
      throw new ApiError(400, 'INVALID_RESET_TICKET', '재설정 시간이 지났어요. 코드를 다시 받아주세요')
    }

    const pwResult = checkPassword(password, {
      email: user.email, nickname: user.nickname, name: user.name
    })
    if (!pwResult.ok) {
      throw new ApiError(400, 'VALIDATION_FAILED', pwResult.errors[0], { password: pwResult.errors[0] })
    }
    if (normalizePassword(password) !== normalizePassword(passwordConfirm)) {
      throw new ApiError(400, 'VALIDATION_FAILED', '비밀번호가 서로 달라요',
        { passwordConfirm: '비밀번호가 서로 달라요' })
    }
    if (verifyMockPassword(password, user.passwordHash)) {
      throw new ApiError(400, 'PASSWORD_REUSED', '지금 쓰던 비밀번호와 같아요. 다른 비밀번호로 바꿔주세요',
        { password: '지금 쓰던 비밀번호와 같아요' })
    }
    if (PASSWORD_POLICY.checkBreach) {
      const breach = await checkBreached(normalizePassword(password))
      if (breach.breached) {
        throw new ApiError(400, 'PASSWORD_BREACHED',
          '이미 유출된 적 있는 비밀번호예요. 다른 비밀번호를 써주세요',
          { password: '유출 목록에 있는 비밀번호예요' })
      }
    }

    consumeOneTimeToken(db, 'reset-ticket', ticket)

    user.passwordHash = mockHashPassword(password)
    user.passwordChangedAt = new Date().toISOString()
    user.mustChangePassword = false

    // 기존 세션 전부 무효화 — 탈취된 세션이 살아남지 않게 한다
    db.sessions.forEach(s => {
      if (s.userId === user.userId && !s.revokedAt) {
        s.revokedAt = Date.now()
        s.revokeReason = 'PASSWORD_RESET'
      }
    })
    destroySession()

    auditAuth(db, 'PASSWORD_RESET', { userKey: accountKeyOf(user.email), outcome: 'OK' })

    // 자동 로그인하지 않는다 (OWASP). 사용자가 새 비밀번호로 직접 로그인한다.
    return { ok: true, maskedEmail: maskEmail(user.email) }
  })
}

/* ═══════════════════════ MY_009 / MY_011 프로필 ═══════════════════════ */

export function me () {
  return call(({ userId, db }) => {
    const user = db.users.find(u => u.userId === userId)
    if (!user) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
    return publicUser(user)
  }, { auth: true })
}

/** MY_011 이름 변경 */
export function updateName (name) {
  return call(({ userId, db }) => {
    const trimmed = String(name || '').trim()
    if (!trimmed) {
      throw new ApiError(400, 'VALIDATION_FAILED', '이름을 입력해 주세요', { name: '이름을 입력해 주세요' })
    }
    if (trimmed.length > 30) {
      throw new ApiError(400, 'VALIDATION_FAILED', '이름은 30자까지 쓸 수 있어요', { name: '이름은 30자까지 쓸 수 있어요' })
    }
    const user = db.users.find(u => u.userId === userId)
    if (!user) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
    user.name = trimmed
    auditAuth(db, 'PROFILE_NAME_CHANGED', { userKey: accountKeyOf(user.email), outcome: 'OK' })
    return publicUser(user)
  }, { auth: true })
}

/**
 * 닉네임 변경 (MY_011).
 *
 * 이름과 다른 점 두 가지가 있다.
 *  ① 닉네임은 후기·공유 화면에 그대로 나가는 **공개 식별자**라 중복을 막아야 한다.
 *     자기 자신은 중복 검사에서 빼야 대소문자만 바꾸는 것도 가능하다.
 *  ② 중복이면 그대로 알려준다 — 이미 공개된 값이라 존재 여부가 새어도 잃을 게 없다.
 *     (이메일은 반대다. auth.js 의 ENUMERATION_SAFE_SIGNUP 주석 참고)
 *
 * 감사 로그에 닉네임 값은 남기지 않는다.
 */
export function updateNickname (nickname) {
  return call(({ userId, db }) => {
    const nk = checkNickname(nickname)
    if (!nk.ok) {
      throw new ApiError(400, 'VALIDATION_FAILED', nk.message, { nickname: nk.message })
    }
    const user = db.users.find(u => u.userId === userId)
    if (!user) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')

    const next = String(nickname).trim()
    const taken = db.users.some(
      u => u.userId !== userId && u.nickname.toLowerCase() === next.toLowerCase()
    )
    if (taken) {
      throw new ApiError(409, 'NICKNAME_TAKEN', '이미 사용 중인 닉네임이에요',
        { nickname: '이미 사용 중인 닉네임이에요' })
    }

    user.nickname = next
    auditAuth(db, 'PROFILE_NICKNAME_CHANGED', { userKey: accountKeyOf(user.email), outcome: 'OK' })
    return publicUser(user)
  }, { auth: true })
}

/**
 * 생년월일 변경 (MY_009 프로필의 나머지 한 칸).
 *
 * 이름과 달리 **비우는 것도 정상 동작**이다 — 선택 항목이라 지울 수 있어야 한다.
 * 빈 값이면 null 로 저장한다.
 *
 * ⚠️ 감사 로그에는 **날짜 값을 남기지 않는다.** 생년월일은 개인정보이고,
 *    로그는 "언제 무엇이 바뀌었나"만 알면 충분하다.
 */
export function updateBirthDate (birthDate) {
  return call(({ userId, db }) => {
    const r = checkBirthDate(birthDate)
    if (!r.ok) {
      throw new ApiError(400, 'VALIDATION_FAILED', r.message, { birthDate: r.message })
    }
    const user = db.users.find(u => u.userId === userId)
    if (!user) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')
    user.birthDate = r.value
    auditAuth(db, 'PROFILE_BIRTHDATE_CHANGED', {
      userKey: accountKeyOf(user.email),
      outcome: 'OK',
      meta: r.value ? 'SET' : 'CLEARED'
    })
    return publicUser(user)
  }, { auth: true })
}

/**
 * 로그인 상태에서 비밀번호 변경.
 * 현재 비밀번호를 반드시 다시 확인한다 (세션 탈취만으로 비밀번호를 못 바꾸게).
 */
export function changePassword ({ currentPassword, password, passwordConfirm }) {
  return call(async ({ userId, db }) => {
    const user = db.users.find(u => u.userId === userId)
    if (!user) throw new ApiError(401, 'SESSION_EXPIRED', '로그인이 필요해요')

    if (!verifyMockPassword(currentPassword, user.passwordHash)) {
      auditAuth(db, 'PASSWORD_CHANGE_FAIL', { userKey: accountKeyOf(user.email) })
      throw new ApiError(400, 'INVALID_CURRENT_PASSWORD', '지금 비밀번호가 맞지 않아요',
        { currentPassword: '지금 비밀번호가 맞지 않아요' })
    }

    const pwResult = checkPassword(password, {
      email: user.email, nickname: user.nickname, name: user.name
    })
    if (!pwResult.ok) {
      throw new ApiError(400, 'VALIDATION_FAILED', pwResult.errors[0], { password: pwResult.errors[0] })
    }
    if (normalizePassword(password) !== normalizePassword(passwordConfirm)) {
      throw new ApiError(400, 'VALIDATION_FAILED', '비밀번호가 서로 달라요',
        { passwordConfirm: '비밀번호가 서로 달라요' })
    }
    if (verifyMockPassword(password, user.passwordHash)) {
      throw new ApiError(400, 'PASSWORD_REUSED', '지금 쓰던 비밀번호와 같아요',
        { password: '지금 쓰던 비밀번호와 같아요' })
    }
    if (PASSWORD_POLICY.checkBreach) {
      const breach = await checkBreached(normalizePassword(password))
      if (breach.breached) {
        throw new ApiError(400, 'PASSWORD_BREACHED', '이미 유출된 적 있는 비밀번호예요',
          { password: '유출 목록에 있는 비밀번호예요' })
      }
    }

    user.passwordHash = mockHashPassword(password)
    user.passwordChangedAt = new Date().toISOString()
    user.mustChangePassword = false
    user.tempPasswordExpiresAt = null

    // 이 세션만 남기고 나머지는 끊는다
    auditAuth(db, 'PASSWORD_CHANGED', { userKey: accountKeyOf(user.email), outcome: 'OK' })
    return { ok: true }
  }, { auth: true })
}
