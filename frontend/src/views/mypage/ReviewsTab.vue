<script setup>
/** 내 리뷰 조회·삭제. 서버가 반환한 실제 사진과 장소 ID를 사용한다. */
import { onBeforeUnmount, ref, watch } from 'vue'
import StarRating from '../../components/mypage/StarRating.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StateBlock from '../../components/common/StateBlock.vue'
import SortSeg from '../../components/mypage/SortSeg.vue'
import ConfirmDeleteDialog from '../../components/mypage/ConfirmDeleteDialog.vue'
import PlaceImage from '../../components/common/PlaceImage.vue'
import { listMyReviews, deleteMyReview, REVIEW_SORTS, mediaUrl, canHandleActivityError } from '../../api/myActivity.js'
import { getBackendSessionVersion } from '../../api/backendClient.js'
import { useAuthStore } from '../../stores/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { useApiError } from '../../composables/useApiError.js'
import { useMyPageRows } from '../../composables/useMyPageRows.js'
import { fmtK } from '../../utils/format.js'

const auth = useAuthStore()
const emit = defineEmits(['reviews-changed'])
const ui = useUiStore()
const toMessage = useApiError()
const sort = ref('created_desc')
const { items, total, loading, error, hasMore, load } = useMyPageRows(listMyReviews, sort, 10, toMessage)
const deleting = ref(null)
const deleteBusy = ref(false)
const CROWD_KO = { QUIET: '한산했어요', NORMAL: '보통이었어요', CROWDED: '혼잡했어요' }
let alive = true
let deleteVersion = 0
watch(() => auth.user?.userId, () => { deleteVersion += 1; deleting.value = null; deleteBusy.value = false }, { flush: 'sync' })
onBeforeUnmount(() => { alive = false })

async function confirmDelete() {
  if (!deleting.value || deleteBusy.value) return
  const review = deleting.value
  const version = ++deleteVersion
  const userId = auth.user?.userId
  const epoch = getBackendSessionVersion()
  const isCurrent = () => alive && epoch === getBackendSessionVersion()
  deleteBusy.value = true
  try {
    // 기존 삭제 API의 성공 결과는 평점 객체가 아닌 null이다.
    await deleteMyReview(review.reviewId)
    if (!isCurrent()) return
    deleting.value = null
    ui.toast('리뷰를 삭제했어요')
    emit('reviews-changed')
    await load(true)
  } catch (e) {
    if (!alive || version !== deleteVersion || !canHandleActivityError(e, epoch, userId, auth.user?.userId)) return
    const message = toMessage(e)
    if (message) ui.toast(message)
  } finally {
    if (alive && version === deleteVersion) deleteBusy.value = false
  }
}
</script>

<template>
  <section>
    <div class="bar-top">
      <h2 class="sect">작성한 리뷰 <span class="cnt tnum">{{ total }}</span></h2>
      <SortSeg v-model="sort" :options="REVIEW_SORTS" label="리뷰 정렬 기준" />
    </div>
    <StateBlock :loading="loading && !items.length" :error="error" :rows="3" @retry="load(!items.length)" />
    <EmptyState v-if="!loading && !error && !items.length" title="작성한 리뷰가 없어요"
      hint="지도에서 장소를 열고 별점과 혼잡 제보를 남기면 여기에 모여요."
      action-label="장소 둘러보기" action-to="/map" />
    <ul v-if="items.length" class="list">
      <li v-for="r in items" :key="r.reviewId">
        <article class="card">
          <div class="corner">
            <RouterLink class="cbtn" :to="{ name: 'map', query: { place: r.placeId } }">장소 보기</RouterLink>
            <button class="x" type="button" :aria-label="`${r.placeName} 리뷰 삭제`" @click="deleting = r">×</button>
          </div>
          <div class="top">
            <RouterLink :to="{ name: 'map', query: { place: r.placeId } }" class="place">{{ r.placeName }}</RouterLink>
            <span v-if="r.placeCategory" class="bdg neutral">{{ r.placeCategory }}</span>
          </div>
          <div class="rate">
            <template v-if="r.rating != null">
              <StarRating :model-value="r.rating" :size="14" />
              <span class="score tnum">{{ Number(r.rating).toFixed(1) }}</span>
            </template>
            <span v-else class="note">별점 없는 혼잡 제보</span>
            <span v-if="CROWD_KO[r.congestionReport]" class="bdg" :class="r.congestionReport">{{ CROWD_KO[r.congestionReport] }}</span>
          </div>
          <p v-if="r.content" class="content">{{ r.content }}</p>
          <div v-if="r.imageUrls?.length" class="photos">
            <template v-for="(url, index) in r.imageUrls" :key="`${index}-${url}`">
              <a v-if="mediaUrl(url)" :href="mediaUrl(url)" target="_blank" rel="noopener noreferrer" :aria-label="`${r.placeName} 리뷰 사진 ${index + 1} 크게 보기`">
                <PlaceImage :src="mediaUrl(url)" :alt="`${r.placeName} 리뷰 사진 ${index + 1}`" />
              </a>
            </template>
          </div>
          <p class="dates note">
            {{ fmtK(r.createdAt) }} 작성<template v-if="r.updatedAt && r.updatedAt !== r.createdAt"> · {{ fmtK(r.updatedAt) }} 수정</template>
          </p>
        </article>
      </li>
    </ul>
    <button v-if="hasMore && !error" class="btn2 more-btn" :disabled="loading" @click="load()">
      {{ loading ? '불러오는 중…' : '더보기' }} <span class="tnum">({{ items.length }} / {{ total }})</span>
    </button>
    <ConfirmDeleteDialog v-if="deleting" :key="deleting.reviewId" title="이 리뷰를 삭제할까요?"
      :subject="deleting.placeName" :detail="deleting.content || ''"
      :warning="deleting.imageUrls?.length ? `첨부한 사진 ${deleting.imageUrls.length}장도 함께 지워져요.` : ''"
      confirm-label="삭제할게요" :busy="deleteBusy"
      @close="!deleteBusy && (deleting = null)" @confirm="confirmDelete" />
  </section>
</template>

<style scoped>
.bar-top { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.bar-top .sect { margin: 0; flex: 1; }
.cnt { color: var(--tx3); font-weight: 700; }
.list { display: flex; flex-direction: column; gap: 10px; list-style: none; padding: 0; }
.card { position: relative; }
.corner { position: absolute; top: 10px; right: 10px; display: flex; align-items: center; gap: 4px; }
.cbtn { padding: 8px 12px; border-radius: var(--rp); background: var(--surf2); color: var(--tx2); font-size: 12px; font-weight: 700; white-space: nowrap; }
.cbtn:hover { background: var(--ac-bg); color: var(--ac-dk); }
.x { width: 36px; height: 36px; border-radius: 50%; color: var(--tx3); font-size: 22px; }
.x:hover { background: var(--busy-bg); color: var(--busy); }
.top { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; padding-right: 128px; }
.place { font-size: 15px; font-weight: 800; overflow-wrap: anywhere; }
.place:hover { color: var(--ac-dk); }
.bdg.QUIET { background: var(--calm-bg); color: var(--calm); }
.bdg.NORMAL { background: var(--mid-bg); color: var(--mid); }
.bdg.CROWDED { background: var(--busy-bg); color: var(--busy); }
.rate { display: flex; align-items: center; gap: 7px; margin: 10px 0 6px; flex-wrap: wrap; }
.score { font-size: 12.5px; font-weight: 800; }
.content { font-size: 12.5px; color: var(--tx2); line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.photos { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.photos img { display: block; width: 72px; height: 72px; object-fit: cover; border-radius: 10px; }
.dates { margin-top: 10px; }
.more-btn { width: 100%; margin-top: 12px; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--ac); outline-offset: 3px; }
@media (max-width: 480px) {
  .corner { position: static; justify-content: flex-end; margin-bottom: 8px; }
  .top { padding-right: 0; }
}
</style>
