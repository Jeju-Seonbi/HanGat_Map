<script setup>
/** 마이페이지·지도 공통 후기 편집. 기존 사진은 URL을 유지하고 새 사진만 업로드한다. */
import { computed, ref, onBeforeUnmount } from 'vue'
import BaseModal from '../common/BaseModal.vue'
import ReviewApiService, { absUrl } from '@/services/map/ReviewApiService'
import { useReviewEditWindow, isReviewEditable } from '@/composables/useReviewEditWindow.js'
import { getBackendSessionVersion } from '@/api/backendClient.js'

const props = defineProps({ review: { type: Object, required: true } })
const emit = defineEmits(['close', 'saved'])
const rating = ref(props.review.rating ?? '')
const report = ref(props.review.congestionReport ?? '')
const content = ref(props.review.content ?? '')
const photos = ref((props.review.imageUrls ?? []).map(url => ({ url, preview: absUrl(url) })))
const busy = ref(false)
const sessionExpired = ref(false)
const error = ref('')
const { canEdit } = useReviewEditWindow()
const editable = computed(() => canEdit(props.review))
const canSave = computed(() => editable.value && !busy.value && !sessionExpired.value && (!!rating.value || !!report.value))
const epoch = getBackendSessionVersion()
const previousFocus = typeof document === 'undefined' ? null : document.activeElement
let alive = true
const current = () => alive && epoch === getBackendSessionVersion()

function discard(photo) {
  if (photo.file) URL.revokeObjectURL(photo.preview)
}
onBeforeUnmount(() => {
  alive = false
  photos.value.forEach(discard)
  if (previousFocus?.isConnected) previousFocus.focus()
})
function removePhoto(index) {
  discard(photos.value[index])
  photos.value.splice(index, 1)
}
function choosePhotos(event) {
  error.value = ''
  const files = [...event.target.files]
  for (const file of files) {
    if (photos.value.length >= 5) { error.value = '사진은 최대 5장까지 첨부할 수 있어요.'; break }
    const validType = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      || (!file.type && /\.(jpe?g|png|webp)$/i.test(file.name))
    if (!validType || file.size > 5 * 1024 * 1024) {
      error.value = '사진은 JPG·PNG·WebP, 한 장당 5MB 이하로 선택해 주세요.'
      continue
    }
    photos.value.push({ file, url: null, preview: URL.createObjectURL(file) })
  }
  event.target.value = ''
}
function trapFocus(event) {
  if (event.key !== 'Tab') return
  const nodes = [...event.currentTarget.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)')]
  const first = nodes[0], last = nodes.at(-1)
  if (event.shiftKey && (document.activeElement === first || !nodes.includes(document.activeElement))) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && (document.activeElement === last || !nodes.includes(document.activeElement))) { event.preventDefault(); first?.focus() }
}
async function save() {
  if (!canSave.value || !isReviewEditable(props.review)) return
  if (!current()) { sessionExpired.value = true; error.value = '로그인 상태가 변경됐어요. 창을 닫고 다시 로그인해 주세요.'; return }
  busy.value = true
  error.value = ''
  try {
    const added = photos.value.filter(photo => !photo.url)
    if (added.length) {
      const urls = await ReviewApiService.uploadPhotos(added.map(photo => photo.file), { sessionBound: true })
      if (!current()) { if (alive) emit('close'); return }
      // 저장 요청만 실패하면 다음 시도에서 이미 업로드한 사진을 재사용한다.
      added.forEach((photo, index) => { photo.url = urls[index] })
    }
    if (!isReviewEditable(props.review)) {
      error.value = '사진 전송 중 수정 기한이 지났어요. 더 이상 저장할 수 없어요.'
      return
    }
    const result = await ReviewApiService.update(props.review.id ?? props.review.reviewId, {
      rating: rating.value === '' ? null : Number(rating.value),
      congestionReport: report.value || null,
      content: content.value || null,
      imageUrls: photos.value.map(photo => photo.url)
    })
    if (current()) emit('saved', result)
  } catch (e) {
    if (alive) {
      sessionExpired.value = !current() || e?.code === 'SESSION_CHANGED'
      error.value = sessionExpired.value ? '로그인 상태가 변경됐어요. 창을 닫고 다시 로그인해 주세요.'
        : (e?.message || '리뷰를 수정하지 못했어요. 다시 시도해 주세요.')
    }
  } finally {
    if (alive) busy.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <BaseModal title="리뷰 수정" labelled-by="review-edit-title" @close="!busy && emit('close')" @keydown="trapFocus">
      <form class="review-edit" @submit.prevent="save">
        <p class="hint">작성 후 7일 이내에 수정할 수 있어요. 수정해도 기한은 늘어나지 않아요.</p>
        <p v-if="!editable" class="error" role="status">수정 기한이 지났어요. 리뷰는 삭제만 할 수 있어요.</p>
        <fieldset :disabled="busy || !editable">
          <div class="choices">
            <label>별점
              <select v-model="rating"><option value="">선택 안 함</option><option v-for="n in 5" :key="n" :value="n">{{ n }}점</option></select>
            </label>
            <label>방문 당시 혼잡도
              <select v-model="report"><option value="">선택 안 함</option><option value="QUIET">한산했어요</option><option value="NORMAL">보통이었어요</option><option value="CROWDED">혼잡했어요</option></select>
            </label>
          </div>
          <label>한 줄 후기 <span class="hint">{{ content.length }}/60</span>
            <textarea v-model="content" maxlength="60" rows="3" placeholder="방문 경험을 남겨주세요 (선택)"></textarea>
          </label>
          <div class="photos">
            <div v-for="(photo, index) in photos" :key="photo.preview" class="photo">
              <img :src="photo.preview" :alt="`첨부 사진 ${index + 1}`">
              <button type="button" :aria-label="`사진 ${index + 1} 제거`" @click="removePhoto(index)">×</button>
            </div>
          </div>
          <label v-if="photos.length < 5">사진 추가 <span class="hint">{{ photos.length }}/5 · 한 장당 최대 5MB</span>
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="choosePhotos">
          </label>
        </fieldset>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div class="actions">
          <button type="button" class="cancel" :disabled="busy" @click="emit('close')">취소</button>
          <button type="submit" class="save" :disabled="!canSave">{{ busy ? '저장 중…' : '수정 저장' }}</button>
        </div>
      </form>
    </BaseModal>
  </Teleport>
</template>

<style scoped>
.review-edit { color: var(--tx); max-height: 70dvh; overflow-y: auto; }
.hint { color: var(--tx3); font-size: 12px; line-height: 1.65; font-weight: 400; }
fieldset { border: 0; padding: 0; margin: 18px 0; min-width: 0; }
label { display: block; font-size: 13px; font-weight: 700; margin-bottom: 16px; }
.choices { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
select, textarea, input { display: block; width: 100%; box-sizing: border-box; margin-top: 7px; padding: 10px; border-radius: 10px; border: 1px solid var(--line); background: var(--surf2); color: var(--tx); font: inherit; }
textarea { resize: vertical; }
input { font-size: 12px; }
.photos { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.photo { position: relative; }
.photo img { display: block; width: 72px; height: 72px; object-fit: cover; border-radius: 10px; }
.photo button { position: absolute; right: -5px; top: -5px; width: 28px; height: 28px; border-radius: 50%; background: var(--surf); color: var(--tx); border: 1px solid var(--line); }
.error { color: var(--busy); font-size: 13px; line-height: 1.6; }
.actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 8px; }
.actions button { padding: 12px 20px; border-radius: var(--rp); font-size: 13px; font-weight: 700; }
.cancel { background: var(--surf2); color: var(--tx2); }
.save { background: var(--ac); color: var(--on-ac); }
button:disabled { opacity: .5; cursor: not-allowed; }
button:focus-visible, input:focus-visible, select:focus-visible, textarea:focus-visible { outline: 2px solid var(--ac); outline-offset: 2px; }
</style>
