import { apiRequest, BACKEND_BASE_URL, getBackendAccessToken, getBackendSessionVersion, getBackendUserId, reissueAccessToken } from './backendClient.js'

// 백엔드 배포 전에는 새 API를 호출하지 않는다. Vite 빌드 시 활성화한다.
export const NOTIFICATIONS_ENABLED = import.meta.env.VITE_NOTIFICATIONS_ENABLED === 'true'
export const ASYNC_COURSES_ENABLED = import.meta.env.VITE_ASYNC_COURSES_ENABLED === 'true'
export const TRIP_ALERTS_ENABLED = import.meta.env.VITE_TRIP_ALERTS_ENABLED === 'true'
const authenticated = { auth: true, sessionBound: true, timeoutMs: 15000 }

export const listNotifications = (cursor = null) => apiRequest(`/users/me/notifications?size=30${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, authenticated)
export const readNotification = id => apiRequest(`/users/me/notifications/${encodeURIComponent(id)}/read`, { ...authenticated, method: 'PUT' })
export const readAllNotifications = () => apiRequest('/users/me/notifications/read-all', { ...authenticated, method: 'PUT' })

/** 서버가 보낸 임의 URL로 이동하지 않고, 앱에서 허용한 목적지만 해석한다. */
export function notificationDestination (item) {
  const id = String(item.targetId ?? '')
  if (item.targetType === 'COURSE_GENERATION' && /^[a-f\d]{8}(-[a-f\d]{4}){3}-[a-f\d]{12}$/i.test(id)) {
    return { name: 'course-generation-job', params: { jobId: id } }
  }
  if (item.targetType === 'COURSE' && /^[1-9]\d{0,14}$/.test(id)) return { name: 'course-detail', params: { courseId: id } }
  if (item.targetType === 'PLACE' && /^[1-9]\d{0,14}$/.test(id)) return { name: 'place-detail', params: { placeId: id } }
  if (item.targetType === 'SECURITY') return { name: 'my-profile' }
  return { name: 'my-alerts' }
}

/** UTF-8 디코딩 뒤 전달된 조각을 SSE 줄/이벤트로 조립한다. heartbeat는 무시한다. */
export function createSseParser (onEvent) {
  let buffer = ''; let data = []; let event = 'message'; let length = 0
  return chunk => {
    buffer += chunk
    if (buffer.length + length > 65536) throw new Error('알림 이벤트 크기가 너무 큽니다.')
    for (;;) {
      const match = /\r\n|\n|\r(?!$)/.exec(buffer)
      if (!match) break
      const line = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      if (!line) {
        if (data.length) onEvent({ event, data: data.join('\n') })
        data = []; event = 'message'; length = 0
      } else if (!line.startsWith(':')) {
        const colon = line.indexOf(':')
        const key = colon < 0 ? line : line.slice(0, colon)
        const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '')
        if (key === 'data') { data.push(value); length += value.length }
        if (key === 'event') event = value
      }
    }
  }
}

/** 한 번의 연결만 담당한다. 재연결과 화면 생명주기는 공유 store가 관리한다. */
export async function streamNotifications ({ signal, onEvent, onOpen }) {
  const epoch = getBackendSessionVersion()
  const userId = getBackendUserId()
  if (!getBackendAccessToken()) await reissueAccessToken()
  const connect = () => {
    if (signal.aborted || epoch !== getBackendSessionVersion()) throw new Error('SESSION_CHANGED')
    const token = getBackendAccessToken()
    let subject
    try { subject = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub } catch { /* 아래에서 거부 */ }
    if (userId == null || String(subject) !== String(userId)) throw new Error('SESSION_CHANGED')
    return fetch(`${BACKEND_BASE_URL}/users/me/notifications/stream`, {
      headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
      credentials: 'include', cache: 'no-store', signal
    })
  }
  let response = await connect()
  if (response.status === 401) {
    await response.body?.cancel()
    await reissueAccessToken()
    response = await connect()
  }
  if (!response.ok || !response.headers.get('Content-Type')?.startsWith('text/event-stream') || !response.body) {
    await response.body?.cancel()
    throw new Error('알림 실시간 연결을 시작하지 못했어요.')
  }
  onOpen()
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const feed = createSseParser(value => {
    if (!signal.aborted && epoch === getBackendSessionVersion()) onEvent(value)
  })
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read()
      if (done) break
      feed(decoder.decode(value, { stream: true }))
    }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
