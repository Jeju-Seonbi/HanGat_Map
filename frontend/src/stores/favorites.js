import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useAuthStore } from './auth.js'
import { canHandleActivityError, getFavorite, setFavorite } from '../api/myActivity.js'
import { getBackendSessionVersion } from '../api/backendClient.js'

/** 지도의 하트와 마이페이지가 공유하는 찜 상태. 계정이 바뀌면 메모리를 비운다. */
export const useFavoritesStore = defineStore('favorites', () => {
  const auth = useAuthStore()
  const values = ref({})
  const busy = ref({})
  const revision = ref(0)
  const reads = new Map()
  const writes = new Map()

  watch(() => auth.user?.userId, () => {
    values.value = {}
    busy.value = {}
    reads.clear()
    writes.clear()
    revision.value += 1
  }, { flush: 'sync' })

  const selected = id => values.value[String(id)] === true
  const isBusy = id => busy.value[String(id)] === true

  async function loadStatus(id) {
    if (!auth.user || isBusy(id)) return
    const epoch = getBackendSessionVersion()
    const userId = auth.user.userId
    const version = revision.value
    const request = Symbol()
    reads.set(String(id), request)
    try {
      const value = await getFavorite(id)
      if (epoch !== getBackendSessionVersion() || version !== revision.value || reads.get(String(id)) !== request) return
      if (typeof value !== 'boolean') throw new Error('찜 상태 응답 형식을 확인해주세요.')
      values.value[String(id)] = value
    } catch (e) {
      if (version === revision.value && canHandleActivityError(e, epoch, userId, auth.user?.userId)) throw e
    } finally {
      if (reads.get(String(id)) === request) reads.delete(String(id))
    }
  }

  async function change(id, next) {
    if (!auth.user || isBusy(id)) return false
    const epoch = getBackendSessionVersion()
    const userId = auth.user.userId
    const isCurrent = () => epoch === getBackendSessionVersion() && auth.user?.userId === userId
    const operation = Symbol()
    writes.set(String(id), operation)
    busy.value[String(id)] = true
    try {
      await setFavorite(id, next)
      if (!isCurrent()) return false
      values.value[String(id)] = next
      revision.value += 1
      return true
    } catch (e) {
      if (canHandleActivityError(e, epoch, userId, auth.user?.userId)) throw e
      return false
    } finally {
      if (writes.get(String(id)) === operation) {
        busy.value[String(id)] = false
        writes.delete(String(id))
      }
    }
  }

  return { revision, selected, isBusy, loadStatus, change }
})
