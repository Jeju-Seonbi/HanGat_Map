<script setup>
/**
 * 폼 필드 (디자인.md §8.1).
 * 원본에는 검증 UI가 없어 새로 정의했고, 색은 원본 관례(경고=--busy, 성공=--calm)를 그대로 쓴다.
 */
import { computed, ref, useId } from 'vue'
import AppIcon from '../common/AppIcon.vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, required: true },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  required: { type: Boolean, default: false },
  error: { type: String, default: '' },
  hint: { type: String, default: '' },
  ok: { type: String, default: '' },
  autocomplete: { type: String, default: 'off' },
  disabled: { type: Boolean, default: false },
  maxlength: { type: Number, default: null },
  inputmode: { type: String, default: null },
  /**
   * date · number 전용 범위. 달력 UI 에서 아예 못 고르게 막는 용도다.
   * ⚠️ 브라우저 검사일 뿐이라 우회된다 — 실제 판정은 checkBirthDate / API 가 한다.
   */
  min: { type: [String, Number], default: null },
  max: { type: [String, Number], default: null },
  /**
   * 허용하지 않는 문자를 **입력 단계에서 지운다.**
   * 넘기면 매칭되는 문자가 값에 남지 않는다. (예: 이메일 = /[^A-Za-z0-9@.]/g)
   *
   * ⚠️ 이건 보안 장치가 아니다. DOM 은 콘솔·확장·자동화로 얼마든지 우회된다.
   *    실제 판정은 checkEmail / checkPassword / API 계층이 한다. 여기는 **UX** 다.
   *    "왜 안 되는지 모른 채 제출 버튼이 안 눌리는" 상황을 줄이는 게 목적이다.
   */
  filter: { type: RegExp, default: null },
  /** filter 가 문자를 지웠을 때 잠깐 띄울 안내 문구 */
  filterMessage: { type: String, default: '' },
  /** 입력창 왼쪽 아이콘 이름 (AppIcon). 시안의 `pl-10` 아이콘 자리 */
  icon: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'blur', 'enter', 'filtered'])

const uid = useId()
const inputId = computed(() => `f-${uid}`)
const msgId = computed(() => `m-${uid}`)

/* ── 입력 필터링 ── */

// 지워진 게 있을 때만 잠깐 보여 주는 안내. 사용자가 다시 입력하면 사라진다.
const dropped = ref('')
let dropTimer = null

/*
  한글 입력기(IME) 조합 중에는 필터를 걸지 않는다.
  조합 중간 글자('ㅎ', '하')를 지워 버리면 입력기가 깨져 글자를 아예 못 친다.
  조합이 끝나는 compositionend 에서 한 번에 거른다.
*/
const composing = ref(false)

function apply (el) {
  if (!props.filter) return el.value
  const before = el.value
  // 전역 정규식은 lastIndex 가 남으므로 매번 초기화한다 (안 하면 한 글자씩 건너뛴다)
  props.filter.lastIndex = 0
  const after = before.replace(props.filter, '')
  if (after === before) return before

  /*
    커서가 문자열 끝으로 튀지 않게 위치를 보정한다.

    ⚠️ try/catch 가 꼭 필요하다.
       `<input type="email">` · `number` · `date` 는 선택 API 를 지원하지 않아
       Chrome 에서 selectionStart 읽기와 setSelectionRange 호출이 InvalidStateError 를 던진다.
         "The input element's type ('email') does not support selection."
       감싸지 않으면 핸들러가 그 자리에서 죽어 emit 이 실행되지 않고,
       Vue 가 예전 modelValue 로 되돌려 **입력창이 통째로 비워진다.** (실제로 겪었다)
       커서 보정은 편의 기능이므로, 실패하면 조용히 포기하고 값만 반영한다.
  */
  let caret = null
  try {
    const pos = el.selectionStart ?? before.length
    props.filter.lastIndex = 0
    const removed = (before.slice(0, pos).match(props.filter) || []).length
    caret = Math.max(0, pos - removed)
  } catch { /* 선택 API 미지원 타입 — 커서 보정 생략 */ }

  el.value = after
  if (caret !== null) {
    try { el.setSelectionRange(caret, caret) } catch { /* 위와 같음 */ }
  }

  if (props.filterMessage) {
    dropped.value = props.filterMessage
    clearTimeout(dropTimer)
    dropTimer = setTimeout(() => { dropped.value = '' }, 2600)
  }
  emit('filtered', before)
  return after
}

function onInput (e) {
  if (composing.value) {
    // 조합 중에는 거르지 않고 값만 올린다
    emit('update:modelValue', e.target.value)
    return
  }
  emit('update:modelValue', apply(e.target))
}

function onCompositionEnd (e) {
  composing.value = false
  emit('update:modelValue', apply(e.target))
}

const message = computed(() => props.error || dropped.value || props.ok || props.hint)
const msgClass = computed(() => (props.error || dropped.value ? 'err' : props.ok ? 'ok' : ''))
</script>

<template>
  <div class="fld">
    <label class="fld-lab" :for="inputId">
      {{ props.label }}<span v-if="props.required" class="req" aria-hidden="true">*</span>
      <span v-if="props.required" class="sr-only">(필수)</span>
    </label>

    <div class="fld-box" :class="{ err: !!props.error }">
      <AppIcon v-if="props.icon" :name="props.icon" class="fld-ico" />
      <input
        :id="inputId"
        :type="props.type"
        :value="props.modelValue"
        :placeholder="props.placeholder"
        :autocomplete="props.autocomplete"
        :disabled="props.disabled"
        :maxlength="props.maxlength || undefined"
        :inputmode="props.inputmode || undefined"
        :min="props.min ?? undefined"
        :max="props.max ?? undefined"
        :aria-invalid="props.error ? 'true' : undefined"
        :aria-describedby="message ? msgId : undefined"
        @input="onInput"
        @compositionstart="composing = true"
        @compositionend="onCompositionEnd"
        @blur="$emit('blur')"
        @keydown.enter="$emit('enter')"
      >
      <slot name="suffix" />
    </div>

    <p v-if="message" :id="msgId" class="fld-msg" :class="msgClass">{{ message }}</p>
  </div>
</template>
