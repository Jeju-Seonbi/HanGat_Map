<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { kakaoPlaceSearchService, type KakaoPlaceSearchMode } from '../../services/kakaoPlaceSearchService'
import type { KakaoPlaceSearchResult } from '../../assets/types/course'
import KakaoPagination from './KakaoPagination.vue'

const props = withDefaults(defineProps<{
  mode: KakaoPlaceSearchMode
  placeholder: string
  loadingText: string
  emptyText: string
}>(), {})
const emit = defineEmits<{ select: [value: KakaoPlaceSearchResult]; queryChange: [] }>()

const query = ref('')
const results = ref<KakaoPlaceSearchResult[]>([])
const loading = ref(false)
const searched = ref(false)
const currentPage = ref(1)
const lastPage = ref(1)
let debounceTimer: ReturnType<typeof setTimeout> | undefined
let searchToken = 0

async function search(page: number) {
  const clean = query.value.trim()
  if (clean.length < 2) return
  const token = ++searchToken
  loading.value = true
  try {
    const response = await kakaoPlaceSearchService.search(clean, { mode: props.mode, page })
    if (token !== searchToken) return
    results.value = response.items
    currentPage.value = response.current_page
    lastPage.value = response.last_page
    searched.value = true
  } finally {
    if (token === searchToken) loading.value = false
  }
}

watch(query, value => {
  emit('queryChange')
  if (debounceTimer) clearTimeout(debounceTimer)
  searchToken += 1
  results.value = []
  searched.value = false
  currentPage.value = 1
  lastPage.value = 1
  if (value.trim().length < 2) {
    loading.value = false
    return
  }
  debounceTimer = setTimeout(() => { void search(1) }, 280)
})

function choose(item: KakaoPlaceSearchResult) {
  emit('select', { ...item })
  query.value = ''
  results.value = []
  searched.value = false
}

function reset() {
  query.value = ''
  results.value = []
  searched.value = false
  currentPage.value = 1
  lastPage.value = 1
}

defineExpose({ reset })
onBeforeUnmount(() => { if (debounceTimer) clearTimeout(debounceTimer) })
</script>

<template>
  <div class="kakao-place-search">
    <input v-model="query" type="search" autocomplete="off" :placeholder="placeholder">
    <small class="field-help">2글자 이상 입력하면 제주 지역 후보를 정확도 순으로 검색해요.</small>
    <div v-if="loading" class="search-status">{{ loadingText }}</div>
    <div v-else-if="results.length" class="search-results">
      <article v-for="item in results" :key="`${item.source_code}:${item.source_place_id}`">
        <div><b>{{ item.place_name }}</b><span>{{ item.road_address || item.address }}</span><small v-if="item.category_name">{{ item.category_name }}</small></div>
        <button type="button" @click="choose(item)">선택</button>
      </article>
      <KakaoPagination :current-page="currentPage" :last-page="lastPage" :disabled="loading" @change="search" />
    </div>
    <div v-else-if="searched" class="search-status">{{ emptyText }}</div>
  </div>
</template>

<style scoped>
.kakao-place-search{display:grid;gap:6px}.field-help{color:var(--course-text-3);font-size:.68rem;font-weight:500}.search-status{padding:9px 11px;border-radius:10px;background:var(--course-surface-2);color:var(--course-text-2);font-size:.72rem}.search-results{display:grid;gap:6px}.search-results article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid var(--course-line);border-radius:11px;background:var(--course-surface)}.search-results article>div{display:grid;gap:2px;min-width:0}.search-results b{font-size:.78rem}.search-results span,.search-results small{overflow:hidden;color:var(--course-text-3);font-size:.66rem;text-overflow:ellipsis;white-space:nowrap}.search-results article>button{flex:0 0 auto;border:0;border-radius:9px;background:var(--course-accent-bg);padding:7px 10px;color:var(--course-accent-dark);font-size:.68rem;font-weight:800}
</style>
