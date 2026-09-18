<script setup lang="ts">
/**
 * 메인 날씨 띠 - 회색 상자 7개 대신 한 줄(7일 칸). 제목·출처·오늘 요약은 부모의 구간 제목이 맡는다(2026-09-18 사용자 요청: 다른 구간과 같은 제목 모양).
 * 아이콘은 지도가 쓰는 선 아이콘(weatherIconSvg)으로 통일한다(이모지 금지 - 2026-09-18 메인 시안).
 * 값은 WeatherService 가 준 그대로만 보여준다. 없으면 '-'.
 */
import type { DailyWeather } from '../../services/WeatherService'
import { weatherIconSvg } from '../../utils/crowd.js'

defineProps<{ days: DailyWeather[] }>()

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
</script>

<template>
  <div class="wx" role="group" aria-label="제주 일주일 날씨">
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
.wx{padding:16px 8px;border:1px solid var(--border);border-radius:16px;background:var(--surface)}
.wx-days{display:grid;grid-template-columns:repeat(7,minmax(0,1fr))}
.wx-day{display:flex;flex-direction:column;align-items:center;gap:3px;text-align:center}
.wx-day small{font-size:11.5px;color:var(--sub);font-weight:600;white-space:nowrap}
.wx-day strong{font-size:17px;font-weight:800;letter-spacing:-.01em}
.wx-ic{line-height:0}
@media (max-width:767px){
  .wx{padding:12px}
  .wx-days{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;padding-bottom:2px}
  .wx-days::-webkit-scrollbar{display:none}
  .wx-day{flex:0 0 76px}
}
</style>
