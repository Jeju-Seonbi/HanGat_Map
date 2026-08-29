<script setup>
/* MAP_007 장소 상세 — 상세 화면과 후기 화면을 한 패널 안에서 전환한다 */
import { ref, computed, watch } from 'vue'
import StarIcon from './StarIcon.vue'
import ReviewSection from './ReviewSection.vue'
import { state, reviewsOf, toggleFav, isFav, toast, savePlaceImgs } from '@/stores/mapStore'

import { crowd, tier, tierKo, rank30, bestDay, CROWD_KO } from '@/utils/crowd'
import { at, fmtK } from '@/utils/date'
import { wxOf, wxIcon } from '@/utils/weather'
import { dist, won, shrink } from '@/utils/geo'
import MapPlaceService from '@/services/map/MapPlaceService'

const props = defineProps({ place: { type: Object, required: true } })
const emit = defineEmits(['close', 'open-place', 'open-photo'])

const view = ref('info')
const hint = ref('')
const imgInput = ref(null)
/** 휴무일·입장료는 상세 API에만 있다. 못 받아오면 null - 해당 줄만 안 보인다 */
const detail = ref(null)

const s = computed(() => props.place)
const c = computed(() => crowd(s.value, state.di))
const t = computed(() => tier(c.value))

/* 리드 문장은 그 장소의 30일 예보 안에서의 순위만 말한다 — 다른 장소와 비교하지 않는다 */
const rankText = computed(() => {
  const r = rank30(s.value, state.di)
  return r <= 8 ? '한산한 편' : r >= 23 ? '혼잡한 편' : null
})
const best = computed(() => bestDay(s.value, 0, 30))
const tipText = computed(() => {
  if (c.value == null) return ''
  if (best.value.k === state.di) return null
  const g = c.value - best.value.c
  return g >= 25 ? '훨씬 한산한' : g >= 12 ? '꽤 한산한' : '조금 더 한산한'
})

/* 선택일을 가운데 두고 7일 — 범위를 넘지 않게 시작점을 당긴다 */
const week = computed(() => {
  const st = Math.max(0, Math.min(state.di - 1, 23))
  return Array.from({ length: 7 }, (_, j) => {
    const k = st + j, d = at(k), w = wxOf(k), cc = crowd(s.value, k)
    return { k, d, w, cc, t: tier(cc), ko: tierKo(cc), label: `${d.getMonth() + 1}/${d.getDate()} ${'일월화수목금토'[d.getDay()]}` }
  })
})

const photos = computed(() => state.placeImgs[s.value.n] || [])
const reviews = computed(() => reviewsOf(s.value.n))
const rated = computed(() => reviews.value.filter(r => r.r > 0))
const avg = computed(() => rated.value.length
  ? rated.value.reduce((a, b) => a + b.r, 0) / rated.value.length : 0)

watch(() => props.place.n, loadDetail, { immediate: true })

async function loadDetail() {
  view.value = 'info'
  hint.value = ''
  detail.value = null
  // 목업 모드는 id 가 없다
  if (s.value.id == null) return
  const id = s.value.id
  const d = await MapPlaceService.getDetail(id)
  // 응답이 늦게 와도 그새 다른 장소를 열었으면 버린다
  if (s.value.id === id) detail.value = d
}

function jumpToBest() {
  if (best.value.k !== state.di) state.di = best.value.k
}

/** 한산한 날 찾기 — 앞으로 2주 안에서 */
function findCalmDay() {
  if (c.value == null) {
    hint.value = '<span style="color:var(--tx3)">혼잡 예보가 제공되지 않는 장소예요.</span>'
    return
  }
  const b = bestDay(s.value, state.di, 14)
  const g = c.value - b.c, word = g >= 25 ? '훨씬' : g >= 12 ? '꽤' : '조금'
  hint.value = b.k === state.di
    ? '<span style="color:var(--calm)">앞으로 2주 중엔 오늘이 가장 한산해요.</span>'
    : `<span style="color:var(--calm)"><b>${fmtK(at(b.k))}</b>로 옮기면 ${word} 한산해져요.</span>`
  if (b.k !== state.di) setTimeout(() => { state.di = b.k }, 1000)
}

const nearby = ref([])
function findNearby() {
  nearby.value = state.layers.spot.filter(x => x.n !== s.value.n)
    .map(x => ({ s: x, c: crowd(x, state.di), d: dist(s.value, x) }))
    .filter(o => o.d < 12 && o.c != null && o.c < 40)
    .sort((a, b) => a.c - b.c).slice(0, 3)
  hint.value = nearby.value.length ? '' : '<span style="color:var(--tx3)">반경 12km 안에는 한산한 대안이 없어요.</span>'
}

function copyAddr() {
  const done = () => toast('주소를 복사했어요')
  if (navigator.clipboard?.writeText) navigator.clipboard.writeText(s.value.addr).then(done).catch(done)
  else done()
}

async function onImgFiles(e) {
  const list = state.placeImgs[s.value.n] || (state.placeImgs[s.value.n] = [])
  for (const f of [...e.target.files].slice(0, 5 - list.length)) list.push(await shrink(f, 640))
  savePlaceImgs()
  e.target.value = ''
}
function delImg(i) {
  ;(state.placeImgs[s.value.n] || []).splice(i, 1)
  savePlaceImgs()
}
</script>

<template>
  <div class="fl pop on">
    <div v-show="view === 'info'">
      <div class="poh">
        <div style="flex:1">
          <h4>{{ s.n }}</h4>
          <div class="sub">{{ s.c }} · {{ s.r }}</div>
        </div>
        <!-- MAP_009 찜 -->
        <button class="fav" :class="{ on: isFav(s.n) }" aria-label="찜하기" @click="toggleFav(s.n)">♥</button>
        <button class="share" @click="toast('현재 화면 주소를 복사해 공유하세요 (URL에 상태 저장됨)')">친구에게 공유</button>
        <button class="pox" @click="emit('close')">×</button>
      </div>

      <button class="rvchip" @click="view = 'rv'">
        <template v-if="reviews.length">
          <template v-if="rated.length">
            <StarIcon filled :size="15" /><span class="sc">{{ avg.toFixed(1) }}</span>
          </template>
          <span class="ct">후기 {{ reviews.length }}</span>
        </template>
        <template v-else>
          <StarIcon :size="15" />
          <span class="ct" style="font-weight:700;color:var(--tx2)">첫 후기를 남겨보세요</span>
        </template>
        <span class="ar">›</span>
      </button>

      <!-- 장소 사진: 실서비스=KTO firstimage, 프로토타입=데모 업로드 -->
      <div class="pimg">
        <img v-for="(p, i) in photos" :key="i" :src="p" :alt="`${s.n} 사진`"
          title="더블클릭하면 삭제 (데모)" @dblclick="delImg(i)">
        <button v-if="photos.length < 5" class="add" @click="imgInput.click()">
          <span style="font-size:17px">＋</span>사진 추가<span style="font-size:9px;opacity:.7">데모</span>
        </button>
        <input ref="imgInput" type="file" accept="image/*" multiple style="display:none" @change="onImgFiles">
      </div>

      <div class="lead">
        <!-- MAP_004 예외: 예보 미제공 — 없는 데이터는 추측하지 않는다 -->
        <template v-if="c == null">
          <span class="bdg" style="background:var(--none);color:#fff">정보 없음</span>
          &nbsp;이 장소는 혼잡 예보가 제공되지 않아요.
        </template>
        <template v-else>
          <span class="bdg" :style="{ background: `var(--${t})`, color: '#fff' }">{{ tierKo(c) }}</span>
          &nbsp;{{ fmtK(at(state.di)) }} · 이곳의 30일 예보 중에선
          <template v-if="rankText"><b>{{ rankText }}</b>이에요.</template>
          <template v-else>중간쯤이에요.</template>
        </template>
      </div>

      <div v-if="c != null" class="tipbox" @click="jumpToBest">
        <template v-if="!tipText">✓ 30일 중 <b>오늘이 가장 한산</b>해요.</template>
        <template v-else>🕐 <b>{{ fmtK(at(best.k)) }}</b>로 가면 <b>{{ tipText }}</b> 날이에요. 눌러서 옮겨보세요.</template>
      </div>

      <div class="spark" style="margin-bottom:6px"><div class="st"><i></i>날짜별 날씨와 혼잡</div></div>
      <div class="wxrow">
        <div v-for="w in week" :key="w.k" class="wxc" :class="{ on: w.k === state.di }"
          @click="state.di = w.k">
          <div class="wd">{{ w.label }}</div>
          <div class="wi" v-html="wxIcon(w.w.k, 27)"></div>
          <div class="wt">{{ w.w.t }}°</div>
          <div class="wc" :style="{ background: `var(--${w.t})` }" :title="w.ko"></div>
        </div>
      </div>

      <div class="amen">
        <span class="am">입장 {{ s.fee ? won(s.fee) + '원' : '무료' }}</span>
        <span class="am" :class="{ no: !s.park }">주차</span>
        <span class="am" :class="{ no: !s.wc }">화장실</span>
        <span v-if="s.in" class="am">실내</span>
      </div>

      <!-- 주소(복사) · 운영시간(있을 때만 — 상시 개방은 줄 자체를 표시하지 않음) · 전화 -->
      <div class="pinfo">
        <div class="pi">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11Z"/>
            <circle cx="12" cy="10" r="2.6"/></svg>
          <span class="tx">{{ s.addr }}</span>
          <button class="cp" @click="copyAddr">복사</button>
        </div>
        <div v-if="s.hours" class="pi">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.2 1.9"/></svg>
          <span class="tx">{{ s.hours }}<span class="sub2">운영시간</span></span>
        </div>
        <div v-if="s.tel" class="pi">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linejoin="round"><path d="M5 4h4l1.6 4-2.2 1.6a13 13 0 0 0 6 6L16 13.4l4 1.6v4a2 2 0 0 1-2.2 2A17 17 0 0 1 3 6.2 2 2 0 0 1 5 4Z"/></svg>
          <span class="tx"><a :href="`tel:${s.tel}`">{{ s.tel }}</a></span>
        </div>
      </div>

      <div class="acts">
        <button class="p1" @click="findCalmDay">한산한 날 찾기</button>
        <button @click="findNearby">근처 대안 보기</button>
      </div>
      <div v-if="hint || nearby.length" class="hint" style="display:block">
        <span v-if="hint" v-html="hint"></span>
        <template v-if="nearby.length">
          근처에 한산한 곳이 있어요 ·
          <template v-for="(o, i) in nearby" :key="o.s.n">
            <a style="color:var(--calm);cursor:pointer;font-weight:700"
              @click="emit('open-place', o.s.n)">{{ o.s.n }}</a><template v-if="i < nearby.length - 1">, </template>
          </template>
        </template>
      </div>

      <!-- 상세 하단 후기 미리보기 (최근 3개) -->
      <div class="rvprev">
        <div class="rvp-h"><i></i>방문 후기
          <span v-if="reviews.length" class="rvp-avg">
            <template v-if="rated.length"><StarIcon filled :size="13" /> {{ avg.toFixed(1) }} · </template>
            {{ reviews.length }}개
          </span>
        </div>
        <template v-if="reviews.length">
          <div v-for="(r, ri) in reviews.slice(0, 3)" :key="ri" class="rv-i">
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
          <button class="rvp-more" @click="view = 'rv'">
            {{ reviews.length > 3 ? `후기 ${reviews.length}개 모두 보기 ›` : '후기 남기기 ›' }}
          </button>
        </template>
        <template v-else>
          <div class="rv-none">아직 후기가 없어요.</div>
          <button class="rvp-more" @click="view = 'rv'">첫 후기 남기기 ›</button>
        </template>
      </div>
    </div>

    <div v-show="view === 'rv'">
      <div class="rvhead">
        <button class="rvback" @click="view = 'info'">‹</button>
        <div style="flex:1"><h4>{{ s.n }}</h4><div class="sub">방문 후기</div></div>
        <button class="pox" @click="emit('close')">×</button>
      </div>
      <ReviewSection :place="s" @open-photo="p => emit('open-photo', p)" />
    </div>
  </div>
</template>
