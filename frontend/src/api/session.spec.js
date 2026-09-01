/**
 * 세션 · 토큰 보관 검증.
 * "localStorage 에 토큰을 넣지 않는다"는 주장을 말이 아니라 테스트로 고정한다.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { resetDb, load } from './db.js'
import { storage } from './storage.js'
import {
  createSession, destroySession, refreshAccessToken, currentUserId,
  hasResumableSession, getAccessToken, decodeAccessToken,
  accessTokenRemainSeconds, _resetSessionState, _setRefreshCookieForTest, ACCESS_TTL_MS
} from './session.js'
import { ApiError } from './errors.js'

beforeEach(() => {
  storage._resetMemory()
  _resetSessionState()
  resetDb()
})

describe('토큰 보관 위치', () => {
  it('로그인해도 저장소(localStorage 대체 포함)에 토큰 문자열이 남지 않는다', () => {
    const { accessToken } = createSession('u1')
    expect(accessToken).toBeTruthy()

    // storage 는 목 DB 와 최근 로그인 목록만 담는다. 토큰은 들어가면 안 된다.
    const dump = JSON.stringify(load()) + (storage.get('hangat_mock_db_v2') || '')
    expect(dump.includes(accessToken)).toBe(false)
    expect(storage.get('hangat_tokens')).toBeNull()
  })

  it('액세스 토큰은 메모리에만 있다 — 상태를 비우면 사라진다', () => {
    createSession('u1')
    expect(getAccessToken()).toBeTruthy()
    _resetSessionState()
    expect(getAccessToken()).toBeNull()
    expect(currentUserId()).toBeNull()
  })

  it('리프레시 토큰은 쿠키 경로로만 오간다 — 앱이 값을 직접 들고 있지 않는다', () => {
    const res = createSession('u1')
    expect(res).not.toHaveProperty('refreshToken')
    expect(hasResumableSession()).toBe(true)
  })

  it('액세스 토큰에 만료 시각이 들어 있고 수명이 짧다', () => {
    createSession('u1')
    const p = decodeAccessToken(getAccessToken())
    expect(p.sub).toBe('u1')
    expect(p.exp - Date.now()).toBeLessThanOrEqual(ACCESS_TTL_MS)
    expect(accessTokenRemainSeconds()).toBeGreaterThan(0)
  })
})

describe('세션 수명주기', () => {
  it('로그인하면 이전 세션을 끊고 새로 만든다 (세션 고정 차단)', () => {
    createSession('u1')
    const first = load().sessions.at(-1)
    createSession('u1')
    const second = load().sessions.at(-1)

    expect(second.sessionId).not.toBe(first.sessionId)
    expect(load().sessions.find(s => s.sessionId === first.sessionId).revokedAt).toBeTruthy()
  })

  it('로그아웃하면 서버 쪽 세션도 끊긴다', () => {
    createSession('u1')
    const rec = load().sessions.at(-1)
    destroySession()
    expect(load().sessions.find(s => s.sessionId === rec.sessionId).revokedAt).toBeTruthy()
    expect(currentUserId()).toBeNull()
    expect(hasResumableSession()).toBe(false)
  })

  it('끊긴 세션의 액세스 토큰은 만료 전이라도 무효다', () => {
    createSession('u1')
    const sid = decodeAccessToken(getAccessToken()).sid
    load().sessions.find(s => s.sessionId === sid).revokedAt = Date.now()
    expect(currentUserId()).toBeNull()
  })
})

describe('리프레시 토큰 회전 · 재사용 탐지', () => {
  it('재발급마다 토큰이 바뀐다 (rotation)', () => {
    createSession('u1')
    const before = load().sessions.at(-1).refreshToken
    refreshAccessToken()
    const after = load().sessions.at(-1).refreshToken
    expect(after).not.toBe(before)
    expect(load().sessions.find(s => s.refreshToken === before).consumedAt).toBeTruthy()
  })

  it('회전해도 절대 만료 시각은 연장되지 않는다', () => {
    createSession('u1')
    const first = load().sessions.at(-1).absoluteExpiresAt
    refreshAccessToken()
    expect(load().sessions.at(-1).absoluteExpiresAt).toBe(first)
  })

  it('이미 쓴 리프레시 토큰이 다시 오면 계열 전체를 끊는다', () => {
    createSession('u1')
    const stolen = load().sessions.at(-1).refreshToken
    refreshAccessToken() // 정상 사용자가 회전시킴

    const familyId = load().sessions.at(-1).familyId
    expect(load().sessions.find(s => s.refreshToken === stolen).consumedAt).toBeTruthy()

    // 공격자가 훔쳐 둔 예전 토큰으로 재발급을 시도한다
    _setRefreshCookieForTest(stolen)
    expect(() => refreshAccessToken()).toThrow(ApiError)

    // 정상 사용자 세션까지 포함해 계열 전체가 끊겨야 한다
    const alive = load().sessions.filter(s => s.familyId === familyId && !s.revokedAt)
    expect(alive).toHaveLength(0)
    expect(load().sessions.some(s => s.revokeReason === 'REFRESH_TOKEN_REUSE')).toBe(true)
  })

  it('절대 만료가 지나면 재발급을 거부한다', () => {
    createSession('u1')
    load().sessions.at(-1).absoluteExpiresAt = Date.now() - 1
    expect(() => refreshAccessToken()).toThrow(/다시 로그인/)
  })

  it('오래 안 쓰면(유휴 만료) 재발급을 거부한다', () => {
    createSession('u1')
    load().sessions.at(-1).lastUsedAt = Date.now() - 13 * 60 * 60 * 1000
    expect(() => refreshAccessToken()).toThrow(/로그아웃|로그인/)
  })

  it('쿠키가 없으면 401 을 낸다', () => {
    _resetSessionState()
    expect(() => refreshAccessToken()).toThrow(ApiError)
  })
})
