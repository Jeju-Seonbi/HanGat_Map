<script setup>
/* MAP_008 후기 — 별점 + 혼잡 제보 + 한 줄 + 사진(최대 6장). 열람은 누구나, 작성은 회원만 */
import { ref, computed, watch } from 'vue'
import StarIcon from './StarIcon.vue'
import { state, reviewsOf, addReview, toast } from '@/stores/mapStore'
import { CROWD_KO } from '@/utils/crowd'
import { shrink } from '@/utils/geo'

const props = defineProps({ place: { type: Object, required: true } })
const emit = defineEmits(['open-photo'])

const star = ref(0)
const crowdVote = ref('')
const text = ref('')
const photos = ref([])
const shown = ref(6)
const fileInput = ref(null)

const list = computed(() => reviewsOf(props.place.n))
const rated = computed(() => list.value.filter(r => r.r > 0))
const avg = computed(() => rated.value.length
  ? rated.value.reduce((a, b) => a + b.r, 0) / rated.value.length : 0)
const counts = computed(() => {
  const c = { calm: 0, mid: 0, busy: 0 }
  list.value.forEach(r => { if (r.c) c[r.c]++ })
  return c
})
const total = computed(() => (counts.value.calm + counts.value.mid + counts.value.busy) || 1)
const canSubmit = computed(() => !!star.value || !!crowdVote.value)

function reset() { star.value = 0; crowdVote.value = ''; text.value = ''; photos.value = []; shown.value = 6 }
watch(() => props.place.n, reset)

function submit() {
  if (!canSubmit.value) return
  if (addReview(props.place.n, { star: star.value, crowdVote: crowdVote.value, text: text.value.trim(), photos: photos.value })) reset()
}

async function onFiles(e) {
  const remain = 6 - photos.value.length
  if (remain <= 0) { toast('사진은 최대 6장까지예요'); e.target.value = ''; return }
  for (const f of [...e.target.files].slice(0, remain)) photos.value.push(await shrink(f, 640))
  if (e.target.files.length > remain) toast('사진은 최대 6장까지예요')
  e.target.value = ''
}
</script>

<template>
  <div>
    <div class="rv-t">방문 후기를 남겨주세요</div>
    <div class="rv-star">
      <button v-for="n in 5" :key="n" :aria-label="`${n}점`" @click="star = n">
        <StarIcon :filled="n <= star" :size="26" />
      </button>
    </div>

    <div class="rv-c">
      <button v-for="c in ['calm', 'mid', 'busy']" :key="c" :class="[c, { on: crowdVote === c }]"
        @click="crowdVote = crowdVote === c ? '' : c">{{ CROWD_KO[c] }}했어요</button>
    </div>

    <div class="rv-phrow">
      <span v-for="(p, i) in photos" :key="i" class="rv-pht">
        <img :src="p" alt="첨부한 사진">
        <button class="del" aria-label="사진 삭제" @click="photos.splice(i, 1)">×</button>
      </span>
      <button v-if="photos.length < 6" class="rv-phadd" @click="fileInput.click()">
        사진<br>{{ photos.length }}/6
      </button>
      <input ref="fileInput" type="file" accept="image/*" multiple style="display:none" @change="onFiles">
    </div>

    <div class="rv-in">
      <input v-model="text" placeholder="한 줄 남기기 (선택)" maxlength="60" @keydown.enter="submit">
      <button :disabled="!canSubmit" @click="submit">등록</button>
    </div>

    <template v-if="list.length">
      <div class="rv-sum">
        <StarIcon filled :size="15" />
        <!-- 별점 없이 혼잡 제보만 있으면 평균을 '-'로 띄우지 않는다 -->
        <span class="avg">{{ avg ? avg.toFixed(1) : '-' }}</span>
        <span class="cnt">후기 {{ list.length }}</span>
      </div>
      <div class="rv-bar">
        <div v-for="c in ['calm', 'mid', 'busy']" :key="c" class="rv-b">
          <span :style="{ color: `var(--${c})` }">{{ CROWD_KO[c] }}</span>
          <span class="bg">
            <i :style="{ width: Math.round(counts[c] / total * 100) + '%', background: `var(--${c})` }"></i>
          </span>
          <b>{{ counts[c] }}</b>
        </div>
      </div>

      <div v-for="(r, ri) in list.slice(0, shown)" :key="ri" class="rv-i">
        <div class="rv-h">
          <span class="rv-av">{{ r.u.slice(0, 1) }}</span>
          <span class="rv-nm">{{ r.u }}</span>
          <span class="rv-dt">{{ r.d }} 방문</span>
        </div>
        <div class="rv-mt">
          <template v-if="r.r"><StarIcon v-for="n in 5" :key="n" :filled="n <= r.r" :size="12" /></template>
          <span v-if="r.c" class="bdg"
            :style="{ background: `var(--${r.c}-bg)`, color: `var(--${r.c})`, fontSize: '10px', padding: '2px 8px' }">
            {{ CROWD_KO[r.c] }}
          </span>
        </div>
        <div v-if="r.t" class="rv-tx">{{ r.t }}</div>
        <div v-if="r.ph && r.ph.length" class="rv-imgs">
          <img v-for="(p, pi) in r.ph" :key="pi" :src="p" :alt="`후기 사진 ${pi + 1}`"
            title="클릭하면 크게 보기" @click="emit('open-photo', { photos: r.ph, index: pi })">
        </div>
      </div>

      <!-- MAP_008: 6건씩 노출 + 더보기 -->
      <button v-if="list.length > shown" class="rvchip"
        style="justify-content:center;margin-top:8px" @click="shown += 6">
        <span class="ct">후기 {{ list.length - shown }}개 더보기</span>
      </button>
    </template>

    <div v-else class="rv-none">아직 후기가 없어요.<br>첫 방문 후기를 남겨보세요.</div>
  </div>
</template>
