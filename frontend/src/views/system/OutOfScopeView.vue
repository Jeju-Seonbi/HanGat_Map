<script setup>
/**
 * 이 저장소가 맡지 않은 화면 안내.
 * 이번 작업 범위는 요구사항 정의서의 **회원(USER_001~003)** 과 **마이 페이지(MY_001~011)** 뿐이다.
 * 홈 · 지도 · AI 코스는 기존 프로토타입(../../index.html)이 맡고 있어 링크로 안내한다.
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const INFO = {
  '/home': {
    title: '메인 화면',
    ids: 'MAIN_001 ~ MAIN_004',
    desc: '오늘 한적한 장소 추천, 샘플 코스, 서비스 소개가 들어갈 자리예요.'
  },
  '/map': {
    title: '혼잡 지도',
    ids: 'MAP_001 ~ MAP_009',
    desc: '권역 필터, 장소 검색, 혼잡·날씨 레이어, 장소 상세와 후기, 찜하기가 들어갈 자리예요.'
  },
  '/course': {
    title: 'AI 코스',
    ids: 'COURSE_001 ~ COURSE_008',
    desc: '조건 입력, 코스 자동 생성, 대안 제시, 예산 게이지, 코스 저장이 들어갈 자리예요.'
  }
}

const info = computed(() => INFO[route.path] || INFO['/home'])
const place = computed(() => route.query.place || null)
</script>

<template>
  <main class="doc">
    <div class="doc-narrow">
      <div class="fl pane">
        <span class="bdg neutral">{{ info.ids }}</span>
        <h1>{{ info.title }}은(는) 아직 이 화면에 없어요</h1>
        <p class="lead">{{ info.desc }}</p>

        <p v-if="place" class="target">
          요청한 장소 ID: <b class="tnum">{{ place }}</b><br>
          지도 화면이 붙으면 이 값으로 장소 상세를 열게 돼요.
        </p>

        <div class="box">
          <div class="sect">지금 만들어진 범위</div>
          <ul class="ids">
            <li><b>회원</b> USER_001 로그인 · USER_002 회원가입 · USER_003 비밀번호 찾기</li>
            <li><b>마이 페이지</b> MY_001 ~ MY_011 (코스 · 리뷰 · 찜 · 알림 · 설정)</li>
          </ul>
          <p class="note mt">
            홈 · 지도 · AI 코스는 저장소 루트의 <code>index.html</code> 프로토타입에 들어 있어요.
          </p>
        </div>

        <div class="acts">
          <RouterLink class="btn2 primary" to="/mypage">마이페이지로</RouterLink>
          <RouterLink class="btn2" to="/login">로그인 화면으로</RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.pane { padding: 26px 24px 22px; }
h1 { font-size: 18px; font-weight: 800; letter-spacing: -.035em; margin: 10px 0 6px; }
.lead { font-size: 12.5px; color: var(--tx2); line-height: 1.65; margin-bottom: 16px; }
.target { background: var(--ac-bg); color: var(--ac-dk); border-radius: 12px; padding: 10px 13px; font-size: 12px; line-height: 1.6; margin-bottom: 14px; }
.box { background: var(--surf2); border-radius: 14px; padding: 14px 16px; }
.ids { display: flex; flex-direction: column; gap: 5px; font-size: 12px; color: var(--tx2); }
.ids b { font-weight: 800; color: var(--tx); margin-right: 4px; }
.mt { margin-top: 10px; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11.5px; background: var(--surf); padding: 1px 5px; border-radius: 5px; }
.acts { display: flex; gap: 8px; margin-top: 16px; }
.acts > * { flex: 1; text-align: center; }
</style>
