<script setup>
/**
 * 이메일 인증 링크 진입 (USER_002).
 *
 * 토큰은 메일 링크로 오기 때문에 URL 에 실릴 수밖에 없다. 대신
 *  · 읽는 즉시 주소창에서 지운다 (히스토리·복사 유출 차단)
 *  · index.html 의 referrer=no-referrer 로 Referer 유출을 막는다
 *  · 서버에서 1회용 + 24시간 만료로 처리한다 (ASVS 6.4.1)
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import { useAuthStore } from '../../stores/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { stripQueryParams } from '../../utils/handoff.js'
import { ApiError } from '../../api/errors.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const state = ref('working')
const message = ref('')

onMounted(async () => {
  const token = String(route.query.token || '')
  stripQueryParams(['token'])

  if (!token) {
    state.value = 'fail'
    message.value = '인증 링크에 필요한 값이 없어요'
    return
  }
  try {
    await auth.verifyEmail(token)
    state.value = 'ok'
    ui.toast('이메일 인증을 마쳤어요')
    setTimeout(() => router.replace('/home'), 900)
  } catch (e) {
    state.value = 'fail'
    message.value = e instanceof ApiError ? e.message : '인증하지 못했어요'
  }
})
</script>

<template>
  <AuthLayout
    :title="state === 'working' ? '인증하는 중이에요' : state === 'ok' ? '인증을 마쳤어요' : '인증하지 못했어요'"
  >
    <p v-if="state === 'working'" class="body">잠시만 기다려 주세요.</p>

    <p v-else-if="state === 'ok'" class="body ok">
      메인 화면으로 이동할게요.
    </p>

    <template v-else>
      <p class="body">{{ message }}</p>
      <p class="body muted">
        링크는 한 번만 쓸 수 있고 24시간 뒤 만료돼요. 가입 화면에서 메일을 다시 받아주세요.
      </p>
    </template>

    <template v-if="state === 'fail'" #footer>
      <div class="acts">
        <RouterLink class="btn2" to="/login">로그인 화면으로</RouterLink>
        <RouterLink class="btn2 primary" to="/signup">다시 가입하기</RouterLink>
      </div>
    </template>
  </AuthLayout>
</template>

<style scoped>
.body { font-size: 13px; color: var(--tx2); line-height: 1.8; margin-bottom: 8px; }
.body.ok { color: var(--calm); font-weight: 600; }
.body.muted { font-size: 12px; color: var(--tx3); }
.acts { display: flex; gap: 8px; }
.acts > * { flex: 1; text-align: center; }
</style>
