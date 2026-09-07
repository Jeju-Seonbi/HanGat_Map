<script setup>
/** 실제 찜 목록. 카드/목록 보기를 제공하며 장소 선택은 실제 지도 상세로 연결한다. */
import { onBeforeUnmount, ref, watch } from 'vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StateBlock from '../../components/common/StateBlock.vue'
import PlaceImage from '../../components/common/PlaceImage.vue'
import SortSeg from '../../components/mypage/SortSeg.vue'
import StarRating from '../../components/mypage/StarRating.vue'
import { listFavorites, FAVORITE_SORTS, mediaUrl, canHandleActivityError } from '../../api/myActivity.js'
import { getBackendSessionVersion } from '../../api/backendClient.js'
import { useFavoritesStore } from '../../stores/favorites.js'
import { useAuthStore } from '../../stores/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { useApiError } from '../../composables/useApiError.js'
import { useMyPageRows } from '../../composables/useMyPageRows.js'

const favorites = useFavoritesStore()
const auth = useAuthStore()
const ui = useUiStore()
const toMessage = useApiError()
const sort = ref('recent')
const view = ref('card')
const { items, total, loading, error, hasMore, load } = useMyPageRows(listFavorites, sort, 20, toMessage)
const STATUS = { OPEN: '영업 중', TEMP_CLOSED: '임시 휴업', CLOSED: '폐업', UNKNOWN: '영업 상태 정보 없음' }
let alive = true
onBeforeUnmount(() => { alive = false })

// 지도 또는 이 목록에서 찜을 변경하면 현재 서버 목록으로 갱신한다.
watch(() => favorites.revision, () => load(true))
async function unfavorite(item) {
  const epoch = getBackendSessionVersion()
  const userId = auth.user?.userId
  try {
    if (await favorites.change(item.placeId, false) && alive) ui.toast(`${item.name} 찜을 해제했어요`)
  } catch (e) {
    if (!alive || !canHandleActivityError(e, epoch, userId, auth.user?.userId)) return
    const message = toMessage(e)
    if (message) ui.toast(message)
  }
}
</script>

<template>
  <section>
    <div class="bar-top">
      <h2 class="sect">찜한 장소 <span class="cnt tnum">{{ total }}</span></h2>
      <SortSeg v-model="sort" :options="FAVORITE_SORTS" label="찜 정렬 기준" />
    </div>
    <div class="tools">
      <div class="seg small" role="group" aria-label="찜 보기 방식">
        <button :class="{ on: view === 'card' }" :aria-pressed="view === 'card'" @click="view = 'card'">카드</button>
        <button :class="{ on: view === 'list' }" :aria-pressed="view === 'list'" @click="view = 'list'">목록</button>
      </div>
      <RouterLink class="btn2" to="/map">지도에서 장소 찾기</RouterLink>
    </div>
    <StateBlock :loading="loading && !items.length" :error="error" :rows="3" @retry="load(!items.length)" />
    <EmptyState v-if="!loading && !error && !items.length" title="찜한 장소가 없어요"
      hint="지도에서 마음에 드는 장소를 열고 하트를 누르면 여기에 모여요."
      action-label="지도에서 찾아보기" action-to="/map" />
    <ul v-if="items.length" class="places" :class="view">
      <li v-for="p in items" :key="p.placeId">
        <article class="card favorite">
          <RouterLink class="picture" :to="{ name: 'map', query: { place: p.placeId } }" :aria-label="`${p.name} 지도에서 열기`">
            <PlaceImage v-if="mediaUrl(p.imageUrl)" :src="mediaUrl(p.imageUrl)" :alt="p.name" />
            <span v-else class="no-image">장소 사진 준비 중</span>
          </RouterLink>
          <div class="body">
            <div class="heading">
              <RouterLink class="name" :to="{ name: 'map', query: { place: p.placeId } }">{{ p.name }}</RouterLink>
              <button class="unfav" type="button" :disabled="favorites.isBusy(p.placeId)" :aria-label="`${p.name} 찜 해제`" @click="unfavorite(p)">
                {{ favorites.isBusy(p.placeId) ? '…' : '♥' }}
              </button>
            </div>
            <p class="note">{{ [p.category, p.region].filter(Boolean).join(' · ') }}</p>
            <p class="address">{{ p.address || '주소 정보 없음' }}</p>
            <div class="rating">
              <template v-if="p.rating != null">
                <StarRating :model-value="Math.round(p.rating)" :size="12" />
                <span class="score tnum">{{ Number(p.rating).toFixed(1) }}</span>
                <span class="note tnum">({{ p.reviewCount }})</span>
              </template>
              <span v-else class="note">평점 없음</span>
            </div>
            <dl class="meta">
              <div><dt>운영</dt><dd>{{ STATUS[p.businessStatus] || STATUS.UNKNOWN }}</dd></div>
              <div><dt>시간</dt><dd>{{ p.operatingHours || '운영 시간 정보 없음' }}</dd></div>
              <div><dt>요금</dt><dd>{{ p.feeText || '요금 정보 없음' }}</dd></div>
            </dl>
            <RouterLink class="open" :to="{ name: 'map', query: { place: p.placeId } }">지도에서 열기</RouterLink>
          </div>
        </article>
      </li>
    </ul>
    <button v-if="hasMore && !error" class="btn2 more-btn" :disabled="loading" @click="load()">
      {{ loading ? '불러오는 중…' : '더보기' }} <span class="tnum">({{ items.length }} / {{ total }})</span>
    </button>
  </section>
</template>

<style scoped>
.bar-top { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.bar-top .sect { margin: 0; flex: 1; }
.cnt { color: var(--tx3); font-weight: 700; }
.tools { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.seg.small { width: 150px; }
.places { list-style: none; padding: 0; }
.places.card { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(224px, 100%), 1fr)); gap: 12px; padding: 0; border: 0; background: none; box-shadow: none; }
.favorite { padding: 10px; height: 100%; overflow: hidden; }
.picture { display: block; border-radius: 12px; overflow: hidden; background: var(--surf2); }
.picture img, .no-image { display: block; width: 100%; height: 130px; object-fit: cover; }
.no-image { display: flex; align-items: center; justify-content: center; color: var(--tx3); font-size: 12px; }
.body { padding: 8px 4px 2px; min-width: 0; }
.heading { display: flex; align-items: center; gap: 8px; }
.name { flex: 1; min-width: 0; font-size: 15px; font-weight: 800; overflow-wrap: anywhere; }
.name:hover, .open:hover { text-decoration: underline; }
.unfav { width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%; background: var(--pink-bg); color: var(--pink); font-size: 18px; }
.unfav:disabled { opacity: .55; cursor: wait; }
.address { font-size: 12px; color: var(--tx2); margin-top: 8px; overflow-wrap: anywhere; }
.rating { display: flex; align-items: center; gap: 5px; margin: 8px 0; }
.score { font-size: 12px; font-weight: 800; }
.meta { margin: 0; }
.meta > div { display: flex; gap: 8px; padding: 4px 0; font-size: 11.5px; }
.meta dt { width: 28px; flex-shrink: 0; color: var(--tx3); }
.meta dd { margin: 0; color: var(--tx2); overflow-wrap: anywhere; }
.open { display: inline-block; margin-top: 10px; padding: 6px 0; color: var(--ac-dk); font-size: 12px; font-weight: 700; }
.places.list { display: flex; flex-direction: column; gap: 10px; }
.list .favorite { display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 12px; }
.list .picture img, .list .no-image { height: 120px; }
.list .body { padding-top: 0; }
.more-btn { width: 100%; margin-top: 12px; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--ac); outline-offset: 3px; }
@media (max-width: 480px) {
  .list .favorite { grid-template-columns: 80px minmax(0, 1fr); gap: 8px; }
  .list .picture img, .list .no-image { height: 80px; }
  .list .no-image { font-size: 10px; text-align: center; }
}
</style>
