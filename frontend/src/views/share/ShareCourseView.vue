<script setup>
/**
 * 공유된 코스 (요구사항 정의서 MY_003).
 *
 *  - 로그인하지 않아도 볼 수 있다
 *  - 일정 · 방문 장소 · 이동 경로 · 예상 소요 시간만 보여준다
 *    (예산 · 예상 비용 · 인원 · 소유자 정보는 서버 응답에 아예 담기지 않는다 — api/mypage.js)
 *  - 삭제되었거나 공유가 중지된 코스는 조회할 수 없다는 안내를 보여준다
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CrowdBadge from '../../components/common/CrowdBadge.vue'
import WeatherBadge from '../../components/common/WeatherBadge.vue'
import { getSharedCourse } from '../../api/mypage.js'
import { ApiError } from '../../api/errors.js'
import { fmt, fmtPeriod, fmtMinutes } from '../../utils/format.js'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const course = ref(null)

onMounted(async () => {
  try {
    course.value = await getSharedCourse(route.params.token)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '불러오지 못했어요'
  } finally {
    loading.value = false
  }
})

const totalStay = computed(() => {
  if (!course.value) return 0
  let sum = 0
  course.value.days.forEach(d => d.stops.forEach(s => { sum += s.stayMinutes || 0 }))
  return sum
})
</script>

<template>
  <main class="doc">
    <div class="doc-in narrow">
      <div v-if="loading" class="fl pane center">
        <p class="note">불러오는 중이에요…</p>
      </div>

      <div v-else-if="error" class="fl pane center">
        <div class="mark bad" aria-hidden="true">!</div>
        <h1>{{ error }}</h1>
        <p class="lead">공유가 중지되었거나 삭제된 코스일 수 있어요.</p>
        <RouterLink class="btn2 primary go" to="/course">내 코스 만들어 보기</RouterLink>
      </div>

      <template v-else-if="course">
        <div class="fl pane">
          <span class="bdg pink">공유된 코스</span>
          <h1>{{ course.courseName }}</h1>
          <p v-if="course.summary" class="lead">{{ course.summary }}</p>

          <dl class="meta">
            <div><dt>여행 기간</dt><dd>{{ fmtPeriod(course.startDate, course.endDate) }}</dd></div>
            <div><dt>이동수단</dt><dd>{{ course.transportLabel }}</dd></div>
            <div><dt>총 이동 시간</dt><dd class="tnum">{{ fmtMinutes(course.totalTravelMinutes) }}</dd></div>
            <div><dt>총 머무는 시간</dt><dd class="tnum">{{ fmtMinutes(totalStay) }}</dd></div>
          </dl>

          <p class="note priv">
            공유 화면에는 일정 · 방문 장소 · 이동 경로 · 예상 소요 시간만 보여요.
            예산과 개인 정보는 공유되지 않아요.
          </p>
        </div>

        <div v-for="d in course.days" :key="d.day" class="day">
          <div class="dayh">
            {{ fmt(d.date) }} · {{ d.day }}일차 <i />
            <WeatherBadge :kind="d.weather.kind" :t="d.weather.t" :size="15" />
          </div>
          <ul>
            <li v-for="(s, i) in d.stops" :key="i">
              <div v-if="i > 0 && s.travelMinutes" class="mv">↓ 차로 {{ s.travelMinutes }}분</div>
              <div class="stop">
                <div class="tm tnum">{{ s.time }}</div>
                <div>
                  <div class="hd">
                    <span class="nm">{{ s.name }}</span>
                    <CrowdBadge v-if="s.kind !== 'meal'" :value="s.crowd" />
                    <span v-else class="bdg pink">착한가격</span>
                  </div>
                  <p class="sub">
                    <template v-if="s.kind === 'meal'">{{ s.menu }}</template>
                    <template v-else>{{ s.category }} · {{ s.stayMinutes }}분 머물기 · {{ s.addr }}</template>
                  </p>
                </div>
              </div>
            </li>
          </ul>
        </div>

        <div class="fl cta-box">
          <p class="lead">이 코스가 마음에 드나요? 내 조건으로 다시 짜 볼 수 있어요.</p>
          <RouterLink class="cta" to="/course">내 조건으로 코스 만들기</RouterLink>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.narrow { max-width: 660px; }
.pane { padding: 24px; }
.pane.center { text-align: center; padding: 40px 24px; }
h1 { font-size: 20px; font-weight: 800; letter-spacing: -.04em; margin: 8px 0 6px; }
.lead { font-size: 12.5px; color: var(--tx2); line-height: 1.65; }

.mark {
  width: 48px; height: 48px; margin: 0 auto 12px; border-radius: 50%;
  font-size: 21px; font-weight: 800; display: flex; align-items: center; justify-content: center;
}
.mark.bad { background: var(--busy-bg); color: var(--busy); }
.go { display: inline-block; margin-top: 16px; }

.meta { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; margin: 14px 0 12px; }
.meta > div { display: flex; align-items: baseline; gap: 10px; padding: 7px 0; border-bottom: 1px solid var(--line); }
.meta dt { font-size: 11.5px; color: var(--tx3); font-weight: 600; width: 86px; flex-shrink: 0; }
.meta dd { font-size: 12.5px; font-weight: 600; }
.priv { background: var(--sky); border-radius: 12px; padding: 10px 13px; }

.dayh { font-size: 12px; font-weight: 800; color: var(--tx2); padding: 16px 3px 7px; display: flex; align-items: center; gap: 8px; }
.dayh i { flex: 1; height: 1px; background: var(--line); }
.stop { display: grid; grid-template-columns: 46px 1fr; gap: 8px; padding: 11px 12px; border-radius: 13px; background: var(--surf); border: 1px solid var(--line); }
.tm { font-size: 11px; color: var(--tx3); padding-top: 3px; font-weight: 600; }
.hd { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.nm { font-size: 14px; font-weight: 700; }
.sub { font-size: 11.5px; color: var(--tx3); margin-top: 4px; line-height: 1.5; }
.mv { font-size: 11px; color: var(--tx3); padding: 4px 0 4px 50px; }
.day ul { display: flex; flex-direction: column; }

.cta-box { margin-top: 22px; padding: 20px; text-align: center; }
.cta-box .cta { margin-top: 12px; display: block; }

@media (max-width: 640px) { .meta { grid-template-columns: 1fr; } }
</style>
