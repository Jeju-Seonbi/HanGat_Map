import {
  BACKEND_BASE_URL,
  acceptLoginResponse,
  apiRequest,
  clearBackendSession,
  reissueAccessToken,
  syncAuthenticatedUser
} from './backendClient.js'

/** 백엔드 UserResponse를 기존 화면이 읽는 최소 사용자 모델로 맞춘다. */
export function normalizeUser (user) {
  if (!user) return null
  return {
    userId: user.userId,
    email: user.email,
    nickname: user.nickname,
    birthDate: user.birthDate ?? null,
    status: user.status,
    statusCode: user.status,
    emailVerified: !!user.emailVerified,
    lastLoginAt: user.lastLoginAt ?? null,
    createdAt: user.createdAt ?? null
  }
}

function normalizeLogin (loginResponse) {
  if (!loginResponse) return null
  const normalized = {
    ...loginResponse,
    user: normalizeUser(loginResponse.user)
  }
  acceptLoginResponse(normalized)
  return normalized
}

// ────────────────────────── 가입·로그인 ──────────────────────────

export async function signup ({ email, password, passwordConfirm, nickname }) {
  const user = await apiRequest('/auth/signup', {
    method: 'POST',
    body: { email, password, passwordConfirm, nickname }
  })
  return normalizeUser(user)
}

export async function login ({ email, password }) {
  const result = await apiRequest('/auth/login', {
    method: 'POST',
    body: { email, password }
  })
  return normalizeLogin(result)
}

export async function restoreSession () {
  await reissueAccessToken()
  const user = normalizeUser(await apiRequest('/users/me', {
    auth: true,
    retryAuth: false
  }))
  syncAuthenticatedUser(user)
  return user
}

export async function logout () {
  try {
    await apiRequest('/auth/logout', { method: 'POST' })
  } finally {
    clearBackendSession()
  }
}

// ────────────────────────── 이메일 인증 ──────────────────────────

export function verifyEmail (token) {
  return apiRequest(`/auth/verify?token=${encodeURIComponent(token)}`)
}

export function resendVerification (email) {
  return apiRequest('/auth/resend-verification', {
    method: 'POST',
    body: { email }
  })
}

// ────────────────────────── 비밀번호 재설정 ──────────────────────────

export async function sendResetCode (email) {
  const result = await apiRequest('/auth/password/code', {
    method: 'POST',
    body: { email }
  })
  return { ...result, expiresInMs: result.expiresIn }
}

export async function verifyResetCode ({ code, requestId }) {
  const result = await apiRequest('/auth/password/verify', {
    method: 'POST',
    body: { code, requestId }
  })
  return { ...result, expiresInMs: result.expiresIn }
}

export async function resetPassword ({ ticket, password, passwordConfirm }) {
  const result = await apiRequest('/auth/password/reset', {
    method: 'POST',
    body: { ticket, password, passwordConfirm }
  })
  // 백엔드가 refresh 쿠키와 서버 세션을 폐기하므로 남은 Access Token도 즉시 버린다.
  clearBackendSession()
  return result
}

// ────────────────────────── 내 정보 ──────────────────────────

export async function me () {
  const user = normalizeUser(await apiRequest('/users/me', { auth: true }))
  syncAuthenticatedUser(user)
  return user
}

export async function checkNicknameAvailable (nickname) {
  const query = new URLSearchParams({ nickname: String(nickname || '').trim() })
  return apiRequest(`/users/check-nickname?${query}`)
}

export async function updateNickname (nickname) {
  const user = normalizeUser(await apiRequest('/users/me/nickname', {
    method: 'PATCH',
    auth: true,
    body: { nickname }
  }))
  syncAuthenticatedUser(user)
  return user
}

export async function updateBirthDate (birthDate) {
  const user = normalizeUser(await apiRequest('/users/me/birth-date', {
    method: 'PATCH',
    auth: true,
    body: { birthDate: birthDate || null }
  }))
  syncAuthenticatedUser(user)
  return user
}

// ────────────────────────── 소셜 로그인 ──────────────────────────

export function oauthAuthorizationUrl (provider) {
  const normalized = String(provider || '').toLowerCase()
  if (!['google', 'kakao'].includes(normalized)) {
    throw new TypeError('지원하지 않는 소셜 로그인 공급자입니다.')
  }
  return `${BACKEND_BASE_URL}/oauth2/authorization/${normalized}`
}

export function getOAuthFlow () {
  return apiRequest('/auth/oauth/flow')
}

export function sendOAuthSignupCode ({ email, nickname }) {
  return apiRequest('/auth/oauth/signup/code', {
    method: 'POST',
    body: { email: email || null, nickname }
  })
}

export function sendOAuthLinkCode () {
  return apiRequest('/auth/oauth/link/code', { method: 'POST' })
}

export async function verifyOAuthCode (code) {
  const result = await apiRequest('/auth/oauth/code/verify', {
    method: 'POST',
    body: { code }
  })
  if (!result.login) return result
  return { ...result, login: normalizeLogin(result.login) }
}

export async function completeOAuthLink () {
  return normalizeLogin(await apiRequest('/auth/oauth/link/complete', {
    method: 'POST'
  }))
}

export function cancelOAuthFlow () {
  return apiRequest('/auth/oauth/flow', { method: 'DELETE' })
}
