/**
 * 회원 API 검증 (요구사항 정의서 USER_001 ~ USER_003, MY_009 ~ MY_011)
 * + 보안 요구사항(NIST SP 800-63B-4 / OWASP ASVS 5.0 / 개인정보 안전성 확보조치 기준).
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  resetDb, TEST_ACCOUNT, MOCK_HASH_PREFIX, load,
  SEED_STAMP, SCHEMA_VERSION, TEST_PASSWORD, mockHashPassword, _resetLoadCache
} from './db.js'
import { storage } from './storage.js'
import { setLatency, setBackoffEnabled, setPaddingScale } from './client.js'
import { _resetSessionState, currentUserId, hasResumableSession } from './session.js'
import { ApiError } from './errors.js'
import { PASSWORD_POLICY } from '../components/security/passwordPolicy.js'
import { LIMITS } from '../components/security/rateLimit.js'
import {
  login, logout, signup, verifyEmail, resendVerification,
  requestPasswordReset, verifyResetCode, resetPassword,
  me, updateName, updateNickname, updateBirthDate, changePassword,
  readRecentLogins, removeRecentLogin, clearRecentLogins, checkNicknameAvailable,
  readLastProvider, writeLastProvider, clearLastProvider, LOGIN_PROVIDERS,
  ENUMERATION_SAFE_SIGNUP, RESET_CODE_LENGTH, RESET_CODE_MAX_ATTEMPTS,
  generateNumericCode
} from './auth.js'

setLatency(0)
// 지수 backoff 를 실제로 기다리면 테스트가 30초씩 멈춘다. 계산 자체는 rateLimit.spec.js 가 검증한다.
setBackoffEnabled(false)
// 응답 시간 평탄화는 비율만 유지하고 10분의 1로 줄인다 (전체 실행 시간 단축)
setPaddingScale(0.1)
// 유출 조회는 외부 네트워크를 타므로 단위 테스트에서는 끈다 (별도 검증은 breachCheck 쪽)
PASSWORD_POLICY.checkBreach = false

// 현재 정책(조합 강제)을 만족하는 값. 특수문자는 하이픈 3개 — 상한이 3개다.
const GOOD_PW = 'Lantern-Fig-Marble2026'
const OTHER_PW = 'Quiet-Harbor-Pinewood88'

beforeEach(() => {
  storage._resetMemory()
  _resetSessionState()
  resetDb()
})

const expectApiError = (fn, status, code) =>
  expect(fn()).rejects.toMatchObject({ status, ...(code ? { code } : {}) })

/* ═══════════════════ USER_001 로그인 ═══════════════════ */

describe('USER_001 로그인', () => {
  it('정상 계정은 로그인되고 세션이 생긴다', async () => {
    const res = await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    expect(res.user.email).toBe(TEST_ACCOUNT.email)
    expect(res.tokens.accessToken).toBeTruthy()
    expect(currentUserId()).toBe(res.user.userId)
    expect(hasResumableSession()).toBe(true)
  })

  it('응답에 리프레시 토큰이 들어 있지 않다 (쿠키 경로로만 오간다)', async () => {
    const res = await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    expect(JSON.stringify(res)).not.toContain('refreshToken')
  })

  it('비밀번호가 틀리든 계정이 없든 같은 메시지·같은 코드를 낸다 (ASVS 6.3.8)', async () => {
    const [a, b] = await Promise.allSettled([
      login({ email: TEST_ACCOUNT.email, password: 'wrong-but-long-enough-1' }),
      login({ email: 'nobody@hangat.kr', password: 'wrong-but-long-enough-1' })
    ])
    expect(a.reason.message).toBe(b.reason.message)
    expect(a.reason.code).toBe('INVALID_CREDENTIALS')
    expect(a.reason.status).toBe(b.reason.status)
  })

  it('계정이 없어도 해시 비교를 건너뛰지 않는다 (타이밍 오라클 차단)', async () => {
    // padTo 가 목표 시간까지 늘려 주므로, 두 경로의 소요 시간이 비슷해야 한다
    const t = async (email) => {
      const s = Date.now()
      await login({ email, password: 'wrong-but-long-enough-1' }).catch(() => {})
      return Date.now() - s
    }
    const existing = await t(TEST_ACCOUNT.email)
    resetDb() // 시도 제한 초기화
    const missing = await t('nobody@hangat.kr')
    expect(Math.abs(existing - missing)).toBeLessThan(150)
  })

  it('탈퇴·이용 제한 계정은 로그인할 수 없다', async () => {
    await expectApiError(
      () => login({ email: 'withdrawn@hangat.kr', password: TEST_ACCOUNT.password }),
      403, 'ACCOUNT_WITHDRAWN'
    )
    await expectApiError(
      () => login({ email: 'suspended@hangat.kr', password: TEST_ACCOUNT.password }),
      403, 'ACCOUNT_SUSPENDED'
    )
  })

  it('로그인에서는 문자 조합을 검사하지 않는다 — 옛 비밀번호를 가진 회원이 막히면 안 된다', async () => {
    // 소문자만 있는 값도 "형식 오류"가 아니라 "자격 증명 불일치"로 처리돼야 한다
    await expectApiError(
      () => login({ email: TEST_ACCOUNT.email, password: 'alllowercasenodigits' }),
      401, 'INVALID_CREDENTIALS'
    )
  })

  it('지나치게 긴 비밀번호는 해시 전에 막는다 (연산 DoS)', async () => {
    await expectApiError(
      () => login({ email: TEST_ACCOUNT.email, password: 'a'.repeat(5000) }),
      400, 'PASSWORD_TOO_LONG'
    )
  })

  it('실패가 쌓이면 계정을 잠근다 (고시 제5조⑥ / ASVS 6.3.1)', async () => {
    for (let i = 0; i < LIMITS.hardThreshold; i++) {
      await login({ email: TEST_ACCOUNT.email, password: 'wrong-but-long-enough-1' }).catch(() => {})
    }
    await expectApiError(
      () => login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password }),
      423, 'ACCOUNT_LOCKED'
    )
  }, 20000)

  it('인증 시도를 감사 로그에 남긴다 (비밀번호·원문 이메일 없이)', async () => {
    await login({ email: TEST_ACCOUNT.email, password: 'wrong-but-long-enough-1' }).catch(() => {})
    const log = load().authLog
    expect(log.some(e => e.event === 'LOGIN_FAIL')).toBe(true)
    const dump = JSON.stringify(log)
    expect(dump).not.toContain(TEST_ACCOUNT.email)
    expect(dump).not.toContain(TEST_ACCOUNT.password)
  })

  it('최근 로그인 내역은 기본적으로 마스킹된 주소만 저장한다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    const list = readRecentLogins()
    expect(list).toHaveLength(1)
    expect(list[0].maskedEmail).toContain('*')
    expect(list[0].email).toBeNull()
    expect(JSON.stringify(list)).not.toContain(TEST_ACCOUNT.email)
  })

  it('사용자가 켠 경우에만 원문 이메일을 저장한다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password, rememberEmail: true })
    expect(readRecentLogins()[0].email).toBe(TEST_ACCOUNT.email)
    expect(removeRecentLogin(readRecentLogins()[0].maskedEmail)).toHaveLength(0)
  })

  it('로그아웃하면 보호 API 가 401 이 된다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    await expect(me()).resolves.toBeTruthy()
    await logout()
    await expect(me()).rejects.toBeInstanceOf(ApiError)
  })
})

/* ═══════════════ 저장된 목 데이터의 재사용 판단 ═══════════════
   실제로 났던 문제를 고정한다 — TEST_PASSWORD 를 바꿨는데 SCHEMA_VERSION 은
   그대로라, 예전에 접속한 브라우저가 옛 해시를 계속 들고 있어
   화면에 적힌 새 비밀번호로 로그인이 안 됐다. */

describe('시드 지문(seedStamp)', () => {
  const STORAGE_KEY = 'hangat_mock_db_v2'

  it('지문은 테스트 비밀번호에서 나온다 (평문이 저장소에 남지 않는다)', () => {
    expect(SEED_STAMP).toBe(mockHashPassword(TEST_PASSWORD))
    expect(SEED_STAMP).toContain(MOCK_HASH_PREFIX)
    expect(SEED_STAMP).not.toContain(TEST_PASSWORD)
  })

  it('시드에 버전과 지문이 함께 저장된다', () => {
    const saved = storage.getJson(STORAGE_KEY, null)
    expect(saved.version).toBe(SCHEMA_VERSION)
    expect(saved.seedStamp).toBe(SEED_STAMP)
  })

  it('지문이 같으면 저장된 데이터를 그대로 쓴다', () => {
    const db = load()
    db.users[0].nickname = '손댄값'
    storage.setJson(STORAGE_KEY, db)
    _resetLoadCache()
    expect(load().users[0].nickname).toBe('손댄값')
  })

  it('지문이 다르면 다시 시드해서 데모 계정 로그인이 살아난다', async () => {
    // 옛 비밀번호로 시드된 브라우저를 흉내낸다
    const stale = JSON.parse(JSON.stringify(load()))
    stale.seedStamp = MOCK_HASH_PREFIX + 'old.stamp'
    stale.users.find(u => u.email === TEST_ACCOUNT.email).passwordHash = MOCK_HASH_PREFIX + 'old.hash'
    storage.setJson(STORAGE_KEY, stale)
    _resetLoadCache()

    const res = await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    expect(res.user.email).toBe(TEST_ACCOUNT.email)
  })

  it('지문 필드가 아예 없는 옛 저장본도 다시 시드한다', async () => {
    const old = JSON.parse(JSON.stringify(load()))
    delete old.seedStamp
    old.users.find(u => u.email === TEST_ACCOUNT.email).passwordHash = MOCK_HASH_PREFIX + 'old.hash'
    storage.setJson(STORAGE_KEY, old)
    _resetLoadCache()

    await expect(login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password }))
      .resolves.toBeTruthy()
  })
})

/* ═══════════════ 최근 로그인 수단 ("최근에 로그인했습니다" 말풍선) ═══════════════ */

describe('마지막 로그인 수단', () => {
  it('로그인 전에는 아무 수단도 기록돼 있지 않다', () => {
    expect(readLastProvider()).toBeNull()
  })

  it('이메일 로그인이 성공하면 email 로 기록된다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    expect(readLastProvider().provider).toBe('email')
  })

  it('로그인이 실패하면 기록되지 않는다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: 'wrong-but-long-enough-1' }).catch(() => {})
    expect(readLastProvider()).toBeNull()
  })

  it('알려진 수단만 저장한다', () => {
    expect(LOGIN_PROVIDERS).toEqual(['email', 'kakao', 'google'])
    // 네이버·Apple 버튼은 화면에서 제거했으므로 값으로도 들어갈 수 없다
    for (const bad of ['naver', 'apple', '<script>', '', null, undefined]) {
      expect(writeLastProvider(bad)).toBeNull()
    }
    expect(writeLastProvider('kakao').provider).toBe('kakao')
    expect(writeLastProvider('naver').provider).toBe('kakao') // 거부돼도 기존 값이 남는다
  })

  it('개인 식별 정보를 담지 않는다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password, rememberEmail: true })
    const dump = JSON.stringify(readLastProvider())
    expect(dump).not.toContain(TEST_ACCOUNT.email)
    expect(dump).not.toContain('한갓')
    expect(Object.keys(readLastProvider()).sort()).toEqual(['at', 'provider'])
  })

  it('최근 로그인 기록을 지우면 수단도 같이 지워진다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    expect(readLastProvider()).not.toBeNull()
    clearRecentLogins()
    expect(readLastProvider()).toBeNull()
  })

  it('직접 지울 수 있다', () => {
    writeLastProvider('google')
    expect(clearLastProvider()).toBeNull()
    expect(readLastProvider()).toBeNull()
  })

  it('저장소가 손상돼도 null 로 떨어진다', () => {
    storage.set('hangat_last_provider', '{{{not json')
    expect(readLastProvider()).toBeNull()
    storage.setJson('hangat_last_provider', { provider: 'facebook' })
    expect(readLastProvider()).toBeNull()
  })
})

/* ═══════════════════ USER_002 회원가입 ═══════════════════ */

describe('USER_002 회원가입', () => {
  const base = () => ({
    email: 'new@hangat.kr',
    password: GOOD_PW,
    passwordConfirm: GOOD_PW,
    nickname: '새사람',
    name: '신입',
    birthDate: '2001-03-03'
  })

  it('가입하면 계정이 만들어지고 인증 토큰이 발급된다', async () => {
    const res = await signup(base())
    expect(res.pending).toBe(true)
    expect(res.devOnlyVerifyToken).toBeTruthy()
    expect(load().users.some(u => u.email === 'new@hangat.kr')).toBe(true)
  })

  it('비밀번호를 평문으로 저장하지 않는다 (고시 제7조① 일방향 암호화)', async () => {
    await signup(base())
    const saved = load().users.find(u => u.email === 'new@hangat.kr')
    expect(saved.passwordHash).not.toBe(GOOD_PW)
    expect(saved.passwordHash.startsWith(MOCK_HASH_PREFIX)).toBe(true)
    expect(JSON.stringify(load())).not.toContain(GOOD_PW)
  })

  it('이미 가입된 이메일이어도 응답이 성공과 구별되지 않는다 (열거 방지)', async () => {
    expect(ENUMERATION_SAFE_SIGNUP).toBe(true)
    const dup = await signup({ ...base(), email: TEST_ACCOUNT.email, nickname: '중복시도' })
    expect(dup.pending).toBe(true)
    // 계정은 늘지 않는다
    expect(load().users.filter(u => u.email === TEST_ACCOUNT.email)).toHaveLength(1)
  })

  it('중복 이메일 시도는 감사 로그에는 남는다', async () => {
    await signup({ ...base(), email: TEST_ACCOUNT.email, nickname: '중복시도2' })
    expect(load().authLog.some(e => e.event === 'SIGNUP_DUPLICATE_EMAIL')).toBe(true)
  })

  it('닉네임 중복은 그대로 알려준다 (공개 식별자)', async () => {
    await expectApiError(() => signup({ ...base(), nickname: '한갓이' }), 409, 'NICKNAME_TAKEN')
  })

  it('비밀번호 확인이 다르면 막는다', async () => {
    await expectApiError(() => signup({ ...base(), passwordConfirm: OTHER_PW }), 400)
  })

  it('정책에 못 미치는 비밀번호를 막는다', async () => {
    await expectApiError(() => signup({ ...base(), password: 'short1!A', passwordConfirm: 'short1!A' }), 400)
    await expectApiError(
      () => signup({ ...base(), password: 'password123456789', passwordConfirm: 'password123456789' }),
      400
    )
  })

  it('필수 항목이 비면 막는다', async () => {
    await expectApiError(() => signup({ ...base(), email: '' }), 400, 'VALIDATION_FAILED')
    await expectApiError(() => signup({ ...base(), nickname: '' }), 400, 'VALIDATION_FAILED')
    await expectApiError(() => signup({ ...base(), name: '' }), 400, 'VALIDATION_FAILED')
  })

  it('닉네임 중복 확인은 호출량 제한이 걸린다', async () => {
    const max = 30
    for (let i = 0; i < max; i++) await checkNicknameAvailable('임시닉' + i)
    await expectApiError(() => checkNicknameAvailable('한번더'), 429, 'TOO_MANY_REQUESTS')
  })

  it('인증 링크를 열면 인증이 끝나고 로그인된다', async () => {
    const res = await signup(base())
    const verified = await verifyEmail(res.devOnlyVerifyToken)
    expect(verified.user.emailVerified).toBe(true)
    expect((await me()).email).toBe('new@hangat.kr')
  })

  it('인증 토큰은 1회용이다', async () => {
    const res = await signup(base())
    await verifyEmail(res.devOnlyVerifyToken)
    await expectApiError(() => verifyEmail(res.devOnlyVerifyToken), 400, 'INVALID_VERIFY_TOKEN')
  })

  it('인증 토큰은 만료된다 (ASVS 6.4.1)', async () => {
    const res = await signup(base())
    const rec = load().tokens.find(t => t.value === res.devOnlyVerifyToken)
    rec.expiresAt = Date.now() - 1
    await expectApiError(() => verifyEmail(res.devOnlyVerifyToken), 400, 'INVALID_VERIFY_TOKEN')
  })

  it('메일 재발송은 가입 여부를 알려주지 않는다', async () => {
    const known = await resendVerification(TEST_ACCOUNT.email)
    const unknown = await resendVerification('nobody@hangat.kr')
    expect(known.ok).toBe(unknown.ok)
    expect(Object.keys(known).sort()).toEqual(Object.keys(unknown).sort())
  })
})

/* ═══════════════════ USER_003 비밀번호 재설정 (6자리 코드) ═══════════════════ */

describe('USER_003 재설정 1단계 — 코드 발송', () => {
  it('계정이 있든 없든 같은 모양의 응답을 준다 (ASVS 6.3.8)', async () => {
    const hit = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    const miss = await requestPasswordReset({ email: 'nobody@hangat.kr', name: '없음' })
    expect(Object.keys(hit).sort()).toEqual(Object.keys(miss).sort())
    expect(hit.ok).toBe(miss.ok)
    // requestId 는 계정 유무와 무관하게 항상 발급된다 — 없으면 그 자체가 단서가 된다
    expect(miss.requestId).toBeTruthy()
  })

  it('이메일 형식을 먼저 검사한다 — 형식 오류는 계정 정보를 드러내지 않는다', async () => {
    /*
      login·signup 과 달리 이 경로만 형식 검사가 빠져 있었다(실측으로 발견).
      형식 오류는 입력 문자열만으로 정해지므로 위의 "항상 동일한 응답" 원칙과 충돌하지 않는다.
      막지 않으면 절대 매칭될 수 없는 요청이 레이트리밋 예산을 태운다.
    */
    await expect(requestPasswordReset({ email: 'kim@t-online.de', name: '홍길동' }))
      .rejects.toMatchObject({ code: 'INVALID_EMAIL' })
    await expect(requestPasswordReset({ email: 'user+tag@gmail.com', name: '홍길동' }))
      .rejects.toMatchObject({ code: 'INVALID_EMAIL' })
    await expect(requestPasswordReset({ email: 'not-an-email', name: '홍길동' }))
      .rejects.toMatchObject({ code: 'INVALID_EMAIL' })
  })

  it('응답에 마스킹된 이메일을 담지 않는다 — 계정 존재가 새지 않게', async () => {
    const res = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    expect(res).not.toHaveProperty('maskedEmail')
  })

  it('이름이 다르면 코드를 만들지 않는다', async () => {
    const res = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: '다른이름' })
    expect(res.devOnlyResetCode).toBeNull()
  })

  it('코드는 정확히 6자리 숫자다 (NIST §3.1.3.2 최소 6자리)', async () => {
    const res = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    expect(res.devOnlyResetCode).toMatch(/^\d{6}$/)
    expect(RESET_CODE_LENGTH).toBe(6)
  })

  it('코드 수명이 10분을 넘지 않는다 (NIST "within 10 minutes")', async () => {
    const res = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    expect(res.expiresInMs).toBeLessThanOrEqual(10 * 60 * 1000)
  })

  it('코드 생성기: 항상 6자리이고 앞자리 0 이 잘리지 않는다', () => {
    let leadingZero = 0
    const seen = new Set()
    for (let i = 0; i < 5000; i++) {
      const c = generateNumericCode()
      expect(c).toMatch(/^\d{6}$/)
      if (c[0] === '0') leadingZero++
      seen.add(c)
    }
    // 0으로 시작하는 코드가 대략 10% 나와야 한다 (잘리면 0이 된다)
    expect(leadingZero).toBeGreaterThan(300)
    // 값이 고르게 흩어져야 한다
    expect(seen.size).toBeGreaterThan(4800)
  })

  it('코드 생성기: 모듈로 편향이 없다 (자릿수 분포가 고름)', () => {
    const first = new Array(10).fill(0)
    const N = 60000
    for (let i = 0; i < N; i++) first[Number(generateNumericCode()[0])]++
    const expected = N / 10
    // 각 자릿수 빈도가 기대값의 ±10% 안에 들어와야 한다
    first.forEach((n, d) => {
      expect(Math.abs(n - expected) / expected, `${d} 로 시작: ${n}`).toBeLessThan(0.1)
    })
  })

  it('새 코드를 받으면 이전 코드는 무효가 된다', async () => {
    const first = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    const second = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: first.devOnlyResetCode, requestId: first.requestId }),
      400, 'INVALID_RESET_CODE'
    )
    await expect(verifyResetCode({
      email: TEST_ACCOUNT.email, code: second.devOnlyResetCode, requestId: second.requestId
    })).resolves.toBeTruthy()
  })

  it('발송 요청 횟수를 제한한다', async () => {
    for (let i = 0; i < 5; i++) {
      await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    }
    await expectApiError(
      () => requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name }),
      429, 'TOO_MANY_REQUESTS'
    )
  })
})

describe('USER_003 재설정 2단계 — 코드 확인', () => {
  const ask = () => requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })

  it('맞는 코드면 티켓과 마스킹된 이메일을 준다 (여기서만 공개)', async () => {
    const req = await ask()
    const res = await verifyResetCode({
      email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId
    })
    expect(res.ticket).toBeTruthy()
    expect(res.maskedEmail).toBe('te*t@hangat.kr')
    expect(res.expiresInMs).toBeGreaterThan(0)
  })

  it('코드는 1회용이다 (NIST "only once … replay resistance")', async () => {
    const req = await ask()
    await verifyResetCode({ email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId })
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId }),
      400, 'INVALID_RESET_CODE'
    )
  })

  it('연속 5회 틀리면 코드를 폐기한다 (NIST 64비트 미만 → 레이트리밋 SHALL)', async () => {
    const req = await ask()
    const wrong = req.devOnlyResetCode === '000000' ? '111111' : '000000'
    for (let i = 0; i < RESET_CODE_MAX_ATTEMPTS; i++) {
      await verifyResetCode({ email: TEST_ACCOUNT.email, code: wrong, requestId: req.requestId }).catch(() => {})
    }
    // 이제는 **맞는 코드**를 넣어도 통과하지 못한다
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId }),
      400, 'INVALID_RESET_CODE'
    )
  })

  it('남은 시도 횟수를 알려준다', async () => {
    const req = await ask()
    const wrong = req.devOnlyResetCode === '000000' ? '111111' : '000000'
    const err = await verifyResetCode({
      email: TEST_ACCOUNT.email, code: wrong, requestId: req.requestId
    }).catch(e => e)
    expect(err.message).toContain('번 더')
  })

  it('만료된 코드는 거부한다', async () => {
    const req = await ask()
    load().tokens.find(t => t.kind === 'reset-code' && !t.usedAt).expiresAt = Date.now() - 1
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId }),
      400, 'INVALID_RESET_CODE'
    )
  })

  it('코드를 요청한 화면이 아니면 거부한다 (요청 세션 결속)', async () => {
    const req = await ask()
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: 'someone-elses' }),
      400, 'INVALID_RESET_CODE'
    )
  })

  it('없는 계정에 대한 실패 메시지가 틀린 코드일 때와 같다', async () => {
    const req = await ask()
    const wrong = req.devOnlyResetCode === '000000' ? '111111' : '000000'
    const a = await verifyResetCode({ email: 'nobody@hangat.kr', code: '123456', requestId: req.requestId }).catch(e => e)
    const b = await verifyResetCode({ email: TEST_ACCOUNT.email, code: wrong.slice(0, 5) + 'x', requestId: req.requestId }).catch(e => e)
    expect(a.code).toBe(b.code)
    expect(a.status).toBe(b.status)
  })

  it('확인 시도 횟수를 계정 단위로도 제한한다', async () => {
    const req = await ask()
    for (let i = 0; i < 20; i++) {
      await verifyResetCode({ email: TEST_ACCOUNT.email, code: '000000', requestId: req.requestId }).catch(() => {})
    }
    await expectApiError(
      () => verifyResetCode({ email: TEST_ACCOUNT.email, code: '000000', requestId: req.requestId }),
      429, 'TOO_MANY_REQUESTS'
    )
  }, 20000)
})

describe('USER_003 재설정 3단계 — 새 비밀번호', () => {
  const getTicket = async () => {
    const req = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: TEST_ACCOUNT.name })
    const res = await verifyResetCode({
      email: TEST_ACCOUNT.email, code: req.devOnlyResetCode, requestId: req.requestId
    })
    return res.ticket
  }

  it('새 비밀번호로 바꾸면 예전 비밀번호로는 로그인되지 않는다', async () => {
    const ticket = await getTicket()
    await resetPassword({ ticket, password: OTHER_PW, passwordConfirm: OTHER_PW })

    await expectApiError(
      () => login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password }), 401
    )
    const ok = await login({ email: TEST_ACCOUNT.email, password: OTHER_PW })
    expect(ok.user.email).toBe(TEST_ACCOUNT.email)
  })

  it('재설정해도 자동 로그인하지 않는다 (OWASP Forgot Password)', async () => {
    const ticket = await getTicket()
    await resetPassword({ ticket, password: OTHER_PW, passwordConfirm: OTHER_PW })
    expect(currentUserId()).toBeNull()
    expect(hasResumableSession()).toBe(false)
  })

  it('재설정하면 기존 세션이 전부 끊긴다', async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
    const ticket = await getTicket()
    await resetPassword({ ticket, password: OTHER_PW, passwordConfirm: OTHER_PW })
    const alive = load().sessions.filter(s => s.userId === 'u1' && !s.revokedAt)
    expect(alive).toHaveLength(0)
  })

  it('티켓은 1회용이다', async () => {
    const ticket = await getTicket()
    await resetPassword({ ticket, password: OTHER_PW, passwordConfirm: OTHER_PW })
    await expectApiError(
      () => resetPassword({ ticket, password: GOOD_PW, passwordConfirm: GOOD_PW }),
      400, 'INVALID_RESET_TICKET'
    )
  })

  it('티켓 없이는 비밀번호를 바꿀 수 없다', async () => {
    await expectApiError(
      () => resetPassword({ ticket: 'made-up-ticket', password: OTHER_PW, passwordConfirm: OTHER_PW }),
      400, 'INVALID_RESET_TICKET'
    )
  })

  it('만료된 티켓은 거부한다', async () => {
    const ticket = await getTicket()
    load().tokens.find(t => t.kind === 'reset-ticket').expiresAt = Date.now() - 1
    await expectApiError(
      () => resetPassword({ ticket, password: OTHER_PW, passwordConfirm: OTHER_PW }),
      400, 'INVALID_RESET_TICKET'
    )
  })

  it('같은 비밀번호로는 못 바꾼다', async () => {
    const ticket = await getTicket()
    await expectApiError(
      () => resetPassword({
        ticket, password: TEST_ACCOUNT.password, passwordConfirm: TEST_ACCOUNT.password
      }),
      400, 'PASSWORD_REUSED'
    )
  })

  it('정책에 못 미치는 비밀번호는 거부한다', async () => {
    const ticket = await getTicket()
    await expectApiError(
      () => resetPassword({ ticket, password: 'short-pw-11', passwordConfirm: 'short-pw-11' }),
      400, 'VALIDATION_FAILED'
    )
  })
})

describe('MY_009 프로필 · MY_011 이름 변경', () => {
  beforeEach(async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
  })

  it('프로필에 이메일 · 이름 · 생년월일이 들어 있다', async () => {
    const p = await me()
    expect(p.email).toBe(TEST_ACCOUNT.email)
    expect(p.name).toBe('김한갓')
    expect(p.birthDate).toBe('1999-04-12')
  })

  it('프로필 응답에 비밀번호 해시가 섞여 나오지 않는다', async () => {
    const p = await me()
    expect(p).not.toHaveProperty('passwordHash')
    expect(p).not.toHaveProperty('mustChangePassword')
  })

  it('이름을 바꾸면 재설정 본인 확인 값도 함께 바뀐다', async () => {
    await updateName('이한갓')
    const miss = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: '김한갓' })
    expect(miss.devOnlyResetCode).toBeNull()
    const hit = await requestPasswordReset({ email: TEST_ACCOUNT.email, name: '이한갓' })
    expect(hit.devOnlyResetCode).toBeTruthy()
  })

  it('빈 이름은 막는다', async () => {
    await expectApiError(() => updateName('   '), 400, 'VALIDATION_FAILED')
  })
})

describe('로그인 상태 비밀번호 변경', () => {
  beforeEach(async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
  })

  it('지금 비밀번호를 다시 확인한다 (세션 탈취만으로 못 바꾸게)', async () => {
    await expectApiError(
      () => changePassword({
        currentPassword: 'wrong-but-long-enough-1', password: OTHER_PW, passwordConfirm: OTHER_PW
      }),
      400, 'INVALID_CURRENT_PASSWORD'
    )
  })

  it('바꾸면 새 비밀번호로 로그인된다', async () => {
    await changePassword({
      currentPassword: TEST_ACCOUNT.password, password: OTHER_PW, passwordConfirm: OTHER_PW
    })
    await logout()
    const ok = await login({ email: TEST_ACCOUNT.email, password: OTHER_PW })
    expect(ok.user.email).toBe(TEST_ACCOUNT.email)
  })

  it('같은 비밀번호로는 못 바꾼다', async () => {
    await expectApiError(
      () => changePassword({
        currentPassword: TEST_ACCOUNT.password,
        password: TEST_ACCOUNT.password,
        passwordConfirm: TEST_ACCOUNT.password
      }),
      400, 'PASSWORD_REUSED'
    )
  })

  it('강제 변경 표시가 걸린 계정은 변경 후 풀린다', async () => {
    // 관리자 조치 등으로 mustChangePassword 가 서 있는 상태를 흉내 낸다
    load().users.find(u => u.userId === 'u1').mustChangePassword = true
    await changePassword({
      currentPassword: TEST_ACCOUNT.password, password: OTHER_PW, passwordConfirm: OTHER_PW
    })
    expect(load().users.find(u => u.userId === 'u1').mustChangePassword).toBe(false)
  })
})

/* ═══════════════════ 개발 전용 필드 노출 ═══════════════════ */

describe('devOnly 필드', () => {
  it('개발 빌드에서만 값이 담긴다', async () => {
    // import.meta.env.DEV 는 vitest 에서 true 다. 프로덕션 빌드에서는 null 이 되어야 한다.
    expect(import.meta.env.DEV).toBe(true)
    const res = await signup({
      email: 'devcheck@hangat.kr', password: GOOD_PW, passwordConfirm: GOOD_PW,
      nickname: '개발확인', name: '개발'
    })
    expect(res.devOnlyVerifyToken).toBeTruthy()
    // 프로덕션에서 null 이 되는지는 소스의 IS_DEV 게이트로 보장한다 (아래 회귀 확인)
    const src = String(signup)
    expect(src.includes('IS_DEV')).toBe(true)
  })
})

// vi 가 쓰이지 않아도 import 오류를 내지 않도록 참조만 남긴다
void vi

/* ═══════════════ MY_009 생년월일 변경 ═══════════════ */

describe('생년월일 변경', () => {
  beforeEach(async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
  })

  it('바꾸면 프로필에 반영된다', async () => {
    const u = await updateBirthDate('1995-03-07')
    expect(u.birthDate).toBe('1995-03-07')
    expect((await me()).birthDate).toBe('1995-03-07')
  })

  it('빈 값을 넣으면 등록이 지워진다 (선택 항목)', async () => {
    await updateBirthDate('1995-03-07')
    const u = await updateBirthDate('')
    expect(u.birthDate).toBeNull()
  })

  it('없는 날짜 · 미래 · 잘못된 형식을 막는다', async () => {
    for (const bad of ['2026-02-31', '2999-01-01', '1999/04/12', 'abc', '1899-12-31']) {
      await expectApiError(() => updateBirthDate(bad), 400, 'VALIDATION_FAILED')
    }
  })

  it('막힌 값은 저장되지 않는다', async () => {
    await updateBirthDate('1995-03-07')
    await updateBirthDate('2026-02-31').catch(() => {})
    expect((await me()).birthDate).toBe('1995-03-07')
  })

  it('어느 칸이 틀렸는지 알려준다', async () => {
    const e = await updateBirthDate('2026-02-31').catch(x => x)
    expect(e.detail.birthDate).toBeTruthy()
  })

  it('로그인하지 않으면 막는다', async () => {
    await logout()
    await expectApiError(() => updateBirthDate('1995-03-07'), 401)
  })

  it('감사 로그에 날짜 값을 남기지 않는다', async () => {
    await updateBirthDate('1995-03-07')
    const dump = JSON.stringify(load().authLog)
    expect(dump).toContain('PROFILE_BIRTHDATE_CHANGED')
    expect(dump).not.toContain('1995-03-07')
  })
})

/* 가입 때도 같은 검사를 태운다 — 예전에는 검사가 아예 없어 아무 값이나 저장됐다 */
describe('가입 시 생년월일 검사', () => {
  const base = () => ({
    email: 'birth@example.com',
    password: GOOD_PW,
    passwordConfirm: GOOD_PW,
    nickname: '생일테스트',
    name: '김생일'
  })

  it('없는 날짜로는 가입되지 않는다', async () => {
    const e = await signup({ ...base(), birthDate: '2026-02-31' }).catch(x => x)
    expect(e.status).toBe(400)
    expect(e.detail.birthDate).toBeTruthy()
    expect(load().users.some(u => u.email === 'birth@example.com')).toBe(false)
  })

  it('미래 날짜로는 가입되지 않는다', async () => {
    await expectApiError(() => signup({ ...base(), birthDate: '2999-01-01' }), 400)
  })

  it('비워 두면 null 로 저장된다', async () => {
    await signup({ ...base(), birthDate: '' })
    expect(load().users.find(u => u.email === 'birth@example.com').birthDate).toBeNull()
  })

  it('정상 날짜는 그대로 저장된다', async () => {
    await signup({ ...base(), birthDate: '1990-12-25' })
    expect(load().users.find(u => u.email === 'birth@example.com').birthDate).toBe('1990-12-25')
  })
})

/* ═══════════════ MY_011 닉네임 변경 ═══════════════ */

describe('닉네임 변경', () => {
  beforeEach(async () => {
    await login({ email: TEST_ACCOUNT.email, password: TEST_ACCOUNT.password })
  })

  it('바꾸면 프로필에 반영된다', async () => {
    const u = await updateNickname('바다보는사람')
    expect(u.nickname).toBe('바다보는사람')
    expect((await me()).nickname).toBe('바다보는사람')
  })

  it('앞뒤 공백은 잘라서 저장한다', async () => {
    expect((await updateNickname('  한갓이2  ')).nickname).toBe('한갓이2')
  })

  it('이미 쓰는 닉네임은 409 로 막고, 그대로 알려준다 (공개 식별자)', async () => {
    const other = load().users.find(u => u.email === 'other@hangat.kr')
    const e = await updateNickname(other.nickname).catch(x => x)
    expect(e.status).toBe(409)
    expect(e.code).toBe('NICKNAME_TAKEN')
    expect(e.detail.nickname).toBeTruthy()
  })

  it('대소문자만 다른 남의 닉네임도 막는다', async () => {
    const other = load().users.find(u => u.email === 'other@hangat.kr')
    await expectApiError(() => updateNickname(other.nickname.toUpperCase()), 409)
  })

  it('자기 닉네임은 중복 검사에서 빠진다 (같은 값 저장 가능)', async () => {
    const mine = (await me()).nickname
    await expect(updateNickname(mine)).resolves.toMatchObject({ nickname: mine })
  })

  it('길이·공백 규칙을 지킨다', async () => {
    // 가운데 공백은 막는다. 앞뒤 공백은 **막지 않고 잘라낸다** (위 트림 테스트 참고)
    for (const bad of ['', ' ', 'a', 'x'.repeat(21), '한 갓']) {
      await expectApiError(() => updateNickname(bad), 400)
    }
  })

  it('막힌 값은 저장되지 않는다', async () => {
    const before = (await me()).nickname
    await updateNickname('a').catch(() => {})
    expect((await me()).nickname).toBe(before)
  })

  it('로그인하지 않으면 막는다', async () => {
    await logout()
    await expectApiError(() => updateNickname('아무개'), 401)
  })

  it('감사 로그에 닉네임 값을 남기지 않는다', async () => {
    await updateNickname('숨겨야하는닉')
    const dump = JSON.stringify(load().authLog)
    expect(dump).toContain('PROFILE_NICKNAME_CHANGED')
    expect(dump).not.toContain('숨겨야하는닉')
  })

  it('이름은 그대로 남는다 (비밀번호 재설정 본인 확인용)', async () => {
    const before = (await me()).name
    await updateNickname('새닉네임')
    expect((await me()).name).toBe(before)
  })
})
