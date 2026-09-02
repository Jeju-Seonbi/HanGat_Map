import { describe, it, expect, beforeEach } from 'vitest'
import {
  LIMITS, ACTION_LIMITS, checkLoginAllowed, recordLoginFailure,
  recordLoginSuccess, consumeAction, formatRetryAfter, resetThrottle
} from './rateLimit.js'

let store
beforeEach(() => { store = {}; resetThrottle(store) })

describe('계정 단위 시도 제한', () => {
  it('임계값 전까지는 지연이 없다', () => {
    for (let i = 0; i < LIMITS.softThreshold; i++) recordLoginFailure(store, 'a')
    expect(checkLoginAllowed(store, 'a').delayMs).toBe(0)
  })

  it('임계값을 넘으면 지연이 지수적으로 늘어난다', () => {
    const seen = []
    for (let i = 0; i < LIMITS.softThreshold + 4; i++) {
      recordLoginFailure(store, 'a')
      seen.push(checkLoginAllowed(store, 'a').delayMs)
    }
    const delays = seen.filter(d => d > 0)
    expect(delays.length).toBeGreaterThan(0)
    for (let i = 1; i < delays.length; i++) {
      expect(delays[i]).toBeGreaterThanOrEqual(delays[i - 1])
    }
    expect(Math.max(...delays)).toBeLessThanOrEqual(LIMITS.delayMaxMs)
  })

  it('실패가 hardThreshold 에 닿으면 계정을 잠근다 (고시 제5조⑥)', () => {
    for (let i = 0; i < LIMITS.hardThreshold; i++) recordLoginFailure(store, 'a')
    const gate = checkLoginAllowed(store, 'a')
    expect(gate.allowed).toBe(false)
    expect(gate.reason).toBe('ACCOUNT_LOCKED')
    expect(gate.retryAfterMs).toBeGreaterThan(0)
  })

  it('잠금은 그 계정에만 걸린다 — 다른 계정은 계속 로그인할 수 있다', () => {
    for (let i = 0; i < LIMITS.hardThreshold; i++) recordLoginFailure(store, 'victim')
    expect(checkLoginAllowed(store, 'victim').allowed).toBe(false)
    expect(checkLoginAllowed(store, 'other').allowed).toBe(true)
  })

  it('성공하면 계정 카운터가 비워진다', () => {
    for (let i = 0; i < LIMITS.softThreshold + 2; i++) recordLoginFailure(store, 'a')
    expect(checkLoginAllowed(store, 'a').delayMs).toBeGreaterThan(0)
    recordLoginSuccess(store, 'a')
    expect(checkLoginAllowed(store, 'a').delayMs).toBe(0)
  })

  it('관찰 창이 지난 실패는 잊는다', () => {
    const t0 = Date.now() - LIMITS.windowMs - 1000
    for (let i = 0; i < LIMITS.hardThreshold - 1; i++) recordLoginFailure(store, 'a', t0)
    // 창 밖의 실패라 현재 시점에는 남아 있지 않아야 한다
    expect(checkLoginAllowed(store, 'a').delayMs).toBe(0)
  })
})

describe('전역(스터핑) 제한', () => {
  it('계정을 바꿔가며 시도해도 전역 상한에 걸린다', () => {
    for (let i = 0; i < LIMITS.globalThreshold; i++) {
      recordLoginFailure(store, `acct-${i}`)
    }
    const gate = checkLoginAllowed(store, 'brand-new-account')
    expect(gate.allowed).toBe(false)
    expect(gate.reason).toBe('TOO_MANY_REQUESTS')
  })

  it('로그인 성공은 전역 카운터를 비우지 않는다 (공격 흔적 유지)', () => {
    for (let i = 0; i < LIMITS.globalThreshold; i++) recordLoginFailure(store, `acct-${i}`)
    recordLoginSuccess(store, 'acct-0')
    expect(checkLoginAllowed(store, 'acct-99').allowed).toBe(false)
  })
})

describe('동작별 호출량 제한', () => {
  it('비밀번호 재설정 요청 횟수를 제한한다', () => {
    const max = ACTION_LIMITS['password-reset'].max
    for (let i = 0; i < max; i++) {
      expect(consumeAction(store, 'password-reset', 'k').allowed, `${i}회차`).toBe(true)
    }
    const over = consumeAction(store, 'password-reset', 'k')
    expect(over.allowed).toBe(false)
    expect(over.retryAfterMs).toBeGreaterThan(0)
  })

  it('키가 다르면 따로 센다', () => {
    const max = ACTION_LIMITS['password-reset'].max
    for (let i = 0; i < max; i++) consumeAction(store, 'password-reset', 'k1')
    expect(consumeAction(store, 'password-reset', 'k2').allowed).toBe(true)
  })

  it('정의되지 않은 동작은 제한하지 않는다', () => {
    expect(consumeAction(store, 'unknown-action', 'k').allowed).toBe(true)
  })
})

describe('formatRetryAfter', () => {
  it('초와 분을 사람이 읽을 형태로 바꾼다', () => {
    expect(formatRetryAfter(3000)).toBe('3초')
    expect(formatRetryAfter(90_000)).toBe('2분')
  })
})
