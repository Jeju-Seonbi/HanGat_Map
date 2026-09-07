import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { getBackendSessionVersion } from '../api/backendClient.js'
import { canHandleActivityError } from '../api/myActivity.js'

/** 내 목록의 정렬·더보기. 계정/정렬이 바뀌면 이전 응답을 버리고 페이지는 성공했을 때만 넘긴다. */
export function useMyPageRows(fetcher, sort, size = 10, toMessage = () => '목록을 불러오지 못했어요.') {
  const auth = useAuthStore()
  const items = ref([])
  const total = ref(0)
  const number = ref(-1)
  const totalPages = ref(0)
  const loading = ref(false)
  const error = ref('')
  let requestVersion = 0

  const hasMore = computed(() => number.value + 1 < totalPages.value)

  async function load(reset = false) {
    if (!reset && (loading.value || (number.value >= 0 && !hasMore.value))) return
    const version = ++requestVersion
    const epoch = getBackendSessionVersion()
    const userId = auth.user?.userId
    const isCurrent = () => version === requestVersion && epoch === getBackendSessionVersion()
    if (reset) {
      items.value = []
      total.value = 0
      number.value = -1
      totalPages.value = 0
    }
    error.value = ''
    if (!auth.user) { loading.value = false; return }
    loading.value = true
    try {
      const result = await fetcher({ page: reset ? 0 : number.value + 1, size, sort: sort.value })
      if (!isCurrent()) return
      const rows = reset ? result.content : [...items.value, ...result.content]
      // 정렬 사이에 새 데이터가 생겨 페이지가 밀려도 같은 항목을 중복 표시하지 않는다.
      items.value = [...new Map(rows.map(item => [item.reviewId ?? item.placeId, item])).values()]
      total.value = result.totalElements
      number.value = result.number
      totalPages.value = result.totalPages
    } catch (e) {
      if (version === requestVersion && canHandleActivityError(e, epoch, userId, auth.user?.userId)) {
        error.value = toMessage(e) || ''
      }
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  watch(() => [auth.user?.userId, sort.value], () => load(true), { immediate: true, flush: 'sync' })
  onBeforeUnmount(() => { requestVersion += 1 })
  return { items, total, loading, error, hasMore, load }
}
