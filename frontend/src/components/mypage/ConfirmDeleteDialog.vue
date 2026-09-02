<script setup>
/**
 * 2단계 삭제 확인.
 *
 * 요구사항 정의서:
 *   MY_002 — "삭제 버튼을 선택하면 코스명과 함께 확인 메시지를 표시 / 최종확인까지해야 제거가 된다"
 *   MY_005 — "리뷰 삭제 전 장소명과 리뷰 내용을 확인할 수 있는 삭제 확인 메시지를 표시"
 *
 * 1단계에서 대상 내용을 보여주고, 2단계에서 되돌릴 수 없다는 점을 다시 확인한다.
 */
import { ref } from 'vue'
import BaseModal from '../common/BaseModal.vue'

const props = defineProps({
  title: { type: String, required: true },
  subject: { type: String, required: true },   // 코스명 / 장소명
  detail: { type: String, default: '' },       // 리뷰 내용 등
  warning: { type: String, default: '' },
  confirmLabel: { type: String, default: '삭제' },
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'confirm'])

const step = ref(1)
</script>

<template>
  <BaseModal :title="step === 1 ? props.title : '정말 삭제할까요?'" @close="emit('close')">
    <template v-if="step === 1">
      <p class="sub">아래 항목을 삭제해요. 지운 뒤에는 되돌릴 수 없어요.</p>
      <div class="target">
        <div class="subj">{{ props.subject }}</div>
        <p v-if="props.detail" class="det">{{ props.detail }}</p>
      </div>
      <p v-if="props.warning" class="warn">{{ props.warning }}</p>
      <div class="acts">
        <button class="btn2" @click="emit('close')">취소</button>
        <button class="btn2 danger" @click="step = 2">{{ props.confirmLabel }}</button>
      </div>
    </template>

    <template v-else>
      <div class="target">
        <div class="subj">{{ props.subject }}</div>
      </div>
      <p class="sub">
        삭제하면 목록과 공유 화면에서 바로 사라져요. 마지막으로 한 번 더 확인해 주세요.
      </p>
      <div class="acts">
        <button class="btn2" :disabled="props.busy" @click="step = 1">뒤로</button>
        <button class="btn2 danger" :disabled="props.busy" @click="emit('confirm')">
          {{ props.busy ? '삭제하는 중…' : '네, 삭제할게요' }}
        </button>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.sub { font-size: 12.5px; color: var(--tx2); line-height: 1.65; margin-bottom: 12px; }
.sub b { font-weight: 800; color: var(--tx); }
.target { background: var(--surf2); border-radius: 13px; padding: 12px 14px; margin-bottom: 12px; }
.subj { font-size: 14px; font-weight: 800; letter-spacing: -.02em; }
.det {
  font-size: 12px; color: var(--tx2); margin-top: 6px; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.warn { font-size: 11.5px; color: var(--busy); font-weight: 600; margin-bottom: 12px; line-height: 1.6; }
.acts { display: flex; gap: 8px; }
.acts button { flex: 1; }
</style>
