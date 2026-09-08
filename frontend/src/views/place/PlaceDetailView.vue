<script setup lang="ts">
/**
 * 장소 상세 페이지 (MAP_008).
 *
 * 이전 화면은 목업 데이터(data/data.ts)만 알아서 백엔드 id로 들어오면 항상 첫 목업 장소를
 * 보여줬고 운영시간·요금·예보가 하드코딩이었다. 여기서는 전부 백엔드에서 읽는다.
 *
 * 정직성 규칙
 * - 혼잡은 날짜 단위 예보뿐이다. 시간대(오전/오후) 표현을 만들지 않는다
 * - 예보 대상(345곳) 밖이면 "예보 없음"이라고 말한다. 0으로 채우지 않는다
 * - 요금은 착한가격업소만 검증가로 부르고, 나머지는 원천 문구 그대로 옮긴다
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PlaceDetailService, { type PlaceDetail, type PlaceForecast } from '../../services/PlaceDetailService'
import ReviewApiService, { type ReviewItem, absUrl } from '../../services/map/ReviewApiService'
import PlaceImage from '../../components/common/PlaceImage.vue'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import { congestionLabel, levelOf } from '../../utils/congestion'
import { levelLabel } from '../../data/data'
import { addCalendarDays, calendarDayOffset, fmt, todayKst } from '../../utils/format.js'

const route = useRoute()
const placeId = computed(() => Number(route.params.placeId))

const loading = ref(true)
const place = ref<PlaceDetail | null>(null)
const forecast = ref<PlaceForecast | null>(null)
const reviews = ref<ReviewItem[]>([])
const reviewTotal = ref(0)

const today = todayKst()

/** 오늘부터 7일. 예보가 없는 날짜는 rate를 null로 두고 화면이 "예보 없음"을 말한다 */
const week = computed(() => {
  const rows = Array.from({ length: 7 }, (_, i) => addCalendarDays(today, i))
  return rows.map(date => {
    const offset = forecast.value ? calendarDayOffset(forecast.value.from, date) : -1
    const rate = forecast.value && offset >= 0 ? forecast.value.rates[offset] ?? null : null
    return {
      date,
      label: fmt(date), // 9/9 (수) - 지도 슬라이더와 같은 표기
      rate: rate ?? null,
      level: levelOf(rate),
      label2: congestionLabel(rate),
    }
  })
})

const todayRate = computed(() => week.value[0]?.rate ?? null)
const todayLevel = computed(() => levelOf(todayRate.value))

/** 예보 창 안에서 가장 한산한 날. 창 밖 비교는 하지 않는다 */
const calmestDay = computed(() => {
  const rated = week.value.filter(day => day.rate != null)
  if (rated.length < 2) return null
  return rated.reduce((best, day) => (day.rate! < best.rate! ? day : best))
})

const cover = computed(() => place.value?.images?.[0] ?? null)
const address = computed(() => place.value?.roadAddress ?? place.value?.lotAddress ?? null)
const feeText = computed(() => {
  const p = place.value
  if (!p) return '정보 없음'
  if (p.useFeeText) return p.useFeeText
  return p.free ? '무료' : '정보 없음'
})

async function load () {
  loading.value = true
  place.value = null
  forecast.value = null
  reviews.value = []
  reviewTotal.value = 0

  const id = placeId.value
  if (!Number.isFinite(id)) {
    loading.value = false
    return
  }
  const detail = await PlaceDetailService.getDetail(id)
  place.value = detail
  loading.value = false
  if (!detail) return

  // 예보·후기는 상세가 떠 있는 동안 채운다 - 하나가 실패해도 나머지는 보여준다
  forecast.value = await PlaceDetailService.getForecast(id)
  try {
    const page = await ReviewApiService.list(id, 0)
    reviews.value = page.content.slice(0, 3)
    reviewTotal.value = page.totalElements
  } catch {
    reviews.value = []
  }
}

onMounted(load)
watch(placeId, load)
</script>

<template>
  <section
    v-if="loading"
    class="page place-state"
  >
    <h2>장소 정보를 불러오고 있어요.</h2>
  </section>

  <section
    v-else-if="!place"
    class="page place-state"
  >
    <h2>장소를 찾지 못했어요.</h2>
    <p class="muted">
      주소가 바뀌었거나 더 이상 제공하지 않는 장소예요.
    </p>
    <RouterLink
      class="btn primary"
      to="/map"
    >
      지도로 돌아가기
    </RouterLink>
  </section>

  <section
    v-else
    class="page place-page"
  >
    <div class="page-head">
      <div>
        <span class="eyebrow">{{ place.regionName ?? '제주' }} · {{ place.categoryName ?? '장소' }}</span>
        <h1>{{ place.name }}</h1>
        <p
          v-if="place.tagName"
          class="muted"
        >
          {{ place.tagName }}
        </p>
        <div class="place-badges">
          <CongestionBadge
            v-if="todayLevel"
            :level="todayLevel"
          />
          <span
            v-else
            class="chip"
          >오늘 예보 없음</span>
          <span
            v-if="place.goodPrice"
            class="chip good"
          >착한가격업소</span>
          <span
            v-if="place.hiddenGem"
            class="chip"
          >숨은 명소</span>
          <span
            v-if="place.reviewCount > 0 && place.ratingAvg != null"
            class="chip"
          >★ {{ place.ratingAvg.toFixed(1) }} · 후기 {{ place.reviewCount }}</span>
        </div>
      </div>
      <div class="actions">
        <RouterLink
          class="btn primary"
          :to="`/map?place=${place.id}`"
        >
          지도에서 보기
        </RouterLink>
      </div>
    </div>

    <div
      v-if="cover"
      class="place-cover"
    >
      <PlaceImage
        :src="cover.url"
        :alt="`${place.name} 사진`"
        eager
      />
      <small
        v-if="cover.attribution"
        class="muted"
      >사진: {{ cover.attribution }}</small>
    </div>

    <div class="panel place-facts">
      <div>
        <small>주소</small><b>{{ address ?? '정보 없음' }}</b>
      </div>
      <div>
        <small>운영시간</small><b>{{ place.operatingHoursText ?? '정보 없음' }}</b>
      </div>
      <div>
        <small>휴무</small><b>{{ place.restDayText ?? '정보 없음' }}</b>
      </div>
      <div>
        <small>{{ place.goodPrice ? '가격(검증가)' : '이용요금' }}</small><b>{{ feeText }}</b>
      </div>
      <div v-if="place.phone">
        <small>전화</small><b>{{ place.phone }}</b>
      </div>
      <div v-if="place.parkingAvailable != null">
        <small>주차</small><b>{{ place.parkingAvailable ? '가능' : '불가' }}</b>
      </div>
    </div>
    <p
      v-if="place.goodPrice"
      class="muted source-note"
    >
      착한가격업소 명단: 행정안전부{{ place.goodPriceBaseDate ? ` · ${place.goodPriceBaseDate} 기준` : '' }}
    </p>

    <section class="place-section">
      <h2>이번 주 혼잡 예보</h2>
      <p class="muted">
        한국관광공사 집중률 예보 · 날짜 단위예요. 시간대별 예측은 제공하지 않아요.
      </p>
      <div class="forecast-strip">
        <div
          v-for="day in week"
          :key="day.date"
          class="forecast-cell"
          :class="[day.level ? day.level.toLowerCase() : 'none', { best: calmestDay?.date === day.date }]"
        >
          <small>{{ day.date === today ? '오늘' : day.label }}</small>
          <b>{{ day.rate == null ? '-' : Math.round(day.rate) }}</b>
          <span>{{ day.label2 }}</span>
        </div>
      </div>
      <p
        v-if="calmestDay"
        class="muted"
      >
        이번 주 중에는 {{ calmestDay.date === today ? '오늘' : calmestDay.label }}이 가장 한산해요.
      </p>
      <p
        v-else
        class="muted"
      >
        이 장소는 집중률 예보 대상이 아니라 날짜별 예보가 없어요.
      </p>
    </section>

    <section
      v-if="place.overview"
      class="place-section"
    >
      <h2>소개</h2>
      <p class="place-overview">
        {{ place.overview }}
      </p>
      <small class="muted">한국관광공사 TourAPI</small>
    </section>

    <section class="place-section">
      <h2>방문 후기 {{ reviewTotal > 0 ? `(${reviewTotal})` : '' }}</h2>
      <ul
        v-if="reviews.length"
        class="review-list"
      >
        <li
          v-for="review in reviews"
          :key="review.id"
          class="panel review-item"
        >
          <div class="review-head">
            <b>{{ review.nickname ?? `여행자${review.userId}` }}</b>
            <span
              v-if="review.rating != null"
              class="muted"
            >★ {{ review.rating }}</span>
            <span
              v-if="review.congestionReport"
              class="chip"
            >{{ levelLabel[review.congestionReport] }} 제보</span>
          </div>
          <p v-if="review.content">
            {{ review.content }}
          </p>
          <div
            v-if="review.imageUrls.length"
            class="review-photos"
          >
            <img
              v-for="url in review.imageUrls.slice(0, 3)"
              :key="url"
              :src="absUrl(url)"
              alt="후기 사진"
            >
          </div>
        </li>
      </ul>
      <p
        v-else
        class="muted"
      >
        아직 후기가 없어요.
      </p>
      <RouterLink
        class="btn ghost"
        :to="`/map?place=${place.id}`"
      >
        후기 쓰기 · 전체 보기
      </RouterLink>
    </section>
  </section>
</template>

<style scoped>
.place-state{display:grid;gap:12px;justify-items:start}
.place-badges{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}
.chip{padding:4px 10px;border:1px solid var(--border);border-radius:999px;font-size:12px;color:var(--muted-text,var(--text))}
.chip.good{border-color:var(--primary);color:var(--primary)}
.place-cover{margin-top:18px;display:grid;gap:6px}
.place-cover :deep(.place-image){width:100%;max-height:360px;object-fit:cover;border-radius:16px}
.place-facts{margin-top:18px;display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px}
.place-facts small{display:block;color:var(--muted-text,var(--text));opacity:.75;font-size:12px;margin-bottom:4px}
.place-facts b{font-weight:600;line-height:1.4}
.source-note{margin-top:8px;font-size:12px}
.place-section{margin-top:32px;display:grid;gap:10px;justify-items:start}
.place-section h2{font-size:18px}
.forecast-strip{display:grid;grid-template-columns:repeat(7,1fr);gap:8px;width:100%}
.forecast-cell{display:grid;gap:2px;justify-items:center;padding:10px 4px;border:1px solid var(--border);border-radius:12px;text-align:center}
.forecast-cell small{font-size:11px;opacity:.8}
.forecast-cell b{font-size:18px}
.forecast-cell span{font-size:11px;opacity:.85}
.forecast-cell.quiet{border-color:var(--primary)}
.forecast-cell.crowded{opacity:.75}
.forecast-cell.none b{opacity:.5}
.forecast-cell.best{background:var(--muted)}
.place-overview{line-height:1.7;white-space:pre-line}
.review-list{display:grid;gap:12px;width:100%;list-style:none;padding:0;margin:0}
.review-item{display:grid;gap:8px}
.review-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.review-photos{display:flex;gap:8px}
.review-photos img{width:88px;height:88px;object-fit:cover;border-radius:10px}
@media (max-width:767px){
  .forecast-strip{grid-template-columns:repeat(4,1fr)}
}
</style>
