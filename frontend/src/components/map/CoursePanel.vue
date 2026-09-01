<script setup>
/* MAP_006 코스 표시 — 생성은 AI 코스 페이지 담당, 지도는 결과 표시와 일차 전환만 맡는다 */
import { ref, computed, watch } from 'vue'
import { state, isCourseSaved, saveCourse, toast } from '@/stores/mapStore'
import { useAuthStore } from '@/stores/auth'
import { swapStop } from '@/utils/course'
import { at, fmt } from '@/utils/date'
import { tier } from '@/utils/crowd'
import { won } from '@/utils/geo'
import { wxOf, wxIcon } from '@/utils/weather'

const emit = defineEmits(['close', 'open-place'])
const auth = useAuthStore()

const naming = ref(false)
const draftTitle = ref('')
const nameInput = ref(null)

const course = computed(() => state.course)
const byDay = computed(() => {
  const g = {}
  course.value?.stops.forEach(s => (g[s.d] = g[s.d] || []).push(s))
  return g
})
const gap = computed(() => (course.value.pav == null ? null : course.value.pav - course.value.avg))
const lead = computed(() => {
  const g = gap.value
  if (g == null) return 'AI가 혼잡·날씨를 보고 짠 코스예요'
  return g >= 30 ? '훨씬 한산한 코스예요' : g >= 15 ? '꽤 한산한 코스예요'
    : g >= 5 ? '조금 더 한산해요' : '인기 코스와 비슷해요'
})
const moveText = computed(() => {
  const m = course.value.move
  return `${Math.floor(m / 60) ? Math.floor(m / 60) + '시간 ' : ''}${m % 60}분`
})
const spentPct = computed(() => Math.min(100, course.value.spent / course.value.bud * 100))
const rest = computed(() => course.value.bud - course.value.spent)
const saved = computed(() => isCourseSaved())

const dayWeather = d => wxOf(state.di + +d - 1)

function swap(time, day) {
  if (!swapStop(state.course, time, day, state.di)) return
  state.course = { ...state.course }
}

function startSave() {
  if (!auth.isLoggedIn) { toast('코스 저장은 로그인이 필요해요'); return }
  draftTitle.value = `${state.F.reg} ${course.value.days - 1}박${course.value.days}일`
  naming.value = true
  requestAnimationFrame(() => nameInput.value?.select())
}
function confirmSave() {
  const t = draftTitle.value.trim()
  if (!t) { toast('코스 이름을 입력해 주세요'); return }
  saveCourse(t)
  naming.value = false
}

watch(() => state.course, () => { naming.value = false })
</script>

<template>
  <div v-if="course" class="fl panel" :class="{ push: !!state.sel }">
    <div class="ph">
      <h3>추천 코스</h3>
      <div class="days">
        <span class="chip" :class="{ on: state.courseDay === 'all' }"
          @click="state.courseDay = 'all'">전체</span>
        <span v-for="d in Object.keys(byDay)" :key="d" class="chip"
          :class="{ on: String(state.courseDay) === String(d) }"
          @click="state.courseDay = +d">{{ d }}일차</span>
      </div>
      <button class="x" @click="emit('close')">×</button>
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
                <span class="rpin" :class="s.o ? tier(s.c) : 'food'"></span>
                <span class="nm">{{ s.o ? s.o.n : s.f.n }}</span>
                <button class="sw" @click.stop="swap(s.t, s.d)">다른 곳</button>
              </div>
              <div class="why">{{ s.o ? s.why : `${s.f.m} · ${s.why}` }}</div>
              <div v-if="s.cost" class="pr">{{ s.o ? '입장료' : '2인' }} {{ won(s.cost) }}원</div>
            </div>
          </div>
        </template>
      </template>
    </div>

    <div class="pf">
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

      <!-- MY_001: 코스 저장 (회원 전용) -->
      <div>
        <div v-if="naming" class="savebox">
          <input ref="nameInput" v-model="draftTitle" maxlength="60" placeholder="코스 이름"
            @keydown.enter="confirmSave" @keydown.esc="naming = false">
          <button @click="confirmSave">저장</button>
        </div>
        <button v-else-if="saved" class="save done">✓ 마이페이지에 저장됨</button>
        <button v-else class="save" @click="startSave">이 코스 저장하기</button>
      </div>
    </div>
  </div>
</template>
