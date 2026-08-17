<script setup>
/**
 * 로그인 (요구사항 정의서 USER_001).
 *
 * 보안 관련 결정
 *  · 실패 메시지는 **한 종류**로 통일한다 — 계정 존재 여부가 새지 않게 (ASVS 6.3.8)
 *  · 로그인 폼에서 **비밀번호 구성 규칙을 검사하지 않는다**.
 *    이전 정책으로 만든 비밀번호를 가진 회원이 로그인 자체를 못 하게 되기 때문.
 *  · 이메일은 사용자가 직접 기억하기를 켠 경우에만 다시 채운다.
 *  · 시도가 쌓이면 서버(목)가 응답을 늦추고, 일정 횟수를 넘기면 423 으로 잠근다.
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import FieldText from '../../components/auth/FieldText.vue'
import FieldPassword from '../../components/auth/FieldPassword.vue'
import BrandMark from '../../components/auth/BrandMark.vue'
import { useAuthStore } from '../../stores/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { readRecentLogins } from '../../api/auth.js'
import { isValidEmail, checkEmail, EMAIL_INPUT_FILTER, EMAIL_INPUT_MESSAGE } from '../../utils/validators.js'
import { TEST_ACCOUNT, resetDb } from '../../api/db.js'
import { ApiError } from '../../api/errors.js'
import { AUTH_HERO_IMAGES } from '../../data/authHeroImages.js'

// 데모 계정 안내를 띄울지 — 정의와 이유는 src/config.js 참고
import { SHOW_DEMO } from '../../config.js'

/**
 * 소셜 로그인은 **아직 없다.**
 * OAuth 는 서버(리다이렉트 URI 등록·시크릿 보관·토큰 교환)가 있어야 성립하고,
 * 이 저장소에는 서버가 없다. 요구사항 정의서에도 소셜 로그인 항목은 없다.
 * 버튼을 시안대로 두되, 눌리면 상태를 밝힌다.
 *
 * Apple 로그인은 요청에 따라 **아예 제거**했다.
 * (Apple 은 다른 소셜 로그인을 제공하는 iOS 앱에 Sign in with Apple 을 요구하지만,
 *  웹 전용 서비스에는 해당하지 않는다 — App Store Review Guideline 4.8)
 */
const SOCIALS = [
  { key: 'kakao', label: '카카오로 시작하기' },
  { key: 'google', label: '구글로 시작하기' }
]

function notReady (label) {
  ui.toast(`${label} 로그인은 아직 연결되지 않았어요`)
}

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const rememberEmail = ref(false)
const touched = ref({ email: false })
const serverError = ref('')
const locked = ref(false)

onMounted(() => {
  const first = readRecentLogins().find(r => r.email)
  if (first) {
    email.value = first.email
    rememberEmail.value = true
  }
})

const emailError = computed(() => {
  if (!touched.value.email) return ''
  if (!email.value.trim()) return '이메일을 입력해 주세요'
  // 허용 문자가 좁아진 만큼(@ . 만) 왜 막혔는지까지 알려준다
  return checkEmail(email.value).message
})

const canSubmit = computed(() =>
  isValidEmail(email.value) && !!password.value && !auth.loading
)

const redirectTo = computed(() => route.query.redirect || auth.returnTo || '/mypage/reviews')

async function submit () {
  touched.value.email = true
  serverError.value = ''
  locked.value = false
  if (!canSubmit.value) return
  try {
    await auth.login({
      email: email.value.trim(),
      password: password.value,
      rememberEmail: rememberEmail.value
    })
    ui.toast(`${auth.displayName}님, 반가워요`)
    const to = redirectTo.value
    auth.returnTo = null
    router.replace(to)
  } catch (e) {
    if (e instanceof ApiError) {
      serverError.value = e.message
      locked.value = e.status === 423 || e.status === 429
    } else {
      serverError.value = '로그인하지 못했어요. 잠시 뒤 다시 시도해 주세요'
    }
    password.value = ''
  }
}

async function loginDemo () {
  email.value = TEST_ACCOUNT.email
  password.value = TEST_ACCOUNT.password
  rememberEmail.value = false
  touched.value.email = false
  serverError.value = ''
  await submit()
}

/**
 * 데모 전용: 목 데이터를 처음 상태로 되돌린다.
 *
 * 브라우저에 남은 옛 시드 때문에 로그인이 안 되는 상황을 손으로 풀 수 있는 탈출구다.
 * (db.js 의 SEED_STAMP 가 이제 자동으로 잡지만, 시도 잠금·수정한 데이터까지
 *  한 번에 치우고 싶을 때가 있다.)
 */
function demoReset () {
  resetDb()
  serverError.value = ''
  locked.value = false
  password.value = ''
  ui.toast('데모 데이터를 처음 상태로 되돌렸어요')
}
</script>

<template>
  <AuthLayout
    title="환영합니다"
    lead="나만의 제주 여행 코스를 저장하고, 혼잡도를 피해 여유로운 여행을 계획해보세요."
    :hero-images="AUTH_HERO_IMAGES"
  >
    <p v-if="route.query.redirect" class="notice">
      로그인하면 보던 화면으로 돌아가요.
    </p>
    <p v-else-if="auth.endedReason === 'SESSION_REVOKED'" class="notice warn">
      보안을 위해 이전 세션을 모두 끊었어요. 다시 로그인해 주세요.
    </p>

    <form novalidate @submit.prevent="submit">
      <FieldText
        v-model="email"
        label="이메일"
        type="email"
        required
        placeholder="hangat@example.com"
        autocomplete="username"
        inputmode="email"
        :error="emailError"
        :filter="EMAIL_INPUT_FILTER"
        :filter-message="EMAIL_INPUT_MESSAGE"
        icon="mail"
        @blur="touched.email = true"
      />

      <FieldPassword
        v-model="password"
        label="비밀번호"
        autocomplete="current-password"
        @enter="submit"
      />

      <!-- 시안: 기억하기 체크박스와 비밀번호 찾기를 한 줄 양끝에 둔다 -->
      <div class="opts">
        <label class="remember">
          <input v-model="rememberEmail" type="checkbox">
          <span>이 기기에 이메일 기억하기</span>
        </label>
        <RouterLink to="/find-password" class="forgot">비밀번호 찾기</RouterLink>
      </div>

      <p v-if="serverError" class="srv" :class="{ hard: locked }" role="alert">{{ serverError }}</p>

      <button class="cta" type="submit" :disabled="!canSubmit">
        {{ auth.loading ? '확인하는 중…' : '이메일로 로그인' }}
      </button>
    </form>

    <!--
      시안에는 카카오·네이버·Apple 이 있었으나 요청에 따라 **카카오·구글 두 개만** 둔다.
      ⚠️ 둘 다 **연동 구현이 없다.** OAuth 는 서버(리다이렉트 URI·클라이언트 시크릿 검증)가
         있어야 성립하는데 이 저장소에는 서버가 없다.
         버튼만 두고 눌리면 "준비 중" 을 띄운다. 눌러도 아무 일이 없는 것보다는
         상태를 밝히는 편이 낫다고 봤다. 요구사항 정의서에도 소셜 로그인 항목은 없다.
    -->
    <div class="divider"><span>또는 소셜 로그인</span></div>
    <div class="socials">
      <div v-for="s in SOCIALS" :key="s.key" class="slot">
        <button
          class="social"
          :class="s.key"
          type="button"
          @click="notReady(s.label.replace('로 시작하기', ''))"
        >
          <BrandMark :name="s.key" />{{ s.label }}
        </button>
      </div>
    </div>

    <template #footer>
      <p class="links">
        아직 계정이 없으신가요? <RouterLink to="/signup">회원가입</RouterLink>
      </p>

      <div class="demo-entry">
        <p>가입하기 전에 저장 코스와 마이페이지를 먼저 둘러보세요.</p>
        <button class="demo-login" type="button" :disabled="auth.loading" @click="loginDemo">
          <span>{{ auth.loading ? '데모 계정 여는 중…' : '데모 계정으로 둘러보기' }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </div>

      <div v-if="SHOW_DEMO" class="demo">
        <div class="lbl">데모 빌드 · 화면 확인 도구</div>
        <p class="muted">
          로그인 차단 확인용: <code>suspended@</code>(이용 제한) · <code>withdrawn@</code>(탈퇴)
        </p>
        <p class="muted">
          로그인이 안 되면
          <button class="sw" type="button" @click="demoReset">데모 데이터 초기화</button>
        </p>
      </div>
    </template>
  </AuthLayout>
</template>

<style scoped>
.notice {
  border-left: 2px solid var(--ac);
  padding: 2px 0 2px 12px;
  font-size: 12px; color: var(--tx2); line-height: 1.55; margin-bottom: 20px;
}
.notice.warn { border-color: var(--busy); color: var(--busy); }

/* 시안: 기억하기 ↔ 비밀번호 찾기를 한 줄 양끝에 */
.opts {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin: -4px 0 22px;
}
.remember {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; color: var(--tx2); cursor: pointer;
}
.remember input { accent-color: var(--ac); width: 16px; height: 16px; }
.forgot {
  font-size: 13px; font-weight: 600; color: var(--ac);
  text-underline-offset: 4px;
}
.forgot:hover { text-decoration: underline; }

.socials { display: flex; flex-direction: column; gap: 10px; }
.slot { position: relative; }

.srv {
  border-left: 2px solid var(--busy);
  padding: 3px 0 3px 12px;
  color: var(--busy); font-size: 12px; font-weight: 600; line-height: 1.55;
  margin-bottom: 14px;
}
.srv.hard { background: var(--busy-bg); border-radius: 0 8px 8px 0; padding: 9px 12px; }

/* 시안: "아직 계정이 없으신가요? 회원가입" 을 가운데 한 줄로 */
.links { text-align: center; font-size: 14px; color: var(--tx2); }
.links a { color: var(--ac); font-weight: 700; text-underline-offset: 4px; }
.links a:hover { text-decoration: underline; }

.demo-entry {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--rule);
}
.demo-entry > p {
  margin-bottom: 10px;
  color: var(--tx3);
  font-size: 11.5px;
  line-height: 1.55;
  text-align: center;
}
.demo-login {
  width: 100%; min-height: 46px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 18px;
  border: 1px solid color-mix(in srgb, var(--ac) 34%, var(--rule));
  border-radius: var(--rp);
  background: var(--ac-bg);
  color: var(--ac-dk);
  font-family: var(--font-head);
  font-size: 13px; font-weight: 800;
  transition: border-color .18s ease, background .18s ease, transform .18s ease;
}
.demo-login:hover:not(:disabled) {
  border-color: var(--ac);
  background: color-mix(in srgb, var(--ac-bg) 72%, var(--bg));
  transform: translateY(-1px);
}
.demo-login:disabled { opacity: .58; cursor: wait; }

.demo { margin-top: 22px; padding-top: 16px; border-top: 1px solid var(--rule); }
.demo p { font-size: 11.5px; color: var(--tx2); line-height: 1.9; }
.demo .muted { color: var(--tx3); }
code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px; background: var(--surf2); padding: 2px 6px; border-radius: 5px;
}
.demo .sw { margin-left: 6px; }
</style>
