<script setup>
/**
 * 가입 완료 안내 (USER_002).
 *
 * 이 화면은 **계정이 실제로 만들어졌는지 알려주지 않는다.**
 * 이미 가입된 주소로 시도했을 때와 새 계정을 만들었을 때가 같은 화면이다.
 * (OWASP Authentication Cheat Sheet — 계정 생성 시 올바른 응답:
 *  "A link to activate your account has been emailed to the address provided.")
 *
 * 이메일과 개발용 토큰은 URL 이 아니라 1회성 handoff 로 받는다.
 */
import { onMounted, ref } from 'vue'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import { resendVerification } from '../../api/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { maskEmail } from '../../utils/validators.js'
import { takeHandoff } from '../../utils/handoff.js'
import { ApiError } from '../../api/errors.js'

const IS_DEV = !!import.meta.env.DEV
const ui = useUiStore()

const email = ref('')
const token = ref('')
const devNote = ref('')
const busy = ref(false)
const error = ref('')

onMounted(() => {
  const data = takeHandoff('signup')
  if (data) {
    email.value = data.email || ''
    token.value = data.token || ''
    devNote.value = data.note || ''
  }
})

async function resend () {
  busy.value = true
  error.value = ''
  try {
    const res = await resendVerification(email.value)
    if (res.devOnlyVerifyToken) token.value = res.devOnlyVerifyToken
    ui.toast('메일을 다시 보냈어요')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '보내지 못했어요'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthLayout title="메일함을 확인해 주세요">
    <p class="body">
      <b>{{ email ? maskEmail(email) : '입력하신 주소' }}</b> 로 인증 링크를 보냈어요.<br>
      링크를 누르면 인증이 끝나고 바로 서비스로 들어가요.
    </p>
    <p class="body muted">
      링크는 24시간 뒤에 만료되고, 한 번 쓰면 다시 쓸 수 없어요.
    </p>

    <div v-if="IS_DEV" class="dev">
      <div class="lbl">개발 빌드 · 메일 서버 없음</div>
      <p v-if="devNote" class="note">{{ devNote }}</p>
      <RouterLink v-if="token" class="cta" :to="{ name: 'verify', query: { token } }">
        인증 링크 열기
      </RouterLink>
      <p v-else class="note">
        이 화면에 링크가 없으면 이미 인증됐거나, 이미 가입된 주소예요.
        어느 쪽인지는 화면에서 알려주지 않아요 — 계정 존재 여부가 새지 않게 하려는 거예요.
      </p>
    </div>

    <p v-if="error" class="srv" role="alert">{{ error }}</p>

    <template #footer>
      <div class="acts">
        <button class="btn2" :disabled="busy" @click="resend">
          {{ busy ? '보내는 중…' : '메일 다시 보내기' }}
        </button>
        <RouterLink class="btn2" to="/login">로그인 화면으로</RouterLink>
      </div>
    </template>
  </AuthLayout>
</template>

<style scoped>
.body { font-size: 13px; color: var(--tx2); line-height: 1.8; margin-bottom: 10px; }
.body b { color: var(--tx); font-weight: 800; }
.body.muted { font-size: 12px; color: var(--tx3); }

.dev { margin-top: 22px; padding-top: 16px; border-top: 1px solid var(--rule); }
.dev .note { font-size: 11.5px; color: var(--tx3); line-height: 1.7; margin: 6px 0; }
.dev .cta { display: block; text-align: center; margin-top: 12px; }

.srv {
  border-left: 2px solid var(--busy); padding: 3px 0 3px 12px;
  color: var(--busy); font-size: 12px; font-weight: 600; margin-top: 14px;
}
.acts { display: flex; gap: 8px; }
.acts > * { flex: 1; text-align: center; }
</style>
