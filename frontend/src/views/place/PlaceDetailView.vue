<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { usePlaces } from '../../composables/usePlaces'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import PlaceImage from '../../components/common/PlaceImage.vue'
import PlaceCard from '../../components/place/PlaceCard.vue'
import MapRenderer from '../../components/map/MapRenderer.vue'

const route = useRoute(),
  liked = ref(false),
  added = ref(false),
  mapSection = ref<HTMLElement>(),
  { places } = usePlaces()
const place = computed(
  () => places.value.find((p) => p.id === route.params.placeId) ?? places.value[0],
)
const alternatives = computed(() => places.value.filter((p) => p.id !== place.value.id).slice(0, 2))
const scrollToMap = () => mapSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })

// 날짜 단위 7일 혼잡 예보 목업 - 혼잡 데이터는 날짜 해상도까지만 존재한다 (시간대 예측 금지)
const DAY_OFFSETS = [0, -4, 6, 11, 3, -7, -2]
const weekForecast = computed(() =>
  DAY_OFFSETS.map((offset, i) => {
    const date = new Date()
    date.setDate(date.getDate() + i)
    return {
      label: i === 0 ? '오늘' : `${date.getMonth() + 1}/${date.getDate()}`,
      rate: Math.min(95, Math.max(5, place.value.score + offset)),
    }
  }),
)
const bestDay = computed(() =>
  weekForecast.value.reduce((best, d) => (d.rate < best.rate ? d : best)),
)
</script>

<template>
  <main class="place-page">
    <nav class="place-breadcrumb" aria-label="현재 위치">
      <RouterLink to="/">메인</RouterLink>
      <span aria-hidden="true">/</span>
      <RouterLink to="/map">장소</RouterLink>
      <span aria-hidden="true">/</span>
      <strong>{{ place.name }}</strong>
    </nav>

    <section class="place-hero-card">
      <div class="place-visual">
        <PlaceImage
          :src="place.imageUrl ?? place.image"
          :alt="`${place.name} 장소 사진`"
          eager
        />
        <span class="place-photo-label">{{ place.region }} · {{ place.category }}</span>
      </div>

      <div class="place-summary">
        <span class="place-kicker">한갓지게 머물기 좋은 곳</span>
        <h1>{{ place.name }}</h1>
        <p class="place-lead">{{ place.description }}</p>

        <div class="place-glance">
          <div>
            <small>현재 혼잡</small>
            <CongestionBadge :level="place.level" :score="place.score" />
          </div>
          <div>
            <small>권장 체류</small>
            <strong>{{ place.stay }}</strong>
          </div>
          <div>
            <small>예상 비용</small>
            <strong>{{ place.cost }}</strong>
          </div>
        </div>

        <div class="place-actions">
          <button
            type="button"
            class="btn place-primary"
            @click="added = !added"
          >
            {{ added ? '코스에 담았어요' : '코스에 담기' }}
          </button>
          <button
            type="button"
            :class="['btn place-secondary', { active: liked }]"
            :aria-pressed="liked"
            @click="liked = !liked"
          >
            {{ liked ? '찜했어요' : '찜하기' }}
          </button>
          <button type="button" class="btn place-secondary" @click="scrollToMap">
            지도 보기
          </button>
        </div>

        <p class="place-data-note">혼잡도와 날씨는 날짜 단위 시연 데이터예요.</p>
      </div>
    </section>

    <div class="place-content-grid">
      <div class="place-main-column">
        <section class="place-card-section place-forecast-card">
          <div class="place-section-head">
            <div>
              <span class="place-section-label">이번 주 혼잡 예보</span>
              <h2>{{ bestDay.label }}이 가장 여유로워요</h2>
            </div>
            <span class="place-best-chip">추천일 {{ bestDay.label }}</span>
          </div>
          <p class="place-section-copy">
            낮을수록 한산해요. 붐비는 날 대신 여유로운 날짜를 골라보세요.
          </p>
          <div class="place-forecast-strip">
            <article
              v-for="(d, index) in weekForecast"
              :key="d.label"
              :class="{ best: d.label === bestDay.label, today: index === 0 }"
              :style="`--forecast-rate:${d.rate}%`"
            >
              <small>{{ d.label }}</small>
              <strong>{{ d.rate }}</strong>
              <div class="place-forecast-track"><i /></div>
              <span>{{ d.label === bestDay.label ? '가장 여유' : d.rate < 40 ? '여유' : '보통' }}</span>
            </article>
          </div>
        </section>

        <section class="place-card-section place-story-card">
          <span class="place-section-label">이곳에서의 시간</span>
          <h2>서두르지 않아도 좋은 이유</h2>
          <p>
            {{ place.description }} 바람과 나무, 제주의 오래된 결을 천천히 느껴보세요.
            혼잡한 주요 관광지와는 다른 고요한 경험을 제공합니다.
          </p>
        </section>

        <section ref="mapSection" class="place-card-section place-map-section">
          <div class="place-section-head">
            <div>
              <span class="place-section-label">위치</span>
              <h2>지도에서 확인하기</h2>
            </div>
            <RouterLink class="place-map-link" :to="`/map?placeId=${place.id}`">
              큰 지도에서 보기 →
            </RouterLink>
          </div>
          <div class="place-map-frame">
            <MapRenderer :places="[place]" :selected-id="place.id" />
          </div>
          <p class="place-address">{{ place.address }}</p>
        </section>

        <section class="place-card-section place-reviews">
          <div class="place-section-head">
            <div>
              <span class="place-section-label">여행자 리뷰</span>
              <h2>먼저 다녀온 사람의 기록</h2>
            </div>
            <strong class="place-review-score">4.7 <small>/ 5</small></strong>
          </div>
          <article>
            <b>“아침에는 새소리만 들릴 만큼 고요했어요.”</b>
            <p>목요일 09:20 방문 · 체감 혼잡도 여유 · 대기 없음</p>
          </article>
        </section>
      </div>

      <aside class="place-side-column">
        <section class="place-card-section place-visit-card">
          <span class="place-section-label">방문 전 확인</span>
          <h2>필요한 정보만 모았어요</h2>
          <div class="place-facts-grid">
            <div><small>운영시간</small><strong>09:00 — 18:00</strong></div>
            <div><small>입장료</small><strong>{{ place.cost }}</strong></div>
            <div><small>오늘 날씨</small><strong>27°C · 맑음</strong></div>
            <div>
              <small>주차</small>
              <strong>{{ place.parkingAvailable ? '이용 가능' : '확인 필요' }}</strong>
            </div>
            <div>
              <small>화장실</small>
              <strong>{{ place.restroomAvailable ? '이용 가능' : '확인 필요' }}</strong>
            </div>
          </div>
        </section>

        <section class="place-card-section place-amenity-card">
          <span class="place-section-label">여행 편의</span>
          <h2>이런 여행에 맞아요</h2>
          <div class="place-amenities">
            <span v-for="tag in place.tags" :key="tag">{{ tag }}</span>
            <span>유아 동반</span>
            <span>반려동물 확인 필요</span>
          </div>
        </section>

        <section class="place-source-card">
          <b>데이터 출처</b>
          <p>장소 사진: 프로젝트 로컬 asset<br>혼잡도·날씨·리뷰: 시연용 Mock 데이터</p>
        </section>
      </aside>
    </div>

    <section class="place-alternatives">
      <span class="place-section-label">조금 더 한적한 선택</span>
      <div class="place-alternatives-head">
        <h2>비슷하지만 더 여유로운 곳</h2>
        <RouterLink to="/map">지도에서 더 찾아보기 →</RouterLink>
      </div>
      <div class="cards">
        <PlaceCard
          v-for="alternative in alternatives"
          :key="alternative.id"
          :place="alternative"
        />
      </div>
    </section>
  </main>
</template>
<style src="../../assets/place-detail.css"></style>
