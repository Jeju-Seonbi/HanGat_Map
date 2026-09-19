<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import FixedSchedulePicker from './FixedSchedulePicker.vue'
import AccommodationSearch from './AccommodationSearch.vue'
import KakaoPlaceSearch from './KakaoPlaceSearch.vue'
import { findPreferenceConflict } from '../../services/placePreferenceService'
import type { AccommodationInput, CourseCondition, CourseStyle, KakaoPlaceSearchResult, PlacePreference, PreferenceType, RegionRef, Transport } from '../../assets/types/course'
import { courseDateError, courseDateWindow } from '../../services/course/courseDatePolicy'

const props = defineProps<{ initial: CourseCondition; loading: boolean }>()
const emit = defineEmits<{ submit: [condition: CourseCondition]; draft: [condition: CourseCondition] }>()

const cloneCondition = (value: CourseCondition) => JSON.parse(JSON.stringify(value)) as CourseCondition
const form = reactive<CourseCondition>(cloneCondition(props.initial))
const formElement = ref<HTMLFormElement>()
const step = ref(1)
const steps = ['일정', '권역', '취향', '필터']
const stepTitles = ['일정 & 동행', '선호 권역', '이동 & 취향', '장소 필터링', '여정 확인']
function goStep(value: number) { step.value = Math.max(1, Math.min(5, value)) }
const { minimum: minimumDate, maximum: maximumDate } = courseDateWindow()
watch(form, () => emit('draft', cloneCondition(form)), { deep: true })
const preferenceKey = (item: PlacePreference) => item.source_place_id ? `KAKAO:${item.source_place_id}` : `DB:${item.place_id ?? item.place_name}`
const fixedSchedules = reactive(new Set(form.course_place_preferences.filter(item => item.fixed_date || item.fixed_time).map(preferenceKey)))

const basicErrors = reactive({ dates: '', people: '' })
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

  basicErrors.dates = courseDateError(form.start_date, form.end_date, minimumDate)

  if (!Number.isFinite(Number(form.people)) || Number(form.people) < 1) basicErrors.people = '인원은 1명 이상 입력해 주세요.'
  if (!form.transport) selectionErrors.transport = '이동수단을 선택해 주세요.'
  if (!form.course_styles.length) selectionErrors.styles = '여행 스타일을 하나 이상 선택해 주세요.'

  validatePreferences()
  return !Object.values(basicErrors).some(Boolean)
    && !Object.values(selectionErrors).some(Boolean)
    && Object.keys(preferenceErrors).length === 0
}

function submit() {
  if (props.loading) return
  if (validate()) emit('submit', cloneCondition(form))
}

// Share the submit validator; input changes never move focus or scroll.
watch(form, validate, { deep: true, immediate: true })

const validationIssues = computed(() => {
  const issues: Array<{ target: string; message: string }> = []
  if (basicErrors.dates) issues.push({
    target: !form.start_date || form.start_date < minimumDate || form.start_date > maximumDate ? 'start-date' : 'end-date',
    message: basicErrors.dates,
  })
  if (basicErrors.people) issues.push({ target: 'people', message: basicErrors.people })
  if (selectionErrors.transport) issues.push({ target: 'transport', message: selectionErrors.transport })
  if (selectionErrors.styles) issues.push({ target: 'styles', message: selectionErrors.styles })
  for (const preference of [...wantPreferences.value, ...avoidPreferences.value]) {
    const key = preferenceKey(preference)
    if (preferenceErrors[key] && !issues.some(issue => issue.target === key)) {
      issues.push({ target: key, message: `${preference.place_name}: ${preferenceErrors[key]}` })
    }
  }
  return issues
})

async function focusFirstError() {
  const first = validationIssues.value[0]
  if (!first || props.loading) return
  step.value = ['start-date', 'end-date', 'people'].includes(first.target) ? 1
    : ['transport', 'styles'].includes(first.target) ? 3 : 4
  await nextTick()
  const field = Array.from(formElement.value?.querySelectorAll<HTMLElement>('[data-error-target]') ?? [])
    .find(element => element.dataset.errorTarget === first.target)
  if (!field) return
  const control = field.querySelector<HTMLElement>('.fixed-schedule-picker button:not([disabled])')
    ?? field.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled])')
    ?? field.querySelector<HTMLElement>('button:not([disabled])') ?? field
  control.focus({ preventScroll: true })
  field.scrollIntoView({ block: 'center', behavior: 'auto' })
}

const summary = computed(() => ({
  title: `${form.people >= 1 ? form.people : 0}명이 떠나는 제주`,
  dates: form.start_date && form.end_date ? `${form.start_date} → ${form.end_date}` : '날짜를 선택해 주세요',
  regions: form.course_regions.map(region => region.name).join(' · ') || '전체',
  transport: transports.find(([value]) => value === form.transport)?.[1] ?? '선택 전',
  styles: form.course_styles.map(style => style.name).join(' · ') || '선택 전',
  wants: wantPreferences.value.map(item => item.place_name).join(' · ') || '추가 전',
  avoids: avoidPreferences.value.map(item => item.place_name).join(' · ') || '추가 전',
  accommodation: form.accommodation?.place_name.trim() || '미정',
}))
</script>

<template>
  <form ref="formElement" class="course-builder stitch-builder" :class="`step-${step}`" novalidate :aria-busy="loading" @submit.prevent="submit">
    <div class="builder-progress">
      <p>{{ step }}단계 : {{ stepTitles[step - 1] }}</p>
      <nav aria-label="코스 조건 입력 단계"><button v-for="(label, i) in steps" :key="label" type="button" :class="{ active: step === i + 1, complete: step > i + 1 }" :aria-current="step === i + 1 ? 'step' : undefined" @click="goStep(i + 1)"><span>{{ i + 1 }}</span>{{ label }}</button></nav>
    </div>
    <div class="course-form-grid">
      <div class="condition-main">
        <section v-show="step === 1" class="condition-section basic-condition">
          <div class="section-title"><span>01</span><div><h2>여행 기본 정보</h2><p>여행 기간과 인원, 숙소를 선택해 주세요.</p></div></div>
          <div class="field-grid">
            <label data-error-target="start-date">여행 시작일<input v-model="form.start_date" type="date" :min="minimumDate" :max="maximumDate" :aria-invalid="!!basicErrors.dates" :aria-describedby="basicErrors.dates ? 'course-dates-error' : undefined"></label>
            <label data-error-target="end-date">여행 종료일<input v-model="form.end_date" type="date" :min="minimumDate" :max="maximumDate" :aria-invalid="!!basicErrors.dates" :aria-describedby="basicErrors.dates ? 'course-dates-error' : undefined"></label>
            <small class="field-span field-help">오늘부터 30일까지 혼잡 예보 범위로 선택할 수 있어요. 범위 안이어도 아직 적재되지 않은 예보는 '정보 없음'으로 표시돼요.</small>
            <p v-if="basicErrors.dates" id="course-dates-error" class="course-field-error field-span">{{ basicErrors.dates }}</p>
            <label data-error-target="people">인원<input v-model.number="form.people" type="number" min="1" :aria-invalid="!!basicErrors.people" :aria-describedby="basicErrors.people ? 'course-people-error' : undefined"><small v-if="basicErrors.people" id="course-people-error" class="course-field-error">{{ basicErrors.people }}</small></label>
            <AccommodationSearch class="field-span" :selected="form.accommodation" @select="selectAccommodation" @clear="clearAccommodation" />
          </div>
        </section>

        <section v-show="step === 2" class="condition-section region-step">
          <div class="section-title"><span>02</span><div><h2>선호 여행 권역</h2><p>여러 지역을 함께 선택할 수 있어요.</p></div></div>
          <div class="chips"><button type="button" :class="{ active: isAllRegions }" @click="selectAllRegions">전체</button><button v-for="region in regions" :key="region.region_id" type="button" :class="{ active: hasRegion(region.region_id) }" @click="toggleRegion(region)">{{ region.name }}</button></div>
        </section>

        <section v-show="step === 3" class="condition-section">
          <div class="section-title"><span>03</span><div><h2>이동수단</h2><p>여행 중 주로 이용할 수단을 하나 골라주세요.</p></div></div>
          <div class="course-radio" data-error-target="transport" role="group" aria-label="이동수단" :aria-describedby="selectionErrors.transport ? 'course-transport-error' : undefined"><label v-for="[value, label] in transports" :key="value"><input v-model="form.transport" type="radio" :value="value" :aria-invalid="!!selectionErrors.transport"><span>{{ label }}</span></label></div>
          <p v-if="selectionErrors.transport" id="course-transport-error" class="course-field-error section-field-error">{{ selectionErrors.transport }}</p>
        </section>

        <section v-show="step === 3" class="condition-section">
          <div class="section-title"><span>04</span><div><h2>여행 스타일</h2><p>내 취향에 가까운 키워드를 여러 개 골라주세요.</p></div></div>
          <div class="chips" data-error-target="styles" role="group" aria-label="여행 스타일" :aria-describedby="selectionErrors.styles ? 'course-styles-error' : undefined"><button v-for="style in styles" :key="style.tag_id" type="button" :class="{ active: hasStyle(style.tag_id) }" :aria-pressed="hasStyle(style.tag_id)" @click="toggleStyle(style)">{{ style.name }}</button></div>
          <p v-if="selectionErrors.styles" id="course-styles-error" class="course-field-error section-field-error">{{ selectionErrors.styles }}</p>
        </section>
      </div>

      <aside class="condition-summary">
        <div class="ticket-head"><span class="summary-kicker">선택한 여행 조건</span>
        <h2>{{ summary.title }}</h2>
        <p>나에게 맞는 한갓진 여정을 준비해요</p></div>
        <div class="summary-primary"><b>{{ summary.dates }}</b></div>
        <dl>
          <div><dt>선호 권역</dt><dd>{{ summary.regions }}</dd></div>
          <div><dt>이동</dt><dd>{{ summary.transport }}</dd></div>
          <div><dt>취향</dt><dd>{{ summary.styles }}</dd></div>
          <div><dt>꼭 가고 싶은 곳</dt><dd>{{ summary.wants }}</dd></div>
          <div><dt>피하고 싶은 곳</dt><dd>{{ summary.avoids }}</dd></div>
          <div><dt>숙소</dt><dd>{{ summary.accommodation }}</dd></div>
        </dl>
      </aside>
    </div>

    <div v-show="step === 4" class="preference-sections">
      <section class="preference-section">
        <div class="section-title"><span>05</span><div><h2>꼭 가고 싶은 장소 (선택)</h2><p>일정에 포함하고 싶은 장소를 여러 개 추가할 수 있어요. 비워 두어도 괜찮아요.</p></div></div>
        <KakaoPlaceSearch mode="GENERAL" placeholder="가고 싶은 장소를 검색해 주세요" loading-text="장소를 검색하고 있어요..." empty-text="제주에서 해당 장소를 찾지 못했어요." @query-change="preferenceInputErrors.WANT = ''" @select="selectPreference($event, 'WANT')" />
        <p v-if="preferenceInputErrors.WANT" class="course-field-error">{{ preferenceInputErrors.WANT }}</p>
        <div v-for="(preference, index) in wantPreferences" :key="preferenceKey(preference)" class="preference want-preference" :data-error-target="preferenceKey(preference)" :aria-describedby="preferenceErrors[preferenceKey(preference)] ? `course-want-error-${index}` : undefined" role="group" :aria-label="preference.place_name" tabindex="-1">
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
          <p v-if="preferenceErrors[preferenceKey(preference)]" :id="`course-want-error-${index}`" class="course-field-error">{{ preferenceErrors[preferenceKey(preference)] }}</p>
        </div>
      </section>

      <section class="preference-section">
        <div class="section-title"><span>06</span><div><h2>피하고 싶은 장소 (선택)</h2><p>추천에서 제외할 장소를 여러 개 추가할 수 있어요. 비워 두어도 괜찮아요.</p></div></div>
        <KakaoPlaceSearch mode="GENERAL" placeholder="피하고 싶은 장소를 검색해 주세요" loading-text="장소를 검색하고 있어요..." empty-text="제주에서 해당 장소를 찾지 못했어요." @query-change="preferenceInputErrors.AVOID = ''" @select="selectPreference($event, 'AVOID')" />
        <p v-if="preferenceInputErrors.AVOID" class="course-field-error">{{ preferenceInputErrors.AVOID }}</p>
        <div v-for="(preference, index) in avoidPreferences" :key="preferenceKey(preference)" class="preference compact-pref" :data-error-target="preferenceKey(preference)" role="group" :aria-label="preference.place_name" :aria-describedby="preferenceErrors[preferenceKey(preference)] ? `course-avoid-error-${index}` : undefined" tabindex="-1"><div><b>{{ preference.place_name }}</b><small v-if="preference.road_address || preference.address">{{ preference.road_address || preference.address }}</small><p v-if="preferenceErrors[preferenceKey(preference)]" :id="`course-avoid-error-${index}`" class="course-field-error">{{ preferenceErrors[preferenceKey(preference)] }}</p></div><button type="button" @click="removePreference(preference)">삭제</button></div>
      </section>
    </div>

    <div class="builder-navigation">
      <button type="button" :disabled="step === 1" @click="goStep(step - 1)">← 이전 단계</button>
      <button v-if="step < 4" type="button" class="next-step" @click="goStep(step + 1)">다음 단계 →</button>
      <button v-else-if="step === 4" type="button" class="next-step mobile-review" @click="goStep(5)">여정 확인 →</button>
      <span v-if="step === 4" class="desktop-helper">오른쪽 티켓에서 코스를 생성해 주세요</span>
    </div>
    <div class="course-form-footer">
      <div class="validation-feedback" aria-live="polite" aria-atomic="true">
        <template v-if="!loading && validationIssues.length">
          <p id="course-validation-summary">{{ validationIssues[0]?.message }}<span v-if="validationIssues.length > 1"> (외 {{ validationIssues.length - 1 }}개 오류)</span></p>
          <button type="button" class="validation-review" @click="focusFirstError">입력 확인하기</button>
        </template>
      </div>
      <button class="course-cta" :disabled="loading || validationIssues.length > 0" :aria-describedby="!loading && validationIssues.length ? 'course-validation-summary' : undefined">{{ loading ? '코스를 만들고 있어요…' : 'AI 코스 만들기' }}</button>
      <p class="ticket-note">생성 후 결과를 확인하고 마음에 들면 저장해 주세요.</p>
    </div>
  </form>
</template>

<style scoped>
.stitch-builder { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); column-gap: 32px; align-items: start; border: 0; background: transparent; box-shadow: none; overflow: visible; }
.course-form-grid { display: contents; }
.builder-progress { grid-column: 1; grid-row: 1; padding: 22px 24px; background: var(--course-surface); border: 1px solid var(--course-line); border-bottom: 0; border-radius: 24px 24px 0 0; }
.builder-progress p { font-size: 12px; font-weight: 700; color: var(--course-accent); padding-bottom: 16px; border-bottom: 1px solid var(--course-line); margin-bottom: 16px; }
.builder-progress nav { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.builder-progress button { text-align: left; padding-top: 10px; border-top: 5px solid var(--course-line); font-size: 12px; color: var(--course-muted); }
.builder-progress button.active, .builder-progress button.complete { border-color: var(--course-accent); color: var(--course-accent); }
.builder-progress button span { display: inline-grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; background: var(--course-surface-2); margin-right: 5px; }
.builder-progress button.active span { background: var(--course-accent); color: var(--course-on-accent, white); }
.condition-main, .preference-sections { grid-column: 1; grid-row: 2; background: var(--course-surface); border-inline: 1px solid var(--course-line); padding: 0 24px; min-height: 340px; min-width: 0; }
.condition-section { border: 0; padding: 20px 0; }
.step-4 .condition-main, .step-5 .condition-main { display: none; }
.section-title > span { display: none; }
.basic-condition .field-grid, .chips, .course-radio, .section-field-error { margin-left: 0; }
.field-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.section-title h2 { font-size: 18px; }
.region-step .chips { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; }
.region-step .chips button { min-height: 70px; font-size: 15px; }
.preference-sections { display: grid; grid-template-columns: 1fr; }
.preference-section { padding: 20px 0; border: 0; }
.preference-section + section { border-left: 0; border-top: 1px solid var(--course-line); }
.builder-navigation { grid-column: 1; grid-row: 3; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: var(--course-surface); border: 1px solid var(--course-line); border-top: 0; border-radius: 0 0 24px 24px; padding: 24px; }
.builder-navigation button { min-height: 42px; padding: 10px 16px; border: 1px solid var(--course-line); border-radius: 12px; font-size: 12px; }
.builder-navigation button:disabled { opacity: .4; }
.builder-navigation .next-step { color: white; background: #1f7a6d; }
.mobile-review { display: none; }
.desktop-helper { font-size: 11px; color: var(--course-muted); }
.condition-summary { grid-column: 2; grid-row: 1 / 3; position: static; border: 1px solid var(--course-line); border-bottom: 0; border-radius: 24px 24px 0 0; background: var(--course-surface); padding: 0 24px 16px; overflow: hidden; min-width: 0; }
.ticket-head { margin: 0 -24px 20px; padding: 24px; background: #1f7a6d; color: white; border-bottom: 10px solid #eff9f3; }
.ticket-head h2 { color: white; font-size: 22px; margin: 14px 0 8px; }
.ticket-head p { font-size: 12px; opacity: .85; }
.ticket-head .summary-kicker { color: white; background: #ffffff26; border-radius: 20px; padding: 5px 10px; font-size: 11px; }
.condition-summary dl { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; padding: 16px; border-radius: 16px; background: var(--course-surface-2); }
.condition-summary dl > div { display: block; border: 0; padding: 0; min-width: 0; }
.condition-summary dd { overflow-wrap: anywhere; text-align: left; margin: 4px 0 0; }
.course-form-footer { grid-column: 2; grid-row: 3; background: var(--course-surface); border: 1px solid var(--course-line); border-top: 0; border-radius: 0 0 24px 24px; padding: 0 24px 24px; }
.course-cta { width: 100%; min-width: 0; border-radius: 16px; }
.ticket-note { font-size: 11px; color: var(--course-muted); margin: 12px 0 0; }
@media(max-width: 1023px) {
  .stitch-builder { display: flex; flex-direction: column; gap: 0; }
  .builder-progress, .condition-main, .preference-sections, .builder-navigation, .condition-summary, .course-form-footer { width: 100%; }
  .condition-summary, .course-form-footer { display: none; }
  .step-5 .condition-summary, .step-5 .course-form-footer { display: block; }
  .step-5 .condition-summary { border-radius: 0; }
  .step-5 .builder-navigation { order: 5; border-radius: 0 0 24px 24px; }
  .step-5 .course-form-footer { border-radius: 0; }
  .desktop-helper { display: none; }
  .mobile-review { display: block; }
  .condition-main { min-height: 300px; }
}
@media(max-width: 480px) {
  .builder-progress, .condition-main, .preference-sections, .builder-navigation { padding-inline: 16px; }
  .field-grid { grid-template-columns: 1fr; }
}
[data-error-target] {
  scroll-margin-block: 100px calc(var(--mobile-tabbar-h, 0px) + 24px);
}
.course-form-footer {
  padding-bottom: calc(28px + var(--mobile-tabbar-h, 0px));
}
.validation-feedback {
  max-width: 440px;
  margin-inline: auto;
  color: var(--course-text);
  font-size: 0.85rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.validation-feedback p { margin: 0 0 8px; }
.validation-review {
  min-height: 44px;
  margin-bottom: 12px;
  padding: 8px 14px;
  border: 1px solid var(--course-line-2);
  border-radius: 10px;
  background: var(--course-surface-2);
  color: var(--course-text);
  font: inherit;
  cursor: pointer;
}
.validation-review:focus-visible,
[data-error-target]:focus-visible {
  outline: 2px solid var(--course-accent);
  outline-offset: 3px;
}
</style>
