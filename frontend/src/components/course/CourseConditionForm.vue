<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import FixedSchedulePicker from './FixedSchedulePicker.vue'
import AccommodationSearch from './AccommodationSearch.vue'
import KakaoPlaceSearch from './KakaoPlaceSearch.vue'
import { findPreferenceConflict } from '../../services/placePreferenceService'
import type { AccommodationInput, CourseCondition, CourseStyle, KakaoPlaceSearchResult, PlacePreference, PreferenceType, RegionRef, Transport } from '../../assets/types/course'

const props = defineProps<{ initial: CourseCondition; loading: boolean }>()
const emit = defineEmits<{ submit: [condition: CourseCondition] }>()

const cloneCondition = (value: CourseCondition) => JSON.parse(JSON.stringify(value)) as CourseCondition
const form = reactive<CourseCondition>(cloneCondition(props.initial))
const preferenceKey = (item: PlacePreference) => item.source_place_id ? `KAKAO:${item.source_place_id}` : `DB:${item.place_id ?? item.place_name}`
const fixedSchedules = reactive(new Set(form.course_place_preferences.filter(item => item.fixed_date || item.fixed_time).map(preferenceKey)))

const basicErrors = reactive({ dates: '', people: '', budget: '' })
const selectionErrors = reactive({ regions: '', transport: '', styles: '' })
const preferenceInputErrors = reactive<Record<PreferenceType, string>>({ WANT: '', AVOID: '' })
const preferenceErrors = reactive<Record<string, string>>({})

const regions: RegionRef[] = [
  { region_id: 1, code: 'EAST', name: '동부' },
  { region_id: 2, code: 'WEST', name: '서부' },
  { region_id: 3, code: 'SOUTH', name: '남부' },
  { region_id: 4, code: 'NORTH', name: '북부' },
]
const styles: CourseStyle[] = [
  { tag_id: 1, code: 'NATURE', name: '자연', weight: 1 },
  { tag_id: 2, code: 'LOCAL', name: '로컬', weight: 1 },
  { tag_id: 3, code: 'CAFE', name: '카페', weight: 1 },
  { tag_id: 4, code: 'ACTIVITY', name: '액티비티', weight: 1 },
  { tag_id: 5, code: 'WITH_KIDS', name: '아이와', weight: 1 },
  { tag_id: 6, code: 'PHOTO', name: '사진', weight: 1 },
]
const transports: [Transport, string][] = [
  ['RENTAL_CAR', '렌터카'],
  ['PUBLIC_TRANSIT', '대중교통'],
  ['TAXI', '택시'],
  ['WALK_BIKE', '도보·자전거'],
]

const wantPreferences = computed(() => form.course_place_preferences.filter(item => item.preference_type === 'WANT'))
const avoidPreferences = computed(() => form.course_place_preferences.filter(item => item.preference_type === 'AVOID'))
const isAllRegions = computed(() => form.course_regions.length === 0)
const hasRegion = (id: number) => form.course_regions.some(region => region.region_id === id)
const hasStyle = (id: number) => form.course_styles.some(style => style.tag_id === id)

function toggleRegion(region: RegionRef) {
  form.course_regions = hasRegion(region.region_id)
    ? form.course_regions.filter(item => item.region_id !== region.region_id)
    : [...form.course_regions, region]
  selectionErrors.regions = ''
}

function selectAllRegions() {
  form.course_regions = []
  selectionErrors.regions = ''
}

function toggleStyle(style: CourseStyle) {
  form.course_styles = hasStyle(style.tag_id)
    ? form.course_styles.filter(item => item.tag_id !== style.tag_id)
    : [...form.course_styles, style]
  selectionErrors.styles = ''
}

function selectAccommodation(accommodation: AccommodationInput) {
  form.accommodation = { ...accommodation }
}

function clearAccommodation() {
  delete form.accommodation
}

function selectPreference(place: KakaoPlaceSearchResult, preferenceType: PreferenceType) {
  preferenceInputErrors[preferenceType] = ''
  const conflict = findPreferenceConflict(form.course_place_preferences, place.source_place_id, preferenceType)
  const existing = form.course_place_preferences.find(item => item.source_code === place.source_code && item.source_place_id === place.source_place_id)
  if (conflict && existing) {
    preferenceInputErrors[preferenceType] = conflict === 'DUPLICATE'
      ? `이미 ${preferenceType === 'WANT' ? '꼭 가고 싶은' : '피하고 싶은'} 장소에 등록되어 있어요.`
      : `이미 ${existing.preference_type === 'WANT' ? '꼭 가고 싶은' : '피하고 싶은'} 장소로 등록된 곳이에요.`
    return
  }
  form.course_place_preferences.push({
    source_code: place.source_code,
    source_place_id: place.source_place_id,
    place_name: place.place_name,
    address: place.address,
    road_address: place.road_address,
    latitude: place.latitude,
    longitude: place.longitude,
    category_name: place.category_name,
    preference_type: preferenceType,
  })
}

function removePreference(preference: PlacePreference) {
  const key = preferenceKey(preference)
  form.course_place_preferences = form.course_place_preferences.filter(item => preferenceKey(item) !== key)
  fixedSchedules.delete(key)
  delete preferenceErrors[key]
  preferenceInputErrors.WANT = ''
  preferenceInputErrors.AVOID = ''
}

function toggleFixed(preference: PlacePreference) {
  const key = preferenceKey(preference)
  if (fixedSchedules.has(key)) {
    fixedSchedules.delete(key)
    delete preference.fixed_date
    delete preference.fixed_time
    delete preferenceErrors[key]
    return
  }
  fixedSchedules.add(key)
}

function updateFixedDate(preference: PlacePreference, value?: string) {
  if (value) preference.fixed_date = value
  else delete preference.fixed_date
  delete preferenceErrors[preferenceKey(preference)]
}

function updateFixedTime(preference: PlacePreference, value?: string) {
  if (value) preference.fixed_time = value
  else delete preference.fixed_time
  delete preferenceErrors[preferenceKey(preference)]
}

function clearErrors() {
  basicErrors.dates = ''
  basicErrors.people = ''
  basicErrors.budget = ''
  selectionErrors.regions = ''
  selectionErrors.transport = ''
  selectionErrors.styles = ''
  Object.keys(preferenceErrors).forEach(key => delete preferenceErrors[key])
}

function validatePreferences() {
  const seen = new Map<string, PlacePreference>()
  for (const preference of form.course_place_preferences) {
    const key = preferenceKey(preference)
    const existing = seen.get(key)
    if (existing) {
      const message = existing.preference_type === preference.preference_type
        ? '같은 장소가 중복 등록되어 있어요.'
        : '같은 장소를 꼭 갈 곳과 피할 곳에 동시에 등록할 수 없어요.'
      preferenceErrors[preferenceKey(existing)] = message
      preferenceErrors[key] = message
    } else {
      seen.set(key, preference)
    }

    if (preference.preference_type !== 'WANT') continue
    if (preference.fixed_time && !preference.fixed_date) {
      preferenceErrors[key] = '시간을 지정하려면 날짜도 선택해 주세요.'
    } else if (
      preference.fixed_date
      && form.start_date
      && form.end_date
      && (preference.fixed_date < form.start_date || preference.fixed_date > form.end_date)
    ) {
      preferenceErrors[key] = '고정 방문일은 여행 기간 안이어야 해요.'
    }
  }
}

function validate() {
  clearErrors()

  if (!form.start_date || !form.end_date) basicErrors.dates = '여행 시작일과 종료일을 모두 입력해 주세요.'
  else if (form.start_date > form.end_date) basicErrors.dates = '여행 종료일은 시작일보다 빠를 수 없어요.'

  if (!Number.isFinite(Number(form.people)) || Number(form.people) < 1) basicErrors.people = '인원은 1명 이상 입력해 주세요.'
  if (!Number.isFinite(Number(form.budget_total)) || Number(form.budget_total) <= 0) basicErrors.budget = '전체 예산을 1원 이상 입력해 주세요.'
  if (!form.transport) selectionErrors.transport = '이동수단을 선택해 주세요.'
  if (!form.course_styles.length) selectionErrors.styles = '여행 스타일을 하나 이상 선택해 주세요.'

  validatePreferences()
  return !Object.values(basicErrors).some(Boolean)
    && !Object.values(selectionErrors).some(Boolean)
    && Object.keys(preferenceErrors).length === 0
}

function submit() {
  if (validate()) emit('submit', cloneCondition(form))
}

const summary = computed(() => ({
  title: `${form.people >= 1 ? form.people : 0}명이 떠나는 제주`,
  dates: form.start_date && form.end_date ? `${form.start_date} → ${form.end_date}` : '날짜를 선택해 주세요',
  budget: form.budget_total > 0 ? `${form.budget_total.toLocaleString()}원` : '예산을 입력해 주세요',
  regions: form.course_regions.map(region => region.name).join(' · ') || '전체',
  transport: transports.find(([value]) => value === form.transport)?.[1] ?? '선택 전',
  styles: form.course_styles.map(style => style.name).join(' · ') || '선택 전',
  wants: wantPreferences.value.map(item => item.place_name).join(' · ') || '추가 전',
  avoids: avoidPreferences.value.map(item => item.place_name).join(' · ') || '추가 전',
  accommodation: form.accommodation?.place_name.trim() || '미정',
}))
</script>

<template>
  <form class="course-builder" novalidate @submit.prevent="submit">
    <div class="course-form-grid">
      <div class="condition-main">
        <section class="condition-section basic-condition">
          <div class="section-title"><span>01</span><div><h2>여행 기본 정보</h2><p>여행 기간과 인원, 전체 예산을 입력해 주세요.</p></div></div>
          <div class="field-grid">
            <label>여행 시작일<input v-model="form.start_date" type="date" @input="basicErrors.dates = ''"></label>
            <label>여행 종료일<input v-model="form.end_date" type="date" @input="basicErrors.dates = ''"></label>
            <p v-if="basicErrors.dates" class="course-field-error field-span">{{ basicErrors.dates }}</p>
            <label>인원<input v-model.number="form.people" type="number" min="1" @input="basicErrors.people = ''"><small v-if="basicErrors.people" class="course-field-error">{{ basicErrors.people }}</small></label>
            <label>전체 예산<input v-model.number="form.budget_total" type="number" min="1" step="10000" placeholder="원 단위" @input="basicErrors.budget = ''"><small v-if="basicErrors.budget" class="course-field-error">{{ basicErrors.budget }}</small></label>
            <AccommodationSearch class="field-span" :selected="form.accommodation" @select="selectAccommodation" @clear="clearAccommodation" />
          </div>
        </section>

        <section class="condition-section">
          <div class="section-title"><span>02</span><div><h2>선호 여행 권역</h2><p>여러 지역을 함께 선택할 수 있어요.</p></div></div>
          <div class="chips"><button type="button" :class="{ active: isAllRegions }" @click="selectAllRegions">전체</button><button v-for="region in regions" :key="region.region_id" type="button" :class="{ active: hasRegion(region.region_id) }" @click="toggleRegion(region)">{{ region.name }}</button></div>
        </section>

        <section class="condition-section">
          <div class="section-title"><span>03</span><div><h2>이동수단</h2><p>여행 중 주로 이용할 수단을 하나 골라주세요.</p></div></div>
          <div class="course-radio"><label v-for="[value, label] in transports" :key="value"><input v-model="form.transport" type="radio" :value="value" @change="selectionErrors.transport = ''"><span>{{ label }}</span></label></div>
          <p v-if="selectionErrors.transport" class="course-field-error section-field-error">{{ selectionErrors.transport }}</p>
        </section>

        <section class="condition-section">
          <div class="section-title"><span>04</span><div><h2>여행 스타일</h2><p>내 취향에 가까운 키워드를 여러 개 골라주세요.</p></div></div>
          <div class="chips"><button v-for="style in styles" :key="style.tag_id" type="button" :class="{ active: hasStyle(style.tag_id) }" @click="toggleStyle(style)">{{ style.name }}</button></div>
          <p v-if="selectionErrors.styles" class="course-field-error section-field-error">{{ selectionErrors.styles }}</p>
        </section>
      </div>

      <aside class="condition-summary">
        <span class="summary-kicker">MY TRIP</span>
        <h2>{{ summary.title }}</h2>
        <div class="summary-primary"><b>{{ summary.dates }}</b></div>
        <dl>
          <div><dt>선호 권역</dt><dd>{{ summary.regions }}</dd></div>
          <div><dt>이동</dt><dd>{{ summary.transport }}</dd></div>
          <div><dt>예산</dt><dd>{{ summary.budget }}</dd></div>
          <div><dt>취향</dt><dd>{{ summary.styles }}</dd></div>
          <div><dt>꼭 가고 싶은 곳</dt><dd>{{ summary.wants }}</dd></div>
          <div><dt>피하고 싶은 곳</dt><dd>{{ summary.avoids }}</dd></div>
          <div><dt>숙소</dt><dd>{{ summary.accommodation }}</dd></div>
        </dl>
      </aside>
    </div>

    <div class="preference-sections">
      <section class="preference-section">
        <div class="section-title"><span>05</span><div><h2>꼭 가고 싶은 장소</h2><p>일정에 포함하고 싶은 장소를 여러 개 추가할 수 있어요.</p></div></div>
        <KakaoPlaceSearch mode="GENERAL" placeholder="가고 싶은 장소를 검색해 주세요" loading-text="장소를 검색하고 있어요..." empty-text="제주에서 해당 장소를 찾지 못했어요." @query-change="preferenceInputErrors.WANT = ''" @select="selectPreference($event, 'WANT')" />
        <p v-if="preferenceInputErrors.WANT" class="course-field-error">{{ preferenceInputErrors.WANT }}</p>
        <div v-for="preference in wantPreferences" :key="preferenceKey(preference)" class="preference want-preference">
          <div class="preference-head"><b>{{ preference.place_name }}</b><button type="button" @click="removePreference(preference)">삭제</button></div>
          <small v-if="preference.road_address || preference.address">{{ preference.road_address || preference.address }}</small>
          <label class="fixed-toggle"><input type="checkbox" :checked="fixedSchedules.has(preferenceKey(preference))" @change="toggleFixed(preference)"><span>방문 일정 고정</span></label>
          <FixedSchedulePicker
            v-if="fixedSchedules.has(preferenceKey(preference))"
            :date="preference.fixed_date"
            :time="preference.fixed_time"
            :min-date="form.start_date"
            :max-date="form.end_date"
            @update:date="updateFixedDate(preference, $event)"
            @update:time="updateFixedTime(preference, $event)"
          />
          <p v-if="preferenceErrors[preferenceKey(preference)]" class="course-field-error">{{ preferenceErrors[preferenceKey(preference)] }}</p>
        </div>
      </section>

      <section class="preference-section">
        <div class="section-title"><span>06</span><div><h2>피하고 싶은 장소</h2><p>추천에서 제외할 장소를 여러 개 추가할 수 있어요.</p></div></div>
        <KakaoPlaceSearch mode="GENERAL" placeholder="피하고 싶은 장소를 검색해 주세요" loading-text="장소를 검색하고 있어요..." empty-text="제주에서 해당 장소를 찾지 못했어요." @query-change="preferenceInputErrors.AVOID = ''" @select="selectPreference($event, 'AVOID')" />
        <p v-if="preferenceInputErrors.AVOID" class="course-field-error">{{ preferenceInputErrors.AVOID }}</p>
        <div v-for="preference in avoidPreferences" :key="preferenceKey(preference)" class="preference compact-pref"><div><b>{{ preference.place_name }}</b><small v-if="preference.road_address || preference.address">{{ preference.road_address || preference.address }}</small></div><button type="button" @click="removePreference(preference)">삭제</button></div>
      </section>
    </div>

    <div class="course-form-footer">
      <button class="course-cta" :disabled="loading">{{ loading ? '코스를 만들고 있어요…' : 'AI 코스 만들기' }}</button>
    </div>
  </form>
</template>
