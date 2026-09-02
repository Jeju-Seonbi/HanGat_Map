<script setup>
/* MAP_002 검색 — 관광지(이름·종류·권역)와 착한가격업소(상호·메뉴·권역)를 부분 일치로 찾는다 */
import { ref, computed } from 'vue'
import { state } from '@/stores/mapStore'

import { crowd, tier } from '@/utils/crowd'
import { won } from '@/utils/geo'
import { mapBridge } from '@/composables/mapBridge'

const emit = defineEmits(['pick-spot'])

const q = ref('')
const open = ref(false)
const input = ref(null)
const EXAMPLES = ['오름', '해변', '전시', '국수']

const hits = computed(() => {
  const k = q.value.trim()
  if (!k) return []
  return [
    ...state.layers.spot.filter(s => s.n.includes(k) || s.c.includes(k) || s.r.includes(k))
      .map(s => ({ type: 'spot', o: s, c: crowd(s, state.di) })),
    ...state.layers.food.filter(f => f.n.includes(k) || (f.c ?? '').includes(k) || f.r.includes(k))
      .map(f => ({ type: 'food', o: f })),
  ].slice(0, 8)
})

const showPanel = computed(() => open.value && q.value.trim().length > 0)

function close() { q.value = ''; open.value = false }

function pickSpot(name) { close(); emit('pick-spot', name) }

function pickFood(f) {
  close()
  mapBridge.panTo(f.y, f.x)
  mapBridge.zoomTo(5)
}

function pickFirst() {
  const h = hits.value[0]
  if (!h) { input.value?.focus(); return }
  h.type === 'spot' ? pickSpot(h.o.n) : pickFood(h.o)
}

function useExample(t) { q.value = t; open.value = true; input.value?.focus() }

defineExpose({ close })
</script>

<template>
  <div class="srch">
    <input ref="input" v-model="q" placeholder="관광지·맛집 검색" autocomplete="off"
      @input="open = true" @focus="open = true"
      @keydown.esc="close" @keydown.enter="pickFirst">
    <button class="sb-x" :class="{ on: q }" aria-label="지우기" @click="close">×</button>
    <button class="sb-go" aria-label="검색" @click="pickFirst">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="2.3" stroke-linecap="round"><circle cx="10.4" cy="10.4" r="6.6" />
        <path d="M15.4 15.4L21 21" /></svg>
    </button>
  </div>

  <div class="sb-rs" :class="{ on: showPanel }">
    <template v-if="hits.length">
      <div v-for="h in hits" :key="h.type + h.o.n" class="sr"
        @click="h.type === 'spot' ? pickSpot(h.o.n) : pickFood(h.o)">
        <span class="rpin" :class="h.type === 'spot' ? tier(h.c) : 'food'"></span>
        <span class="info">
          <span class="rn">{{ h.o.n }}</span>
          <span class="rs">{{ h.type === 'spot' ? `${h.o.c} · ${h.o.r}` : `${h.o.m} · ${h.o.r}` }}</span>
        </span>
        <span v-if="h.type === 'food'" class="bdg"
          style="background:var(--pink-bg);color:var(--pink)">{{ won(h.o.p) }}원</span>
      </div>
    </template>

    <div v-else-if="showPanel" class="sb-none">
      <div class="ic">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          stroke-width="1.9" stroke-linecap="round"><circle cx="10.4" cy="10.4" r="6.6" />
          <path d="M15.4 15.4L21 21" /></svg>
      </div>
      <b>'{{ q.trim() }}'에 맞는 곳이 없어요</b>
      <p>장소 이름, 종류, 메뉴로 찾을 수 있어요</p>
      <div class="sb-eg">
        <button v-for="t in EXAMPLES" :key="t" @click="useExample(t)">{{ t }}</button>
      </div>
    </div>
  </div>
</template>
