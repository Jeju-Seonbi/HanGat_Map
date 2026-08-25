/**
 * 백엔드(hangat-api) 호출 공용 클라이언트.
 * - 응답은 공통 포맷 BaseResponse{success, code, message, result}로 오며 result만 꺼내 돌려준다
 * - 백엔드 경로에는 /api 프리픽스를 붙이지 않는다 (배포 시 프록시가 부여 - Nexus 방식)
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

interface BaseResponse<T> {
  success: boolean
  code: number
  message: string
  result: T
}

export async function apiGet<T> (path: string, timeoutMs = 5000): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(`${BASE_URL}${path}`, { signal: controller.signal })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const body = await res.json() as BaseResponse<T>
    if (!body.success) throw new Error(`API ${body.code} ${body.message}`)
    return body.result
  } finally {
    clearTimeout(timer)
  }
}
