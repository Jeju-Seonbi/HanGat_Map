import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBackendSessionVersion } from '../api/backendClient.js'
import { NOTIFICATIONS_ENABLED, listNotifications, readNotification, readAllNotifications, streamNotifications } from '../api/notifications.js'

/** 앱 전체에서 알림함과 SSE 연결 하나를 공유한다. 알림 내용은 DB 조회 결과가 기준이다. */
export const useNotificationStore = defineStore('notifications', () => {
  const items = ref([])
  const unread = ref(0)
  const nextCursor = ref(null)
  const loading = ref(false)
  const connected = ref(false)
  const error = ref('')
  let lifecycle = 0; let request = 0; let controller; let timer; let watchdog; let active = false
  let expanded = false

  async function refresh (more = false) {
    if (!NOTIFICATIONS_ENABLED || !active || (more && !nextCursor.value)) return
    const ticket = ++request
    const life = lifecycle
    const epoch = getBackendSessionVersion()
    loading.value = true
    try {
      const page = await listNotifications(more ? nextCursor.value : null)
      if (life !== lifecycle || epoch !== getBackendSessionVersion() || ticket !== request) return
      if (!Array.isArray(page?.items) || !Number.isFinite(page.unreadCount)) throw new Error('알림 응답 형식을 확인해 주세요.')
      const overlaps = page.items.some(item => items.value.some(old => String(old.id) === String(item.id)))
      if (more || expanded) {
        const merged = new Map(items.value.map(item => [String(item.id), item]))
        page.items.forEach(item => merged.set(String(item.id), item))
        items.value = [...merged.values()].sort((a, b) => {
          const left = BigInt(a.id); const right = BigInt(b.id)
          return left > right ? -1 : left < right ? 1 : 0
        })
      } else items.value = page.items
      // 새 알림이 한 페이지를 넘으면 그 사이도 조회할 수 있도록 새 커서를 사용한다.
      if (more || !expanded || !overlaps || page.nextCursor == null) nextCursor.value = page.nextCursor ?? null
      if (more) expanded = true
      unread.value = page.unreadCount
      error.value = ''
    } catch (failure) {
      if (life === lifecycle && ticket === request) error.value = failure.message || '알림을 불러오지 못했어요.'
    } finally {
      if (life === lifecycle && ticket === request) loading.value = false
    }
  }
  async function markRead (id) {
    const life = lifecycle
    await readNotification(id)
    if (life === lifecycle) await refresh()
  }
  async function markAllRead () {
    const life = lifecycle
    await readAllNotifications()
    if (life === lifecycle) {
      items.value = items.value.map(item => ({ ...item, readAt: item.readAt ?? new Date().toISOString() }))
      await refresh()
    }
  }
  function stop () {
    active = false; expanded = false; lifecycle++; request++
    controller?.abort(); clearTimeout(timer); clearTimeout(watchdog)
    document.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('online', onVisibility)
    connected.value = false; loading.value = false
    items.value = []; unread.value = 0; nextCursor.value = null; error.value = ''
  }
  function onVisibility () {
    if (!active) return
    clearTimeout(timer); clearTimeout(watchdog); controller?.abort(); controller = undefined
    connected.value = false
    if (!document.hidden) { void refresh(); timer = setTimeout(connect, 100) }
  }
  async function connect () {
    if (!active || document.hidden) return
    const life = lifecycle
    const connection = new AbortController()
    controller = connection
    // 서버는 15초 heartbeat 대신 invalidate 이벤트를 보내도 된다. 90초마다 재접속하여 JWT도 검증한다.
    watchdog = setTimeout(() => connection.abort(), 90000)
    try {
      await streamNotifications({ signal: connection.signal,
        onOpen: () => { if (life === lifecycle) { connected.value = true; void refresh() } },
        onEvent: ({ event }) => { if (event === 'invalidate' && life === lifecycle) void refresh() }
      })
    } catch { /* 지속 실패 시 REST 조회 오류를 화면에 표시하고, 제한된 간격으로 재연결한다. */ }
    finally {
      if (life === lifecycle && controller === connection) {
        clearTimeout(watchdog); connected.value = false
        if (active && !document.hidden) timer = setTimeout(() => { void refresh(); void connect() }, 15000 + Math.random() * 5000)
      }
    }
  }
  function start () {
    stop()
    if (!NOTIFICATIONS_ENABLED) return
    active = true
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', onVisibility)
    void refresh(); void connect()
  }
  return { items, unread, nextCursor, loading, connected, error, refresh, markRead, markAllRead, start, stop }
})
