<script setup>
/**
 * 테마 페이지 (/themes) - 구석구석 테마 페이지 구조를 우리 데이터로.
 * 2커밋(2026-09-17): 세부분류 전부를 묶음(자연·문화와 시설·역사·체험·레저·축제·캠핑·먹고 마시기·머물기·한갓지도가 고른)별
 * 타일로 그린다. 곳수는 장소 목록 API 에서 세고 예보 유무는 보지 않는다. 타일을 눌렀을 때의 소개·장소 목록은 다음 커밋.
 */
import { onMounted, ref } from 'vue'
import MapPlaceService from '../../services/map/MapPlaceService'
import { buildThemes } from '../../config/themes.js'

const groups = ref([])
const loading = ref(true)
const failed = ref([])   // 못 받은 레이어 이름들 - 그 묶음만 비어 보이는 이유를 말해 준다

onMounted(async () => {
  // 관광지·식당·착한가격·숙소 네 레이어만 - 카페·편의점·마트(소상공인 상가 5천여 곳)는 소개글·사진이 없어 테마에 안 넣는다
  const keys = ['spot', 'dine', 'food', 'stay']
  const results = await Promise.allSettled(keys.map(k => MapPlaceService.getLayer(k)))
  const layers = {}
  results.forEach((r, i) => {
    if (r.status === 'fulfilled' && r.value) layers[keys[i]] = r.value
    else failed.value.push({ spot: '관광지', dine: '식당', food: '착한가격', stay: '숙소' }[keys[i]])
  })
  groups.value = buildThemes(layers)
  loading.value = false
})
</script>

<template>
  <div class="theme-page">
    <header class="theme-head">
      <span class="pill">테마로 고르기</span>
      <h1>어떤 제주를 찾고 있나요?</h1>
      <p>오름부터 야영장까지, 관광공사가 나눈 종류 그대로 모았어요. 종류를 고르면 그 안의 장소를 하나씩 소개합니다.</p>
    </header>

    <p v-if="loading" class="note">장소 목록을 불러오고 있어요…</p>
    <p v-else-if="failed.length" class="note warn">{{ failed.join('·') }} 목록을 불러오지 못했어요 · 새로고침해 주세요</p>

    <section v-for="g in groups" :key="g.key" class="group" :aria-labelledby="`theme-group-${g.key}`">
      <div class="group-head">
        <span class="group-emoji" :style="{ background: g.tint }" aria-hidden="true">{{ g.emoji }}</span>
        <h2 :id="`theme-group-${g.key}`">{{ g.title }}<small>{{ g.total.toLocaleString() }}곳 · {{ g.tiles.length }}종류</small></h2>
        <p>{{ g.desc }}</p>
      </div>
      <ul class="tiles">
        <li v-for="t in g.tiles" :key="t.key">
          <button type="button" class="tile" :title="t.raw !== t.title ? `관광공사 분류명: ${t.raw}` : undefined">
            <b>{{ t.title }}</b>
            <small>{{ t.count.toLocaleString() }}곳</small>
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.theme-page{max-width:1100px;margin:0 auto;padding:28px 20px 60px}
.theme-head{margin-bottom:26px;padding:26px 24px;border-radius:20px;
  background:linear-gradient(135deg,var(--ac-bg) 0%,var(--surf) 70%);border:1px solid var(--line)}
.pill{display:inline-block;padding:4px 11px;border-radius:999px;background:var(--ac-bg);color:var(--ac-dk);font-size:12px;font-weight:700}
.theme-head h1{font-size:26px;font-weight:800;letter-spacing:-.03em;margin:10px 0 6px}
.theme-head p,.note{color:var(--tx2);font-size:14px;margin:0}
.note{margin-bottom:18px}
.note.warn{color:var(--busy)}
.group{margin-bottom:30px}
.group-head{display:grid;grid-template-columns:auto 1fr;column-gap:12px;align-items:center;margin-bottom:12px}
.group-emoji{grid-row:1/3;width:46px;height:46px;border-radius:50%;display:flex;align-items:center;justify-content:center;
  font-size:22px;line-height:1;box-shadow:inset 0 0 0 1px rgba(0,0,0,.04)}
.group-head h2{font-size:18px;font-weight:800;letter-spacing:-.02em;margin:0;display:flex;align-items:baseline;gap:8px}
.group-head h2 small{font-size:12px;font-weight:600;color:var(--tx3)}
.group-head p{margin:0;font-size:12.5px;color:var(--tx3)}
.tiles{list-style:none;padding:0;margin:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px}
.tile{width:100%;display:flex;align-items:baseline;justify-content:space-between;gap:8px;padding:11px 13px;
  background:var(--surf);border:1px solid var(--line);border-radius:12px;text-align:left;cursor:pointer;
  transition:transform .12s,box-shadow .12s,border-color .12s}
.tile:hover{transform:translateY(-1px);box-shadow:var(--sh);border-color:var(--ac)}
.tile:focus-visible{outline:2px solid var(--ac);outline-offset:2px}
.tile b{font-size:14px;font-weight:700;letter-spacing:-.02em;color:var(--tx);word-break:keep-all}
.tile small{font-size:11.5px;color:var(--tx3);white-space:nowrap}
@media (max-width:640px){
  .theme-page{padding:20px 16px 48px}.theme-head{padding:20px 18px}.theme-head h1{font-size:22px}.group-emoji{width:40px;height:40px;font-size:20px}
  .tiles{grid-template-columns:repeat(2,1fr);gap:7px}.tile{padding:10px 11px}
}
</style>
