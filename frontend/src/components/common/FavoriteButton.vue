<script setup>
/** 실제 장소 ID로 찜하고, 비회원은 현재 장소를 보존한 로그인 화면으로 안내한다. */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth.js'
import { useFavoritesStore } from '../../stores/favorites.js'
import { useUiStore } from '../../stores/ui.js'
import { useApiError } from '../../composables/useApiError.js'
import { getBackendSessionVersion } from '../../api/backendClient.js'
import { canHandleActivityError } from '../../api/myActivity.js'

const props = defineProps({ placeId: { type: [Number, String], default: null } })
const auth = useAuthStore()
const favorites = useFavoritesStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const toMessage = useApiError()
const loading = ref(false)
const error = ref('')
const validId = computed(() => /^[1-9]\d*$/.test(String(props.placeId ?? '')))
const selected = computed(() => favorites.selected(props.placeId))
const busy = computed(() => favorites.isBusy(props.placeId))
let version = 0
let alive = true

async function refresh() {
  const current = ++version
  const epoch = getBackendSessionVersion()
  const userId = auth.user?.userId
  error.value = ''
  loading.value = false
  if (!auth.user || !validId.value) return
  loading.value = true
  try {
    await favorites.loadStatus(props.placeId)
  } catch (e) {
    if (alive && current === version && canHandleActivityError(e, epoch, userId, auth.user?.userId)) error.value = toMessage(e) || ''
  } finally {
    if (alive && current === version) loading.value = false
  }
}
watch(() => [auth.user?.userId, props.placeId, favorites.revision], refresh, { immediate: true })
onBeforeUnmount(() => { alive = false; version += 1 })

async function toggle() {
  if (!validId.value || busy.value || loading.value) return
  if (!auth.user) {
    // 지도는 장소 선택 시 주소만 갱신하므로 route.fullPath 대신 현재 선택 ID를 반영한다.
    const redirect = router.resolve({ name: 'map', query: { ...route.query, place: props.placeId } }).fullPath
    await router.push({ name: 'login', query: { redirect } })
    return
  }
  if (error.value) { await refresh(); return }
  const epoch = getBackendSessionVersion()
  const userId = auth.user.userId
  const placeId = props.placeId
  const next = !selected.value
  try {
    if (await favorites.change(placeId, next) && alive && placeId === props.placeId) {
      ui.toast(next ? '찜했어요 — 마이페이지에서 볼 수 있어요' : '찜을 해제했어요')
    }
  } catch (e) {
    if (alive && placeId === props.placeId && canHandleActivityError(e, epoch, userId, auth.user?.userId)) {
      const message = toMessage(e)
      if (message) ui.toast(message)
    }
  }
}
</script>

<template>
  <button type="button" class="favorite-button" :class="{ selected }"
    :disabled="!validId || loading || busy" :aria-pressed="selected"
    :aria-label="error ? '찜 상태 다시 확인' : selected ? '찜 해제' : '찜하기'"
    :title="error || (!validId ? '실제 장소에서 찜할 수 있어요' : '')" @click.stop="toggle"
  >{{ loading || busy ? '…' : error ? '↻' : selected ? '♥' : '♡' }}</button>
</template>

<style scoped>
.favorite-button {
  width: 40px; height: 40px; flex-shrink: 0; border-radius: 50%;
  background: var(--surf2); color: var(--tx2); border: 1px solid var(--line); font-size: 22px;
}
.favorite-button.selected { color: var(--pink); background: var(--pink-bg); }
.favorite-button:disabled { opacity: .55; cursor: wait; }
.favorite-button:focus-visible { outline: 2px solid var(--ac); outline-offset: 3px; }
</style>
