import { ApiError } from './errors.js'

/** 실제 Spring API 주소. 끝의 슬래시는 경로 중복을 막기 위해 제거한다. */
export const BACKEND_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
).replace(/\/$/, '')

const REFRESH_PATH = '/auth/reissue'
const JWT_ERROR_CODES = new Set([3001, 3002])

let accessToken = null
let accessExpiresAt = 0
let authenticatedUserId = null
let refreshPromise = null
let sessionEpoch = 0

function applyAccessToken (tokens) {
  if (!tokens || typeof tokens.accessToken !== 'string' || !tokens.accessToken) {
    throw new ApiError(500, 'INVALID_TOKEN_RESPONSE', '토큰 응답 형식을 확인해주세요.')
  }

  accessToken = tokens.accessToken
  accessExpiresAt = Date.now() + Math.max(0, Number(tokens.expiresIn) || 0)
  return tokens
}

async function readBaseResponse (response) {
  let body
  try {
    body = await response.json()
  } catch {
    throw new ApiError(
      response.status,
      'INVALID_RESPONSE',
      '서버 응답 형식을 확인해주세요.'
    )
  }

  if (!body || typeof body.success !== 'boolean') {
    throw new ApiError(
      response.status,
      'INVALID_RESPONSE',
      '서버 응답 형식을 확인해주세요.',
      body
    )
  }

  if (!response.ok || !body.success) {
    throw new ApiError(
      response.status,
      body.code ?? 'HTTP_ERROR',
      body.message || '요청을 처리하지 못했습니다.',
      body.result ?? null
    )
  }

  return body.result
}

async function rawRequest (path, { method = 'GET', body, token } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${BACKEND_BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      ...(body !== undefined ? { body: JSON.stringify(body) } : {})
    })
  } catch (error) {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.', error)
  }

  return readBaseResponse(response)
}

function isJwtError (error) {
  return error instanceof ApiError && (
    error.status === 401 || JWT_ERROR_CODES.has(Number(error.code))
  )
}

/** 로그인 응답의 Access Token과 사용자 식별자만 메모리에 보관한다. */
export function acceptLoginResponse (loginResponse) {
  sessionEpoch += 1
  accessToken = null
  accessExpiresAt = 0
  authenticatedUserId = null
  applyAccessToken(loginResponse?.tokens)
  authenticatedUserId = loginResponse?.user?.userId ?? null
  return loginResponse
}

/** 재시작 복원 뒤 조회한 사용자 정보를 메모리 세션에 연결한다. */
export function syncAuthenticatedUser (user) {
  authenticatedUserId = user?.userId ?? null
  return user
}

/** 로그아웃 시 프론트 메모리에 남은 인증 정보도 즉시 제거한다. */
export function clearBackendSession () {
  sessionEpoch += 1
  accessToken = null
  accessExpiresAt = 0
  authenticatedUserId = null
}

export function getBackendAccessToken () {
  return accessToken
}

export function getBackendUserId () {
  return authenticatedUserId
}

/** 기존 목업 콘텐츠의 소유자 키와 실제 회원 ID가 충돌하지 않게 구분한다. */
export function getBackendDemoUserId () {
  return authenticatedUserId == null ? null : `backend-user-${authenticatedUserId}`
}

export function accessTokenRemainSeconds () {
  if (!accessToken) return 0
  return Math.max(0, Math.ceil((accessExpiresAt - Date.now()) / 1000))
}

/** Refresh Token 회전이 겹치지 않도록 진행 중인 한 요청을 모든 호출자가 공유한다. */
export function reissueAccessToken () {
  if (refreshPromise) return refreshPromise

  const requestedEpoch = sessionEpoch
  refreshPromise = rawRequest(REFRESH_PATH, { method: 'POST' })
    .then(tokens => {
      if (requestedEpoch === sessionEpoch) applyAccessToken(tokens)
      return tokens
    })
    .catch(error => {
      if (requestedEpoch === sessionEpoch) clearBackendSession()
      throw error
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

/**
 * 인증 API는 만료 시 한 번만 재발급하고 원 요청도 한 번만 다시 보낸다.
 *
 * @param {string} path
 * @param {{ method?: string, body?: unknown, auth?: boolean, retryAuth?: boolean }} [options]
 */
export async function apiRequest (path, {
  method = 'GET',
  body,
  auth = false,
  retryAuth = true
} = {}) {
  if (auth && !accessToken) await reissueAccessToken()
  const attemptedToken = auth ? accessToken : null

  try {
    return await rawRequest(path, {
      method,
      body,
      token: attemptedToken
    })
  } catch (error) {
    if (!auth || !retryAuth || path === REFRESH_PATH || !isJwtError(error)) {
      throw error
    }

    // 다른 요청이 이미 회전을 마쳤다면 refresh를 또 쓰지 않고 새 토큰으로만 재시도한다.
    if (accessToken && accessToken !== attemptedToken) {
      return rawRequest(path, { method, body, token: accessToken })
    }

    await reissueAccessToken()
    return rawRequest(path, { method, body, token: accessToken })
  }
}
