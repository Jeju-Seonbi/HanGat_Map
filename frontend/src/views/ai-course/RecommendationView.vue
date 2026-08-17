<script setup lang="ts">import{ref}from'vue';import{usePlaces}from'../../composables/usePlaces';import{useTravelStore}from'../../app/stores/travel';import CongestionBadge from'../../components/common/CongestionBadge.vue';import MapRenderer from'../../components/map/MapRenderer.vue';const{places}=usePlaces(),store=useTravelStore(),day=ref(1),toast=ref('');function save(){store.saved=true;toast.value='코스를 저장했어요';setTimeout(()=>toast.value='',1800)}</script>
<template>
  <section class="page">
    <div class="result-hero">
      <span class="pill">✨ AI 분산 코스 · Mock 추천</span><h1>바람 따라, 한적한 동·서부 3일</h1><p>유명 명소는 덜 붐비는 시간으로 옮기고, 숨은 제주를 자연스럽게 이었어요.</p><div class="metrics">
        <div><small>평균 혼잡도</small><b>27 <span>여유</span></b></div><div><small>혼잡도 절감</small><b>38% ↓</b></div><div><small>예상 비용</small><b>286,000원</b></div><div><small>총 이동</small><b>4시간 20분</b></div>
      </div><div class="actions">
        <button
          class="btn primary"
          @click="save"
        >
          {{ store.saved?'저장됨 ✓':'이 코스 저장하기' }}
        </button><RouterLink
          class="btn ghost"
          to="/travel/search"
        >
          조건 수정
        </RouterLink>
      </div>
    </div><div class="tabs">
      <button
        v-for="d in 3"
        :key="d"
        :class="{active:day===d}"
        @click="day=d"
      >
        DAY {{ d }} <small>{{ ['동부의 숲과 오름','서부 바다와 마을','남쪽의 느린 하루'][d-1] }}</small>
      </button>
    </div><div class="result-layout">
      <div>
        <div class="day-head">
          <div><span class="eyebrow">DAY {{ day }}</span><h2>{{ ['숲에서 오름까지, 초록의 하루','바다 곁 마을을 천천히','남쪽의 고요한 풍경'][day-1] }}</h2></div><CongestionBadge
            level="RELAXED"
            :score="27"
          />
        </div><div class="timeline">
          <article
            v-for="(p,i) in places.slice(0,3)"
            :key="p.id"
          >
            <div class="time">
              {{ p.time }}<span>{{ i?`${20+i*5}분 이동`:'여행 시작' }}</span>
            </div><img
              :src="p.image"
              :alt="p.name"
            ><div class="timeline-body">
              <div class="row">
                <div><small>{{ p.region }} · {{ p.category }}</small><h3>{{ p.name }}</h3></div><CongestionBadge
                  :level="p.level"
                  :score="p.score"
                />
              </div><p>{{ p.description }}</p><div class="meta">
                <span>⏱ {{ p.stay }}</span><span>₩ {{ p.cost }}</span><span>🅿 주차</span><span>🚻 화장실</span>
              </div><div class="reason">
                ✨ 추천 이유 · 오전 동선과 날씨가 좋고 이동 거리가 짧아요.
              </div><RouterLink
                class="text-link"
                :to="`/places/${p.id}`"
              >
                장소 상세 보기 →
              </RouterLink>
            </div>
          </article>
        </div>
      </div><aside class="sticky-map">
        <MapRenderer
          :places="places.slice(0,3)"
          show-route
        /><p class="route-note">
          장소 간 추천 순서를 나타낸 선이며 실제 도로 경로와 다를 수 있습니다.
        </p><div class="panel compact">
          <b>코스 동선</b><p>비자림 → 성산일출봉 → 다랑쉬오름</p><RouterLink
            class="text-link"
            to="/courses/demo-course"
          >
            전체 코스 상세 →
          </RouterLink>
        </div>
      </aside>
    </div><section class="alternative panel">
      <span class="eyebrow">SMART ALTERNATIVE</span><h2>붐비는 성산, 이렇게 바꿔보세요</h2><div class="alt-grid">
        <div>
          <small>시간만 바꾸기</small><h3>11:20 → 08:10</h3><p>혼잡도 83에서 42로 감소 예상</p><button class="btn ghost">
            아침으로 변경
          </button>
        </div><div>
          <small>장소 바꾸기</small><h3>성산일출봉 → 다랑쉬오름</h3><p>비슷한 오름 풍경, 혼잡도 31</p><button class="btn primary">
            대체 장소 반영
          </button>
        </div>
      </div>
    </section><div
      v-if="toast"
      class="toast"
    >
      ✓ {{ toast }}
    </div>
  </section>
</template>
