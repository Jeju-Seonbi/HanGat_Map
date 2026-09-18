<script setup lang="ts">
/**
 * 메인 날씨 띠 - 회색 상자 7개 대신 한 줄. 왼쪽에 오늘 요약, 오른쪽에 7일.
 * 아이콘은 지도가 쓰는 선 아이콘(weatherIconSvg)으로 통일한다(이모지 금지 - 2026-09-18 메인 시안).
 * 값은 WeatherService 가 준 그대로만 보여준다. 없으면 '-'.
 */
import { computed } from 'vue'
import type { DailyWeather } from '../../services/WeatherService'
import { weatherIconSvg } from '../../utils/crowd.js'

const props = defineProps<{ days: DailyWeather[]; live: boolean }>()

/** 하늘 문구 → 아이콘 종류. 중기예보는 "흐리고 비" 같은 자유 문장이라 포함 여부로 본다 */
function kindOf (d: DailyWeather): string {
  const s = d.sky ?? ''
  if (s.includes('눈')) return '눈'
  if (s.includes('비') || s.includes('소나기') || (d.rainProb ?? 0) >= 60) return '비'
  if (s.includes('맑음')) return '맑음'
  return '구름'
}
const icon = (d: DailyWeather) => weatherIconSvg(kindOf(d), 24)
const temp = (d: DailyWeather) => (d.temperature === null ? '-' : `${d.temperature}°`)
const note = (d: DailyWeather) => [d.sky, d.rainProb ? `${d.rainProb}%` : null].filter(Boolean).join(' ')
const today = computed(() => props.days[0] ?? null)
</script>

<template>
  <div class="wx" role="group" aria-label="제주 일주일 날씨">
    <div class="wx-head">
      <b>제주 일주일 날씨</b>
      <strong v-if="today">오늘 {{ temp(today) }} {{ today.sky ?? '' }}</strong>
      <small>{{ live ? '기상청 단기·중기예보' : '시연용 데이터 · 백엔드 연결 대기' }}</small>
    </div>
    <div class="wx-days">
      <div v-for="d in days" :key="d.day" class="wx-day">
        <small>{{ d.day }}</small>
        <!-- 우리 코드가 만든 SVG 문자열 - 사용자 입력이 아니다 -->
        <span class="wx-ic" v-html="icon(d)" />
        <strong>{{ temp(d) }}</strong>
        <small>{{ note(d) || '-' }}</small>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wx{display:flex;align-items:center;gap:6px;padding:14px 8px;border:1px solid var(--border);border-radius:16px;background:var(--surface)}
.wx-head{display:flex;flex-direction:column;gap:4px;padding:0 16px;border-right:1px solid var(--border);min-width:170px}
.wx-head b{font-size:12px;font-weight:700;color:var(--sub)}
.wx-head strong{font-size:15px;font-weight:800;color:var(--text);letter-spacing:-.01em}
.wx-head small{font-size:11.5px;color:var(--sub)}
.wx-days{flex:1;display:grid;grid-template-columns:repeat(7,minmax(0,1fr))}
.wx-day{display:flex;flex-direction:column;align-items:center;gap:3px;text-align:center}
.wx-day small{font-size:11.5px;color:var(--sub);font-weight:600;white-space:nowrap}
.wx-day strong{font-size:17px;font-weight:800;letter-spacing:-.01em}
.wx-ic{line-height:0}
@media (max-width:767px){
  .wx{flex-direction:column;align-items:stretch;gap:10px;padding:12px}
  .wx-head{flex-direction:row;align-items:baseline;gap:10px;padding:0 4px 10px;border-right:0;border-bottom:1px solid var(--border);min-width:0;flex-wrap:wrap}
  .wx-days{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;padding-bottom:2px}
  .wx-days::-webkit-scrollbar{display:none}
  .wx-day{flex:0 0 76px}
}
</style>
