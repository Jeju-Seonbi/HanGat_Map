import { useRouter, useRoute } from 'vue-router'
import { ApiError } from '../api/errors.js'
import { useAuthStore } from '../stores/auth.js'

/**
 * 401(세션 만료)을 한 곳에서 처리한다.
 * 명세서 AUTH_005: 만료 시 무한 로딩 없이 재로그인 안내 + 원래 작업 보호(returnTo).
 *
 * @returns {(e:unknown)=>string|null} 화면에 그대로 쓸 메시지. 401 이면 리다이렉트하고 null 반환.
 */
export function useApiError () {
  const router = useRouter()
  const route = useRoute()
  const auth = useAuthStore()

  return function toMessage (e) {
    if (e instanceof ApiError && e.status === 401) {
      auth.handleUnauthorized(route.fullPath, e.code)
      router.replace({ name: 'login', query: { redirect: route.fullPath } })
      return null
    }
    if (e instanceof ApiError) return e.message
    return '불러오지 못했어요. 잠시 뒤 다시 시도해 주세요'
  }
}
