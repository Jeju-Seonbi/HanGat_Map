<script setup>
/* MAP_006 코스 표시 — 생성은 AI 코스 페이지 담당, 지도는 결과 표시와 일차 전환만 맡는다 */
import { computed } from 'vue'
import { state } from '@/stores/mapStore'
import { at, fmt } from '@/utils/date'
import { tier } from '@/utils/crowd'
import { won } from '@/utils/geo'
import { wxOf, wxIcon } from '@/utils/weather'

const emit = defineEmits(['close', 'open-place'])
const course = computed(() => state.course)
const byDay = computed(() => {
  const g = {}
  course.value?.stops.forEach(s => (g[s.d] = g[s.d] || []).push(s))
  return g
})
const gap = computed(() => (course.value.pav == null ? null : course.value.pav - course.value.avg))
const lead = computed(() => {
  const g = gap.value
  if (g == null) {
    return course.value.source === 'saved' ? '저장한 코스예요' : 'AI가 혼잡·날씨를 보고 짠 코스예요'
  }
  return g >= 30 ? '훨씬 한산한 코스예요' : g >= 15 ? '꽤 한산한 코스예요'
    : g >= 5 ? '조금 더 한산해요' : '인기 코스와 비슷해요'
})
const moveText = computed(() => {
  const m = course.value.move
  return `${Math.floor(m / 60) ? Math.floor(m / 60) + '시간 ' : ''}${m % 60}분`
})
const spentPct = computed(() => Math.min(100, course.value.spent / course.value.bud * 100))
const rest = computed(() => course.value.bud - course.value.spent)

const dayWeather = d => wxOf(state.di + +d - 1)

/* 정류지 점: 식당·카페·숙소는 지도 핀과 같은 업종 원+아이콘(.poi-marker.mk-*), 관광지는 혼잡 색 점.
   전엔 업종 장소도 회색 '예보 없음' 점이라 예보가 빠진 관광지처럼 보였다(2026-09-14 결정). 착한가격 식당은 지도처럼 분홍 */
const POI_CLASS = { FOOD: 'mk-dine', CAFE: 'mk-cafe', LODGING: 'mk-stay', CONVENIENCE: 'mk-cvs', MART: 'mk-mart' }
const poiClass = o => (o?.cat === 'FOOD' && o.good ? 'mk-food' : POI_CLASS[o?.cat] ?? null)

</script>

<template>
  <div v-if="course" class="fl panel" :class="{ push: !!state.sel }">
    <!-- 제목·닫기가 첫 줄, 일차 칩은 둘째 줄. 한 줄에 다 넣으면 3일차부터 폭이 모자라 제목이 한 글자씩 세로로 깨졌다(최종점검 #15) -->
    <div class="ph">
      <h3>추천 코스</h3>
      <button class="x" @click="emit('close')">×</button>
      <div class="days">
        <span class="chip" :class="{ on: state.courseDay === 'all' }"
          @click="state.courseDay = 'all'">전체</span>
        <span v-for="d in Object.keys(byDay)" :key="d" class="chip"
          :class="{ on: String(state.courseDay) === String(d) }"
          @click="state.courseDay = +d">{{ d }}일차</span>
      </div>
    </div>

    <div class="pb">
      <div class="stat">
        <div class="n" style="font-size:19px">{{ lead }}</div>
        <!-- 비교값(pav)이 없는 AI 코스는 비교 막대를 그리지 않는다 - 없는 수치를 만들지 않는다 -->
        <div v-if="course.pav != null" class="cmp">
          <div class="cmp-r"><span>이 코스</span>
            <div class="cbar"><i :style="{ width: course.avg + '%', background: 'var(--calm-st)' }"></i></div>
            <b>{{ course.avg }}</b>
          </div>
          <div class="cmp-r"><span>인기 코스</span>
            <div class="cbar"><i :style="{ width: course.pav + '%', background: 'var(--busy-st)' }"></i></div>
            <b>{{ course.pav }}</b>
          </div>
        </div>
        <div v-else-if="course.avg != null" class="cmp">
          <div class="cmp-r"><span>코스 평균 혼잡</span>
            <div class="cbar"><i :style="{ width: course.avg + '%', background: 'var(--calm-st)' }"></i></div>
            <b>{{ course.avg }}</b>
          </div>
        </div>
        <p v-if="course.avg != null">혼잡 정도 · 여행일 기준 · 낮을수록 한산해요</p>
        <p>총 이동 {{ moveText }}</p>
      </div>

      <template v-for="(stops, d) in byDay" :key="d">
        <div class="dayh">
          {{ fmt(at(state.di + +d - 1)) }} · {{ d }}일차 <i></i>
          <span v-if="dayWeather(d)" style="color:var(--tx3);font-weight:500">
            <span v-html="wxIcon(dayWeather(d).k, 15)"></span> {{ dayWeather(d).k }}
          </span>
        </div>
        <template v-for="(s, i) in stops" :key="s.t + s.d">
          <div v-if="i > 0 && s.mv" class="mv">↓ 차로 {{ s.mv }}분</div>
          <div class="stop" @click="emit('open-place', s.o ?? s.f.n)">
            <div class="tm">{{ s.t }}</div>
            <div>
              <!-- 혼잡·업종은 목록과 같은 핀 색으로 표시 (뱃지와 의미 중복 제거) -->
              <div class="hd">
                <span v-if="s.o && poiClass(s.o)" class="poi-marker cpin" :class="poiClass(s.o)"></span>
                <span v-else class="rpin" :class="s.o ? tier(s.c) : 'food'"></span>
                <span class="nm">{{ s.o ? s.o.n : s.f.n }}</span>
              </div>
              <div class="why">{{ s.o ? s.why : `${s.f.m} · ${s.why}` }}</div>
              <div v-if="s.cost" class="pr">{{ s.o ? '입장료' : '2인' }} {{ won(s.cost) }}원</div>
            </div>
          </div>
        </template>
      </template>
    </div>

    <div class="pf">
      <!-- 저장 코스 상세엔 비용 데이터가 없다 - bud=0이면 0원/0원 거짓 표시 대신 숨긴다 -->
      <template v-if="course.bud">
        <div class="bh">
          <span style="color:var(--tx2)">예상 경비</span>
          <b class="tnum">{{ won(course.spent) }} / {{ won(course.bud) }}</b>
        </div>
        <div class="bar">
          <i :style="{ width: spentPct + '%', background: rest < 0 ? 'var(--busy)' : 'var(--ac)' }"></i>
        </div>
        <div class="bn">
          <template v-if="rest >= 0">
            {{ won(rest) }}원 남아요 · 식비 ● 입장료 ● 실측 / 숙박·이동비 ○ 미포함
          </template>
          <template v-else>
            <span style="color:var(--busy)">{{ won(-rest) }}원 넘었어요</span> · 식사를 더 저렴한 곳으로 바꿔보세요
          </template>
        </div>
      </template>

    </div>
  </div>
</template>
