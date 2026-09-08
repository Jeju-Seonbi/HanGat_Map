<script setup lang="ts">
/**
 * 장소 상세 페이지 (MAP_008). 8월 UI 시안(한갓지도/frontend/src/pages/PlacePage.vue)의 2단 구성을 따른다.
 *
 * 좌: 사진 그리드 · 기본 정보 · 메뉴(착한가격업소)
 * 우: 날짜별 혼잡 예보 30일 · 추천 근거 · 날씨 · 후기
 *
 * 이전 화면은 목업 데이터(data/data.ts)만 알아서 백엔드 id로 들어오면 항상 첫 목업 장소를
 * 보여줬고 운영시간·요금·예보가 하드코딩이었다. 여기서는 전부 백엔드에서 읽는다.
 *
 * 정직성 규칙
 * - 혼잡은 날짜 단위 예보뿐이다. 시간대(오전/오후) 표현을 만들지 않는다
 * - 예보 대상(345곳) 밖이면 "예보 커버 밖"이라고 말한다. 0으로 채우지 않는다
 * - 날씨는 백엔드가 북부 격자만 주므로 그렇게 표기한다. 이 장소 권역인 척하지 않는다
 * - 가격은 착한가격업소만 검증가로 부르고 기준일을 함께 적는다
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PlaceDetailService, { type DayWeather, type PlaceDetail, type PlaceForecast } from '../../services/PlaceDetailService'
import ReviewApiService, { type ReviewItem, absUrl } from '../../services/map/ReviewApiService'
import { getBackendUserId } from '../../api/backendClient.js'
import PlaceImage from '../../components/common/PlaceImage.vue'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import { congestionLabel, levelOf } from '../../utils/congestion'
import { levelLabel } from '../../data/data'
import { addCalendarDays, fmt, todayKst } from '../../utils/format.js'

const route = useRoute()
const router = useRouter()
const placeId = computed(() => Number(route.params.placeId))

const loading = ref(true)
const place = ref<PlaceDetail | null>(null)
const forecast = ref<PlaceForecast | null>(null)
const weather = ref<DayWeather[]>([])
const reviews = ref<ReviewItem[]>([])
const reviewTotal = ref(0)
const reviewNotice = ref('')
const draftStars = ref(0)
const draftText = ref('')
const submitting = ref(false)

const today = todayKst()
const loggedIn = computed(() => getBackendUserId() != null)

/** 예보 30일. 발표 기준일이 오늘보다 앞설 수 있어 날짜는 from 기준으로 만든다 */
const series = computed(() => {
  const rows = forecast.value
  if (!rows) return []
  return rows.rates.map((rate, index) => {
    const date = addCalendarDays(rows.from, index)
    return { date, rate, level: levelOf(rate), label: fmt(date) }
  })
})

const todayCell = computed(() => series.value.find(day => day.date === today) ?? null)

/** 예보 창 안에서 가장 한산한 날. 창 밖과 비교하지 않는다 */
const calmestDay = computed(() => {
  const rows = series.value.filter(day => day.date >= today)
  return rows.length > 1 ? rows.reduce((best, day) => (day.rate < best.rate ? day : best)) : null
})

/**
 * 대표 사진은 두 갈래다. place_images(사진 적재 배치)가 있으면 그걸 쓰고,
 * 아직 안 돌린 장소는 목록·카드와 같은 firstimage(imageUrl)를 쓴다.
 */
const cover = computed(() => {
  const row = place.value
  if (!row) return null
  if (row.images?.length) return row.images[0]
  return row.imageUrl ? { url: row.imageUrl, thumbnailUrl: null, caption: null, attribution: null } : null
})
const thumbs = computed(() => place.value?.images?.slice(1, 3) ?? [])
const address = computed(() => place.value?.roadAddress ?? place.value?.lotAddress ?? null)
const feeText = computed(() => {
  const row = place.value
  if (!row) return '정보 없음'
  if (row.useFeeText) return row.useFeeText
  return row.free ? '무료' : '정보 없음'
})

/** 추천 근거 - 백엔드 MainService·AlternativeService 와 같은 우선순위를 화면에도 그대로 공개한다 */
const reasonChips = computed(() => {
  const row = place.value
  if (!row) return []
  const chips: { text: string; kind?: string }[] = []
  if (row.goodPrice) chips.push({ text: '착한가격업소 검증가', kind: 'good' })
  if (row.hiddenGem) chips.push({ text: '덜 알려진 숨은 명소' })
  const rate = todayCell.value?.rate
  chips.push(rate == null
    ? { text: '집중률 예보 대상 아님' }
    : { text: `오늘 집중률 ${Math.round(rate)} · ${congestionLabel(rate)}` })
  if (row.regionName) chips.push({ text: `${row.regionName} 권역` })
  return chips
})

const weatherToday = computed(() => weather.value[0] ?? null)

function skyIcon (day: DayWeather): string {
  const sky = day.sky ?? ''
  if (sky.includes('눈')) return '🌨️'
  if (sky.includes('비') || sky.includes('소나기') || (day.rainProb ?? 0) >= 60) return '🌧️'
  if (sky.includes('맑음')) return '☀️'
  return '☁️'
}

/** fmt() 는 "9/9 (수)" 라 요일만 떼어 쓴다 */
const dowOf = (iso: string) => fmt(iso).slice(fmt(iso).indexOf('(') + 1, -1)

async function loadReviews (id: number) {
  try {
    const page = await ReviewApiService.list(id, 0)
    reviews.value = page.content.slice(0, 5)
    reviewTotal.value = page.totalElements
  } catch {
    reviews.value = []
  }
}

async function load () {
  loading.value = true
  place.value = null
  forecast.value = null
  weather.value = []
  reviews.value = []
  reviewTotal.value = 0
  reviewNotice.value = ''

  const id = placeId.value
  if (!Number.isFinite(id)) {
    loading.value = false
    return
  }
  const detail = await PlaceDetailService.getDetail(id)
  place.value = detail
  loading.value = false
  if (!detail) return

  // 곁가지는 상세가 떠 있는 동안 채운다 - 하나가 실패해도 나머지는 보여준다
  const [forecastRow, weatherRows] = await Promise.all([
    PlaceDetailService.getForecast(id),
    PlaceDetailService.getWeather(),
  ])
  forecast.value = forecastRow
  weather.value = weatherRows
  await loadReviews(id)
}

async function submitReview () {
  if (!loggedIn.value) {
    reviewNotice.value = '후기를 쓰려면 로그인이 필요해요.'
    return
  }
  if (!draftStars.value && !draftText.value.trim()) {
    reviewNotice.value = '별점이나 내용 중 하나는 남겨 주세요.'
    return
  }
  const id = placeId.value
  submitting.value = true
  reviewNotice.value = ''
  try {
    await ReviewApiService.create(id, {
      rating: draftStars.value || null,
      content: draftText.value.trim() || null,
    })
    draftStars.value = 0
    draftText.value = ''
    await loadReviews(id)
    reviewNotice.value = '후기를 남겼어요.'
  } catch (error) {
    reviewNotice.value = error instanceof Error && error.message
      ? `후기를 남기지 못했어요. ${error.message}`
      : '후기를 남기지 못했어요.'
  } finally {
    submitting.value = false
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
      주소가 잘못되었거나 더 이상 제공하지 않는 장소예요.
    </p>
    <RouterLink
      class="btn primary"
      to="/map"
    >
      지도로 가기
    </RouterLink>
  </section>

  <section
    v-else
    class="page place-page"
  >
    <button
      class="back-link"
      @click="router.back()"
    >
      ← 뒤로
    </button>

    <div class="place-grid">
      <!-- 좌: 사진 · 기본 정보 · 메뉴 -->
      <div class="place-col">
        <div class="photo-grid">
          <PlaceImage
            class="photo-main"
            :src="cover?.url ?? '/images/placeholder.svg'"
            :alt="`${place.name} 사진`"
            eager
          />
          <PlaceImage
            v-for="thumb in thumbs"
            :key="thumb.url"
            :src="thumb.url"
            :alt="`${place.name} 사진`"
          />
          <div
            v-for="n in Math.max(0, 2 - thumbs.length)"
            :key="`blank-${n}`"
            class="photo-blank"
          >
            사진 없음
          </div>
        </div>
        <small
          v-if="cover?.attribution"
          class="muted"
        >사진: {{ cover.attribution }}</small>

        <div class="panel place-card">
          <div class="place-title-row">
            <h1>{{ place.name }}</h1>
            <RouterLink
              class="btn ghost"
              :to="`/map?place=${place.id}`"
            >
              지도에서 보기
            </RouterLink>
          </div>
          <p class="muted">
            {{ [place.categoryName, place.regionName, address].filter(Boolean).join(' · ') }}
          </p>
          <p
            v-if="place.reviewCount > 0 && place.ratingAvg != null"
            class="rating-row"
          >
            <b>★ {{ place.ratingAvg.toFixed(1) }}</b>
            <span class="muted">후기 {{ place.reviewCount }}</span>
          </p>

          <div class="chip-row">
            <CongestionBadge
              v-if="todayCell"
              :level="todayCell.level"
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
              v-if="place.parkingAvailable != null"
              class="chip"
            >{{ place.parkingAvailable ? '주차 가능' : '주차 불가' }}</span>
            <span
              v-if="place.toiletAvailable != null"
              class="chip"
            >{{ place.toiletAvailable ? '화장실 있음' : '화장실 없음' }}</span>
          </div>

          <dl class="fact-grid">
            <div>
              <dt>운영시간</dt>
              <dd>{{ place.operatingHoursText ?? '정보 없음' }}</dd>
            </div>
            <div>
              <dt>휴무</dt>
              <dd>{{ place.restDayText ?? '정보 없음' }}</dd>
            </div>
            <div>
              <dt>{{ place.goodPrice ? '가격(검증가)' : '이용요금' }}</dt>
              <dd>{{ feeText }}</dd>
            </div>
            <div v-if="place.phone">
              <dt>전화</dt>
              <dd>{{ place.phone }}</dd>
            </div>
          </dl>
        </div>

        <div
          v-if="place.overview"
          class="panel place-card"
        >
          <h2>{{ place.goodPrice ? '메뉴·가격' : '소개' }}</h2>
          <p class="overview">
            {{ place.overview }}
          </p>
          <small class="muted">
            {{ place.goodPrice
              ? `행정안전부 착한가격업소 등록가(검증가)${place.goodPriceBaseDate ? ` · ${place.goodPriceBaseDate} 기준` : ''}`
              : '한국관광공사 TourAPI' }}
          </small>
        </div>
      </div>

      <!-- 우: 예보 · 추천 근거 · 날씨 · 후기 -->
      <div class="place-col">
        <div
          v-if="series.length"
          class="panel place-card"
        >
          <div class="card-head">
            <h2>날짜별 혼잡 예보</h2>
            <CongestionBadge
              v-if="todayCell"
              :level="todayCell.level"
            />
          </div>
          <div class="bars">
            <div
              v-for="day in series"
              :key="day.date"
              class="bar"
              :class="day.level.toLowerCase()"
              :style="{ height: `${Math.max(4, day.rate)}%` }"
              :title="`${day.label} · 집중률 ${Math.round(day.rate)}`"
            />
          </div>
          <div class="bar-axis muted">
            <span>{{ series[0].label }}</span>
            <span>{{ series[Math.floor(series.length / 2)].label }}</span>
            <span>{{ series[series.length - 1].label }}</span>
          </div>
          <p
            v-if="calmestDay"
            class="calm-note"
          >
            앞으로는 {{ calmestDay.date === today ? '오늘' : calmestDay.label }}이 가장 한산해요.
          </p>
          <p class="muted source-note">
            향후 30일 · 날짜 단위(시간대 아님) · 한국관광공사 집중률 예보
          </p>
        </div>
        <div
          v-else
          class="panel place-card muted-card"
        >
          <h2>혼잡 예보 커버 밖</h2>
          <p class="muted">
            이 장소는 한국관광공사 집중률 예보 대상이 아니라 날짜별 예보가 없어요. 없는 값을 지어내지 않습니다.
          </p>
        </div>

        <div class="panel place-card">
          <h2>추천 근거</h2>
          <div class="chip-row">
            <span
              v-for="chip in reasonChips"
              :key="chip.text"
              class="chip"
              :class="chip.kind"
            >{{ chip.text }}</span>
          </div>
          <small class="muted">추천에 쓰는 근거를 그대로 공개해요.</small>
        </div>

        <div
          v-if="weatherToday"
          class="panel place-card"
        >
          <div class="card-head">
            <h2>제주 북부 날씨</h2>
            <span class="chip">기상청</span>
          </div>
          <div class="weather-now">
            <span class="weather-icon">{{ skyIcon(weatherToday) }}</span>
            <b>{{ weatherToday.maxTemp ?? '-' }}°</b>
            <span class="muted">{{ weatherToday.sky ?? '정보 없음' }}</span>
            <span
              v-if="(weatherToday.rainProb ?? 0) > 0"
              class="chip"
            >강수 {{ weatherToday.rainProb }}%</span>
          </div>
          <div class="weather-week">
            <div
              v-for="day in weather"
              :key="day.date"
              class="weather-day"
            >
              <small>{{ dowOf(day.date) }}</small>
              <span>{{ skyIcon(day) }}</span>
              <small>{{ day.minTemp ?? '-' }}~{{ day.maxTemp ?? '-' }}°</small>
              <small :class="{ rainy: (day.rainProb ?? 0) >= 60 }">{{ day.rainProb ?? '-' }}%</small>
            </div>
          </div>
          <small class="muted">기상청 단기·중기예보 · 현재 북부 권역 격자 기준이에요.</small>
        </div>

        <div class="panel place-card">
          <div class="card-head">
            <h2>후기 {{ reviewTotal > 0 ? `(${reviewTotal})` : '' }}</h2>
            <RouterLink
              class="text-link"
              :to="`/map?place=${place.id}`"
            >
              전체 보기 →
            </RouterLink>
          </div>
          <ul
            v-if="reviews.length"
            class="review-list"
          >
            <li
              v-for="review in reviews"
              :key="review.id"
            >
              <div class="review-head">
                <b>{{ review.nickname ?? `여행자${review.userId}` }}</b>
                <span
                  v-if="review.rating != null"
                  class="stars"
                >{{ '★'.repeat(review.rating) }}</span>
                <span
                  v-if="review.congestionReport"
                  class="chip"
                >{{ levelLabel[review.congestionReport] }} 제보</span>
                <small class="muted">{{ review.createdAt.slice(0, 10) }}</small>
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

          <form
            class="review-form"
            @submit.prevent="submitReview"
          >
            <div class="star-picker">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="star-btn"
                :class="{ on: n <= draftStars }"
                :aria-label="`별점 ${n}점`"
                @click="draftStars = n"
              >
                ★
              </button>
              <small class="muted">{{ draftStars ? `${draftStars}점` : '별점 선택' }}</small>
            </div>
            <textarea
              v-model="draftText"
              rows="3"
              placeholder="방문 후기를 남겨주세요"
            />
            <div class="form-foot">
              <small
                v-if="reviewNotice"
                class="muted"
              >{{ reviewNotice }}</small>
              <button
                class="btn primary"
                type="submit"
                :disabled="submitting"
              >
                {{ submitting ? '올리는 중...' : '등록' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.place-state{display:grid;gap:12px;justify-items:start}
.back-link{background:none;border:0;padding:0;margin-bottom:14px;color:var(--sub);font-weight:600}
.place-grid{display:grid;grid-template-columns:1.05fr .95fr;gap:20px;align-items:start}
.place-col{display:grid;gap:16px;align-content:start;min-width:0}
.photo-grid{display:grid;grid-template-columns:2fr 1fr;grid-template-rows:1fr 1fr;gap:8px;height:280px}
.photo-grid :deep(.place-image){width:100%;height:100%;object-fit:cover;border-radius:14px;background:var(--muted)}
.photo-grid :deep(.photo-main){grid-row:span 2}
.photo-blank{display:grid;place-items:center;border-radius:14px;background:var(--muted);color:var(--sub);font-size:12px}
.place-card{padding:20px;display:grid;gap:10px;justify-items:start}
.place-card h2{font-size:17px;margin:0}
.place-title-row{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%}
.place-title-row h1{font-size:clamp(1.6rem,3vw,2.1rem);margin:0}
.rating-row{display:flex;gap:8px;align-items:baseline;margin:0}
.chip-row{display:flex;flex-wrap:wrap;gap:6px}
.chip{padding:4px 10px;border:1px solid var(--border);border-radius:999px;font-size:12px;color:var(--sub)}
.chip.good{border-color:var(--primary);color:var(--primary)}
.fact-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;width:100%;margin:6px 0 0}
.fact-grid dt{font-size:12px;color:var(--sub);margin-bottom:3px}
.fact-grid dd{margin:0;font-weight:600;line-height:1.45}
.overview{line-height:1.7;white-space:pre-line;margin:0}
.card-head{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%}
.muted-card{background:var(--muted)}
.bars{display:flex;align-items:flex-end;gap:3px;height:128px;width:100%}
.bar{flex:1;border-radius:4px 4px 0 0;background:var(--border)}
.bar.quiet{background:var(--primary)}
.bar.normal{background:#f0b429}
.bar.crowded{background:#e05252}
.bar-axis{display:flex;justify-content:space-between;width:100%;font-size:11px}
.calm-note{margin:0}
.source-note{font-size:12px;margin:0;padding-top:10px;border-top:1px dashed var(--border);width:100%}
.weather-now{display:flex;align-items:center;gap:10px}
.weather-icon{font-size:26px}
.weather-now b{font-size:22px}
.weather-week{display:grid;grid-template-columns:repeat(7,1fr);gap:4px;width:100%}
.weather-day{display:grid;justify-items:center;gap:2px;padding:6px 2px;border-radius:10px;background:var(--muted);font-size:11px}
.weather-day .rainy{color:#2f6fb3;font-weight:700}
.review-list{list-style:none;padding:0;margin:0;display:grid;gap:14px;width:100%}
.review-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.review-head small{margin-left:auto}
.stars{color:#f0a92b;letter-spacing:-1px}
.review-photos{display:flex;gap:6px;margin-top:6px}
.review-photos img{width:72px;height:72px;object-fit:cover;border-radius:10px}
.review-form{width:100%;display:grid;gap:8px;padding-top:14px;border-top:1px solid var(--border)}
.star-picker{display:flex;align-items:center;gap:4px}
.star-btn{background:none;border:0;font-size:20px;color:var(--border);padding:0 1px}
.star-btn.on{color:#f0a92b}
.review-form textarea{width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:12px;resize:none;background:transparent}
.form-foot{display:flex;align-items:center;justify-content:flex-end;gap:10px}
@media (max-width:900px){
  .place-grid{grid-template-columns:1fr}
  .photo-grid{height:220px}
}
</style>
