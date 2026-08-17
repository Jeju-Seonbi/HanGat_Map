<script setup lang="ts">import{ref}from'vue';import{useRouter}from'vue-router';import{useTravelStore}from'../../app/stores/travel';const store=useTravelStore(),router=useRouter(),loading=ref(false),error=ref('');const regions=['동부','서부','남부','북부'],styles=['자연','로컬','카페','액티비티','아이와','사진'];function toggle(list:string[],v:string){const i=list.indexOf(v);i<0?list.push(v):list.splice(i,1)}async function submit(){if(!store.condition.startDate||!store.condition.endDate||store.condition.startDate>store.condition.endDate){error.value='올바른 여행 날짜를 선택해주세요.';return}loading.value=true;setTimeout(()=>router.push('/recommendation'),700)}</script>
<template>
  <section class="form-page">
    <div class="page-intro">
      <span class="eyebrow">PLAN YOUR JEJU</span><h1>어떤 제주를 만나고 싶나요?</h1><p>여행 조건을 알려주면 혼잡을 피해 더 좋은 순간을 찾아드려요.</p>
    </div><div class="form-layout">
      <form
        class="panel travel-form"
        @submit.prevent="submit"
      >
        <div class="field-grid">
          <label>여행 시작일<input
            v-model="store.condition.startDate"
            type="date"
          ></label><label>여행 종료일<input
            v-model="store.condition.endDate"
            type="date"
          ></label><label>인원<input
            v-model.number="store.condition.people"
            type="number"
            min="1"
          ></label><label>전체 예산<input
            v-model.number="store.condition.budget"
            type="number"
            step="10000"
          ></label>
        </div><fieldset>
          <legend>가고 싶은 권역 <small>복수 선택</small></legend><div class="chips">
            <button
              v-for="r in regions"
              :key="r"
              type="button"
              :class="{active:store.condition.regions.includes(r)}"
              @click="toggle(store.condition.regions,r)"
            >
              {{ r }}
            </button>
          </div>
        </fieldset><fieldset>
          <legend>이동수단</legend><div class="chips">
            <button
              v-for="t in ['렌터카','대중교통','택시','도보·자전거']"
              :key="t"
              type="button"
              :class="{active:store.condition.transportation===t}"
              @click="store.condition.transportation=t"
            >
              {{ t }}
            </button>
          </div>
        </fieldset><fieldset>
          <legend>여행 스타일</legend><div class="chips">
            <button
              v-for="s in styles"
              :key="s"
              type="button"
              :class="{active:store.condition.styles.includes(s)}"
              @click="toggle(store.condition.styles,s)"
            >
              {{ s }}
            </button>
          </div>
        </fieldset><fieldset>
          <legend>혼잡도 선호</legend><div class="radio-cards">
            <label
              v-for="p in ['최대한 여유롭게','균형 있게','상관없어요']"
              :key="p"
            ><input
              v-model="store.condition.preference"
              type="radio"
              :value="p"
            ><span>{{ p }}</span></label>
          </div>
        </fieldset><label>꼭 가고 싶은 장소<input placeholder="예: 성산일출봉 (선택)"></label><label>피하고 싶은 장소<input placeholder="예: 대형 테마파크 (선택)"></label><p
          v-if="error"
          class="error"
        >
          {{ error }}
        </p><button
          class="btn primary wide"
          :disabled="loading"
        >
          {{ loading?'제주의 여유를 찾는 중...':'AI 코스 만들기 →' }}
        </button>
      </form><aside class="summary panel">
        <span class="eyebrow">MY TRIP</span><h2>{{ store.condition.people }}명이 떠나는 제주</h2><div class="summary-route">
          <div><small>시작</small><b>{{ store.condition.startDate }}</b></div><span>→</span><div><small>종료</small><b>{{ store.condition.endDate }}</b></div>
        </div><dl><dt>선택 권역</dt><dd>{{ store.condition.regions.join(' · ') }}</dd><dt>이동</dt><dd>{{ store.condition.transportation }}</dd><dt>예산</dt><dd>{{ store.condition.budget.toLocaleString() }}원</dd><dt>취향</dt><dd>{{ store.condition.styles.join(' · ') }}</dd></dl><div class="tip">
          🌿 <b>분산 여행 팁</b><p>동부와 서부를 하루에 오가기보다 권역별로 나누면 이동도, 혼잡도도 줄어요.</p>
        </div>
      </aside>
    </div>
  </section>
</template>
