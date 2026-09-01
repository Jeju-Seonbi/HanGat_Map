/**
 * localStorage 안전 래퍼.
 *
 * localStorage 를 쓸 수 없는 환경(사파리 프라이빗, 용량 초과, 테스트 러너 등)에서도
 * 앱이 죽지 않도록 메모리 저장소로 조용히 넘어간다.
 * 이 경우 새로고침하면 데이터가 사라지지만, 목 데이터라 문제되지 않는다.
 */

const memory = new Map()

let backend = null

function detect () {
  if (backend) return backend
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const probe = '__hangat_probe__'
      window.localStorage.setItem(probe, '1')
      window.localStorage.removeItem(probe)
      backend = window.localStorage
      return backend
    }
  } catch {
    /* 접근 불가 → 메모리 */
  }
  backend = {
    getItem: k => (memory.has(k) ? memory.get(k) : null),
    setItem: (k, v) => memory.set(k, String(v)),
    removeItem: k => memory.delete(k)
  }
  return backend
}

export const storage = {
  get (key) {
    try {
      return detect().getItem(key)
    } catch {
      return null
    }
  },
  set (key, value) {
    try {
      detect().setItem(key, value)
      return true
    } catch {
      // 용량 초과 등 — 메모리로 대체 저장
      memory.set(key, String(value))
      return false
    }
  },
  remove (key) {
    try {
      detect().removeItem(key)
    } catch {
      memory.delete(key)
    }
  },
  getJson (key, fallback = null) {
    const raw = this.get(key)
    if (raw == null) return fallback
    try {
      return JSON.parse(raw)
    } catch {
      return fallback
    }
  },
  setJson (key, value) {
    return this.set(key, JSON.stringify(value))
  },
  /** 테스트에서 상태를 격리하기 위한 훅 */
  _resetMemory () {
    memory.clear()
    backend = null
  }
}
